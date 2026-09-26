package com.school.hub.feature.ai.models

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.StatFs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Resumable model transfer coordinator.
 * Incomplete files stay in .part files. A vision model has two files (main GGUF + mmproj GGUF);
 * the model is INSTALLED only after every file passed the GGUF and size checks.
 */
class ResumableModelDownloadController(
    private val context: Context,
    private val directory: File = (context.getExternalFilesDir("models")
        ?: File(context.filesDir, "models"))
) {
    enum class Status { NOT_INSTALLED, DOWNLOADING, PAUSED, WAITING_FOR_CONNECTION, VERIFYING, INSTALLED, CORRUPTED, ERROR }

    data class State(
        val status: Status = Status.NOT_INSTALLED,
        val downloadedBytes: Long = 0L,
        val totalBytes: Long = 0L,
        val speedBytesPerSecond: Long = 0L,
        val etaSeconds: Long? = null,
        val message: String? = null
    ) {
        val progress: Float get() = if (totalBytes <= 0L) 0f else (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f)
    }

    private class Piece(val url: String, val file: File, val expectedBytes: Long) {
        val part: File get() = File(file.path + ".part")
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = ConcurrentHashMap<String, Job>()
    private val states = MutableStateFlow<Map<String, State>>(emptyMap())
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    init { directory.mkdirs() }

    fun observe(): StateFlow<Map<String, State>> = states.asStateFlow()

    fun state(model: LlmModel): State = states.value[model.id] ?: initialState(model)

    fun start(model: LlmModel, expectedSha256: String? = null) {
        if (jobs[model.id]?.isActive == true) return
        val pending = pieces(model).filter { !it.file.exists() }
        if (pending.isEmpty()) {
            publish(model.id, initialState(model))
            return
        }
        val required = pending.sumOf { (it.expectedBytes - it.part.length()).coerceAtLeast(0L) } + 32L * 1024L * 1024L
        if (availableBytes(directory) < required) {
            publish(model.id, state(model).copy(status = Status.ERROR, message = "Недостаточно свободного места"))
            return
        }
        val job = scope.launch {
            try {
                transfer(model, expectedSha256)
            } catch (c: CancellationException) {
                // pause()/cancel()/delete() publish their own state.
            } catch (t: Throwable) {
                publish(model.id, state(model).copy(status = Status.ERROR, message = userMessage(t)))
            } finally {
                val self = coroutineContext[Job]
                if (jobs[model.id] === self) jobs.remove(model.id)
            }
        }
        jobs[model.id] = job
    }

    fun pause(model: LlmModel) {
        jobs.remove(model.id)?.cancel()
        publish(model.id, initialState(model))
    }

    fun cancel(model: LlmModel) {
        jobs.remove(model.id)?.cancel()
        pieces(model).forEach { it.part.delete() }
        publish(model.id, initialState(model))
    }

    fun delete(model: LlmModel) {
        jobs.remove(model.id)?.cancel()
        pieces(model).forEach { it.part.delete(); it.file.delete() }
        publish(model.id, initialState(model))
    }

    private fun pieces(model: LlmModel): List<Piece> {
        val list = mutableListOf(Piece(model.url, File(directory, model.fileName), mb(model.sizeMb)))
        val mmproj = model.mmprojUrl
        if (mmproj != null) list.add(Piece(mmproj, File(directory, model.mmprojFileName), mb(model.mmprojMb)))
        return list
    }

    private fun mb(value: Int): Long = value.toLong() * 1024L * 1024L

    private suspend fun transfer(model: LlmModel, sha256: String?) {
        val all = pieces(model)
        var before = 0L
        for ((index, piece) in all.withIndex()) {
            if (piece.file.exists()) {
                before += piece.file.length()
                continue
            }
            val rest = all.drop(index + 1).sumOf { if (it.file.exists()) it.file.length() else it.expectedBytes }
            val base = before
            publish(model.id, State(Status.DOWNLOADING, base + piece.part.length(), base + piece.expectedBytes + rest))
            var attempt = 0
            var serverTotal: Long?
            while (true) {
                try {
                    serverTotal = fetch(piece.url, piece.part) { done, total, speed ->
                        val size = if (total > 0L) total else piece.expectedBytes
                        val allDone = base + done
                        val allTotal = base + size + rest
                        val eta = if (speed > 0L) (allTotal - allDone).coerceAtLeast(0L) / speed else null
                        publish(model.id, State(Status.DOWNLOADING, allDone, allTotal, speed, eta))
                    }
                    break
                } catch (c: CancellationException) {
                    throw c
                } catch (t: Throwable) {
                    currentCoroutineContext().ensureActive()
                    if (!isConnected()) {
                        publish(model.id, state(model).copy(status = Status.WAITING_FOR_CONNECTION, message = "Ожидание сети…"))
                        delay(5_000L)
                    } else {
                        attempt++
                        if (attempt >= MAX_ATTEMPTS) throw t
                        delay((1000L * (1L shl attempt.coerceAtMost(5))).coerceAtMost(30_000L))
                    }
                    publish(model.id, state(model).copy(status = Status.DOWNLOADING, message = "Возобновление…"))
                }
            }
            publish(model.id, state(model).copy(status = Status.VERIFYING, message = "Проверка файла…"))
            val strict = serverTotal != null && serverTotal > 0L
            val expected = if (strict) serverTotal!! else piece.expectedBytes
            if (!verify(piece.part, expected, strict, if (index == 0) sha256 else null)) {
                piece.part.delete()
                publish(model.id, State(Status.CORRUPTED, message = "Файл модели повреждён. Скачайте заново"))
                return
            }
            if (!piece.part.renameTo(piece.file)) throw IllegalStateException("Не удалось сохранить файл модели")
            before += piece.file.length()
        }
        publish(model.id, initialState(model))
    }

    /** Returns the full file size reported by the server, or null when unknown. */
    private suspend fun fetch(url: String, part: File, report: (Long, Long, Long) -> Unit): Long? {
        val offset = part.length()
        val request = Request.Builder().url(url).header("User-Agent", "SchoolHub/2.1").apply {
            if (offset > 0L) header("Range", "bytes=$offset-")
        }.build()
        client.newCall(request).execute().use { response ->
            if (response.code == 416) {
                return response.header("Content-Range")?.substringAfter('/')?.toLongOrNull()
                    ?: offset.takeIf { it > 0L }
            }
            if (!response.isSuccessful) throw IllegalStateException("Сервер вернул ошибку ${response.code}")
            val body = response.body ?: throw IllegalStateException("Пустой ответ сервера")
            val append = response.code == 206 && offset > 0L
            if (!append) part.delete()
            val start = if (append) offset else 0L
            val total = response.header("Content-Range")?.substringAfter('/')?.toLongOrNull()
                ?: body.contentLength().takeIf { it >= 0L }?.let { start + it }
            var done = start
            var lastTime = System.nanoTime()
            var lastBytes = done
            FileOutputStream(part, append).use { output ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(256 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        done += count
                        val now = System.nanoTime()
                        if (now - lastTime >= 500_000_000L) {
                            val speed = ((done - lastBytes) * 1_000_000_000L / (now - lastTime)).coerceAtLeast(0L)
                            report(done, total ?: -1L, speed)
                            lastTime = now
                            lastBytes = done
                        }
                    }
                }
            }
            if (total != null && done < total) throw IllegalStateException("Соединение прервано")
            return total
        }
    }

    private fun verify(file: File, expected: Long, strict: Boolean, sha256: String?): Boolean {
        if (!file.exists() || file.length() < 4L) return false
        FileInputStream(file).use { input ->
            val magic = ByteArray(4)
            if (input.read(magic) != 4 || String(magic, Charsets.US_ASCII) != "GGUF") return false
        }
        if (strict) {
            if (file.length() != expected) return false
        } else if (expected > 0L && file.length() < expected / 2L) {
            // Catalog sizes are approximate; only reject clearly truncated files.
            return false
        }
        if (sha256 == null) return true
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(256 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }.equals(sha256, ignoreCase = true)
    }

    private fun isConnected(): Boolean {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun initialState(model: LlmModel): State {
        val all = pieces(model)
        val have = all.sumOf { if (it.file.exists()) it.file.length() else it.part.length() }
        val total = all.sumOf { if (it.file.exists()) it.file.length() else it.expectedBytes }
        return when {
            all.all { it.file.exists() } -> State(Status.INSTALLED, have, have)
            have > 0L -> State(Status.PAUSED, have, total)
            else -> State()
        }
    }

    private fun availableBytes(file: File): Long = StatFs(file.absolutePath).availableBytes

    private fun publish(id: String, value: State) {
        states.update { current -> current.toMutableMap().apply { put(id, value) } }
    }

    private fun userMessage(t: Throwable): String = when {
        t.message?.contains("space", true) == true -> "Недостаточно свободного места"
        t.message?.contains("timeout", true) == true -> "Истекло время ожидания сети"
        else -> t.message?.takeIf { it.isNotBlank() } ?: "Не удалось загрузить модель"
    }

    private companion object {
        const val MAX_ATTEMPTS = 6
    }
}
