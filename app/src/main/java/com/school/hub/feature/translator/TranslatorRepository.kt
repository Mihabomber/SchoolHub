package com.school.hub.feature.translator

import kotlinx.coroutines.async

import android.graphics.Bitmap
import android.graphics.Rect
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await
import java.util.Locale

data class OcrBlock(val rect: Rect, val text: String, val lines: Int, val translated: String = "")

/** Google ML Kit: перевод on-device (пакеты ~30 МБ качаются один раз) + распознавание текста. */
class TranslatorRepository(
    private val scope: kotlinx.coroutines.CoroutineScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO),
) {
    /** Скачивания живут отдельно от экрана: не обрываются, когда пользователь печатает или уходит. */
    private val inFlight = mutableMapOf<String, kotlinx.coroutines.Deferred<Unit>>()
    val downloading = kotlinx.coroutines.flow.MutableStateFlow<Set<String>>(emptySet())

    suspend fun ensure(code: String) {
        if (code == TranslateLanguage.ENGLISH) return // английский встроен
        val job = synchronized(inFlight) {
            inFlight[code]?.takeIf { it.isActive } ?: scope.async {
                downloading.value = downloading.value + code
                try {
                    var last: Throwable? = null
                    repeat(4) { attempt ->
                        try {
                            models.download(TranslateRemoteModel.Builder(code).build(), DownloadConditions.Builder().build()).await()
                            return@async
                        } catch (e: Throwable) { last = e; kotlinx.coroutines.delay(1500L * (attempt + 1)) }
                    }
                    throw IllegalStateException("Не удалось скачать «${displayName(code)}». Проверь интернет. (${last?.message})")
                } finally { downloading.value = downloading.value - code }
            }.also { inFlight[code] = it }
        }
        job.await()
    }
    private val translators = mutableMapOf<String, Translator>()
    private val models = RemoteModelManager.getInstance()
    private val langId by lazy { LanguageIdentification.getClient() }
    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    val languages: List<String> by lazy {
        val popular = listOf("ru", "en", "de", "fr", "es", "it", "uk", "zh", "ja", "ko", "tr", "pt")
        val all = TranslateLanguage.getAllLanguages()
        popular.filter { it in all } + all.filter { it !in popular }.sortedBy { displayName(it) }
    }

    fun displayName(code: String): String =
        Locale.forLanguageTag(code).getDisplayLanguage(Locale.forLanguageTag("ru")).replaceFirstChar { it.uppercase() }

    /** Определяет язык; null если не удалось или язык не поддерживается переводчиком. */
    suspend fun detect(text: String): String? = runCatching {
        val tag = langId.identifyLanguage(text).await()
        if (tag == "und") null else TranslateLanguage.fromLanguageTag(tag)
    }.getOrNull()

    suspend fun translate(text: String, source: String, target: String, wifiOnly: Boolean = false): String {
        if (text.isBlank() || source == target) return text
        val key = "$source>$target"
        val t = translators.getOrPut(key) {
            Translation.getClient(TranslatorOptions.Builder().setSourceLanguage(source).setTargetLanguage(target).build())
        }
        kotlinx.coroutines.coroutineScope {
            val a = async { ensure(source) }
            val b = async { ensure(target) }
            a.await(); b.await()
        }
        t.downloadModelIfNeeded(DownloadConditions.Builder().build()).await()
        return t.translate(text).await()
    }

    suspend fun downloaded(): Set<String> = runCatching {
        models.getDownloadedModels(TranslateRemoteModel::class.java).await().map { it.language }.toSet()
    }.getOrDefault(emptySet())

    suspend fun download(code: String) = ensure(code)

    suspend fun delete(code: String) {
        models.deleteDownloadedModel(TranslateRemoteModel.Builder(code).build()).await()
    }

    suspend fun recognize(bitmap: Bitmap): List<OcrBlock> {
        val result = recognizer.process(InputImage.fromBitmap(bitmap, 0)).await()
        return result.textBlocks.mapNotNull { b ->
            val r = b.boundingBox ?: return@mapNotNull null
            // Склеиваем строки абзаца, убирая переносы «при-\nмер»
            val text = b.lines.joinToString("\n") { it.text }.replace(Regex("-\n(?=\\p{Ll})"), "").replace('\n', ' ')
            if (text.count { it.isLetter() } < 2) return@mapNotNull null
            OcrBlock(r, text, b.lines.size.coerceAtLeast(1))
        }
    }
}
