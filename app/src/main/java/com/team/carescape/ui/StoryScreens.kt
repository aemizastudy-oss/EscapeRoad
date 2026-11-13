package com.team.carescape.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.team.carescape.game.StoryPhase
import com.team.carescape.game.StoryState
import kotlinx.coroutines.delay

/**
 * 시안에만 있는 장소별 표기(영문 태그·편지 이름·도착 제목·지도 메모·단서 뜻).
 * 이야기 원문은 [com.team.carescape.game.Siyeok]에 있고, 여기에는 화면 장식 문구만 둔다.
 */
private data class ChapterLook(
    val tag: String,
    val letter: String,
    val script: String,
    val headline: String,
    val body: String,
    val routeNote: String,
    val listNote: String,
    val solvedNote: String,
    val currentNote: String,
    val meaning: String,
    val node: Offset,
)

private val looks = listOf(
    ChapterLook(
        "Hwaseong · 1795", "왕의 밀서", "Welcome to 1795", "1795년 윤2월,\n화성행궁에 도착했습니다",
        "사흘 뒤면 혜경궁의 회갑연이 열립니다. 어젯밤 쓰러진 궁녀 이야기에 모두 목소리를 낮추고 있습니다. 내의원 의원이 속삭입니다. \"잔치가 열릴 전각에 전하의 글을 숨겨 두었소. 그곳을 천천히 둘러보시오.\"",
        "왕의 밀서와 비방의 첫 장", "왕의 밀서", "왕의 밀서 · 비방 첫 장", "행궁 · 수사 중",
        "백신을 전달할 곳은 물의 근원", Offset(120f, 500f),
    ),
    ChapterLook(
        "Hwahongmun · 1795", "병사의 전갈", "Welcome to 1795", "북쪽 들판을 앞두고,\n화홍문에 도착했습니다",
        "그것들이 몰려오고 있습니다. 수문을 지키던 장용영 병사가 외칩니다. \"놈들은 흐르는 물을 건너지 못하오! 수문만 지키면 되오!\"",
        "일곱 수문과 첫 번째 재료", "일곱 수문", "재료 1 · 흐르는 물", "수문 · 수사 중",
        "물의 근원은 광교산", Offset(300f, 380f),
    ),
    ChapterLook(
        "Manseokgeo · 1795", "노인의 부탁", "Welcome to 1795", "그해 봄 쌓은 둑,\n만석거에 도착했습니다",
        "저수지의 둑 위로 둔전 마을 사람들이 피해 올라와 있습니다. 한 노인이 당신을 붙잡습니다. \"전하께서 우리를 위해 만드신 물이오. 이 물만 있으면 놈들이 들어오지 못하오.\"",
        "왕의 저수지와 두 번째 재료", "왕의 저수지", "재료 2 · 곡식", "만석거 · 수사 중",
        "광교산 물이 모이는 아래쪽", Offset(300f, 230f),
    ),
    ChapterLook(
        "Museum · 2026", "찢어진 기록", "Back to 2026", "2026년,\n수원광교박물관에 도착했습니다",
        "조사국: \"요원, 비방의 마지막 장은 의원이 찢어 여러 기록 사이에 숨겼다. 이 시대에 남은 기록에서 찾아야 한다.\"",
        "흩어진 기록과 비방의 마지막 장", "흩어진 기록", "비방 마지막 장 · 백신", "박물관 · 수사 중",
        "1929년에 생긴 아랫방죽", Offset(480f, 260f),
    ),
    ChapterLook(
        "Gwanggyo · 2026", "마지막 약병", "Back to 2026", "해 질 무렵,\n원천호수에 도착했습니다",
        "의원이 말한 그 골짜기는 지금 커다란 호수가 되어 있습니다. 해가 지고, 물가에서 무언가 움직이기 시작합니다. 창문을 내려 물소리를 들어 보세요.",
        "최종 추리에 성공하면 공개", "최종 추리", "백신 전달", "원천호수 · 수사 중",
        "", Offset(610f, 90f),
    ),
)

/** 백신 재료(물·곡식)만 센다. 완성한 백신은 재료가 아니다. */
private fun ingredientCount(s: StoryState) = s.items.count { it != "완성한 백신" }

private val Int.spx get() = this.sp

// ───────────────────────── 3. 사건 브리핑 (Briefing.dc.html) ─────────────────────────

