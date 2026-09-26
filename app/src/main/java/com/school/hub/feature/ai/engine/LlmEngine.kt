package com.school.hub.feature.ai.engine

import android.graphics.Bitmap
import com.school.hub.BuildConfig
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executors

object LlamaBridge {
    val isAvailable: Boolean by lazy {
        BuildConfig.WITH_LLAMA && runCatching {
            System.loadLibrary("schoolhub_llama"); true
        }.getOrDefault(false)
    }
    external fun nativeLoad(path: String, nCtx: Int, nThreads: Int): Long
    external fun nativeStartTurn(handle: Long, prompt: ByteArray): Int
    external fun nativeAbort(handle: Long)
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

enum class ChatFormat {
    LLAMA3 { override fun turn(system: String?, user: String, first: Boolean) = buildString { if (!first) append("<|eot_id|>") else append("<|begin_of_text|>"); if (!system.isNullOrBlank() && first) append("<|start_header_id|>system<|end_header_id|>\\n\\n$system<|eot_id|>"); append("<|start_header_id|>user<|end_header_id|>\\n\\n$user<|eot_id|><|start_header_id|>assistant<|end_header_id|>\\n\\n") } },
    CHATML { override fun turn(system: String?, user: String, first: Boolean) = (if (first && !system.isNullOrBlank()) "<|im_start|>system\\n$system<|im_end|>\\n" else "") + "<|im_start|>user\\n$user<|im_end|>\\n<|im_start|>assistant\\n" },
    GEMMA { override fun turn(system: String?, user: String, first: Boolean) = (if (first && !system.isNullOrBlank()) "$system\\n" else "") + "<start_of_turn>user\\n$user<end_of_turn>\\n<start_of_turn>model\\n" },
    PHI3 { override fun turn(system: String?, user: String, first: Boolean) = (if (first && !system.isNullOrBlank()) "<|system|>\\n$system<|end|>\\n" else "") + "<|user|>\\n$user<|end|>\\n<|assistant|>\\n" };
    abstract fun turn(system: String?, user: String, first: Boolean): String
    companion object { val STOPS = listOf("<|eot_id|>", "<|im_end|>", "<end_of_turn>", "<|end|>", "<|user|>", "<|im_start|>") }
}

class LlmEngine {
    private val dispatcher = Executors.newSingleThreadExecutor { r -> Thread(r, "llama").apply { priority = Thread.MAX_PRIORITY } }.asCoroutineDispatcher()
    private var handle = 0L
    private var format = ChatFormat.CHATML
    private var firstTurn = true
    private val history = mutableListOf<Pair<String, String>>()
    private var useTemplate = false
    private var visionReady = false
    private var loadedModelId: String? = null
    private var contextSize = 4096
    @Volatile private var stopRequested = false
    private var onRequest: (() -> Unit)? = null
    val isAvailable: Boolean get() = LlamaBridge.isAvailable
    val visionLoaded: Boolean get() = visionReady
    val threads: Int get() = (Runtime.getRuntime().availableProcessors() / 2).coerceIn(2, 4)

