package com.team.carescape.ui

import com.team.carescape.drive.NarrationSwitch

/** Pleos narration facade. The ViewModel rechecks foreground and vehicle safety for every request. */
class Narrator(private val vm: GameViewModel) {
    val ready: Boolean get() = vm.speechReady

    /** Acceptance means the SDK accepted the request, not that audible output was verified. */
    fun speak(text: String): Boolean = NarrationSwitch.isOn(vm.getApplication()) && vm.speakNarration(text)

    fun stop() = vm.stopAudio()
    fun shutdown() = vm.stopAudio()
}