@Composable
fun StoryBriefingScreen(vm: GameViewModel) {
    val ep = vm.selectedCard.story ?: return
    GoldFrame(padTop = 26.dp, padH = 36.dp, padBottom = 24.dp) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(36.dp)) {
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .rotate(-0.6f)
                    .shadow(18.dp)
                    .parchment()
                    .drawBehind {
                        val i1 = 10.dp.toPx(); val i2 = 15.dp.toPx(); val w = 1.dp.toPx()
                        drawRect(Esc.parchEdge, Offset(i1, i1), Size(size.width - 2 * i1, size.height - 2 * i1), style = Stroke(w))
                        drawRect(Esc.parchEdge, Offset(i2, i2), Size(size.width - 2 * i2, size.height - 2 * i2), style = Stroke(w))
                    }
                    .padding(start = 44.dp, end = 44.dp, top = 34.dp, bottom = 26.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row {
                    Text("Case File No. 03", style = Esc.cormorant(15, Esc.inkLabel))
                    Box(Modifier.weight(1f))
                    Text("시간여행 미스터리 · 1795", style = Esc.cormorant(15, Esc.inkLabel))
                }
                Text("${ep.title}, ${ep.subtitle}", Modifier.padding(top = 4.dp), style = Esc.nanum(32, Esc.ink))
                Text("Dear Agent,", style = Esc.pinyon(30, Esc.wine, 1.2f))
                Text(
                    "1795년 윤2월, 혜경궁의 회갑연을 앞둔 화성에 정체불명의 역병이 돌았다. 죽은 자가 다시 일어나는 병, 시역(屍疫)이다. 내의원이 치료제의 비방을 만들었지만 기록은 흩어졌고, 약은 끝내 쓰이지 못했다. 그 결과 역사가 무너지고 있다.",
                    style = Esc.noto(16, Esc.inkBody, lineHeight = 1.8f),
                )
                Text(
                    "1795년으로 건너가 비방과 재료를 모으고, 백신을 완성해 전달하라. 첫 번째 실마리는 왕의 어머니를 위한 잔치가 열린 곳에 있다.",
                    style = Esc.noto(16, Esc.inkBody, lineHeight = 1.8f),
                )
                Box(Modifier.weight(1f))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("— 시간 조사국", style = Esc.noto(15, Esc.inkSoft))
                    Box(Modifier.weight(1f))
                    WaxSeal(Modifier.size(70.dp), glyph = "水")
                }
            }
            Column(Modifier.width(380.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("The Route", style = Esc.pinyon(28))
                    Text("수사 경로", style = Esc.nanum(20))
                }
                Column {
                    ep.chapters.forEachIndexed { i, c ->
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                NumeralCircle(i + 1, 32.dp, Esc.gold, 15)
                                if (i < ep.chapters.lastIndex) Box(Modifier.width(1.dp).height(12.dp).background(Esc.gold))
                            }
                            Column(Modifier.padding(top = 4.dp)) {
                                Text(if (i == ep.finalChapterIndex) "???" else c.place, style = Esc.noto(16, weight = FontWeight.Bold, lineHeight = 1.3f))
                                Text(looks[i].routeNote, style = Esc.noto(13, Esc.label, lineHeight = 1.3f))
                            }
                        }
                    }
                }
                Column(
                    Modifier.fillMaxWidth().drawBehind {
                        val c = Esc.gold.copy(alpha = 0.5f); val w = 1.dp.toPx()
                        drawLine(c, Offset(0f, 0f), Offset(size.width, 0f), w)
                        drawLine(c, Offset(0f, size.height), Offset(size.width, size.height), w)
                    }.padding(vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text("Rules of the Plague", style = Esc.cormorant(13))
                    listOf("시귀는 흐르는 물을 건너지 못한다", "시귀는 해가 지면 깨어난다", "시귀는 소리에 몰려든다").forEach {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("·", style = Esc.noto(14, Esc.gold))
                            Text(it, style = Esc.noto(14))
                        }
                    }
                }
                Row {
                    Text("낮에 출발 · 해 질 무렵 도착", style = Esc.noto(14, Esc.creamSoft))
                    Box(Modifier.weight(1f))
                    Text(diamonds(5), style = Esc.noto(14, Esc.gold).copy(letterSpacing = 3.spx))
                }
                Box(Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlineButton("돌아가기", vm::goThemeSelect, minWidth = 110.dp)
                    ParchmentButton("시간 이동 시작", vm::goPrologue, Modifier.weight(1f), minWidth = 150.dp)
                }
            }
        }
    }
}

@Composable
private fun NumeralCircle(n: Int, size: Dp, color: Color, fontSize: Int) {
    Box(Modifier.size(size).border(1.dp, color, CircleShape), contentAlignment = Alignment.Center) {
        Text(roman(n), style = Esc.cormorant(fontSize, color, spacing = 0f, weight = FontWeight.Bold))
    }
}

