package com.school.hub.feature.ai.models

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.StatFs
import com.school.hub.core.data.SettingsStore
import com.school.hub.feature.ai.engine.ChatFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File

data class LlmModel(
    val id: String,
    val name: String,
    val tagline: String,
    val format: ChatFormat,
    val url: String,
    val fileName: String,
    val sizeMb: Int,
    val minRamGb: Double,
    val russian: Int, // 1..3 — насколько хорошо говорит по-русски
    val contextSize: Int = 2048,
    /** Модель от природы умеет «видеть» картинки (мультимодальная). */
    val vision: Boolean = false,
    /** Условный «уровень ума» 0..100 (наша сводная оценка по открытым бенчмаркам MMLU/GSM8K для школьных задач). */
    val smart: Int = 50,
    /** Примерная доля заметных ошибок/выдумок в школьных ответах, %. */
    val errorRate: Int = 30,
    val year: Int = 2024,
    /** Qwen3 умеет «думать вслух» — выключаем для скорости. */
    val systemSuffix: String = "",
    val isNew: Boolean = false,
)

private const val HF = "https://huggingface.co"

/** Квантованные GGUF (Q4_K_M) с открытых репозиториев Hugging Face — без регистрации. Отсортированы от лёгких к тяжёлым. */
object ModelCatalog {
    val models = listOf(
        LlmModel("qwen3-06b", "Qwen 3 · 0.6B", "Новинка 2025: крошечная, но уже умеет рассуждать", ChatFormat.CHATML,
            "$HF/unsloth/Qwen3-0.6B-GGUF/resolve/main/Qwen3-0.6B-Q4_K_M.gguf", "Qwen3-0.6B-Q4_K_M.gguf",
            397, 2.0, 2, smart = 30, errorRate = 45, year = 2025, systemSuffix = " /no_think", isNew = true),
        LlmModel("qwen25-05b", "Qwen 2.5 · 0.5B", "Самая лёгкая, летает даже на слабых телефонах", ChatFormat.CHATML,
            "$HF/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/qwen2.5-0.5b-instruct-q4_k_m.gguf", "qwen2.5-0.5b-instruct-q4_k_m.gguf",
            491, 2.0, 2, smart = 24, errorRate = 50),
        LlmModel("lfm2-12b", "LFM2 · 1.2B", "Новинка 2025 от Liquid AI: сделана именно для телефонов, очень быстрая", ChatFormat.CHATML,
            "$HF/LiquidAI/LFM2-1.2B-GGUF/resolve/main/LFM2-1.2B-Q4_K_M.gguf", "LFM2-1.2B-Q4_K_M.gguf",
            731, 2.5, 2, contextSize = 4096, smart = 40, errorRate = 36, year = 2025, isNew = true),
        LlmModel("gemma3-1b", "Gemma 3 · 1B", "Новинка 2025 от Google: быстрая и неплохо знает русский", ChatFormat.GEMMA,
            "$HF/unsloth/gemma-3-1b-it-GGUF/resolve/main/gemma-3-1b-it-Q4_K_M.gguf", "gemma-3-1b-it-Q4_K_M.gguf",
            806, 3.0, 2, smart = 36, errorRate = 40, year = 2025, isNew = true),
        LlmModel("llama32-1b", "Llama 3.2 · 1B", "Быстрая модель от Meta", ChatFormat.LLAMA3,
            "$HF/bartowski/Llama-3.2-1B-Instruct-GGUF/resolve/main/Llama-3.2-1B-Instruct-Q4_K_M.gguf", "Llama-3.2-1B-Instruct-Q4_K_M.gguf",
            808, 3.0, 2, smart = 32, errorRate = 42),
        LlmModel("qwen3-17b", "Qwen 3 · 1.7B", "Новинка 2025: лучший выбор для 4 ГБ RAM, сильна в математике", ChatFormat.CHATML,
            "$HF/unsloth/Qwen3-1.7B-GGUF/resolve/main/Qwen3-1.7B-Q4_K_M.gguf", "Qwen3-1.7B-Q4_K_M.gguf",
            1110, 4.0, 3, contextSize = 4096, smart = 52, errorRate = 28, year = 2025, systemSuffix = " /no_think", isNew = true),
        LlmModel("qwen25-15b", "Qwen 2.5 · 1.5B", "Проверенный баланс: умная и хорошо знает русский", ChatFormat.CHATML,
            "$HF/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf", "qwen2.5-1.5b-instruct-q4_k_m.gguf",
            1120, 4.0, 3, smart = 45, errorRate = 32),
        LlmModel("gemma2-2b", "Gemma 2 · 2B", "Модель от Google, аккуратные ответы", ChatFormat.GEMMA,
            "$HF/bartowski/gemma-2-2b-it-GGUF/resolve/main/gemma-2-2b-it-Q4_K_M.gguf", "gemma-2-2b-it-Q4_K_M.gguf",
            1710, 4.0, 3, smart = 44, errorRate = 33),
        LlmModel("llama32-3b", "Llama 3.2 · 3B", "Умнее, но медленнее", ChatFormat.LLAMA3,
            "$HF/bartowski/Llama-3.2-3B-Instruct-GGUF/resolve/main/Llama-3.2-3B-Instruct-Q4_K_M.gguf", "Llama-3.2-3B-Instruct-Q4_K_M.gguf",
            2020, 6.0, 2, smart = 50, errorRate = 29),
        LlmModel("qwen3-4b", "Qwen 3 · 4B", "Новинка 2025: самая умная из тех, что тянет телефон (8 ГБ RAM)", ChatFormat.CHATML,
            "$HF/unsloth/Qwen3-4B-GGUF/resolve/main/Qwen3-4B-Q4_K_M.gguf", "Qwen3-4B-Q4_K_M.gguf",
            2500, 6.0, 3, contextSize = 4096, smart = 68, errorRate = 18, year = 2025, systemSuffix = " /no_think", isNew = true),
        LlmModel("gemma3-4b", "Gemma 3 · 4B", "Новинка 2025: отличный русский, модель умеет видеть картинки", ChatFormat.GEMMA,
            "$HF/unsloth/gemma-3-4b-it-GGUF/resolve/main/gemma-3-4b-it-Q4_K_M.gguf", "gemma-3-4b-it-Q4_K_M.gguf",
            2490, 6.0, 3, contextSize = 4096, vision = true, smart = 62, errorRate = 21, year = 2025, isNew = true),
        LlmModel("phi4-mini", "Phi-4 mini · 3.8B", "Новинка 2025 от Microsoft: сильна в математике и логике", ChatFormat.PHI3,
            "$HF/unsloth/Phi-4-mini-instruct-GGUF/resolve/main/Phi-4-mini-instruct-Q4_K_M.gguf", "Phi-4-mini-instruct-Q4_K_M.gguf",
            2490, 6.0, 2, contextSize = 4096, smart = 64, errorRate = 20, year = 2025, isNew = true),
        LlmModel("gemma3n-e2b", "Gemma 3n · E2B", "Новинка 2025: создана Google для телефонов, мультимодальная", ChatFormat.GEMMA,
            "$HF/unsloth/gemma-3n-E2B-it-GGUF/resolve/main/gemma-3n-E2B-it-Q4_K_M.gguf", "gemma-3n-E2B-it-Q4_K_M.gguf",
            3030, 6.0, 3, contextSize = 4096, vision = true, smart = 58, errorRate = 23, year = 2025, isNew = true),
        LlmModel("phi3-mini", "Phi-3 mini · 3.8B", "Классика: логика и математика (лучше на английском)", ChatFormat.PHI3,
            "$HF/microsoft/Phi-3-mini-4k-instruct-gguf/resolve/main/Phi-3-mini-4k-instruct-q4.gguf", "Phi-3-mini-4k-instruct-q4.gguf",
            2390, 6.0, 1, smart = 55, errorRate = 26),
    )

