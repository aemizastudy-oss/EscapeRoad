package com.team.carescape.ui

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.audiofx.LoudnessEnhancer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.team.carescape.game.VoiceCatalog

/**
 * 녹음한 대사(res/raw/voice_<id>.mp3)를 재생한다. 파일이 없는 줄은 [has]가 false라 기기 음성(TTS)으로 넘긴다.
 * 한 번에 한 줄만 재생하고, 새 줄이 오면 앞 줄을 멈춘다. 메인 스레드에서만 쓴다.
 */
class VoicePlayer(private val context: Context) {
    private var player: MediaPlayer? = null
    private var enhancer: LoudnessEnhancer? = null
    /** 지금 녹음 대사가 나오는 중인지(배경음악을 줄이는 데 쓴다). */
    var playing by mutableStateOf(false)
        private set

    @SuppressLint("DiscouragedApi")
    private fun raw(text: String): Int =
        VoiceCatalog.idFor(text)?.let { context.resources.getIdentifier("voice_$it", "raw", context.packageName) } ?: 0

    fun has(text: String): Boolean = raw(text) != 0

    fun play(text: String): Boolean {
        stop()
        val id = raw(text).takeIf { it != 0 } ?: return false
        val mp = runCatching {
            MediaPlayer().apply {
                // 기기 음성 안내(TTS)와 같은 안내 음성 채널로 내보낸다. 게임 소리 채널은 차량에서 작게 들렸다.
                setAudioAttributes(
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build(),
                )
                setVolume(1f, 1f)
                context.resources.openRawResourceFd(id).use { setDataSource(it.fileDescriptor, it.startOffset, it.length) }
                setOnCompletionListener { stop() }
                prepare()
                start()
            }
        }.getOrNull() ?: return false
        // 녹음은 평균 −16~−22 dBFS라 차 안에서 작게 들린다. 약 15 dB 키운다(내장 리미터가 찢어짐을 막는다).
        enhancer = runCatching { LoudnessEnhancer(mp.audioSessionId).apply { setTargetGain(GAIN_MB); enabled = true } }.getOrNull()
        player = mp
        playing = true
        return true
    }

    fun stop() {
        runCatching { enhancer?.release() }
        enhancer = null
        runCatching { player?.release() }
        player = null
        playing = false
    }
}

/** 녹음 대사를 키우는 양(밀리벨). 1500 = 15 dB. */
private const val GAIN_MB = 1500