// ───────────────────────── 스토리 프롤로그 (Story.dc.html) ─────────────────────────

private val prologueArches = (0 until 7).map { k -> val x = 420 + 62 * k; svgPath("M$x 520 V480 A26 26 0 0 1 ${x + 52} 480 V520") }
private val prologueRoof = svgPath("M490 384 Q640 320 790 384 Z")
private val prologueWall = svgPath("M0 470 L380 470 M900 470 L1280 470")
private val prologueCrenel = svgPath("M0 470 L0 450 L40 450 L40 470 M60 470 L60 450 L100 450 L100 470 M1180 470 L1180 450 L1220 450 L1220 470")
private val prologueWaves = listOf(560, 590, 620).map { y -> svgPath("M0 $y Q160 ${y - 12} 320 $y T640 $y T960 $y T1280 $y") }

@Composable
fun StoryPrologueScreen(vm: GameViewModel) {
    val ep = vm.selectedCard.story ?: return
    val pages = prologuePagesOf(ep)
    val page = vm.prologuePage.coerceIn(0, pages.lastIndex)
    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            inDesignUnits {
                val gold = Esc.gold
                drawRect(Color(0xFF1E0508).copy(alpha = 0.5f), size = Size(1280f, 720f))
                drawCircle(Color(0xFFE8C9A0).copy(alpha = 0.85f), 54f, Offset(1040f, 120f))
                drawRect(Color(0xFF2A070B), Offset(380f, 440f), Size(520f, 80f))
                drawRect(gold.copy(alpha = 0.6f), Offset(380f, 440f), Size(520f, 80f), style = Stroke(1.5f))
                prologueArches.forEach { drawPath(it, Color(0xFF1E0508)); drawPath(it, gold.copy(alpha = 0.5f), style = Stroke(1.2f)) }
                drawRect(Color(0xFF2A070B), Offset(520f, 380f), Size(240f, 60f))
                drawRect(gold.copy(alpha = 0.6f), Offset(520f, 380f), Size(240f, 60f), style = Stroke(1.5f))
                drawPath(prologueRoof, Color(0xFF3A0C10)); drawPath(prologueRoof, gold.copy(alpha = 0.7f), style = Stroke(1.5f))
                drawPath(prologueWall, gold.copy(alpha = 0.4f), style = Stroke(1.5f))
                drawPath(prologueCrenel, gold.copy(alpha = 0.35f), style = Stroke(1f))
                prologueWaves.forEach { drawPath(it, Esc.cream.copy(alpha = 0.35f), style = Stroke(1.5f)) }
                val fig = Color(0xFF0E0204).copy(alpha = 0.85f)
                listOf(Triple(180f, 452f, 430f), Triple(230f, 456f, 437f), Triple(300f, 454f, 433f)).forEachIndexed { i, (x, body, head) ->
                    val rx = if (i == 1) 6f else 7f
                    val ry = when (i) { 0 -> 16f; 1 -> 14f; else -> 15f }
                    drawOval(fig, Offset(x - rx, body - ry), Size(rx * 2, ry * 2))
                    drawCircle(fig, if (i == 1) 5f else 6f, Offset(x, head))
                }
            }
        }
        Column(Modifier.offset(56.dp, 46.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Prologue · 1795", style = Esc.cormorant(16))
            Text("The Plague of the Dead", style = Esc.pinyon(40, Esc.cream, 1.1f))
        }
        Box(Modifier.align(Alignment.TopEnd).padding(top = 44.dp, end = 50.dp)) {
            OutlineButton("건너뛰기", vm::startStory, minHeight = 46.dp, fontSize = 16)
        }
        // 아래 내레이션 상자: 880 폭, 금테 + 바깥 5px 금선
        Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 38.dp).border(1.dp, Esc.gold).padding(5.dp)) {
            Column(
                Modifier.width(880.dp).background(Color(0xE01E0508)).border(1.dp, Esc.gold).padding(start = 40.dp, end = 40.dp, top = 24.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(pages[page], Modifier.fillMaxWidth(), style = Esc.nanum(22, weight = FontWeight.Bold, lineHeight = 1.85f), textAlign = TextAlign.Center)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        pages.indices.forEach { i -> Box(Modifier.size(26.dp, 4.dp).background(if (i == page) Esc.gold else Esc.gold.copy(alpha = 0.35f))) }
                    }
                    Text("게임 마스터 내레이션 재생 중", Modifier.weight(1f), style = Esc.noto(14, Esc.label))
                    ParchmentButton("다음", vm::prologueNext, minWidth = 150.dp, minHeight = 50.dp)
                }
            }
        }
    }
}

