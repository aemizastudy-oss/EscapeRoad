package com.team.carescape.ui

import com.team.carescape.drive.NarrationSwitch

/** Pleos narration facade. The ViewModel rechecks foreground and vehicle safety for every request. */
class Narrator(private val vm: GameViewModel) {
    /** 녹음한 대사 파일. 있으면 TTS 대신 재생한다. */
    val voice: VoicePlayer get() = vm.voice
    val ready: Boolean get() = vm.speechReady

    /** 이 줄을 지금 읽을 수 있는지: 녹음 파일이 있거나 TTS가 준비됐다. */
    fun canSpeak(text: String): Boolean = voice.has(text) || ready

    /**
     * 녹음 파일이 있으면 재생하고, 없으면 TTS에 맡긴다.
     * Acceptance means playback started or the SDK accepted the request, not that audible output was verified.
     */
    fun speak(text: String): Boolean {
        if (!NarrationSwitch.isOn(vm.getApplication())) return false
        if (voice.has(text)) { vm.stopAudio(); return voice.play(text) }
        voice.stop()
        return vm.speakNarration(text)
    }

    fun stop() { voice.stop(); vm.stopAudio() }
    fun shutdown() = stop()
}
