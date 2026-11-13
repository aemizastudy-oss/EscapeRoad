package com.team.carescape.ui

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * 소리 단서를 읽어 주는 진행자. Android 표준 TextToSpeech를 쓴다.
 * Pleos TextToSpeech SDK 연결은 아직 검증 전이라 쓰지 않는다. 기기에 한국어 음성이 없으면 [ready]가 false로 남는다.
 */
class Narrator(context: Context) {
    var ready: Boolean = false
        private set
    private var tts: TextToSpeech? = null

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(Locale.KOREAN)
                ready = result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
            }
        }
    }

    /** 읽기를 시작했으면 true. 음성 엔진이 준비되지 않았으면 false. */
    fun speak(text: String): Boolean {
        if (!ready) return false
        return tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "clue") == TextToSpeech.SUCCESS
    }

    fun shutdown() {
        tts?.shutdown()
        tts = null
        ready = false
    }
}
