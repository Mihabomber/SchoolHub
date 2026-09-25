package com.school.hub.feature.ai.models

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.IBinder
import android.os.StatFs
import androidx.core.app.NotificationCompat
import com.school.hub.R
import com.school.hub.core.data.SettingsStore
import com.school.hub.feature.ai.engine.ChatFormat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

enum class Tier(val title: String) { LITE("🪶 Лёгкие — для любых телефонов"), MID("⚖️ Средние — баланс ума и скорости"), PRO("🚀 Мощные 8–10 ГБ — для флагманов 12–16 ГБ RAM") }

data class LlmModel(
    val id: String,
    val name: String,
    val tagline: String,
    val format: ChatFormat,
    val url: String,
    val fileName: String,
    val sizeMb: Int,
    val minRamGb: Double,
    val russian: Int, // 1..3
    val contextSize: Int = 2048,
    /** Файл «глаз» (mmproj). Если есть — модель по-настоящему видит картинку целиком. */
    val mmprojUrl: String? = null,
    val mmprojMb: Int = 0,
    /** Условный «ум» 0..100 (сводная оценка по открытым бенчмаркам для школьных задач). */
    val smart: Int = 50,
    /** Примерная доля ошибок/выдумок в школьных ответах, %. */
    val errorRate: Int = 30,
    val year: Int = 2024,
    val systemSuffix: String = "",
    val isNew: Boolean = false,
    val tier: Tier = Tier.MID,
    /** Нужен свежий llama.cpp (b8000+). */
    val needsNewEngine: Boolean = false,
) {
    val vision: Boolean get() = mmprojUrl != null
    val mmprojFileName: String get() = "$id-mmproj.gguf"
    val totalMb: Int get() = sizeMb + mmprojMb
}

private const val HF = "https://huggingface.co"
private fun hf(repo: String, file: String) = "$HF/$repo/resolve/main/$file?download=true"

