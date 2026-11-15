package com.team.carescape.drive

import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.File
import java.util.Locale

/**
 * Drive chapter audio: a quiet looping BGM and the travel lines, one at a time.
 *
 * Voice source per line id (e.g. `ch1_travel_01`), first match wins:
 *  1. `res/raw/voice_<id>` – recorded file shipped in the app (docs/voice naming),
 *  2. `<external files>/voice/` or `<files>/voice/voice_<id>.(mp3|ogg|m4a|wav)` – drop-in for testing recordings
 *     without a rebuild (tools/push_demo_voices.py fills the latter on the emulator),
 *  3. Pleos TextToSpeech (ON_DEVICE).
 * BGM: `<external files>/bgm/bgm_drive.*` if present, else the place's `res/raw/bgm_<place>_drive`, else `res/raw/bgm_drive_siyeok`.
 *
 * Never on top of other speech: while navigation prompts or any vehicle/system voice play (detected by audio
 * focus loss and by active playback of navigation/assistant/call/alarm/notification/safety/vehicle usages),
 * BGM and the current line pause, and resume a moment after it ends. A TTS line that was cut is said again.
 */
internal class DriveAudio(private val context: Context, private val onVoice: (String) -> Unit) : AutoCloseable {
    private val main = Handler(Looper.getMainLooper())
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val music = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
    private val speech = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).setAudioAttributes(music)
        // Ask for a pause instead of system ducking, so our voice never plays under a prompt.
        .setWillPauseWhenDucked(true)
        .setOnAudioFocusChangeListener { change ->
            main.post {
                focusLost = change != AudioManager.AUDIOFOCUS_GAIN
                Log.i(TAG, "focus $change")
                update()
            }
        }.build()

    private var bgm: MediaPlayer? = null
    private var bgmId = 0
    private var bgmWanted = false
    private var hasFocus = false
    private var focusLost = false
    private var othersSpeaking = false
    private var paused = false
    private var voice: MediaPlayer? = null
    private var current: Pair<String, String>? = null
    private var currentIsTts = false
    private val queue = ArrayDeque<Pair<String, String>>()
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    private val playback = object : AudioManager.AudioPlaybackCallback() {
        override fun onPlaybackConfigChanged(configs: MutableList<AudioPlaybackConfiguration>) {
            val busy = configs.any { it.audioAttributes.usage in INTERRUPTING_USAGES } && !(currentIsTts && current != null)
            main.postDelayed({ if (othersSpeaking != busy) { othersSpeaking = busy; Log.i(TAG, "other speech $busy"); update() } }, if (busy) 0 else RESUME_DELAY_MS)
        }
    }

    private val ttsEvents = object : EventListener {
        override fun onStart() = Unit
        override fun onReady() = Unit
        override fun onDone() { main.post { if (currentIsTts) finishLine() } }
        override fun onStop() = Unit
        override fun onError(type: ErrorType) { main.post { Log.w(TAG, "tts $type"); if (currentIsTts) finishLine() } }
        override fun onUpdatedRms(rms: Double) = Unit
    }

    init {
        audioManager.registerAudioPlaybackCallback(playback, main)
        runCatching {
            val engine = TextToSpeech(context.applicationContext, Mode.ON_DEVICE)
            tts = engine
            engine.initialize(object : OnInitializeListener {
                override fun onSuccess() {
                    main.post {
                        runCatching { engine.setLocale(Locale.KOREA); engine.setAudioAttributes(speech) }
                        ttsReady = true
                    }
                }
                override fun onFailure(type: ErrorType) { main.post { Log.w(TAG, "tts init $type") } }
            })
            engine.addEventListener(ttsEvents) // documented order: after initialize()
        }.onFailure { Log.w(TAG, "tts unavailable: ${it.message}") }
    }

    /** [track]은 장소별 운전 곡 이름(res/raw). 없으면 공용 `bgm_drive_siyeok`을 쓴다. 곡이 바뀌면 새로 연다. */
    fun startBgm(track: String? = null) {
        bgmWanted = true
        requestFocus()
        val id = track?.let(::raw)?.takeIf { it != 0 } ?: raw("bgm_drive_siyeok")
        if (bgm != null && bgmId != id) { runCatching { bgm?.release() }; bgm = null }
        if (bgm == null) bgm = runCatching {
            MediaPlayer().apply {
                setAudioAttributes(music)
                val override = external("bgm", "bgm_drive")
                if (override != null) setDataSource(override.path) else {
                    if (id == 0) return
                    context.resources.openRawResourceFd(id).use { setDataSource(it.fileDescriptor, it.startOffset, it.length) }
                }
                bgmId = id
                isLooping = true
                prepare()
            }
        }.onFailure { Log.w(TAG, "bgm: ${it.message}") }.getOrNull()
        update()
    }

    fun pauseBgm() { bgmWanted = false; update() }
    fun stopBgm() { bgmWanted = false; bgm?.let { if (it.isPlaying) it.pause(); it.seekTo(0) }; releaseFocusIfIdle() }

    /** Narration switch. Off: stop now and drop lines (never kept for later). */
    var voiceEnabled = true
        set(value) { field = value; if (!value) stopVoice() }

    fun speak(id: String, text: String) {
        if (!voiceEnabled) { Log.i(TAG, "narration off, skipped $id"); return }
        queue.addLast(id to text)
        requestFocus()
        if (current == null) next()
    }

    /** Arrival: the current line stops and queued lines are dropped. */
    fun stopVoice() {
        queue.clear()
        voice?.release(); voice = null
        if (currentIsTts) runCatching { tts?.stop() }
        current = null; currentIsTts = false
        update(); releaseFocusIfIdle()
    }

    private fun next() {
        voice?.release(); voice = null
        val line = queue.removeFirstOrNull() ?: run { current = null; currentIsTts = false; update(); releaseFocusIfIdle(); return }
        current = line; currentIsTts = false
        onVoice(line.second)
        if (!prepareFile(line.first)) currentIsTts = true
        update()
    }

    private fun finishLine() { current = null; currentIsTts = false; next() }

    /** Single place that decides what may sound right now. */
    private fun update() {
        val blocked = focusLost || othersSpeaking
        bgm?.let { player ->
            if (bgmWanted && !blocked) { player.setVolume(volume(), volume()); if (!player.isPlaying) player.start() }
            else if (player.isPlaying) player.pause()
        }
        val line = current
        if (line == null) {
            paused = false
            if (!blocked && queue.isNotEmpty()) next()
            return
        }
        if (blocked) {
            if (!paused) {
                paused = true
                voice?.let { if (it.isPlaying) it.pause() }
                if (currentIsTts) {
                    // TTS can't resume mid-sentence: say the whole line again afterwards.
                    runCatching { tts?.stop() }
                    queue.addFirst(line); current = null; currentIsTts = false; ttsStartedFor = null
                }
            }
            return
        }
        paused = false
        val player = voice
        when {
            player != null -> if (!player.isPlaying) player.start()
            currentIsTts -> speakTts(line)
        }
    }

    private var ttsStartedFor: Pair<String, String>? = null
    private fun speakTts(line: Pair<String, String>) {
        if (ttsStartedFor === line) return
        ttsStartedFor = line
        val engine = tts
        if (engine != null && ttsReady) {
            Log.i(TAG, "speak(tts) ${line.first}")
            runCatching { engine.speak(line.second, true) }.onFailure { main.post { finishLine() } }
        } else {
            Log.i(TAG, "no voice for ${line.first} (text shown in notification)")
            main.postDelayed({ if (current === line) finishLine() }, 1_500)
        }
    }

    private fun prepareFile(id: String): Boolean {
        val name = "voice_$id"
        val file = external("voice", name)
        val rawId = raw(name)
        if (file == null && rawId == 0) return false
        return runCatching {
            voice = MediaPlayer().apply {
                setAudioAttributes(speech)
                if (file != null) setDataSource(file.path)
                else context.resources.openRawResourceFd(rawId).use { setDataSource(it.fileDescriptor, it.startOffset, it.length) }
                setOnCompletionListener { main.post { finishLine() } }
                setOnErrorListener { _, _, _ -> main.post { finishLine() }; true }
                prepare()
            }
            Log.i(TAG, "speak(file) $id")
            true
        }.getOrElse { Log.w(TAG, "voice file $id: ${it.message}"); false }
    }

    private fun requestFocus() {
        if (hasFocus) return
        val result = audioManager.requestAudioFocus(focus)
        hasFocus = result != AudioManager.AUDIOFOCUS_REQUEST_FAILED
        // DELAYED (e.g. a call in progress) means wait for a GAIN callback.
        focusLost = result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun releaseFocusIfIdle() {
        if (hasFocus && !bgmWanted && current == null && queue.isEmpty()) {
            audioManager.abandonAudioFocusRequest(focus); hasFocus = false; focusLost = false
        }
    }

    /** Drop-in files: app-specific external storage, or the app's internal files dir (filled with `adb run-as`). */
    private fun external(dir: String, base: String): File? =
        if (!com.team.carescape.BuildConfig.DEBUG) null else listOfNotNull(context.getExternalFilesDir(dir), File(context.filesDir, dir)).firstNotNullOfOrNull { folder ->
            listOf("mp3", "ogg", "m4a", "wav").map { File(folder, "$base.$it") }.firstOrNull { it.isFile }
        }

    private fun raw(name: String): Int = context.resources.getIdentifier(name, "raw", context.packageName)

    private fun volume() = BGM_VOLUME * if (current != null) 0.3f else 1f

    override fun close() {
        queue.clear()
        runCatching { audioManager.unregisterAudioPlaybackCallback(playback) }
        tts?.let { runCatching { it.stop(); it.removeEventListener(ttsEvents); it.release() } }
        tts = null
        voice?.release(); voice = null
        bgm?.release(); bgm = null
        audioManager.abandonAudioFocusRequest(focus)
        main.removeCallbacksAndMessages(null)
    }

    private companion object {
        const val TAG = "DriveAudio"
        /** Low enough not to cover road noise. */
        const val BGM_VOLUME = 0.45f
        const val RESUME_DELAY_MS = 800L
        /** Navigation prompts, assistants, calls, ringtones, alarms, notifications and car system sounds. */
        val INTERRUPTING_USAGES = setOf(
            AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE,
            AudioAttributes.USAGE_ASSISTANT,
            AudioAttributes.USAGE_VOICE_COMMUNICATION,
            AudioAttributes.USAGE_VOICE_COMMUNICATION_SIGNALLING,
            AudioAttributes.USAGE_NOTIFICATION_RINGTONE,
            AudioAttributes.USAGE_ALARM,
            AudioAttributes.USAGE_NOTIFICATION,
            AudioAttributes.USAGE_NOTIFICATION_EVENT,
            AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY,
            1000, // USAGE_EMERGENCY (AAOS system usage)
            1001, // USAGE_SAFETY
            1002, // USAGE_VEHICLE_STATUS
            1003, // USAGE_ANNOUNCEMENT
        )
    }
}
