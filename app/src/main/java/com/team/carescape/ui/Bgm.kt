package com.team.carescape.ui

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.team.carescape.game.BgmCatalog
import com.team.carescape.game.BgmCue
import com.team.carescape.game.Siyeok
import com.team.carescape.game.StoryPhase
import kotlinx.coroutines.delay

private const val FULL = 0.55f
/** 음성 안내·녹음 대사가 나오는 동안은 대사가 묻히지 않게 크게 줄인다. */
private const val DUCKED = 0.04f

/** res/raw의 배경음악 한 곡을 재생한다. 소리 크기는 [fadeTo]로 천천히 바꾼다. 메인 스레드에서만 쓴다. */
private class BgmPlayer(private val context: Context) {
    private var player: MediaPlayer? = null
    var current: String? = null
        private set
    private var level = 0f

    @SuppressLint("DiscouragedApi")
    private fun raw(name: String) = context.resources.getIdentifier(name, "raw", context.packageName)

    /** 곡을 소리 0으로 시작한다. 파일이 없으면 false. */
    fun start(cue: BgmCue, onDone: () -> Unit): Boolean {
        stop()
        val id = raw(cue.name).takeIf { it != 0 } ?: cue.alt?.let(::raw)?.takeIf { it != 0 } ?: return false
        val mp = runCatching {
            MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build(),
                )
                context.resources.openRawResourceFd(id).use { setDataSource(it.fileDescriptor, it.startOffset, it.length) }
                isLooping = cue.loop
                setVolume(0f, 0f)
                setOnCompletionListener { if (!cue.loop) onDone() }
                prepare()
                start()
            }
        }.getOrNull() ?: return false
        player = mp; current = cue.name; level = 0f
        return true
    }

    fun pause() { runCatching { player?.takeIf { it.isPlaying }?.pause() } }
    fun resume() { runCatching { player?.takeIf { !it.isPlaying }?.start() } }

    suspend fun fadeTo(target: Float, millis: Long = 700) {
        val mp = player ?: return
        val from = level
        val steps = (millis / 35).coerceAtLeast(1)
        for (k in 1..steps) {
            level = from + (target - from) * k / steps
            runCatching { mp.setVolume(level, level) }
            delay(35)
        }
    }

    fun stop() {
        runCatching { player?.release() }
        player = null; current = null; level = 0f
    }
}

/** 지금 화면에 맞는 배경음악. 로비 쪽은 타이틀, 프롤로그는 지금 줄의 배경, 여행 중에는 장소와 단계로 고른다. */
fun bgmFor(vm: GameViewModel): BgmCue? = when (vm.screen) {
    Screen.LOBBY, Screen.THEME_SELECT, Screen.BRIEFING, Screen.STORY_BRIEFING -> BgmCatalog.title
    Screen.STORY_PROLOGUE -> vm.selectedCard.story?.takeIf { it.id == Siyeok.episode.id }?.let { ep ->
        val page = vm.prologuePage.coerceIn(0, (ep.prologueScene.size - 1).coerceAtLeast(0))
        BgmCatalog.prologue(ep.prologueScene.take(page + 1).lastOrNull { it.bg != null }?.bg)
    }
    // 기어 D로 이동하는 동안은 주행 안내 서비스(drive 패키지)가 자기 BGM을 튼다. 둘이 겹치지 않게 앱 쪽은 쉰다.
    Screen.STORY -> vm.story?.takeUnless { it.phase == StoryPhase.EN_ROUTE && !vm.parked }?.let(BgmCatalog::story)
    else -> null
}

/**
 * 화면이 고른 배경음악을 튼다. 곡이 바뀌면 앞 곡을 줄이고 새 곡을 키운다.
 * [active]가 아니면(앱이 뒤로 가거나 저장소를 읽는 중) 멈췄다가 같은 곡이면 이어서 튼다.
 * 오디오 포커스는 잡지 않는다. 음성 안내가 포커스를 잡으므로, 그동안은 스스로 소리를 줄인다.
 */
@Composable
fun BgmHost(cue: BgmCue?, ducked: Boolean, active: Boolean) {
    val context = LocalContext.current.applicationContext
    val player = remember { BgmPlayer(context) }
    DisposableEffect(player) { onDispose { player.stop() } }
    // 반복하지 않는 곡이 끝나면 이어 틀 곡. 화면이 다른 곡을 고르면 지운다.
    var follow by remember(cue) { mutableStateOf<BgmCue?>(null) }
    val target = follow ?: cue
    LaunchedEffect(target, active, ducked) {
        if (target == null) { player.fadeTo(0f); player.stop(); return@LaunchedEffect }
        if (!active) { player.fadeTo(0f, 300); player.pause(); return@LaunchedEffect }
        if (player.current != target.name) {
            player.fadeTo(0f)
            val next = target.then
            if (!player.start(target) { if (next != null) follow = BgmCue(next) }) return@LaunchedEffect
        } else player.resume()
        player.fadeTo(if (ducked) DUCKED else FULL, if (ducked) 400 else 900)
    }
}