/** Квантованные GGUF (Q4_K_M) с открытых репозиториев Hugging Face. */
object ModelCatalog {
    val models = listOf(
        // ---------- лёгкие ----------
        LlmModel("gemma3-270m", "Gemma 3 · 270M", "Крошка от Google: мгновенные ответы, простые вопросы", ChatFormat.GEMMA,
            hf("unsloth/gemma-3-270m-it-GGUF", "gemma-3-270m-it-Q4_K_M.gguf"), "gemma-3-270m-it-Q4_K_M.gguf",
            253, 1.5, 1, smart = 15, errorRate = 60, year = 2025, isNew = true, tier = Tier.LITE),
        LlmModel("smollm2-360m", "SmolLM2 · 360M", "Самая быстрая, для очень слабых телефонов (лучше по-английски)", ChatFormat.CHATML,
            hf("bartowski/SmolLM2-360M-Instruct-GGUF", "SmolLM2-360M-Instruct-Q4_K_M.gguf"), "SmolLM2-360M-Instruct-Q4_K_M.gguf",
            271, 1.5, 1, smart = 14, errorRate = 62, tier = Tier.LITE),
        LlmModel("qwen3-06b", "Qwen 3 · 0.6B", "Крошечная, но уже умеет рассуждать", ChatFormat.CHATML,
            hf("unsloth/Qwen3-0.6B-GGUF", "Qwen3-0.6B-Q4_K_M.gguf"), "Qwen3-0.6B-Q4_K_M.gguf",
            397, 2.0, 2, smart = 30, errorRate = 45, year = 2025, systemSuffix = " /no_think", tier = Tier.LITE),
        LlmModel("gemma3-1b", "Gemma 3 · 1B", "Быстрая и неплохо знает русский", ChatFormat.GEMMA,
            hf("unsloth/gemma-3-1b-it-GGUF", "gemma-3-1b-it-Q4_K_M.gguf"), "gemma-3-1b-it-Q4_K_M.gguf",
            806, 3.0, 2, smart = 36, errorRate = 40, year = 2025, tier = Tier.LITE),
        LlmModel("qwen3-17b", "Qwen 3 · 1.7B", "Лучший выбор для 4 ГБ RAM, сильна в математике", ChatFormat.CHATML,
            hf("unsloth/Qwen3-1.7B-GGUF", "Qwen3-1.7B-Q4_K_M.gguf"), "Qwen3-1.7B-Q4_K_M.gguf",
            1110, 4.0, 3, contextSize = 4096, smart = 52, errorRate = 28, year = 2025, systemSuffix = " /no_think", tier = Tier.LITE),
        // ---------- средние ----------
        LlmModel("gemma4-e2b", "Gemma 4 · E2B", "НОВИНКА 2026 от Google: создана для телефонов, видит фото", ChatFormat.GEMMA,
            hf("unsloth/gemma-4-E2B-it-GGUF", "gemma-4-E2B-it-Q4_K_M.gguf"), "gemma-4-E2B-it-Q4_K_M.gguf",
            3100, 5.0, 3, contextSize = 4096, mmprojUrl = hf("unsloth/gemma-4-E2B-it-GGUF", "mmproj-F16.gguf"), mmprojMb = 600,
            smart = 64, errorRate = 19, year = 2026, isNew = true, tier = Tier.MID, needsNewEngine = true),
        LlmModel("qwen25vl-3b", "Qwen 2.5 VL · 3B", "Видит фото: решает задачи с картинки, читает графики", ChatFormat.CHATML,
            hf("unsloth/Qwen2.5-VL-3B-Instruct-GGUF", "Qwen2.5-VL-3B-Instruct-Q4_K_M.gguf"), "Qwen2.5-VL-3B-Instruct-Q4_K_M.gguf",
            1930, 6.0, 3, contextSize = 4096, mmprojUrl = hf("unsloth/Qwen2.5-VL-3B-Instruct-GGUF", "mmproj-F16.gguf"), mmprojMb = 1340,
            smart = 56, errorRate = 24, year = 2025, tier = Tier.MID),
        LlmModel("gemma3-4b", "Gemma 3 · 4B", "Отличный русский и видит картинки", ChatFormat.GEMMA,
            hf("unsloth/gemma-3-4b-it-GGUF", "gemma-3-4b-it-Q4_K_M.gguf"), "gemma-3-4b-it-Q4_K_M.gguf",
            2490, 6.0, 3, contextSize = 4096, mmprojUrl = hf("unsloth/gemma-3-4b-it-GGUF", "mmproj-F16.gguf"), mmprojMb = 850,
            smart = 62, errorRate = 21, year = 2025, tier = Tier.MID),
        LlmModel("qwen3-4b", "Qwen 3 · 4B", "Самая умная «текстовая» среди средних", ChatFormat.CHATML,
            hf("unsloth/Qwen3-4B-GGUF", "Qwen3-4B-Q4_K_M.gguf"), "Qwen3-4B-Q4_K_M.gguf",
            2500, 6.0, 3, contextSize = 4096, smart = 68, errorRate = 18, year = 2025, systemSuffix = " /no_think", tier = Tier.MID),
        LlmModel("phi4-mini", "Phi-4 mini · 3.8B", "Сильна в математике и логике", ChatFormat.PHI3,
            hf("unsloth/Phi-4-mini-instruct-GGUF", "Phi-4-mini-instruct-Q4_K_M.gguf"), "Phi-4-mini-instruct-Q4_K_M.gguf",
            2490, 6.0, 2, contextSize = 4096, smart = 64, errorRate = 20, year = 2025, tier = Tier.MID),
        LlmModel("gemma4-e4b", "Gemma 4 · E4B", "НОВИНКА 2026: умнее E2B, видит фото, для 8 ГБ RAM", ChatFormat.GEMMA,
            hf("unsloth/gemma-4-E4B-it-GGUF", "gemma-4-E4B-it-Q4_K_M.gguf"), "gemma-4-E4B-it-Q4_K_M.gguf",
            5000, 8.0, 3, contextSize = 4096, mmprojUrl = hf("unsloth/gemma-4-E4B-it-GGUF", "mmproj-F16.gguf"), mmprojMb = 600,
            smart = 72, errorRate = 15, year = 2026, isNew = true, tier = Tier.MID, needsNewEngine = true),
        LlmModel("qwen3-8b", "Qwen 3 · 8B", "Почти «взрослый» ИИ: сложные задачи и сочинения", ChatFormat.CHATML,
            hf("unsloth/Qwen3-8B-GGUF", "Qwen3-8B-Q4_K_M.gguf"), "Qwen3-8B-Q4_K_M.gguf",
            5030, 8.0, 3, contextSize = 4096, smart = 75, errorRate = 14, year = 2025, systemSuffix = " /no_think", tier = Tier.MID),
        // ---------- мощные ----------
        LlmModel("gemma4-12b", "Gemma 4 · 12B", "НОВИНКА 2026: топ-ум + полноценное зрение", ChatFormat.GEMMA,
            hf("unsloth/gemma-4-12b-it-GGUF", "gemma-4-12b-it-Q4_K_M.gguf"), "gemma-4-12b-it-Q4_K_M.gguf",
            7400, 12.0, 3, contextSize = 4096, mmprojUrl = hf("unsloth/gemma-4-12b-it-GGUF", "mmproj-F16.gguf"), mmprojMb = 850,
            smart = 82, errorRate = 10, year = 2026, isNew = true, tier = Tier.PRO, needsNewEngine = true),
        LlmModel("qwen3-14b", "Qwen 3 · 14B", "Очень умная: олимпиадная математика и код", ChatFormat.CHATML,
            hf("unsloth/Qwen3-14B-GGUF", "Qwen3-14B-Q4_K_M.gguf"), "Qwen3-14B-Q4_K_M.gguf",
            9000, 14.0, 3, contextSize = 4096, smart = 84, errorRate = 9, year = 2025, systemSuffix = " /no_think", tier = Tier.PRO),
        LlmModel("phi4-14b", "Phi-4 · 14B", "Microsoft: сильнейшая логика и точные науки", ChatFormat.CHATML,
            hf("unsloth/phi-4-GGUF", "phi-4-Q4_K_M.gguf"), "phi-4-Q4_K_M.gguf",
            9050, 14.0, 2, contextSize = 4096, smart = 83, errorRate = 10, year = 2025, tier = Tier.PRO),
    )