// ───────────────────────── 장소별 진행 ─────────────────────────

@Composable
fun StoryScreen(vm: GameViewModel, narrator: Narrator?) {
    val s = vm.story ?: return
    if (vm.showMap) {
        TripMapScreen(s, vm)
        return
    }
    when (s.phase) {
        StoryPhase.EN_ROUTE -> StoryDrivingScreen(s) { EnRouteActions(s, vm, narrator) }
        StoryPhase.ARRIVED -> ArrivalScreen(s, vm)
        StoryPhase.EXPLORING -> ExploringScreen(s, vm)
        StoryPhase.REVEALED -> RevealScreen(s, vm)
        StoryPhase.FINAL_REVEAL -> FinalRevealScreen(s, vm)
        StoryPhase.ENDED -> EndingScreen(s, vm)
    }
}

// ---- 로드트립 지도 (TripMap.dc.html) ----

private val roadFull = svgPath("M120 500 C 200 470, 220 420, 300 380 S 330 260, 300 230 S 420 180, 480 260 S 560 140, 610 90")
private val roadSegments = listOf(
    "M120 500 C 200 470, 220 420, 300 380",
    "M300 380 C 380 340, 330 260, 300 230",
    "M300 230 C 270 200, 420 180, 480 260",
    "M480 260 C 540 340, 560 140, 610 90",
).map(::svgPath)
private val waters = listOf(
    "M700 40 C 640 120, 600 160, 560 230 S 470 330, 420 380 S 330 470, 300 560",
    "M700 40 C 690 150, 650 220, 590 300",
).map(::svgPath)

private enum class NodeState { SOLVED, CURRENT, LOCKED }

@Composable
private fun TripMapScreen(s: StoryState, vm: GameViewModel) {
    val current = s.chapterIndex
    fun state(i: Int) = when { i < current -> NodeState.SOLVED; i == current -> NodeState.CURRENT; else -> NodeState.LOCKED }
    val finalShown = current >= s.episode.finalChapterIndex
    GoldFrame(padTop = 24.dp, padH = 30.dp, padBottom = 24.dp) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            Box(
                Modifier.width(800.dp).fillMaxHeight().shadow(18.dp).parchment().drawBehind {
                    val i = 10.dp.toPx()
                    drawRect(Esc.parchEdge, Offset(i, i), Size(size.width - 2 * i, size.height - 2 * i), style = Stroke(1.dp.toPx()))
                },
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(760.dp, 580.dp)) {
                    Canvas(Modifier.fillMaxSize()) {
                        inDesignUnits {
                            waters.forEach { drawPath(it, Color(0xFF6F8AA0).copy(alpha = 0.7f), style = Stroke(2f)) }
                            listOf(Triple(610f, 90f, 46f to 22f), Triple(560f, 140f, 34f to 16f), Triple(300f, 230f, 36f to 20f)).forEach { (x, y, r) ->
                                drawOval(Color(0xFF9DB3C2).copy(alpha = 0.45f), Offset(x - r.first, y - r.second), Size(r.first * 2, r.second * 2))
                                drawOval(Color(0xFF6F8AA0), Offset(x - r.first, y - r.second), Size(r.first * 2, r.second * 2), style = Stroke(1.2f))
                            }
                            drawPath(roadFull, Color(0xFFD8C9A6), style = Stroke(12f, cap = StrokeCap.Round))
                            roadSegments.take(current.coerceAtMost(roadSegments.size)).forEach {
                                drawPath(it, Esc.seal, style = Stroke(3.5f, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(2f, 9f))))
                            }
                            drawCompass(Offset(110f, 110f))
                            looks.forEachIndexed { i, l -> drawNode(l.node, state(i)) }
                        }
                    }
                    Text("N", Modifier.offset(104.dp, 40.dp), style = Esc.cormorant(16, Color(0xFF5A4A36), 0f, FontWeight.Bold))
                    MapLabel("광교산", null, Offset(660f, 30f), Color(0xFF5A4A36), 14)
                    looks.forEachIndexed { i, l ->
                        val st = state(i)
                        val isFinal = i == s.episode.finalChapterIndex
                        val name = when {
                            isFinal && !finalShown -> "???"
                            st == NodeState.LOCKED -> "장소 ${i + 1}"
                            else -> s.episode.chapters[i].place
                        }
                        val note = when (st) {
                            NodeState.SOLVED -> l.solvedNote
                            NodeState.CURRENT -> l.currentNote
                            NodeState.LOCKED -> if (isFinal) "단서를 모으면 공개" else "봉인됨"
                        }
                        val color = when (st) { NodeState.SOLVED -> Esc.ink; NodeState.CURRENT -> Esc.seal; NodeState.LOCKED -> Color(0xFF6A5A44) }
                        MapLabel(name, note, Offset(l.node.x + 26f, l.node.y - 4f), color, 17)
                    }
                }
                Text("Follow the Water", Modifier.align(Alignment.TopStart).padding(start = 30.dp, top = 18.dp), style = Esc.pinyon(30, Esc.wine))
            }
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Case No. 03", style = Esc.cormorant(14))
                Text("${s.episode.title},\n${s.episode.subtitle}", Modifier.padding(bottom = 4.dp), style = Esc.nanum(21, lineHeight = 1.35f))
                AwakeningGauge(0)
                Column {
                    looks.forEachIndexed { i, l ->
                        val st = state(i)
                        val c = when (st) { NodeState.SOLVED -> Esc.gold; NodeState.CURRENT -> Esc.cream; NodeState.LOCKED -> Esc.locked }
                        val isFinal = i == s.episode.finalChapterIndex
                        Row(
                            Modifier.fillMaxWidth().drawBehind { drawLine(Esc.goldFaint, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx()) }.padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            NumeralCircle(i + 1, 32.dp, c, 15)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                val name = if (st == NodeState.LOCKED) (if (isFinal && !finalShown) "???" else "장소 ${i + 1}") else s.episode.chapters[i].place
                                Text(name, style = Esc.noto(16, c, FontWeight.Bold, 1.2f))
                                Text(if (st == NodeState.LOCKED && !isFinal) "?" else l.listNote, style = Esc.noto(13, Esc.label, lineHeight = 1.2f))
                            }
                            Text(when (st) { NodeState.SOLVED -> "해결"; NodeState.CURRENT -> "다음 목적지"; NodeState.LOCKED -> "봉인됨" }, style = Esc.noto(14, c))
                        }
                    }
                }
                Box(Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlineButton("닫기", vm::goLobby, minWidth = 90.dp)
                    ParchmentButton("다음 장소 길 안내", vm::startGuidance, Modifier.weight(1f), minWidth = 150.dp)
                }
            }
        }
    }
}

