package com.team.carescape.platform.sdk

import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.constant.Mode as SttMode
import ai.pleos.playground.stt.constant.ErrorType as SttError
import ai.pleos.playground.stt.listener.OnInitializeListener as SttInitialize
import ai.pleos.playground.stt.listener.ResultListener
import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.Mode as TtsMode
import ai.pleos.playground.tts.constant.ErrorType as TtsError
import ai.pleos.playground.tts.listener.OnInitializeListener as TtsInitialize
import ai.pleos.playground.tts.listener.EventListener
import java.util.Locale

internal data class SpeechReading(
    val tts: SdkFeatureState = SdkFeatureState(),
    val stt: SdkFeatureState = SdkFeatureState(),
    val speaking: Boolean = false,
    val listening: Boolean = false,
)

/** ON_DEVICE only. Listener registration and microphone requests never survive the safety gate. */
internal class PleosSpeechAdapter(context: Context, private val emit: (SpeechReading) -> Unit) : AutoCloseable {
    private val context = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        .setAudioAttributes(attributes)
        .setOnAudioFocusChangeListener({ change -> if (change < 0) stopAudio() }, main)
        .build()
    private var focusHeld = false
    private var allowed = false
    private var closed = false
    private var tts: TextToSpeech? = null
    private var stt: SpeechToText? = null
    private var ttsListener: EventListener? = null
    private var sttListener: ResultListener? = null
    private var ttsGeneration = 0L
    private var sttGeneration = 0L
    private var reading = SpeechReading()
    private var result: ((String?) -> Unit)? = null
    private var transcript: String? = null
    private var finalReceived = false
    private var recognitionEnded = false

    fun setAllowed(value: Boolean) {
        if (closed || value == allowed) return
        allowed = value
        if (value) { initializeTts(); initializeStt() }
        else {
            releaseTts(); releaseStt(); abandonFocus()
            reading = SpeechReading(SdkFeatureState(SdkAvailability.STOPPED, "SAFETY_GATE"), SdkFeatureState(SdkAvailability.STOPPED, "SAFETY_GATE"))
            publish()
        }
    }

    private fun initializeTts() {
        if (!allowed || closed || tts != null) return
        if (!granted("pleos.car.permission.TTS_SERVICE")) {
            reading = reading.copy(tts = SdkFeatureState(SdkAvailability.PERMISSION_DENIED, "TTS_SERVICE_PERMISSION")); publish(); return
        }
        val token = ++ttsGeneration
        reading = reading.copy(tts = SdkFeatureState(SdkAvailability.CHECKING)); publish()
        try {
            val engine = TextToSpeech(context, TtsMode.ON_DEVICE)
            tts = engine
            val listener = object : EventListener {
                override fun onReady() = Unit
                override fun onStart() = Unit
                override fun onUpdatedRms(rms: Double) = Unit
                override fun onDone() = ttsActive(token) { finishSpeech() }
                override fun onStop() = ttsActive(token) { finishSpeech() }
                override fun onError(errorType: TtsError) = ttsActive(token) {
                    val failure = speechError(errorType.name)
                    releaseTts(); abandonFocus()
                    reading = reading.copy(tts = failure); publish()
                }
            }
            ttsListener = listener
            engine.initialize(object : TtsInitialize {
                override fun onSuccess() = ttsActive(token) {
                    try {
                        engine.setLocale(Locale.KOREA)
                        engine.setAudioAttributes(attributes)
                        engine.addEventListener(listener)
                        reading = reading.copy(tts = SdkFeatureState(SdkAvailability.READY, "ON_DEVICE_INITIALIZED")); publish()
                    } catch (error: Exception) {
                        releaseTts(); reading = reading.copy(tts = failureState(error)); publish()
                    }
                }
                override fun onFailure(errorType: TtsError) = ttsActive(token) {
                    releaseTts(); reading = reading.copy(tts = speechError(errorType.name)); publish()
                }
            })
            main.postDelayed({
                if (validTts(token) && reading.tts.availability == SdkAvailability.CHECKING) {
                    releaseTts(); reading = reading.copy(tts = SdkFeatureState(SdkAvailability.NO_DATA, "TTS_INITIALIZE_TIMEOUT")); publish()
                }
            }, 12_000)
        } catch (error: Exception) { releaseTts(); reading = reading.copy(tts = failureState(error)); publish() }
    }

