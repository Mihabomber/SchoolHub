package com.school.hub.feature.ai.engine

import com.school.hub.BuildConfig
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.emitAll
import android.graphics.Bitmap
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
    external fun nativeApplyTemplate(handle: Long, roles: Array<ByteArray>, contents: Array<ByteArray>, addAssistant: Boolean): ByteArray
    external fun nativeHasVision(): Boolean
    external fun nativeLoadVision(handle: Long, mmprojPath: String, nThreads: Int): Boolean
    external fun nativeMediaMarker(): String
    external fun nativeVisionTurn(handle: Long, prompt: ByteArray, rgb: ByteArray, w: Int, h: Int): Int
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
        val STOPS = listOf("<|eot_id|>", "<|im_end|>", "<end_of_turn>", "<|end|>", "<|endoftext|>", "<|user|>", "<|im_start|>", "<start_of_turn>user", "<turn|>", "<|turn>")
    }
}

/** Движок живёт в AppContainer: модель остаётся в памяти между экранами. */
class LlmEngine {
    private val dispatcher = Executors.newSingleThreadExecutor { r -> Thread(r, "llama").apply { priority = Thread.MAX_PRIORITY } }
        .asCoroutineDispatcher()

    private var handle = 0L
    private var format = ChatFormat.CHATML
    private var firstTurn = true
    /** История для встроенного чат-шаблона модели (role, text). */
    private val history = mutableListOf<Pair<String, String>>()
    private var useTemplate = false
    private var visionReady = false
    @Volatile private var stopRequested = false

    @Volatile var loadedModelId: String? = null
        private set

    /** Сколько запросов к модели сделано за время работы (для статистики). */
    var onRequest: (() -> Unit)? = null

    val isAvailable: Boolean get() = LlamaBridge.isAvailable
    /** В сборке есть зрение (libmtmd). */
    val visionBuilt: Boolean get() = isAvailable && runCatching { LlamaBridge.nativeHasVision() }.getOrDefault(false)
    val visionLoaded: Boolean get() = visionReady

    private val threads get() = (Runtime.getRuntime().availableProcessors() / 2).coerceIn(2, 4)

    suspend fun load(modelId: String, file: File, format: ChatFormat, contextSize: Int): Boolean = withContext(dispatcher) {
        if (!isAvailable) return@withContext false
        if (loadedModelId == modelId && handle != 0L) return@withContext true
        unloadInternal()
        if (!file.exists() || file.length() < 1_000_000) return@withContext false
        handle = LlamaBridge.nativeLoad(file.absolutePath, contextSize, threads)
        if (handle != 0L) {
            loadedModelId = modelId
            this@LlmEngine.format = format
            firstTurn = true
            history.clear()
            // Проверяем, умеет ли llama.cpp применить шаблон, зашитый в GGUF (нужно для новых моделей, напр. Gemma 4)
            useTemplate = applyTemplate(listOf("user" to "hi"), true).isNotEmpty()
        }
        handle != 0L
    }

    /** Подключить «глаза» модели (mmproj). */
    suspend fun loadVision(mmproj: File): Boolean = withContext(dispatcher) {
        if (handle == 0L || !visionBuilt || !mmproj.exists()) return@withContext false
        visionReady = LlamaBridge.nativeLoadVision(handle, mmproj.absolutePath, threads)
        visionReady
    }

    suspend fun unload() = withContext(dispatcher) { unloadInternal() }

    private fun unloadInternal() {
        if (handle != 0L) LlamaBridge.nativeFree(handle)
        handle = 0L
        loadedModelId = null
        visionReady = false
        history.clear()
    }

    suspend fun reset() = withContext(dispatcher) {
        if (handle != 0L) LlamaBridge.nativeReset(handle)
        firstTurn = true
        history.clear()
    }

    fun stop() { stopRequested = true }

    private fun applyTemplate(msgs: List<Pair<String, String>>, addAssistant: Boolean): String = runCatching {
        val r = msgs.map { it.first.toByteArray() }.toTypedArray()
        val c = msgs.map { it.second.toByteArray() }.toTypedArray()
        String(LlamaBridge.nativeApplyTemplate(handle, r, c, addAssistant), Charsets.UTF_8)
    }.getOrDefault("")

