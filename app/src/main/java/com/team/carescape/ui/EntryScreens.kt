package com.team.carescape.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.team.carescape.game.ThemeCatalog

/** SVG 경로 문자열 → Compose Path. */
fun svgPath(d: String): Path = PathParser().parsePathString(d).toPath()

/** 시안 SVG 좌표(px)를 그대로 쓰도록 1dp 배율로 맞춰 그린다. */
inline fun DrawScope.inDesignUnits(block: DrawScope.() -> Unit) {
    val s = 1.dp.toPx()
    scale(s, s, pivot = Offset.Zero) { block() }
}

// ───────────────────────── 1. 로비 (Lobby.dc.html) ─────────────────────────

private val lobbyPillars = listOf(
    "M40 720 V170 Q40 90 120 90 Q200 90 200 170 V720",
    "M56 720 V176 Q56 106 120 106 Q184 106 184 176 V720",
    "M40 200 H200 M40 230 H200 M70 260 V720 M90 260 V720 M110 260 V720 M130 260 V720 M150 260 V720 M170 260 V720",
    "M20 720 V140 H220 V720",
    "M10 140 H230 M10 128 H230 M24 128 L120 60 L216 128",
    "M1240 720 V170 Q1240 90 1160 90 Q1080 90 1080 170 V720",
    "M1224 720 V176 Q1224 106 1160 106 Q1096 106 1096 176 V720",
    "M1080 200 H1240 M1080 230 H1240 M1110 260 V720 M1130 260 V720 M1150 260 V720 M1170 260 V720 M1190 260 V720 M1210 260 V720",
    "M1060 720 V140 H1260 V720",
    "M1050 140 H1270 M1050 128 H1270 M1064 128 L1160 60 L1256 128",
    "M230 700 H1050 M250 680 H1030 M270 660 H1010",
).map(::svgPath)
private val lobbyFrameOuter = svgPath("M330 60 H950 Q950 90 980 90 V630 Q950 630 950 660 H330 Q330 630 300 630 V90 Q330 90 330 60 Z")
private val lobbyFrameInner = svgPath("M340 72 H940 Q942 100 968 102 V618 Q942 620 940 648 H340 Q338 620 312 618 V102 Q338 100 340 72 Z")
private val lobbyDiamonds = listOf("M640 46 L650 60 L640 74 L630 60 Z", "M640 646 L650 660 L640 674 L630 660 Z").map(::svgPath)

@Composable
fun LobbyScreen(vm: GameViewModel) {
    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            inDesignUnits {
                lobbyPillars.forEach { drawPath(it, Color(0xFF6E2A2E), style = Stroke(1.2f)) }
                drawRect(
                    Brush.radialGradient(0.55f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.55f), center = Offset(640f, 324f), radius = 1100f),
                    size = Size(1280f, 720f),
                )
                drawPath(lobbyFrameOuter, Color(0xFF2A070B).copy(alpha = 0.82f), style = Fill)
                drawPath(lobbyFrameOuter, Esc.gold, style = Stroke(2.5f))
                drawPath(lobbyFrameInner, Esc.gold, style = Stroke(1f))
                lobbyDiamonds.forEach { drawPath(it, Esc.gold) }
            }
        }
        Column(
            Modifier.offset(340.dp, 96.dp).size(600.dp, 540.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Welcome, Detective", style = Esc.pinyon(40))
            VSpace(14.dp)
            Logo(380.dp)
            VSpace(18.dp)
            Flourish("The Detective's Drive Club · MMXXVI", 70.dp, Esc.cormorant(16, spacing = 0.28f))
            VSpace(18.dp)
            Text("내 차로 떠나는 방탈출 여행", style = Esc.nanum(23))
            VSpace(8.dp)
            Text("오늘 맡으실 사건을 고르시면, 첫 번째 단서를 건네드리겠습니다.", style = Esc.noto(16, Esc.creamSoft, lineHeight = 1.7f), textAlign = TextAlign.Center)
            Box(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                ParchmentButton("사건 고르기", vm::goThemeSelect, minWidth = 220.dp, minHeight = 58.dp, fontSize = 19)
                // 시안에 있는 버튼. 사건 기록부 화면은 아직 없다.
                OutlineButton("사건 기록부", {}, minWidth = 170.dp, minHeight = 58.dp, fontSize = 19, transparent = true)
            }
        }
        val story = vm.story
        if (story != null) {
            Box(Modifier.align(Alignment.BottomEnd).padding(end = 26.dp, bottom = 30.dp)) {
                Column(
                    Modifier.width(236.dp).rotate(-2f).shadow(14.dp).parchment().padding(6.dp).border(1.dp, Esc.parchEdge).padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("Case in Progress", style = Esc.cormorant(14, Esc.inkLabel, 0.18f))
                    Text("${story.episode.title},\n${story.episode.subtitle}", style = Esc.nanum(18, Esc.ink, lineHeight = 1.35f), textAlign = TextAlign.Center)
                    Text("장소 ${story.chapterIndex + 1} / ${story.episode.chapters.size} · ${story.chapter.place}", style = Esc.noto(14, Esc.inkSoft))
                    Box(
                        Modifier.fillMaxWidth().padding(top = 4.dp).height(46.dp).background(Esc.btnInk).clickable(role = Role.Button, onClick = vm::resumeStory),
                        contentAlignment = Alignment.Center,
                    ) { Text("수사 재개", style = Esc.nanum(16)) }
                }
            }
        }
        Box(Modifier.align(Alignment.BottomStart).padding(start = 28.dp, bottom = 26.dp)) { ParkBadge("주차 중 · 단서 열람 가능") }
    }
}

