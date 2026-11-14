package com.team.carescape.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.team.carescape.game.Hotspot
import com.team.carescape.game.StoryState
import com.team.carescape.game.TourGate

/**
 * 장면 그림과 조사할 곳(소품·눌러 볼 자리)만 그린다. [outline]이면 모든 조사할 곳에 테두리를 그린다(배치 확인용).
 */
@Composable
fun ExploreSceneLayer(
    s: StoryState,
    selected: String?,
    glowing: String?,
    ready: Boolean,
    onTouch: (String) -> Unit,
    modifier: Modifier = Modifier,
    outline: Boolean = false,
) {
    val scene = s.chapter.scene ?: return
    val glow by rememberInfiniteTransition(label = "glow").animateFloat(0.25f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "glow-a")
    BoxWithConstraints(modifier.fillMaxSize()) {
        val sx = maxWidth / 1280f
        val sy = maxHeight / 720f
        SceneBackdrop(scene.bg, Modifier.fillMaxSize())
        scene.hotspots.filter { s.isHotspotVisible(it) }.forEach { spot ->
            val done = s.isHotspotDone(spot)
            Box(
                Modifier.offset(sx * spot.x, sy * spot.y).size(sx * spot.w, sy * spot.h)
                    .then(when {
                        glowing == spot.id -> Modifier.border(3.dp, Esc.gold.copy(alpha = glow))
                        selected == spot.id -> Modifier.border(1.dp, Esc.gold.copy(alpha = 0.7f))
                        outline -> Modifier.border(1.dp, if (spot.lockStep != null) Color(0xFFE05A4A) else if (spot.record) Color(0xFF7FD0D0) else Color.White)
                        else -> Modifier
                    })
                    .semantics { contentDescription = "조사할 곳 ${spot.name}" }
                    .clickable(enabled = ready, role = Role.Button) { onTouch(spot.id) }
                    .testTag("hotspot-${spot.id}"),
                contentAlignment = Alignment.Center,
            ) {
                if (spot.prop != null && (!done || spot.keepProp)) ItemArt(spot.prop, Modifier.fillMaxSize())
            }
        }
    }
}

/**
 * 탐색 장면. 장면 곳곳을 눌러 살펴보고, 물건을 찾고, 장면 속 자물쇠를 연다.
 * 퀴즈 목록 대신 플레이어가 무엇을 풀어야 하는지 스스로 발견하게 한다. 정차(P) 중에만 열린다.
 */