    fun byId(id: String) = models.firstOrNull { it.id == id }
}

enum class Verdict(val label: String, val color: Long) {
    GREAT("Потянет отлично", 0xFF2ECC71), OK("Потянет, но медленно", 0xFFF39C12), NO("Не хватит памяти", 0xFFE74C3C)
}

fun verdict(model: LlmModel, totalRamGb: Double, is64Bit: Boolean): Verdict = when {
    !is64Bit -> Verdict.NO
    totalRamGb >= model.minRamGb * 1.3 -> Verdict.GREAT
    totalRamGb >= model.minRamGb * 0.85 -> Verdict.OK
    else -> Verdict.NO
}

sealed interface ModelStatus {
    data object NotDownloaded : ModelStatus
    data class Downloading(val downloadedMb: Long, val totalMb: Long, val paused: Boolean, val speedKbs: Long = 0) : ModelStatus {
        val progress: Float get() = if (totalMb <= 0) 0f else (downloadedMb.toFloat() / totalMb).coerceIn(0f, 1f)
    }
    data object Ready : ModelStatus
    data class Failed(val reason: String) : ModelStatus
}

/**
 * Своё надёжное скачивание (OkHttp): докачка с места обрыва (Range), 5 повторов,
 * автопереход на зеркало hf-mirror.com, «глаза» (mmproj) качаются следом за моделью.
 * Пока идёт загрузка, работает foreground-сервис — Android не убьёт процесс.
 */
