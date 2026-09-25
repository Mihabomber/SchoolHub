package com.school.hub.feature.translator

import android.content.Context
import android.content.Intent
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.Locale

/** Озвучка перевода системным TTS (работает офлайн, если голос установлен). */
class Speaker(private val context: Context) {
    private var tts: TextToSpeech? = null
    private var ready = false
    private var pending: Pair<String, String>? = null
    val speaking = MutableStateFlow(false)

    private fun init() {
        if (tts != null) return
        tts = TextToSpeech(context.applicationContext) { status ->
            ready = status == TextToSpeech.SUCCESS
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) { speaking.value = true }
                override fun onDone(id: String?) { speaking.value = false }
                @Deprecated("Deprecated in Java") override fun onError(id: String?) { speaking.value = false }
            })
            pending?.let { (t, l) -> pending = null; speak(t, l) }
        }
    }

    /** @return null если всё ок, иначе текст ошибки. */
    fun speak(text: String, lang: String): String? {
        init()
        val t = tts ?: return "Озвучка недоступна"
        if (!ready) { pending = text to lang; return null }
        val loc = Locale.forLanguageTag(lang)
        when (t.setLanguage(loc)) {
            TextToSpeech.LANG_MISSING_DATA, TextToSpeech.LANG_NOT_SUPPORTED -> {
                runCatching {
                    context.startActivity(Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
                return "Нет голоса для «${loc.getDisplayLanguage(Locale("ru"))}». Открыл установку голосов — скачай и попробуй снова."
            }
        }
        t.speak(text.take(TextToSpeech.getMaxSpeechInputLength() - 1), TextToSpeech.QUEUE_FLUSH, null, "tr")
        return null
    }

    fun stop() { tts?.stop(); speaking.value = false }
}