/** SVG 텍스트처럼 [baseline] 좌표(시안 px)에 이름과 메모를 놓는다. */
@Composable
private fun MapLabel(name: String, note: String?, baseline: Offset, color: Color, size: Int) {
    Column(Modifier.offset(baseline.x.dp, (baseline.y - size * 0.95f).dp)) {
        Text(name, style = Esc.nanum(size, color, lineHeight = 1.15f))
        if (note != null) Text(note, style = Esc.noto(13, Color(0xFF5A4A36), lineHeight = 1.2f))
    }
}

private fun DrawScope.drawCompass(c: Offset) {
    drawCircle(Color(0xFF5A4A36), 40f, c, style = Stroke(1.2f))
    drawPath(Path().apply { moveTo(c.x, c.y - 50); lineTo(c.x + 7, c.y); lineTo(c.x, c.y + 50); lineTo(c.x - 7, c.y); close() }, Color(0xFF5A4A36))
    drawPath(Path().apply { moveTo(c.x - 50, c.y); lineTo(c.x, c.y - 7); lineTo(c.x + 50, c.y); lineTo(c.x, c.y + 7); close() }, Color(0xFF8C7556))
}

private fun DrawScope.drawNode(p: Offset, st: NodeState) {
    when (st) {
        NodeState.SOLVED -> {
            drawCircle(Esc.seal, 16f, p)
            drawPath(Path().apply { moveTo(p.x - 5, p.y); lineTo(p.x - 1, p.y + 4); lineTo(p.x + 6, p.y - 4) }, Esc.cream, style = Stroke(2.4f, cap = StrokeCap.Round))
        }
        NodeState.CURRENT -> {
            drawCircle(Esc.seal, 28f, p, style = Stroke(2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 5f))))
            drawCircle(Esc.gold, 16f, p)
            drawCircle(Color(0xFF6B4A1C), 16f, p, style = Stroke(2f))
            drawCircle(Esc.btnInk, 5f, p)
        }
        NodeState.LOCKED -> {
            val c = Color(0xFF7E6A4E)
            drawCircle(c, 14f, p, style = Stroke(2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f))))
            drawPath(svgPath("M${p.x - 4} ${p.y - 4} Q${p.x - 4} ${p.y - 8} ${p.x} ${p.y - 8} Q${p.x + 4} ${p.y - 8} ${p.x + 4} ${p.y - 4} Q${p.x + 4} ${p.y - 1} ${p.x} ${p.y + 1} L${p.x} ${p.y + 3}"), c, style = Stroke(1.8f, cap = StrokeCap.Round))
            drawCircle(c, 1.4f, Offset(p.x, p.y + 7))
        }
    }
}