    fun byId(id: String) = models.firstOrNull { it.id == id }
}

enum class Verdict(val label: String, val color: Long) {
    GREAT("Потянет отлично", 0xFF2ECC71), OK("Потянет, но медленно", 0xFFF39C12), NO("Не хватит памяти", 0xFFE74C3C)
}

fun verdict(model: LlmModel, totalRamGb: Double, is64Bit: Boolean): Verdict = when {
    !is64Bit -> Verdict.NO
    totalRamGb >= model.minRamGb * 1.4 -> Verdict.GREAT
    totalRamGb >= model.minRamGb * 0.9 -> Verdict.OK
    else -> Verdict.NO
}

sealed interface ModelStatus {
    data object NotDownloaded : ModelStatus
    data class Downloading(val downloadedMb: Long, val totalMb: Long, val paused: Boolean) : ModelStatus {
        val progress: Float get() = if (totalMb <= 0) 0f else (downloadedMb.toFloat() / totalMb).coerceIn(0f, 1f)
    }
    data object Ready : ModelStatus
    data class Failed(val reason: String) : ModelStatus
}

/** Скачивание через системный DownloadManager: продолжает качать, даже если закрыть приложение. */
class ModelDownloader(private val context: Context, private val settings: SettingsStore) {
    private val dm = context.getSystemService(DownloadManager::class.java)
    private val failures = mutableMapOf<String, String>()

