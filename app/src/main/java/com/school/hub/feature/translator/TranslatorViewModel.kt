package com.school.hub.feature.translator

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.school.hub.core.data.ImageStorage
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

const val AUTO = "auto"

data class CameraResult(val bitmap: Bitmap, val blocks: List<OcrBlock>)

@OptIn(FlowPreview::class)
class TranslatorViewModel(
    val repo: TranslatorRepository,
    private val images: ImageStorage,
    private val speaker: Speaker? = null,
    private val engine: com.school.hub.feature.ai.engine.LlmEngine? = null,
    /** ИИ разрешён настройкой (переключатель в Настройках). Без него чип скрыт, перевод мгновенный. */
    val aiAllowed: Boolean = true,
    private val onTranslated: () -> Unit = {},
) : ViewModel() {
    /** Перевод фото через ИИ (медленнее, но понимает контекст). */
    var aiPhoto by mutableStateOf(false)
    val downloadingLangs = repo.downloading
    val speaking = speaker?.speaking

    fun speak(text: String, lang: String) {
        if (text.isBlank()) return
        if (speaking?.value == true) { speaker?.stop(); return }
        speaker?.speak(text, lang)?.let { status = it }
    }
    fun speakOutput() = speak(output, target)
    fun speakInput() = speak(input, if (source == AUTO) detected ?: "en" else source)

    var source by mutableStateOf(AUTO)
        private set
    var target by mutableStateOf("ru")
        private set
    var input by mutableStateOf("")
    var output by mutableStateOf("")
        private set
    var detected by mutableStateOf<String?>(null)
        private set
    var busy by mutableStateOf(false)
        private set
    var status by mutableStateOf<String?>(null)
        private set
    var downloaded by mutableStateOf<Set<String>>(emptySet())
        private set

    // камера
    var camera by mutableStateOf<CameraResult?>(null)
        private set
    var cameraBusy by mutableStateOf(false)
        private set

    init {
        refreshDownloaded()
        viewModelScope.launch {
            snapshotFlow { Triple(input, source, target) }
                .debounce(600)
                .distinctUntilChanged()
                .collectLatest { (text, _, _) -> if (text.isNotBlank()) runTranslate(text) else { output = ""; detected = null } }
        }
    }

    private suspend fun resolveSource(text: String): String? =
        if (source != AUTO) source else repo.detect(text).also { detected = it }

    private suspend fun runTranslate(text: String) {
        busy = true
        status = null
        runCatching {
            val src = resolveSource(text) ?: error("Не удалось определить язык — выбери его вручную")
            if (src !in downloaded || target !in downloaded) status = "Скачиваю языковой пакет (~30 МБ, один раз)…"
            output = repo.translate(text, src, target)
            status = null
            onTranslated()
            refreshDownloaded()
        }.onFailure { status = it.message ?: "Ошибка перевода. Нужен интернет для первого скачивания пакета." }
        busy = false
    }

    fun setSourceLang(code: String) { source = code }
    fun setTargetLang(code: String) { target = code }

    fun swap() {
        val src = if (source == AUTO) detected ?: return else source
        source = target
        target = src
        input = output.ifBlank { input }
    }

    fun refreshDownloaded() { viewModelScope.launch { downloaded = repo.downloaded() } }

    fun deleteLanguage(code: String) {
        viewModelScope.launch { runCatching { repo.delete(code) }; refreshDownloaded() }
    }

    fun downloadLanguage(code: String) {
        viewModelScope.launch {
            status = "Скачиваю «${repo.displayName(code)}»…"
            runCatching { repo.download(code) }.onFailure { status = it.message }.onSuccess { status = null }
            refreshDownloaded()
        }
    }

    // ---------- перевод с камеры ----------
    fun processBitmap(bmp: Bitmap) {
        cameraBusy = true
        viewModelScope.launch {
            runCatching {
                val blocks = repo.recognize(bmp)
                if (blocks.isEmpty()) { camera = CameraResult(bmp, emptyList()); status = "Текст не найден"; return@runCatching }
                val src = if (source != AUTO) source else repo.detect(blocks.joinToString(" ") { it.text }) ?: "en"
                camera = CameraResult(bmp, blocks)
                if (aiPhoto && aiAllowed) {
                    val e = engine
                    if (e == null || e.loadedModelId == null) {
                        status = "🤖 ИИ-модель не загружена — открой вкладку ИИ и запусти модель. Перевожу обычным способом."
                    } else {
                        status = "🤖 ИИ переводит… это может занять некоторое время"
                        val numbered = blocks.mapIndexed { i, b -> "${i + 1}. ${b.text}" }.joinToString("\n")
                        var last = ""
                        e.reset()
                        e.generate(
                            "Ты профессиональный переводчик. Переводи точно и естественно.",
                            "Переведи каждую строку на язык «${repo.displayName(target)}». Сохрани нумерацию, ответь только переводом.\n$numbered",
                        ).collect { last = it }
                        e.reset()
                        val lines = last.lines().mapNotNull { l -> Regex("^\\s*(\\d+)[.)]\\s*(.*)").find(l)?.destructured?.let { (n, t) -> n.toInt() to t } }.toMap()
                        if (lines.isNotEmpty()) {
                            camera = CameraResult(bmp, blocks.mapIndexed { i, b -> b.copy(translated = lines[i + 1] ?: b.text) })
                            status = null; refreshDownloaded(); onTranslated(); return@runCatching
                        }
                    }
                }
                val out = blocks.toMutableList()
                blocks.forEachIndexed { i, b ->
                    out[i] = b.copy(translated = runCatching { repo.translate(b.text, src, target) }.getOrDefault(b.text))
                    camera = CameraResult(bmp, out.toList()) // показываем по мере готовности
                }
                onTranslated()
                refreshDownloaded()
            }.onFailure { status = it.message }
            cameraBusy = false
        }
    }

    fun processUri(uri: Uri) {
        viewModelScope.launch {
            cameraBusy = true
            val bmp = images.decodeBitmap(uri, 2048)
            if (bmp != null) processBitmap(bmp) else { cameraBusy = false; status = "Не удалось открыть фото" }
        }
    }

    fun closeCamera() { camera = null }
    fun clearStatus() { status = null }
}