// ---- 4. 주행 중 (Driving.dc.html) ----

/** 시안의 주행 중 화면. [parkedActions]가 있으면 정차 중 출발 전 버튼을 붙인다(주행 중에는 null). */
@Composable
fun StoryDrivingScreen(s: StoryState?, parkedActions: (@Composable () -> Unit)? = null) {
    GoldFrame(padTop = 40.dp, padH = 40.dp, padBottom = 40.dp, fill = Color(0xB81E0508)) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(22.dp, Alignment.CenterVertically)) {
            Text("En Route · 이동 중", style = Esc.cormorant(18, spacing = 0.3f))
            AwakeningGauge(0)
            Envelope()
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("다음 목적지", style = Esc.noto(22, Esc.creamSoft))
                Text(s?.chapter?.place ?: "주차 후 열람", style = Esc.nanum(58, lineHeight = 1.2f))
            }
            val era = if ((s?.chapterIndex ?: 0) >= 3) "2026년" else "1795년"
            Text(
                if (parkedActions == null) "${era}의 이야기가 들려오는 중 · 도착해서 주차하면 그 시대로 건너갑니다"
                else "출발하면 ${era}의 이야기가 들려옵니다 · 도착해서 주차하면 그 시대로 건너갑니다",
                style = Esc.noto(26, weight = FontWeight.Medium),
                textAlign = TextAlign.Center,
            )
            if (parkedActions != null) parkedActions()
            else Box(Modifier.padding(top = 10.dp)) { Flourish("Drive safely, Agent", 120.dp, Esc.pinyon(30)) }
        }
    }
}

@Composable
private fun EnRouteActions(s: StoryState, vm: GameViewModel, narrator: Narrator?) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        OutlineButton("이동 중 이야기 다시 듣기", { narrator?.speak(s.chapter.travelDrama) })
        OutlineButton("도착한 것으로 하기 (시연)", vm::demoArrive)
    }
}

// ---- 5. 도착 · 1795년으로 (Unlock.dc.html) ----

@Composable
private fun CompassBackdrop(modifier: Modifier) {
    Canvas(modifier) {
        inDesignUnits {
            val c = Offset(260f, 260f); val g = Esc.gold.copy(alpha = 0.55f)
            drawCircle(g, 230f, c, style = Stroke(1f))
            drawCircle(g, 200f, c, style = Stroke(1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(2f, 8f))))
            drawPath(svgPath("M260 10 V60 M260 460 V510 M10 260 H60 M460 260 H510 M83 83 L118 118 M402 402 L437 437 M437 83 L402 118 M118 402 L83 437"), g, style = Stroke(1f))
        }
    }
}

@Composable
private fun MaterialsRow(count: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("모은 재료", style = Esc.noto(15, Esc.creamSoft))
        repeat(2) { i ->
            Box(
                Modifier.size(26.dp).drawBehind {
                    if (i < count) drawCircle(Esc.gold)
                    else drawCircle(Esc.gold, radius = size.minDimension / 2 - 0.5.dp.toPx(), style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))))
                },
            )
        }
        Text("$count / 2", style = Esc.noto(15, Esc.creamSoft))
    }
}

@Composable
private fun ArrivalScreen(s: StoryState, vm: GameViewModel) {
    val l = looks[s.chapterIndex]
    val c = s.chapter
    GoldFrame {
        Row(Modifier.fillMaxSize().padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(40.dp)) {
            Box(Modifier.weight(1f).height(520.dp)) {
                CompassBackdrop(Modifier.size(520.dp))
                Column(
                    Modifier.offset(110.dp, 70.dp).size(300.dp, 370.dp).rotate(-4f).shadow(20.dp).parchment().padding(horizontal = 30.dp, vertical = 34.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(l.tag, style = Esc.cormorant(14, Esc.inkLabel))
                    Text(l.letter, style = Esc.nanum(26, Esc.wine))
                    Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFB9A882)))
                    listOf(0.92f, 0.78f, 0.86f, 0.60f).forEach { Box(Modifier.fillMaxWidth(it).height(10.dp).background(Color(90, 62, 42).copy(alpha = 0.22f))) }
                    Box(Modifier.weight(1f))
                    Text(
                        if (c.explore == null) "약병을 열면 백신이 퍼져 나갑니다" else "둘러보고 오면 ${withSubject(l.letter)} 열립니다",
                        style = Esc.noto(13, Esc.inkSoft),
                    )
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                AwakeningGauge(0)
                ParkBadge("장소 ${s.chapterIndex + 1} / ${s.episode.chapters.size} · ${c.place} 도착", boxed = true)
                Text(l.script, style = Esc.pinyon(42))
                Text(l.headline, style = Esc.nanum(40, lineHeight = 1.3f))
                Text(l.body, style = Esc.noto(17, Esc.creamMuted, lineHeight = 1.75f))
                if (c.explore != null) Text(c.explore, style = Esc.noto(15, Esc.creamSoft))
                MaterialsRow(ingredientCount(s))
                Box(Modifier.padding(top = 6.dp)) { ParchmentButton(c.arrivalAction, vm::leaveCar, minWidth = 260.dp) }
            }
        }
    }
}

