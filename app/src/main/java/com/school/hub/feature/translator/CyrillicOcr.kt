package com.school.hub.feature.translator

import android.content.Context
import android.graphics.Bitmap
import com.googlecode.tesseract.android.TessBaseAPI
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Распознавание кириллицы на фото. ML Kit (text-recognition) умеет только латиницу,
 * поэтому для русского текста используется Tesseract с языками rus+eng.
 * Словари tessdata_fast качаются один раз в filesDir/tesseract/tessdata, дальше всё офлайн.
 */
object CyrillicOcr {
    private val LANGS = listOf("rus", "eng")
    private val MIRRORS = listOf(
        "https://raw.githubusercontent.com/tesseract-ocr/tessdata_fast/main/",
        "https://raw.githubusercontent.com/tesseract-ocr/tessdata_fast/master/",
        "https://github.com/tesseract-ocr/tessdata_fast/raw/main/",
    )
    private const val MIN_SIZE = 100_000L
    private const val MIN_CONFIDENCE = 35f

    @Volatile private var root: File? = null
    private val lock = Mutex()
    private var api: TessBaseAPI? = null

    fun init(context: Context) { root = File(context.applicationContext.filesDir, "tesseract") }

    private fun file(lang: String): File? = root?.let { File(it, "tessdata/$lang.traineddata") }

    private fun present(f: File?): Boolean = f != null && f.exists() && f.length() >= MIN_SIZE

    /** Словари уже на телефоне. */
    fun ready(): Boolean = LANGS.all { present(file(it)) }

    /** Качает недостающие словари. Недокачанный файл удаляется, битый словарь не сохраняется. */
    suspend fun download() = withContext(Dispatchers.IO) {
        for (lang in LANGS) {
            val f = file(lang) ?: error("Распознавание не инициализировано")
            if (present(f)) continue
            f.parentFile?.mkdirs()
            val tmp = File(f.parentFile, "$lang.traineddata.part")
            var ok = false
            var last: Throwable? = null
            for (base in MIRRORS) {
                if (ok) break
                try {
                    val c = URL(base + "$lang.traineddata").openConnection() as HttpURLConnection
                    c.connectTimeout = 15_000
                    c.readTimeout = 30_000
                    c.instanceFollowRedirects = true
                    try {
                        if (c.responseCode != HttpURLConnection.HTTP_OK) error("HTTP ${c.responseCode}")
                        c.inputStream.use { input -> tmp.outputStream().use { out -> input.copyTo(out) } }
                    } finally {
                        c.disconnect()
                    }
                    if (tmp.length() < MIN_SIZE) error("файл неполный")
                    if (!tmp.renameTo(f)) {
                        tmp.copyTo(f, overwrite = true)
                        tmp.delete()
                    }
                    ok = true
                } catch (e: CancellationException) {
                    tmp.delete()
                    throw e
                } catch (e: Throwable) {
                    last = e
                    tmp.delete()
                }
            }
            if (!ok) throw IllegalStateException("Не удалось скачать словарь для русского текста. Проверь интернет. (${last?.message})")
        }
    }

    /** Абзацы с рамками. Пустой список, если словарей нет. */
    suspend fun recognize(bitmap: Bitmap): List<OcrBlock> = withContext(Dispatchers.Default) {
        lock.withLock {
            val dir = root
            if (dir == null || !ready()) return@withLock emptyList<OcrBlock>()
            val tess = api ?: TessBaseAPI().also { t ->
                if (!t.init(dir.absolutePath, LANGS.joinToString("+"))) {
                    t.recycle()
                    error("Не удалось запустить распознавание русского текста")
                }
                api = t
            }
            val img = if (bitmap.config == Bitmap.Config.ARGB_8888) bitmap else bitmap.copy(Bitmap.Config.ARGB_8888, false)
            val out = mutableListOf<OcrBlock>()
            try {
                tess.setImage(img)
                val all = tess.getUTF8Text()
                if (all.isNullOrBlank()) return@withLock emptyList<OcrBlock>()
                val level = TessBaseAPI.PageIteratorLevel.RIL_PARA
                val ri = tess.getResultIterator() ?: return@withLock emptyList<OcrBlock>()
                try {
                    ri.begin()
                    do {
                        val raw = ri.getUTF8Text(level)
                        if (raw != null && ri.confidence(level) >= MIN_CONFIDENCE) {
                            val lines = raw.trim().lines().filter { l -> l.isNotBlank() }
                            // Склеиваем строки абзаца, убирая переносы «при-\nмер»
                            val text = lines.joinToString("\n").replace(Regex("-\n(?=\\p{Ll})"), "").replace('\n', ' ').trim()
                            val r = ri.getBoundingRect(level)
                            if (text.count { c -> c.isLetter() } >= 2 && r.width() > 0 && r.height() > 0) {
                                out += OcrBlock(r, text, lines.size.coerceAtLeast(1))
                            }
                        }
                    } while (ri.next(level))
                } finally {
                    ri.delete()
                }
            } finally {
                tess.clear()
                if (img !== bitmap) img.recycle()
            }
            out
        }
    }
}