// ───────────────────────── 2. 테마 선택 (ThemeSelect.dc.html) ─────────────────────────

@Composable
fun ThemeSelectScreen(vm: GameViewModel) {
    val main = ThemeCatalog.cards.first { it.playable }
    GoldFrame(padTop = 28.dp, padH = 44.dp, padBottom = 26.dp) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(22.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                BackButton(vm::goLobby, "로비로 돌아가기")
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("The Case Board", style = androidx.compose.ui.text.TextStyle(fontFamily = Esc.playfairItalic, fontSize = 17.sp, color = Esc.gold, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic))
                    Text("오늘 맡으실 사건을 고르십시오", style = Esc.nanum(32))
                }
            }
            Row(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(30.dp)) {
                ThemeCard(
                    Modifier.weight(1f), selected = true, tilt = -0.8f, pin = Esc.oxblood, artBg = Color(0xFFD8C9A6),
                    art = { drawGateArt() },
                    label = "Case No. 03 · 시간여행 미스터리", title = "${main.title}, ${main.subtitle}",
                    rows = listOf("난이도" to diamonds(5), "공포도" to diamonds(3), "코스" to "수원 · 장소 5곳 · 한나절"),
                    onClick = { vm.select(main) },
                )
                ThemeCard(
                    Modifier.weight(1f), selected = false, tilt = 0.9f, pin = Color(0xFF6B5A43), artBg = Color(0xFFCDBE9C), edge = 0.4f,
                    art = { drawClockArt() },
                    label = "Case No. 04 · 미래 타임캡슐", title = "2045년에서 온 신호",
                    rows = listOf("난이도" to diamonds(3), "공포도" to diamonds(1), "코스" to "도심 · 장소 3곳 · 2~3시간"),
                    onClick = null,
                )
                SealedCard(Modifier.weight(1f).fillMaxHeight())
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("사건을 맡으면 첫 번째 목적지 안내가 시작됩니다", Modifier.weight(1f), style = Esc.noto(15, Color(0xFFCFC3A8)))
                ParchmentButton("이 사건을 맡겠습니다", { vm.select(main); vm.goBriefing() }, minWidth = 250.dp, minHeight = 58.dp, fontSize = 20)
            }
        }
    }
}

@Composable
private fun ThemeCard(
    modifier: Modifier,
    selected: Boolean,
    tilt: Float,
    pin: Color,
    artBg: Color,
    art: DrawScope.() -> Unit,
    label: String,
    title: String,
    rows: List<Pair<String, String>>,
    onClick: (() -> Unit)?,
    edge: Float = 0.32f,
) {
    Box(modifier.fillMaxHeight()) {
        Column(
            Modifier
                .fillMaxSize()
                .rotate(tilt)
                .then(if (selected) Modifier.padding(0.dp).border(3.dp, Esc.gold) else Modifier)
                .shadow(16.dp)
                .parchment(edge = edge, start = if (edge > 0.35f) 0.5f else 0.55f)
                .then(if (onClick != null) Modifier.clickable(role = Role.RadioButton, onClick = onClick) else Modifier),
        ) {
            Box(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp).fillMaxWidth().height(196.dp).background(artBg), contentAlignment = Alignment.BottomStart) {
                Canvas(Modifier.fillMaxSize()) { inDesignUnits { art() } }
                if (selected) {
                    Text(
                        "Selected",
                        Modifier.padding(12.dp).background(Esc.oxblood).padding(horizontal = 10.dp, vertical = 4.dp),
                        style = androidx.compose.ui.text.TextStyle(fontFamily = Esc.playfairSC, fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 0.12.em, color = Color(0xFFF3E9D2)),
                    )
                }
            }
            Column(Modifier.weight(1f).padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(label, style = androidx.compose.ui.text.TextStyle(fontFamily = Esc.playfairSC, fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 0.14.em, color = Color(0xFF6B5A43)))
                Text(title, style = Esc.nanum(22, Color(0xFF2B2118), lineHeight = 1.35f))
                Box(Modifier.weight(1f))
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    rows.forEach { (k, v) ->
                        Row(Modifier.fillMaxWidth()) {
                            Text(k, style = Esc.noto(15, Color(0xFF5A4A36)))
                            Box(Modifier.weight(1f))
                            val isGrade = v.startsWith("◆") || v.startsWith("◇")
                            Text(v, style = Esc.noto(15, if (isGrade) Esc.oxblood else Color(0xFF2B2118)).copy(letterSpacing = if (isGrade) 3.sp else 0.sp))
                        }
                    }
                }
            }
        }
        Box(Modifier.align(Alignment.TopCenter).offset(y = (-11).dp).size(22.dp).shadow(4.dp, CircleShape).background(pin, CircleShape))
    }
}