// ---- 둘러보는 중 (주행 중 화면 양식) ----

@Composable
private fun ExploringScreen(s: StoryState, vm: GameViewModel) {
    GoldFrame(padTop = 40.dp, padH = 40.dp, padBottom = 40.dp, fill = Color(0xB81E0508)) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(22.dp, Alignment.CenterVertically)) {
            Text("Exploring · 둘러보는 중", style = Esc.cormorant(18, spacing = 0.3f))
            ParkBadge("장소 ${s.chapterIndex + 1} / ${s.episode.chapters.size} · ${s.chapter.place}", boxed = true)
            Text("${withObject(s.chapter.place)} 둘러보는 중", style = Esc.nanum(52, lineHeight = 1.2f))
            Text(s.chapter.explore.orEmpty(), Modifier.widthIn(max = 900.dp), style = Esc.noto(22, Esc.creamMuted, lineHeight = 1.6f), textAlign = TextAlign.Center)
            Text("차 문을 잠그고, 걸을 때는 주변을 살펴 주세요.", style = Esc.noto(16, Esc.creamSoft))
            ParchmentButton("차로 돌아왔어요", vm::returnToCar, minWidth = 260.dp)
            Flourish("Take your time, Agent", 120.dp, Esc.pinyon(30))
        }
    }
}

// ---- 단서 해금 (Unlock 시안의 'The Seal is Broken' 양식) ----

@Composable
private fun RevealScreen(s: StoryState, vm: GameViewModel) {
    val l = looks[s.chapterIndex]
    val c = s.chapter
    GoldFrame {
        Row(Modifier.fillMaxSize().padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(40.dp)) {
            Box(Modifier.weight(1f).height(520.dp)) {
                CompassBackdrop(Modifier.size(520.dp))
                Column(
                    Modifier.offset(80.dp, 40.dp).size(360.dp, 430.dp).rotate(-4f).shadow(20.dp).parchment().padding(28.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("Fragment ${roman(s.chapterIndex + 1)} · ${l.tag}", style = Esc.cormorant(13, Esc.inkLabel))
                    Text(c.revealTitle, style = Esc.nanum(24, Esc.wine))
                    Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFB9A882)))
                    Text(c.revealText, style = Esc.noto(14, Esc.inkBody, lineHeight = 1.7f))
                }
                WaxSeal(Modifier.offset(360.dp, 390.dp).size(96.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                AwakeningGauge(0)
                ParkBadge("장소 ${s.chapterIndex + 1} / ${s.episode.chapters.size} · ${c.place}", boxed = true)
                Text("The Seal is Broken", style = Esc.pinyon(42))
                if (c.clue != null) Text("“${c.clue}”", style = Esc.nanum(30, lineHeight = 1.35f))
                if (c.item != null) Text("얻은 것 · ${c.item}", style = Esc.noto(17, Esc.gold))
                Text("다음 행선지 · ${c.next ?: "??? — 모은 단서로 백신을 전달할 곳을 찾아야 합니다"}", style = Esc.noto(17, Esc.creamMuted, lineHeight = 1.6f))
                MaterialsRow(ingredientCount(s))
                Box(Modifier.padding(top = 6.dp)) {
                    ParchmentButton(if (c.next == null) "모은 단서 펼쳐 보기" else "다음 장소 길 안내", vm::storyNext, minWidth = 260.dp)
                }
            }
        }
    }
}

// ---- 모은 단서 (Explanation.dc.html 양식) ----

