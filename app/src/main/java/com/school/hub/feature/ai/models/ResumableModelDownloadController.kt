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
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Resumable model transfer coordinator. It keeps incomplete files in .part files
 * and only publishes INSTALLED after GGUF and size checks succeed.
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

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = mutableMapOf<String, Job>()
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
        val final = File(directory, model.fileName)
        val part = File(final.path + ".part")
        val expected = model.sizeMb.toLong() * 1024L * 1024L
        val required = (expected - part.length()).coerceAtLeast(0L) + 32L * 1024L * 1024L
        if (availableBytes(directory) < required) {
            update(model.id, state(model).copy(status = Status.ERROR, message = "Недостаточно свободного места"))
            return
        }
        jobs[model.id] = scope.launch {
            try {
                transfer(model, final, part, expected, expectedSha256)
            } catch (_: CancellationException) {
                if (part.exists()) update(model.id, state(model).copy(status = Status.PAUSED, message = "Загрузка приостановлена"))
                else update(model.id, initialState(model))
            } catch (t: Throwable) {
                update(model.id, state(model).copy(status = Status.ERROR, message = userMessage(t)))
            } finally { jobs.remove(model.id) }
        }
    }

    fun pause(model: LlmModel) {
        jobs.remove(model.id)?.cancel()
        val part = File(directory, model.fileName + ".part")
        update(model.id, state(model).copy(status = if (part.exists()) Status.PAUSED else Status.NOT_INSTALLED))
    }

    fun cancel(model: LlmModel) {
        jobs.remove(model.id)?.cancel()
        File(directory, model.fileName + ".part").delete()
        update(model.id, initialState(model))
    }

    fun delete(model: LlmModel) {
        cancel(model)
        File(directory, model.fileName).delete()
        update(model.id, initialState(model))
    }

    private suspend fun transfer(model: LlmModel, final: File, part: File, expected: Long, sha256: String?) {
        val existing = part.length()
        update(model.id, State(Status.DOWNLOADING, existing, expected))
        var attempt = 0
        while (true) {
            try {
                fetch(model.url, part, expected) { done, total, speed ->
                    val eta = if (speed > 0L) ((total - done).coerceAtLeast(0L) / speed) else null
                    update(model.id, State(Status.DOWNLOADING, done, total, speed, eta))
                }
                break
            } catch (t: Throwable) {
                ensureActive()
                attempt++
                if (!isConnected()) update(model.id, state(model).copy(status = Status.WAITING_FOR_CONNECTION, message = "Ожидание сети…"))
                delay((1000L * (1L shl attempt.coerceAtMost(5))).coerceAtMost(30_000L))
                update(model.id, state(model).copy(status = Status.DOWNLOADING, message = "Возобновление…"))
            }
        }
        update(model.id, state(model).copy(status = Status.VERIFYING, message = "Проверка файла…"))
        if (!verify(part, expected, sha256)) {
            update(model.id, state(model).copy(status = Status.CORRUPTED, message = "Файл модели повреждён"))
            return
        }
        if (!part.renameTo(final)) throw IllegalStateException("Не удалось сохранить файл модели")
        update(model.id, State(Status.INSTALLED, final.length(), final.length()))
    }

    private suspend fun fetch(url: String, part: File, expected: Long, report: (Long, Long, Long) -> Unit) {
        val offset = part.length()
        val request = Request.Builder().url(url).header("User-Agent", "SchoolHub/2.1").apply {
            if (offset > 0L) header("Range", "bytes=$offset-")
        }.build()
        client.newCall(request).execute().use { response ->
            if (response.code == 416) return
            if (!response.isSuccessful) throw IllegalStateException("Сервер вернул ошибку ${response.code}")
            val body = response.body ?: throw IllegalStateException("Пустой ответ сервера")
            val append = response.code == 206 && offset > 0L
            if (!append) part.delete()
            val start = if (append) offset else 0L
            val total = response.header("Content-Range")?.substringAfter('/')?.toLongOrNull()
                ?: body.contentLength().takeIf { it >= 0L }?.let { start + it }
                ?: expected
            var done = start
            var lastTime = System.nanoTime()
            var lastBytes = done
            FileOutputStream(part, append).use { output ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(256 * 1024)
                    while (true) {
                        ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        done += count
                        val now = System.nanoTime()
                        if (now - lastTime >= 500_000_000L) {
                            val speed = ((done - lastBytes) * 1_000_000_000L / (now - lastTime)).coerceAtLeast(0L)
                            report(done, total, speed)
                            lastTime = now
                            lastBytes = done
                        }
                    }
                }
            }
            if (total > 0L && done < total) throw IllegalStateException("Соединение прервано")
        }
    }

    private fun verify(file: File, expected: Long, sha256: String?): Boolean {
        if (!file.exists() || file.length() < 4L) return false
        FileInputStream(file).use { input ->
            val magic = ByteArray(4)
            if (input.read(magic) != 4 || String(magic, Charsets.US_ASCII) != "GGUF") return false
        }
        if (expected > 0L && kotlin.math.abs(file.length() - expected) > expected / 100L + 1024L) return false
        if (sha256 == null) return true
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(256 * 1024)
            while (true) { val count = input.read(buffer); if (count < 0) break; digest.update(buffer, 0, count) }
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
        val final = File(directory, model.fileName)
        val part = File(final.path + ".part")
        return when {
            final.exists() -> State(Status.INSTALLED, final.length(), final.length())
            part.exists() -> State(Status.PAUSED, part.length(), model.sizeMb.toLong() * 1024L * 1024L)
            else -> State()
        }
    }

    private fun availableBytes(file: File): Long = StatFs(file.absolutePath).availableBytes

    private fun update(id: String, value: State) { states.value = states.value.toMutableMap().apply { put(id, value) } }

    private fun userMessage(t: Throwable): String = when {
        t.message?.contains("space", true) == true -> "Недостаточно свободного места"
        t.message?.contains("timeout", true) == true -> "Истекло время ожидания сети"
        else -> t.message?.takeIf { it.isNotBlank() } ?: "Не удалось загрузить модель"
    }
}
