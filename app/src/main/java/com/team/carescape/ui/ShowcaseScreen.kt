package com.team.carescape.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.team.carescape.game.EndingKind
import com.team.carescape.game.Line
import com.team.carescape.game.SiyeokScenes
import com.team.carescape.game.StoryCatalog
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/** 자동 시연의 한 구간: 장면 대사와, 화면 위에 띄울 기능 설명. */
private class ShowcaseSegment(val title: String, val caption: String, val lines: List<Line>)

/**
 * 자동 시연(심사·소개용). 프롤로그 → 장소별 도착·단서 장면 → 진 엔딩을 대사와 음성으로 자동으로 넘긴다.
 * 저장된 여행·퍼즐 진행은 읽지도 바꾸지도 않는다. 정차(P) 중에만 열리고, 정차가 풀리면 닫힌다.
 */
@Composable
fun ShowcaseScreen(vm: GameViewModel) {
    val episode = remember { StoryCatalog.episodeFor(StoryCatalog.CURRENT_FLOW_VERSION) }
    val segments = remember {
        val carPuzzle = mapOf(1 to "방향지시등으로 수문을 여닫는 차량 조작 퍼즐", 3 to "실내 독서등으로 봉화를 올리는 차량 조작 퍼즐", 4 to "창문을 내려 소리를 듣는 차량 조작 퍼즐")
        buildList {
            add(ShowcaseSegment("Prologue", "테마마다 캐릭터 음성으로 사건이 시작돼요", SiyeokScenes.prologue.take(4)))
            episode.chapters.forEachIndexed { i, c ->
                if (c.arrivalScene.isNotEmpty()) add(ShowcaseSegment(
                    "${i + 1}. ${c.place} · 도착",
                    "실제 장소에 도착해 15분 관광한 뒤, 차로 돌아와 장면을 뒤지며 자물쇠를 풀어요",
                    c.arrivalScene.take(3),
                ))
                if (c.revealScene.isNotEmpty()) add(ShowcaseSegment(
                    "${i + 1}. ${c.place} · 단서",
                    carPuzzle[i]?.let { "$it · 풀면 다음 단서가 열려요" } ?: "자물쇠를 모두 풀면 다음 장소의 단서가 열려요",
                    c.revealScene.take(3),
                ))
            }
            SiyeokScenes.endingDrama[EndingKind.TRUE]?.let {
                add(ShowcaseSegment("Ending", "추리 결과에 따라 진·일반·배드 엔딩으로 갈려요", it.takeLast(6)))
            }
        }
    }
    var seg by rememberSaveable { mutableIntStateOf(0) }
    var line by rememberSaveable { mutableIntStateOf(0) }
    var paused by rememberSaveable { mutableStateOf(false) }
    var finished by rememberSaveable { mutableStateOf(false) }

    fun advance() {
        val s = segments[seg]
        when {
            line < s.lines.lastIndex -> line++
            seg < segments.lastIndex -> { seg++; line = 0 }
            else -> { finished = true; vm.stopAudio() }
        }
    }

    // 정차가 풀리면 바로 닫는다.
    LaunchedEffect(vm.parked) { if (!vm.parked) vm.stopShowcase() }
    // 대사가 끝날 때까지 기다렸다가 자동으로 넘긴다(최대 25초).
    LaunchedEffect(seg, line, paused, finished) {
        if (paused || finished) return@LaunchedEffect
        delay(2_500)
        withTimeoutOrNull(25_000) { snapshotFlow { vm.voice.playing || vm.sdkStatus.speaking }.first { !it } }
        delay(900)
        advance()
    }

    Box(Modifier.fillMaxSize().testTag("showcase")) {
        if (finished) ShowcaseEnd(vm, onReplay = { seg = 0; line = 0; finished = false })
        else {
            val s = segments[seg]
            ScenePlayer(
                episode, s.lines, line.coerceIn(0, s.lines.lastIndex), s.title,
                onNext = ::advance,
                onSkip = { if (seg < segments.lastIndex) { seg++; line = 0 } else { finished = true; vm.stopAudio() } },
                onPrev = { if (line > 0) line-- },
                onLine = vm::speakLine,
                lastLabel = "다음",
            )
            Text(
                s.caption,
                Modifier.align(Alignment.BottomCenter).padding(bottom = 250.dp).widthIn(max = 900.dp)
                    .background(Color(0xCC140F0C)).border(1.dp, Esc.gold).padding(horizontal = 18.dp, vertical = 10.dp),
                style = Esc.nanum(20, Esc.cream), textAlign = TextAlign.Center,
            )
        }
        Row(
            Modifier.align(Alignment.TopCenter).padding(top = 8.dp).background(Color(0xE6140F0C)).border(1.dp, Esc.gold)
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("자동 시연 · ${(seg + 1).coerceAtMost(segments.size)} / ${segments.size} · 저장된 진행에는 영향 없음", style = Esc.noto(14, Esc.gold))
            if (!finished) OutlineButton(if (paused) "계속" else "일시정지", { paused = !paused; if (paused) vm.stopAudio() },
                Modifier.testTag("showcase-pause"), minHeight = 40.dp, fontSize = 14)
            OutlineButton("종료", vm::stopShowcase, Modifier.testTag("showcase-stop"), minHeight = 40.dp, fontSize = 14)
        }
    }
}

@Composable
private fun ShowcaseEnd(vm: GameViewModel, onReplay: () -> Unit) {
    GoldFrame {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)) {
            Text("Escape Road", style = Esc.pinyon(48))
            Text("내 차로 떠나는 방탈출 여행", style = Esc.nanum(34))
            Column(Modifier.widthIn(max = 900.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    "이동 중에는 화면 없이 음성으로 이야기가 이어져요",
                    "실제 장소에 도착해 관광하고, 차로 돌아와 장면을 뒤지며 퍼즐을 풀어요",
                    "방향지시등·독서등·창문 같은 내 차의 조작이 자물쇠가 돼요",
                    "힌트와 오답, 추리 결과에 따라 진·일반·배드 엔딩으로 갈려요",
                    "여행 도우미로 같은 코스의 일정·맛집·주차와 내 차 주행 가능 거리를 확인해요",
                ).forEach { Text("· $it", style = Esc.noto(18, Esc.creamSoft)) }
            }
            Text("직접 플레이는 로비의 ‘사건 고르기’에서 시작해요.", style = Esc.noto(15, Esc.gold))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                OutlineButton("다시 보기", onReplay, minHeight = 52.dp)
                ParchmentButton("로비로", vm::stopShowcase, Modifier.testTag("showcase-done"), minWidth = 200.dp)
            }
        }
    }
}