    private fun initializeStt() {
        if (!allowed || closed || stt != null) return
        if (!granted("pleos.car.permission.STT_SERVICE")) {
            reading = reading.copy(stt = SdkFeatureState(SdkAvailability.PERMISSION_DENIED, "STT_SERVICE_PERMISSION")); publish(); return
        }
        val token = ++sttGeneration
        reading = reading.copy(stt = SdkFeatureState(SdkAvailability.CHECKING)); publish()
        try {
            val engine = SpeechToText(context, SttMode.ON_DEVICE)
            stt = engine
            val listener = object : ResultListener {
                override fun onReady() = Unit
                override fun onStartedRecognition() = Unit
                override fun onUpdatedRms(rms: Double) = Unit
                override fun onUpdatedEpdData(on: Long, off: Long) = Unit
                override fun onUpdated(stt: String, completed: Boolean) = sttActive(token) {
                    if (reading.listening && completed) {
                        transcript = stt.take(4_000)
                        finalReceived = true
                        if (recognitionEnded) finishRecognition() else runCatching { engine.stop() }.onFailure {
                            releaseStt(); abandonFocus(); reading = reading.copy(stt = failureState(it)); publish()
                        }
                    }
                }
                override fun onEndedRecognition() = sttActive(token) {
                    if (reading.listening) {
                        recognitionEnded = true
                        // SDKs can deliver END before final text; never attribute the late text to another request.
                        if (finalReceived) finishRecognition()
                    }
                }
                override fun onError(errorType: SttError) = sttActive(token) {
                    val failure = speechError(errorType.name)
                    releaseStt(); abandonFocus(); reading = reading.copy(stt = failure); publish()
                }
            }
            sttListener = listener
            engine.initialize(object : SttInitialize {
                override fun onSuccess() = sttActive(token) {
                    try {
                        engine.setLocale(Locale.KOREA)
                        engine.addListener(listener)
                        reading = reading.copy(stt = SdkFeatureState(SdkAvailability.READY, "ON_DEVICE_INITIALIZED")); publish()
                    } catch (error: Exception) { releaseStt(); reading = reading.copy(stt = failureState(error)); publish() }
                }
                override fun onFailure(errorType: SttError) = sttActive(token) {
                    releaseStt(); reading = reading.copy(stt = speechError(errorType.name)); publish()
                }
            })
            main.postDelayed({
                if (validStt(token) && reading.stt.availability == SdkAvailability.CHECKING) {
                    releaseStt(); reading = reading.copy(stt = SdkFeatureState(SdkAvailability.NO_DATA, "STT_INITIALIZE_TIMEOUT")); publish()
                }
            }, 12_000)
        } catch (error: Exception) { releaseStt(); reading = reading.copy(stt = failureState(error)); publish() }
    }

    fun speak(text: String): Boolean {
        if (!allowed || closed || text.isBlank() || text.length > 4_000 || reading.speaking || reading.listening ||
            reading.tts.availability != SdkAvailability.READY || !requestFocus()
        ) return false
        val token = ttsGeneration
        reading = reading.copy(speaking = true); publish()
        return try {
            tts!!.speak(text, true)
            main.postDelayed({
                if (validTts(token) && reading.speaking) {
                    releaseTts(); abandonFocus(); reading = reading.copy(tts = SdkFeatureState(SdkAvailability.NO_DATA, "TTS_PLAYBACK_TIMEOUT")); publish()
                }
            }, 30_000)
            true
        } catch (error: Exception) { releaseTts(); abandonFocus(); reading = reading.copy(tts = failureState(error)); publish(); false }
    }