class ModelDownloader(private val context: Context, private val settings: SettingsStore) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = mutableMapOf<String, Job>()
    private val _states = MutableStateFlow<Map<String, ModelStatus>>(emptyMap())
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true).followSslRedirects(true).retryOnConnectionFailure(true)
        .build()

    val dir: File get() = (context.getExternalFilesDir("models") ?: File(context.filesDir, "models")).apply { mkdirs() }
    fun file(m: LlmModel) = File(dir, m.fileName)
    fun mmprojFile(m: LlmModel) = File(dir, m.mmprojFileName)
    private fun doneMark(f: File) = File(f.path + ".ok")

    fun freeSpaceMb(): Long = runCatching { StatFs(dir.absolutePath).availableBytes / (1024 * 1024) }.getOrDefault(0L)

    private fun ready(f: File) = f.exists() && (doneMark(f).exists() || f.length() > 50L * 1024 * 1024)
    fun isReady(m: LlmModel) = ready(file(m))
    fun isVisionReady(m: LlmModel) = m.vision && ready(mmprojFile(m))

    private fun onWifiOrOk(wifiOnly: Boolean): Boolean {
        if (!wifiOnly) return true
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    fun start(m: LlmModel, wifiOnly: Boolean): Result<Unit> = runCatching {
        val need = m.totalMb + 300L - (file(m).takeIf { it.exists() }?.length() ?: 0L) / (1024 * 1024)
        require(freeSpaceMb() > need) { "Мало места: нужно ≈$need МБ, свободно ${freeSpaceMb()} МБ" }
        require(onWifiOrOk(wifiOnly)) { "Включено «только по Wi-Fi». Подключись к Wi-Fi или выключи это в настройках." }
        if (jobs[m.id]?.isActive == true) return@runCatching
        set(m.id, ModelStatus.Downloading(0, m.totalMb.toLong(), false))
        DownloadService.start(context)
        jobs[m.id] = scope.launch {
            try {
                var base = 0L
                download(m, m.url, file(m)) { done, total, speed -> set(m.id, ModelStatus.Downloading(done, total + m.mmprojMb, false, speed)) }
                base = file(m).length() / (1024 * 1024)
                m.mmprojUrl?.let { url ->
                    download(m, url, mmprojFile(m)) { done, _, speed -> set(m.id, ModelStatus.Downloading(base + done, base + m.mmprojMb, false, speed)) }
                }
                set(m.id, ModelStatus.Ready)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                set(m.id, ModelStatus.Failed("Не скачалось: ${e.message ?: "ошибка сети"}. Нажми «Продолжить» — докачаю с места обрыва."))
            } finally {
                jobs.remove(m.id)
                if (jobs.isEmpty()) DownloadService.stop(context)
            }
        }
    }

    private suspend fun download(m: LlmModel, url: String, target: File, progress: (Long, Long, Long) -> Unit) {
        if (ready(target) && doneMark(target).exists()) return
        val part = File(target.path + ".part")
        if (target.exists() && !doneMark(target).exists()) target.renameTo(part)
        val urls = listOf(url, url.replace(HF, "https://hf-mirror.com"))
        var lastError: Throwable? = null
        repeat(6) { attempt ->
            val u = urls[if (attempt >= 3) 1 else 0]
            try {
                fetch(u, part, progress)
                part.renameTo(target)
                doneMark(target).writeText("ok")
                return
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                lastError = e
                delay(1500L * (attempt + 1))
            }
        }
        throw lastError ?: IllegalStateException("ошибка сети")
    }

    private suspend fun fetch(url: String, part: File, progress: (Long, Long, Long) -> Unit) {
        val have = if (part.exists()) part.length() else 0L
        val req = Request.Builder().url(url).header("User-Agent", "Parta/2.0 (Android)")
            .apply { if (have > 0) header("Range", "bytes=$have-") }.build()
        client.newCall(req).execute().use { resp ->
            if (resp.code == 416) return // уже скачано целиком
            if (!resp.isSuccessful) throw IllegalStateException("сайт ответил ${resp.code}")
            val append = resp.code == 206
            if (!append) part.delete()
            val body = resp.body ?: throw IllegalStateException("пустой ответ")
            val total = (if (append) have else 0L) + body.contentLength().coerceAtLeast(0)
            var done = if (append) have else 0L
            val mb = 1024L * 1024L
            var tick = System.currentTimeMillis(); var tickBytes = done
            FileOutputStream(part, append).use { out ->
                body.byteStream().use { input ->
                    val buf = ByteArray(256 * 1024)
                    while (true) {
                        if (!kotlin.coroutines.coroutineContext.isActive) throw CancellationException()
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        val now = System.currentTimeMillis()
                        if (now - tick > 500) {
                            val speed = (done - tickBytes) * 1000 / (now - tick) / 1024
                            progress(done / mb, total / mb, speed)
                            tick = now; tickBytes = done
                        }
                    }
                }
            }
            if (total > 0 && done < total) throw IllegalStateException("соединение оборвалось")
        }
    }

    private fun set(id: String, st: ModelStatus) = _states.update { it + (id to st) }

    fun cancel(m: LlmModel) {
        jobs.remove(m.id)?.cancel()
        set(m.id, ModelStatus.NotDownloaded)
    }

    fun delete(m: LlmModel) {
        cancel(m)
        listOf(file(m), mmprojFile(m)).forEach { f -> f.delete(); doneMark(f).delete(); File(f.path + ".part").delete() }
    }

    fun status(m: LlmModel): ModelStatus = _states.value[m.id]?.takeIf { it !is ModelStatus.NotDownloaded }
        ?: if (isReady(m) && (!m.vision || isVisionReady(m) || !mmprojFile(m).exists())) ModelStatus.Ready
        else if (File(file(m).path + ".part").exists()) ModelStatus.Failed("Загрузка прервалась. Нажми «Продолжить».")
        else ModelStatus.NotDownloaded

    fun observe(): Flow<Map<String, ModelStatus>> = kotlinx.coroutines.flow.flow {
        while (true) {
            emit(ModelCatalog.models.associate { it.id to status(it) })
            delay(500)
        }
    }

    val states: StateFlow<Map<String, ModelStatus>> = _states.asStateFlow()
}

/** Держит процесс живым, пока качаются гигабайты. */
class DownloadService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel("downloads", "Загрузки", NotificationManager.IMPORTANCE_LOW))
        val n: Notification = NotificationCompat.Builder(this, "downloads")
            .setSmallIcon(R.drawable.ic_notification).setContentTitle("Качаю модель ИИ…")
            .setContentText("Можно свернуть приложение").setOngoing(true).setProgress(0, 0, true).build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(77, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC) else startForeground(77, n)
        return START_NOT_STICKY
    }

    companion object {
        fun start(c: Context) = runCatching { androidx.core.content.ContextCompat.startForegroundService(c, Intent(c, DownloadService::class.java)) }
        fun stop(c: Context) = runCatching { c.stopService(Intent(c, DownloadService::class.java)) }
    }
}
