package com.school.hub.feature.ai.engine

import com.school.hub.BuildConfig
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executors

/** JNI-обёртка над llama.cpp (app/src/main/cpp/llama_jni.cpp). */
object LlamaBridge {
    val isAvailable: Boolean by lazy {
        BuildConfig.WITH_LLAMA && runCatching { System.loadLibrary("schoolhub_llama"); true }.getOrDefault(false)
    }

    external fun nativeLoad(path: String, nCtx: Int, nThreads: Int): Long
    external fun nativeStartTurn(handle: Long, prompt: ByteArray): Int
    external fun nativeNext(handle: Long): ByteArray?
    external fun nativeReset(handle: Long): Boolean
    external fun nativeFree(handle: Long)
    external fun nativeSystemInfo(): String
}

/** Формат чата для разных семейств моделей (свои спец-токены у каждой). */
enum class ChatFormat {
    LLAMA3 {
        override fun turn(system: String?, user: String, first: Boolean) = buildString {
            if (!first) append("<|eot_id|>")
            if (first && system != null) append("<|start_header_id|>system<|end_header_id|>\n\n$system<|eot_id|>")
            append("<|start_header_id|>user<|end_header_id|>\n\n$user<|eot_id|><|start_header_id|>assistant<|end_header_id|>\n\n")
        }
    },
    CHATML {
        override fun turn(system: String?, user: String, first: Boolean) = buildString {
            if (!first) append("<|im_end|>\n")
            if (first && system != null) append("<|im_start|>system\n$system<|im_end|>\n")
            append("<|im_start|>user\n$user<|im_end|>\n<|im_start|>assistant\n")
        }
    },
    GEMMA {
        override fun turn(system: String?, user: String, first: Boolean) = buildString {
            if (!first) append("<end_of_turn>\n")
            append("<start_of_turn>user\n")
            if (first && system != null) append("$system\n\n")
            append("$user<end_of_turn>\n<start_of_turn>model\n")
        }
    },
    PHI3 {
        override fun turn(system: String?, user: String, first: Boolean) = buildString {
            if (!first) append("<|end|>\n")
            if (first && system != null) append("<|system|>\n$system<|end|>\n")
            append("<|user|>\n$user<|end|>\n<|assistant|>\n")
        }
    };

    abstract fun turn(system: String?, user: String, first: Boolean): String

    companion object {
        val STOPS = listOf("<|eot_id|>", "<|im_end|>", "<end_of_turn>", "<|end|>", "<|endoftext|>", "<|user|>", "<|im_start|>")
    }
}

/** Движок живёт в AppContainer: модель остаётся в памяти между экранами. */
class LlmEngine {
    private val dispatcher = Executors.newSingleThreadExecutor { r -> Thread(r, "llama").apply { priority = Thread.MAX_PRIORITY } }
        .asCoroutineDispatcher()

    private var handle = 0L
    private var format = ChatFormat.CHATML
    private var firstTurn = true
    @Volatile private var stopRequested = false

    @Volatile var loadedModelId: String? = null
        private set

    val isAvailable: Boolean get() = LlamaBridge.isAvailable

    suspend fun load(modelId: String, file: File, format: ChatFormat, contextSize: Int): Boolean = withContext(dispatcher) {
        if (!isAvailable) return@withContext false
        if (loadedModelId == modelId && handle != 0L) return@withContext true
        unloadInternal()
        val threads = (Runtime.getRuntime().availableProcessors() - 2).coerceIn(2, 6)
        handle = LlamaBridge.nativeLoad(file.absolutePath, contextSize, threads)
        if (handle != 0L) {
            loadedModelId = modelId
            this@LlmEngine.format = format
            firstTurn = true
        }
        handle != 0L
    }

    suspend fun unload() = withContext(dispatcher) { unloadInternal() }

    private fun unloadInternal() {
        if (handle != 0L) LlamaBridge.nativeFree(handle)
        handle = 0L
        loadedModelId = null
    }

    suspend fun reset() = withContext(dispatcher) {
        if (handle != 0L) LlamaBridge.nativeReset(handle)
        firstTurn = true
    }

    fun stop() { stopRequested = true }

    /** Поток накопленного текста ответа (каждое значение — весь ответ на данный момент). */
    fun generate(system: String?, user: String): Flow<String> = flow {
        check(handle != 0L) { "Модель не загружена" }
        stopRequested = false
        var rc = LlamaBridge.nativeStartTurn(handle, format.turn(system, user, firstTurn).toByteArray(Charsets.UTF_8))
        if (rc == -1) {
            // Контекст переполнен — начинаем диалог заново, сохраняя системную подсказку
            LlamaBridge.nativeReset(handle)
            firstTurn = true
            rc = LlamaBridge.nativeStartTurn(handle, format.turn(system, user, true).toByteArray(Charsets.UTF_8))
        }
        if (rc != 0) throw IllegalStateException(if (rc == -1) "Сообщение слишком длинное для этой модели" else "Ошибка движка ($rc)")
        firstTurn = false

        val sb = StringBuilder()
        while (!stopRequested) {
            val bytes = LlamaBridge.nativeNext(handle) ?: break
            if (bytes.isEmpty()) continue
            sb.append(String(bytes, Charsets.UTF_8))
            val text = sb.toString()
            val stopAt = ChatFormat.STOPS.map { text.indexOf(it) }.filter { it >= 0 }.minOrNull()
            if (stopAt != null) { emit(text.substring(0, stopAt).trim()); break }
            emit(text)
        }
    }.flowOn(dispatcher)

    fun systemInfo(): String = if (isAvailable) runCatching { LlamaBridge.nativeSystemInfo() }.getOrDefault("") else ""
}