    /** Текст, который надо дописать в контекст для нового сообщения пользователя. */
    private fun buildDelta(system: String?, user: String, first: Boolean): String {
        if (!useTemplate) return format.turn(system, user, first)
        if (first) { history.clear(); if (!system.isNullOrBlank()) history += "system" to system }
        val before = if (history.isEmpty()) "" else applyTemplate(history, false)
        history += "user" to user
        val after = applyTemplate(history, true)
        return if (after.startsWith(before)) after.substring(before.length) else after
    }

    /** Поток накопленного текста ответа (каждое значение — весь ответ на данный момент). */
    fun generate(system: String?, user: String): Flow<String> = flow {
        check(handle != 0L) { "Модель не загружена" }
        stopRequested = false
        onRequest?.invoke()
        var rc = LlamaBridge.nativeStartTurn(handle, buildDelta(system, user, firstTurn).toByteArray(Charsets.UTF_8))
        if (rc == -1) {
            // Контекст переполнен — начинаем диалог заново, сохраняя системную подсказку
            LlamaBridge.nativeReset(handle)
            firstTurn = true
            rc = LlamaBridge.nativeStartTurn(handle, buildDelta(system, user, true).toByteArray(Charsets.UTF_8))
        }
        if (rc != 0) throw IllegalStateException(if (rc == -1) "Сообщение слишком длинное для этой модели" else "Ошибка движка ($rc)")
        firstTurn = false
        emitAll(stream())
    }.flowOn(dispatcher)

    /** Вопрос по картинке: модель по-настоящему «смотрит» на фото (нужен mmproj). */
    fun generateWithImage(system: String?, user: String, image: Bitmap): Flow<String> = flow {
        check(handle != 0L) { "Модель не загружена" }
        check(visionReady) { "Зрение модели не подключено" }
        stopRequested = false
        onRequest?.invoke()
        val scaled = scaleForVision(image)
        val w = scaled.width; val h = scaled.height
        val px = IntArray(w * h); scaled.getPixels(px, 0, w, 0, 0, w, h)
        val rgb = ByteArray(w * h * 3)
        for (i in px.indices) { val c = px[i]; rgb[i * 3] = (c shr 16).toByte(); rgb[i * 3 + 1] = (c shr 8).toByte(); rgb[i * 3 + 2] = c.toByte() }
        val content = LlamaBridge.nativeMediaMarker() + "\n" + user
        history.clear()
        if (!system.isNullOrBlank()) history += "system" to system
        history += "user" to content
        val prompt = if (useTemplate) applyTemplate(history, true) else format.turn(system, content, true)
        val rc = LlamaBridge.nativeVisionTurn(handle, prompt.toByteArray(Charsets.UTF_8), rgb, w, h)
        if (rc != 0) throw IllegalStateException(
            when (rc) { -1 -> "Картинка не влезла в память модели"; -3 -> "В этой сборке нет зрения"; else -> "Ошибка зрения ($rc)" },
        )
        firstTurn = false
        emitAll(stream())
    }.flowOn(dispatcher)

    private fun stream(): Flow<String> = flow {
        val sb = StringBuilder()
        var final = ""
        while (!stopRequested) {
            val bytes = LlamaBridge.nativeNext(handle) ?: break
            if (bytes.isEmpty()) continue
            sb.append(String(bytes, Charsets.UTF_8))
            val text = sb.toString()
            val stopAt = ChatFormat.STOPS.map { text.indexOf(it) }.filter { it >= 0 }.minOrNull()
            if (stopAt != null) { final = text.substring(0, stopAt).trim(); emit(final); break }
            final = text
            emit(text)
        }
        if (useTemplate) history += "assistant" to final
    }

    private fun scaleForVision(b: Bitmap): Bitmap {
        val max = 768
        val k = max.toFloat() / maxOf(b.width, b.height)
        val src = if (b.config == Bitmap.Config.ARGB_8888) b else b.copy(Bitmap.Config.ARGB_8888, false)
        return if (k >= 1f) src else Bitmap.createScaledBitmap(src, (b.width * k).toInt().coerceAtLeast(32), (b.height * k).toInt().coerceAtLeast(32), true)
    }

    fun systemInfo(): String = if (isAvailable) runCatching { LlamaBridge.nativeSystemInfo() }.getOrDefault("") else ""
}