    suspend fun load(modelId: String, file: File, format: ChatFormat, contextSize: Int): Boolean = withContext(dispatcher) {
        if (!isAvailable || !file.exists() || file.length() < 1_000_000) return@withContext false
        unloadInternal(); this@LlmEngine.contextSize = contextSize
        handle = LlamaBridge.nativeLoad(file.absolutePath, contextSize, threads)
        if (handle == 0L) throw IllegalStateException("Не удалось загрузить модель. Проверьте файл и свободную память.")
        this@LlmEngine.format = format; loadedModelId = modelId; firstTurn = true; history.clear(); visionReady = false
        useTemplate = runCatching { LlamaBridge.nativeApplyTemplate(handle, arrayOf("user".toByteArray()), arrayOf("test".toByteArray()), true).isNotEmpty() }.getOrDefault(false)
        true
    }
    suspend fun loadVision(mmproj: File): Boolean = withContext(dispatcher) { if (handle == 0L || !mmproj.exists()) return@withContext false; visionReady = LlamaBridge.nativeLoadVision(handle, mmproj.absolutePath, threads); visionReady }
    suspend fun unload() = withContext(dispatcher) { unloadInternal() }
    private fun unloadInternal() { if (handle != 0L) LlamaBridge.nativeFree(handle); handle = 0L; loadedModelId = null; visionReady = false; history.clear(); firstTurn = true }
    suspend fun reset(): Boolean = withContext(dispatcher) { if (handle == 0L) return@withContext false; stopRequested = false; val ok = LlamaBridge.nativeReset(handle); if (!ok) throw IllegalStateException("Не удалось сбросить состояние модели."); history.clear(); firstTurn = true; ok }
    fun stop() { stopRequested = true; val h = handle; if (h != 0L) runCatching { LlamaBridge.nativeAbort(h) }; onRequest?.invoke() }
    private fun estimate(text: String) = kotlin.math.ceil(text.length / 3.5).toInt()
    private fun truncateMiddle(text: String, maxChars: Int): String { if (text.length <= maxChars) return text; val left = maxChars / 2; return text.take(left) + "…" + text.takeLast((maxChars - left - 1).coerceAtLeast(0)) }
    private fun fitContext(system: String?, user: String): String { val budget = (contextSize * .75).toInt() - 1024; var result = user; while (estimate(system.orEmpty()) + estimate(result) + history.sumOf { estimate(it.first) + estimate(it.second) } > budget && history.isNotEmpty()) history.removeAt(0); if (estimate(system.orEmpty()) + estimate(result) + history.sumOf { estimate(it.first) + estimate(it.second) } > budget) result = truncateMiddle(result, ((budget - estimate(system.orEmpty()) - history.sumOf { estimate(it.first) + estimate(it.second) }) * 3.5).toInt().coerceAtLeast(32)); return result }
    private fun buildDelta(system: String?, user: String, first: Boolean): String { if (useTemplate) { val msgs = history.map { it.first to it.second } + ("user" to user); return LlamaBridge.nativeApplyTemplate(handle, msgs.map { it.first.toByteArray() }.toTypedArray(), msgs.map { it.second.toByteArray() }.toTypedArray(), true).toString(Charsets.UTF_8) }; return format.turn(system, user, first) }
    fun generate(system: String?, user: String): Flow<String> = flow { check(handle != 0L) { "Модель не загружена" }; val safeUser = fitContext(system, user); stopRequested = false; val rc = LlamaBridge.nativeStartTurn(handle, buildDelta(system, safeUser, firstTurn).toByteArray()); if (rc != 0) throw IllegalStateException(if (rc == -1) "Слишком длинный запрос для контекста модели." else if (rc == -3) "Генерация остановлена." else "Не удалось обработать запрос модели."); val answer = StringBuilder(); while (!stopRequested) { val bytes = LlamaBridge.nativeNext(handle) ?: break; val part = bytes.toString(Charsets.UTF_8); answer.append(part); val stop = ChatFormat.STOPS.map { answer.indexOf(it) }.filter { it >= 0 }.minOrNull(); if (stop != null) { answer.setLength(stop); break }; emit(answer.toString()) }; if (!stopRequested && answer.isNotEmpty()) { history += safeUser to answer.toString(); firstTurn = false } }.flowOn(dispatcher)
    fun generateWithImage(system: String?, user: String, image: Bitmap): Flow<String> = flow { check(handle != 0L) { "Модель не загружена" }; check(visionReady) { "Зрение модели не загружено" }; stopRequested = false; val scaled = image; val rgb = ByteArray(scaled.width * scaled.height * 3); val pixels = IntArray(scaled.width * scaled.height); scaled.getPixels(pixels, 0, scaled.width, 0, 0, scaled.width, scaled.height); for (i in pixels.indices) { val c = pixels[i]; rgb[i * 3] = (c shr 16).toByte(); rgb[i * 3 + 1] = (c shr 8).toByte(); rgb[i * 3 + 2] = c.toByte() }; val prompt = (system.orEmpty() + "\n" + user).toByteArray(); val rc = LlamaBridge.nativeVisionTurn(handle, prompt, rgb, scaled.width, scaled.height); if (rc != 0) throw IllegalStateException("Не удалось обработать изображение моделью."); emitAll(stream()) }.flowOn(dispatcher)
    private fun stream(): Flow<String> = flow { val out = StringBuilder(); while (!stopRequested) { val b = LlamaBridge.nativeNext(handle) ?: break; out.append(b.toString(Charsets.UTF_8)); emit(out.toString()) } }
    fun systemInfo(): String = if (isAvailable) runCatching { LlamaBridge.nativeSystemInfo() }.getOrDefault("") else ""
}