    fun listen(onResult: (String?) -> Unit): Boolean {
        if (!allowed || closed || reading.speaking || reading.listening || reading.stt.availability != SdkAvailability.READY || !requestFocus()) return false
        val token = sttGeneration
        result = onResult; transcript = null; finalReceived = false; recognitionEnded = false
        reading = reading.copy(listening = true); publish()
        return try {
            stt!!.request()
            main.postDelayed({
                if (validStt(token) && reading.listening) {
                    releaseStt(); abandonFocus(); reading = reading.copy(stt = SdkFeatureState(SdkAvailability.NO_DATA, "STT_RESULT_TIMEOUT")); publish()
                }
            }, 15_000)
            true
        } catch (error: Exception) { releaseStt(); abandonFocus(); reading = reading.copy(stt = failureState(error)); publish(); false }
    }

    fun stopAudio() {
        // ViewModel refresh may call this repeatedly while tour/location gates are closed.
        // Idle initialization is not playback: resetting it would feed status -> refresh -> stop loops.
        if (!reading.speaking && !reading.listening && result == null) return
        releaseTts(); releaseStt(); abandonFocus()
        reading = SpeechReading(); publish()
        // Defer rearming so a following foreground/safety invalidation can close the adapter first.
        if (allowed && !closed) main.post { if (allowed && !closed) { initializeTts(); initializeStt() } }
    }
    private fun finishSpeech() {
        if (!reading.speaking) return
        releaseTts(); abandonFocus(); publish()
        // A new SDK object gives the next utterance its own listener generation.
        initializeTts()
    }
    private fun finishRecognition() {
        val callback = result
        val text = transcript?.takeIf { allowed && finalReceived && recognitionEnded }
        result = null
        releaseStt(); abandonFocus(); publish()
        initializeStt()
        callback?.invoke(text)
    }
    private fun releaseTts() {
        ttsGeneration++
        val engine = tts; tts = null
        val listener = ttsListener; ttsListener = null
        runCatching { if (listener != null) engine?.removeEventListener(listener) }
        runCatching { engine?.stop() }; runCatching { engine?.release() }
        reading = reading.copy(speaking = false, tts = SdkFeatureState())
    }
    private fun releaseStt() {
        sttGeneration++
        val callback = result; result = null
        transcript = null; finalReceived = false; recognitionEnded = false
        val engine = stt; stt = null
        val listener = sttListener; sttListener = null
        runCatching { if (listener != null) engine?.removeListener(listener) }
        runCatching { engine?.stop() }; runCatching { engine?.release() }
        reading = reading.copy(listening = false, stt = SdkFeatureState())
        callback?.invoke(null)
    }
    private fun requestFocus(): Boolean {
        val accepted = runCatching { audioManager?.requestAudioFocus(focus) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED }.getOrDefault(false)
        focusHeld = accepted
        return accepted
    }
    private fun abandonFocus() {
        if (focusHeld) runCatching { audioManager?.abandonAudioFocusRequest(focus) }
        focusHeld = false
    }
    private fun validTts(token: Long) = !closed && allowed && token == ttsGeneration
    private fun validStt(token: Long) = !closed && allowed && token == sttGeneration
    private fun ttsActive(token: Long, action: () -> Unit) = onMain { if (validTts(token)) action() }
    private fun sttActive(token: Long, action: () -> Unit) = onMain { if (validStt(token)) action() }
    private fun granted(permission: String) = context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    private fun speechError(code: String) = SdkFeatureState(
        when { "PERMISSION" in code -> SdkAvailability.PERMISSION_DENIED; "NOT_SUPPORTED" in code -> SdkAvailability.UNSUPPORTED; else -> SdkAvailability.ERROR }, code,
    )
    private fun publish() { if (!closed) emit(reading) }
    private fun onMain(action: () -> Unit) { if (Looper.myLooper() == Looper.getMainLooper()) action() else main.post(action) }
    override fun close() {
        if (closed) return
        allowed = false
        releaseTts(); releaseStt(); abandonFocus()
        closed = true
        main.removeCallbacksAndMessages(null)
    }
}