@Composable
fun ExploreScreen(s: StoryState, vm: GameViewModel) {
    val scene = s.chapter.scene ?: return
    val ready = vm.tourGate == TourGate.READY && vm.parked
    val steps = s.steps
    val solved = steps.count { s.isStepDone(it.id) }
    // 첫 장면(아직 아무것도 하지 않은 화성행궁)에서는 도움말을 먼저 띄운다. ? 버튼으로 다시 연다.
    val progress = s.puzzleProgressForCurrent
    val untouched = s.chapterIndex == 0 && progress != null && progress.found.isEmpty() && progress.solvedSteps.isEmpty() && progress.wrongAttempts == 0
    var guideOpen by rememberSaveable(vm.storyStartedAtMs) { mutableStateOf(untouched) }
    Box(Modifier.fillMaxSize().testTag("explore")) {
        ExploreSceneLayer(s, vm.exploreSelected, vm.sceneGlow, ready, onTouch = vm::touchHotspot)

        // 위쪽 막대
        Row(
            // 오른쪽 끝은 시연 패널 자리라 비워 둔다.
            Modifier.fillMaxWidth().padding(start = 40.dp, end = 170.dp, top = 26.dp, bottom = 26.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text("Search the Scene · ${s.chapter.place}", style = Esc.cormorant(16))
                Text("풀린 자물쇠 $solved / ${steps.size}", Modifier.testTag("explore-progress"), style = Esc.noto(15, Esc.cream))
            }
            AwakeningGauge(s.awakening.coerceAtMost(10))
            OutlineButton("주변 살피기 · 각성도 +1", vm::sceneHint, Modifier.testTag("explore-hint"), enabled = ready && !vm.saving, minHeight = 44.dp, fontSize = 14)
            OutlineButton("사건 기록부", vm::openCasebook, Modifier.testTag("casebook-open"), enabled = vm.parked, minHeight = 44.dp, fontSize = 14)
            OutlineButton("?", { guideOpen = true }, Modifier.testTag("explore-help"), minWidth = 44.dp, minHeight = 44.dp, fontSize = 16)
            OutlineButton("로비", vm::goLobby, minHeight = 44.dp, fontSize = 14)
        }

        // 아래 살펴보기 상자
        val spot: Hotspot? = scene.hotspots.firstOrNull { it.id == vm.exploreSelected && s.isHotspotVisible(it) }
        Column(
            Modifier.align(Alignment.BottomCenter).padding(start = 60.dp, end = 60.dp, bottom = 18.dp).fillMaxWidth()
                .heightIn(min = 96.dp).background(Color(0xE61E0508)).border(1.dp, Esc.gold).padding(horizontal = 30.dp, vertical = 18.dp)
                .testTag("explore-panel"),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (!ready) {
                Text(tourGateMessage(vm.tourGate), style = Esc.noto(16, Esc.creamMuted))
            } else if (spot == null) {
                Text(vm.storyPuzzleMessage ?: scene.intro, Modifier.testTag("explore-text"), style = Esc.noto(18, Esc.creamMuted, lineHeight = 1.6f))
            } else {
                val done = s.isHotspotDone(spot)
                val who = s.episode.speakerFor(spot.speaker)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (who != null) SpeakerSeal(who, Esc.gold, 34)
                    Text(if (who != null) "${spot.name} · ${who.name}" else spot.name, style = Esc.nanum(19, Esc.gold))
                }
                // 처음 찾은 순간에는 살펴보기 글을, 다시 볼 때는 찾은 뒤 글을 보여 준다.
                val half = !done && spot.thenLock != null && spot.lockStep != null && s.isStepDone(spot.lockStep)
                val text = when {
                    done && vm.exploreFresh != spot.id -> spot.doneLook ?: spot.look
                    half -> spot.midLook ?: spot.look
                    else -> spot.look
                }
                Text(if (who != null) "“$text”" else text, Modifier.testTag("explore-text"), style = Esc.noto(18, Esc.creamMuted, lineHeight = 1.6f))
                vm.storyPuzzleMessage?.let { Text(it, style = Esc.noto(15, Esc.gold)) }
                if (spot.lockStep != null && !done) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ParchmentButton("자세히 보기", { vm.openHotspotLock(spot.id) }, Modifier.testTag("hotspot-open"), enabled = ready, minWidth = 200.dp, minHeight = 48.dp)
                    }
                }
            }
        }

        if (guideOpen) ExploreGuide { guideOpen = false }
    }
}

private val guideTips = listOf(
    "살펴보기" to "장면 곳곳을 눌러 보세요. 표시가 없어도 눌러 볼 수 있는 곳이 숨어 있어요. 무언가를 찾으면 새로운 곳이 나타나기도 해요.",
    "자물쇠" to "잠긴 물건은 ‘자세히 보기’로 풀어요. 순서는 자유예요. 장소의 자물쇠를 모두 풀면 다음 장소로 가요.",
    "사건 기록부와 소지품" to "찾은 문서와 인물 기록은 ‘사건 기록부’에 쌓여요. 얻은 물건은 소지품에 들어가고, 자물쇠에서 ‘쓰기’로 사용해요.",
    "막혔을 때" to "‘주변 살피기’는 손댈 곳 하나를 반짝여 줘요(각성도 +1). 자물쇠마다 힌트도 있어요. 오답은 장소마다 한 번까지 괜찮지만, 그다음부터는 각성도가 올라가요.",
)

/** 탐색 장면 도움말. 처음 장면에서 한 번 뜨고, ? 버튼으로 다시 연다. */
@Composable
private fun ExploreGuide(onClose: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color(0xCC0A0204)).clickable(remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) {}
        .testTag("explore-guide"), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 820.dp).background(Esc.wallDeep).border(1.dp, Esc.gold).padding(5.dp).border(1.dp, Esc.goldDim)
                .padding(horizontal = 36.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("How to Search", style = Esc.pinyon(32))
            Text("장면을 직접 뒤져 단서를 찾으세요", style = Esc.nanum(24))
            guideTips.forEachIndexed { i, (title, body) ->
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("${i + 1}", Modifier.width(24.dp), style = Esc.nanum(20, Esc.gold))
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(title, style = Esc.nanum(18, Esc.gold))
                        Text(body, style = Esc.noto(15, Esc.creamMuted, lineHeight = 1.55f))
                    }
                }
            }
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                ParchmentButton("수색 시작", onClose, Modifier.testTag("explore-guide-close"), minWidth = 220.dp, minHeight = 50.dp)
            }
        }
    }
}