private fun DrawScope.drawGateArt() {
    drawCircle(Color(0xFFF6EDD6).copy(alpha = 0.8f), 18f, Offset(250f, 40f))
    drawRect(Color(0xFF5C4A34), Offset(30f, 112f), Size(254f, 40f))
    for (x in listOf(40, 74, 108, 142, 176, 210, 244)) drawPath(svgPath("M$x 150 V132 A12 12 0 0 1 ${x + 24} 132 V150"), Color(0xFF2E2418))
    drawRect(Color(0xFF3B2E21), Offset(96f, 92f), Size(122f, 20f))
    drawPath(svgPath("M86 94 Q157 66 228 94 Z"), Color(0xFF3B2E21))
    drawPath(svgPath("M0 160 Q40 154 80 160 T160 160 T240 160 T320 160 V196 H0 Z"), Color(0xFF6B5A43))
    drawPath(svgPath("M10 172 H90 M120 178 H210 M230 170 H300"), Color(0xFFD8C9A6), style = Stroke(2f))
}

private fun DrawScope.drawClockArt() {
    val c = Color(0xFF3B2E21)
    drawCircle(c, 58f, Offset(157f, 92f), style = Stroke(3f))
    drawCircle(c, 50f, Offset(157f, 92f), style = Stroke(1f))
    drawLine(c, Offset(157f, 92f), Offset(157f, 54f), 3f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
    drawLine(c, Offset(157f, 92f), Offset(184f, 104f), 2f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
    drawCircle(c, 4f, Offset(157f, 92f))
    drawRect(c, Offset(149f, 22f), Size(16f, 12f))
}

@Composable
private fun SealedCard(modifier: Modifier) {
    Column(
        modifier
            .background(Color(0xFF3A0F12))
            .drawBehind {
                drawRect(Esc.gold, style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))))
            }
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
    ) {
        Canvas(Modifier.size(60.dp)) {
            val s = size.width / 24f
            scale(s, s, pivot = Offset.Zero) {
                drawRoundRect(Esc.gold, Offset(3f, 7f), Size(18f, 12f), CornerRadius(1f), style = Stroke(1.4f))
                drawPath(svgPath("M3 8 L12 14 L21 8"), Esc.gold, style = Stroke(1.4f))
                drawCircle(Esc.oxblood, 2.2f, Offset(12f, 15.5f))
            }
        }
        Text("봉인된 사건", style = Esc.nanum(22))
        Text("새로운 의뢰가 곧 도착합니다", style = Esc.noto(15, Color(0xFFCFC3A8), lineHeight = 1.6f))
    }
}

// ───────────────────────── 공용 ─────────────────────────

/** 50×50 뒤로 가기 버튼. */
@Composable
fun BackButton(onClick: () -> Unit, label: String) {
    Box(
        Modifier.size(50.dp).border(1.dp, Esc.gold).clickable(role = Role.Button, onClick = onClick).semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(20.dp)) {
            val s = size.width / 24f
            scale(s, s, pivot = Offset.Zero) { drawPath(svgPath("M15 5 L8 12 L15 19"), Esc.cream, style = Stroke(2f, cap = androidx.compose.ui.graphics.StrokeCap.Round)) }
        }
    }
}

fun roman(n: Int): String = listOf("I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X").getOrElse(n - 1) { n.toString() }

/** 받침에 맞춰 주격 조사(이/가)를 붙인다. */
fun withSubject(word: String): String {
    val last = word.lastOrNull() ?: return word
    if (last !in '가'..'힣') return "${word}이(가)"
    return if ((last - '가') % 28 != 0) "${word}이" else "${word}가"
}