@Composable
private fun FinalRevealScreen(s: StoryState, vm: GameViewModel) {
    val target = s.episode.chapters[s.episode.finalChapterIndex]
    GoldFrame {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Row(
                Modifier.fillMaxWidth().drawBehind { drawLine(Esc.goldDim, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx()) }.padding(bottom = 14.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("The Four Clues", style = Esc.pinyon(34))
                    Text("모은 단서", style = Esc.nanum(30))
                }
                Box(Modifier.weight(1f))
                Text("Case No. 03", style = Esc.cormorant(15))
            }
            Column(
                Modifier.weight(1f).fillMaxWidth().shadow(18.dp).parchment().drawBehind {
                    val i = 10.dp.toPx()
                    drawRect(Esc.parchEdge, Offset(i, i), Size(size.width - 2 * i, size.height - 2 * i), style = Stroke(1.dp.toPx()))
                }.padding(horizontal = 36.dp, vertical = 14.dp),
            ) {
                s.clues.forEachIndexed { i, clue ->
                    Row(
                        Modifier.fillMaxWidth().drawBehind { drawLine(Color(0xFFC9B590), Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx()) }.padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        Box(Modifier.width(50.dp)) { NumeralCircle(i + 1, 42.dp, Esc.seal, 18) }
                        Column(Modifier.width(220.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(s.episode.chapters[i].place, style = Esc.nanum(18, Esc.ink))
                            Text("단서 ${i + 1}", style = Esc.noto(13, Esc.inkSoft))
                        }
                        Text(clue, Modifier.width(300.dp), style = Esc.nanum(17, Esc.seal, lineHeight = 1.4f))
                        Text(looks[i].meaning, Modifier.weight(1f), style = Esc.noto(14, Esc.inkBody, lineHeight = 1.6f))
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Text(s.episode.finalRevealText, Modifier.weight(1f), style = Esc.noto(15, Esc.creamMuted, lineHeight = 1.6f))
                ParchmentButton("${target.place}으로 길 안내", vm::storyNext, minWidth = 280.dp)
            }
        }
    }
}

// ---- 탈출 성공 (EscapeSuccess.dc.html) ----

@Composable
private fun EndingScreen(s: StoryState, vm: GameViewModel) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(1000) } }
    val sec = ((now - vm.storyStartedAtMs) / 1000).coerceAtLeast(0)
    GoldFrame {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(40.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Mission Complete", style = Esc.pinyon(44))
                Text("임무 완료 · 진 엔딩", style = Esc.nanum(44))
                Text(
                    "약병이 열리고, 백신이 물을 타고 퍼져 나갑니다. 물로 이어진 고을 곳곳에서 쓰러졌던 사람들이 숨을 고르기 시작합니다. \"이 고을의 이름은 수원(水原). 물의 근원이라는 뜻이다. 이 땅을 지킨 것은 처음부터 물이었다.\" 1795년의 회갑연은 무사히 열리고, 역사가 복원되었습니다.",
                    style = Esc.noto(16, Esc.creamMuted, lineHeight = 1.7f),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatBox("누적 수사 시간", "%02d : %02d : %02d".format(sec / 3600, (sec / 60) % 60, sec % 60), Modifier.weight(1f))
                    StatBox("힌트 사용", "0회", Modifier.weight(1f))
                    StatBox("시귀 각성도", "0 / 10", Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlineButton("로비로", vm::endStory, minWidth = 180.dp)
                    ParchmentButton("처음부터 다시", vm::restartStory, minWidth = 240.dp)
                }
                Text(s.episode.disclaimer, style = Esc.noto(13, Esc.label, lineHeight = 1.5f))
            }
            Box(Modifier.width(460.dp), contentAlignment = Alignment.Center) {
                Column(
                    Modifier.rotate(-8f).border(2.dp, Esc.alarm).padding(2.dp).border(2.dp, Esc.alarm).background(Color(239, 228, 204).copy(alpha = 0.06f)).padding(horizontal = 34.dp, vertical = 26.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("Case No. 03", style = Esc.cormorant(22, Esc.alarm, 0.3f))
                    Text("SOLVED", style = Esc.cormorant(72, Esc.alarm, 0f, FontWeight.Bold).copy(lineHeight = 76.sp))
                    Text("임무 완료", Modifier.padding(top = 6.dp), style = Esc.nanum(18, Esc.alarm))
                }
            }
        }
    }
}

@Composable
private fun StatBox(label: String, value: String, modifier: Modifier) {
    Column(
        modifier.background(Esc.panel).border(1.dp, Esc.goldDim).padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(label, style = Esc.noto(14, Esc.label))
        Text(value, style = Esc.cormorant(28, Esc.cream, 0f, FontWeight.Bold))
    }
}