    val dir: File get() = (context.getExternalFilesDir("models") ?: File(context.filesDir, "models")).apply { mkdirs() }
    fun file(m: LlmModel) = File(dir, m.fileName)
    private fun key(m: LlmModel) = "dl_${m.id}"

    fun freeSpaceMb(): Long = runCatching { StatFs(dir.absolutePath).availableBytes / (1024 * 1024) }.getOrDefault(0L)

    fun start(m: LlmModel, wifiOnly: Boolean): Result<Unit> = runCatching {
        require(freeSpaceMb() > m.sizeMb + 200) { "Мало места: нужно ${m.sizeMb + 200} МБ, свободно ${freeSpaceMb()} МБ" }
        failures.remove(m.id)
        file(m).delete()
        val req = DownloadManager.Request(Uri.parse(m.url))
            .setTitle("SchoolHub: ${m.name}")
            .setDescription("Скачивание офлайн-модели ИИ")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, "models", m.fileName)
            .setAllowedOverMetered(!wifiOnly)
            .setAllowedOverRoaming(false)
        settings.putLong(key(m), dm.enqueue(req))
    }

    fun cancel(m: LlmModel) {
        val id = settings.getLong(key(m))
        if (id >= 0) runCatching { dm.remove(id) }
        settings.remove(key(m))
        file(m).delete()
    }

    fun delete(m: LlmModel) = cancel(m)

    fun status(m: LlmModel): ModelStatus {
        val id = settings.getLong(key(m))
        if (id < 0) {
            failures[m.id]?.let { return ModelStatus.Failed(it) }
            val f = file(m)
            return if (f.exists() && f.length() > m.sizeMb * 1024L * 1024L * 8 / 10) ModelStatus.Ready else ModelStatus.NotDownloaded
        }
        val cursor = dm.query(DownloadManager.Query().setFilterById(id)) ?: return ModelStatus.NotDownloaded
        cursor.use { c ->
            if (!c.moveToFirst()) { settings.remove(key(m)); return ModelStatus.NotDownloaded }
            val st = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            val done = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
            val total = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
            val mb = 1024L * 1024L
            return when (st) {
                DownloadManager.STATUS_SUCCESSFUL -> { settings.remove(key(m)); ModelStatus.Ready }
                DownloadManager.STATUS_FAILED -> {
                    val reason = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                    settings.remove(key(m)); file(m).delete()
                    val msg = "Ошибка скачивания (код $reason)"
                    failures[m.id] = msg
                    ModelStatus.Failed(msg)
                }
                DownloadManager.STATUS_PAUSED -> ModelStatus.Downloading(done / mb, if (total > 0) total / mb else m.sizeMb.toLong(), true)
                else -> ModelStatus.Downloading(done / mb, if (total > 0) total / mb else m.sizeMb.toLong(), false)
            }
        }
    }

    fun observe(): Flow<Map<String, ModelStatus>> = flow {
        while (true) {
            emit(ModelCatalog.models.associate { it.id to status(it) })
            delay(700)
        }
    }.flowOn(Dispatchers.IO)
}
