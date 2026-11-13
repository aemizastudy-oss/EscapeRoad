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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.team.carescape.game.Answer
import com.team.carescape.game.Feedback
import com.team.carescape.game.GameState
import com.team.carescape.game.Puzzle
import com.team.carescape.game.PuzzleEngine
import com.team.carescape.game.RevealCondition
import com.team.carescape.game.TurnSide
import com.team.carescape.game.VehicleData
import kotlinx.coroutines.delay

// ───────────────────────── 4. 주행 중 ─────────────────────────

/**
 * 정차가 확인되지 않으면 이 화면만 보인다. 누를 것이 없고, 큰 글씨 두 줄만 둔다.
 * [nextLabel]이 null이면 아직 사건을 시작하지 않은 상태.
 */
@Composable
fun DrivingScreen(nextLabel: String?) {
    GoldFrame {
        Column(
            Modifier.fillMaxSize().background(Color(0xB81E0508)).padding(40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(22.dp, Alignment.CenterVertically),
        ) {
            Text("En Route · 이동 중", style = Esc.smallCaps.copy(fontSize = 18.sp, color = Esc.gold, letterSpacing = 5.sp))
            EnvelopeOld(Modifier.size(width = 132.dp, height = 104.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(if (nextLabel != null) "다음 단서" else "사건 파일", style = Esc.body.copy(fontSize = 22.sp, color = Esc.creamSoft))
                Text(nextLabel ?: "주차 후 열람", style = Esc.title.copy(fontSize = 52.sp, lineHeight = 64.sp), textAlign = TextAlign.Center)
            }
            Text("주차하면 다음 단서가 열립니다", style = Esc.title.copy(fontSize = 26.sp, fontWeight = FontWeight.Medium))
            Flourish("Drive safely, Detective", lineWidth = 120.dp, style = Esc.pinyon(30))
        }
    }
}

@Composable
private fun EnvelopeOld(modifier: Modifier) {
    Canvas(modifier.semantics { contentDescription = "봉인된 편지" }) {
        val sx = size.width / 132f; val sy = size.height / 104f
        drawRect(Esc.parchment, Offset(4 * sx, 10 * sy), Size(124 * sx, 86 * sy))
        drawPath(Path().apply { moveTo(4 * sx, 14 * sy); lineTo(66 * sx, 60 * sy); lineTo(128 * sx, 14 * sy) }, Color(0xFFA8875A), style = Stroke(2 * sx))
        drawCircle(Esc.seal, 20 * sx, Offset(66 * sx, 60 * sy))
        drawCircle(Color(0xFFA33A40), 13 * sx, Offset(66 * sx, 60 * sy), style = Stroke(2 * sx))
    }
}

// ───────────────────────── 6. 퍼즐 풀이 ─────────────────────────

@Composable
fun PuzzleScreen(vm: GameViewModel, narrator: Narrator?) {
    val game = vm.game ?: return
    val puzzle = game.currentPuzzle ?: return
    var ttsMessage by remember(puzzle.id) { mutableStateOf<String?>(null) }
    LaunchedEffect(game.feedback, game.hintsShown, game.turnProgress, game.clueRevealed) { ttsMessage = null }

    GoldFrame {
        Column(Modifier.fillMaxSize().padding(start = 32.dp, end = 32.dp, top = 14.dp, bottom = 20.dp)) {
            // 머리줄
            Row(
                Modifier.fillMaxWidth().height(60.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text("${game.episode.title} · Fragment ${roman(game.puzzleIndex + 1)}", style = Esc.smallCaps.copy(fontSize = 15.sp, color = Esc.gold))
                Text(puzzle.title, style = Esc.body.copy(fontSize = 15.sp, color = Esc.creamSoft))
                Box(Modifier.weight(1f))
                Elapsed(vm.startedAtMs)
                OutlineButton(
                    "음성 단서 듣기",
                    onClick = {
                        val ok = narrator?.speak(puzzle.audioClue) == true
                        ttsMessage = if (ok) null else "음성 엔진을 쓸 수 없습니다 · 힌트를 이용해 주세요"
                    },
                    minHeight = 44.dp,
                )
                val left = puzzle.hints.size - game.hintsShown
                OutlineButton("힌트 · ${left}회 남음", onClick = vm::showHint, enabled = left > 0, minHeight = 44.dp)
                OutlineButton("로비", onClick = vm::goLobby, minHeight = 44.dp)
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(Esc.goldDim))

            Row(Modifier.weight(1f).padding(top = 22.dp), horizontalArrangement = Arrangement.spacedBy(40.dp)) {
                // 왼쪽: 단서 양피지 + 펼친 힌트
                Column(Modifier.width(470.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    ClueSheet(game, puzzle, Modifier.weight(1f, fill = false))
                    if (game.visibleHints.isNotEmpty()) HintList(game.visibleHints)
                }
                // 오른쪽: 자물쇠
                Column(
                    Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    when (val answer = puzzle.answer) {
                        is Answer.TurnSequence -> TurnLock(answer, game.turnProgress)
                        Answer.LowBeamOn -> LightLock()
                        is Answer.Code -> NumberLock(puzzle.id, answer, locked = answer.revealWhen != null && !game.clueRevealed, onSubmit = vm::submitCode)
                        is Answer.Choice -> ChoiceLock(game, puzzle, answer, onPick = vm::submitChoice)
                    }
                    FeedbackLine(game.feedback, ttsMessage)
                }
            }
        }
    }
}

@Composable
private fun Elapsed(startedAtMs: Long) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(startedAtMs) {
        while (true) { now = System.currentTimeMillis(); delay(1000) }
    }
    val sec = ((now - startedAtMs) / 1000).coerceAtLeast(0)
    Text(
        "%02d : %02d : %02d".format(sec / 3600, (sec / 60) % 60, sec % 60),
        style = Esc.smallCaps.copy(fontSize = 17.sp, color = Esc.cream, letterSpacing = 1.sp),
    )
}

@Composable
private fun ClueSheet(game: GameState, puzzle: Puzzle, modifier: Modifier) {
    val hidden = when (val a = puzzle.answer) {
        is Answer.Code -> a.revealWhen != null && !game.clueRevealed
        is Answer.Choice -> a.revealWhen != null && !game.clueRevealed
        else -> false
    }
    Column(
        modifier
            .fillMaxWidth()
            .rotate(-1f)
            .shadow(18.dp)
            .parchment()
            .padding(10.dp)
            .border(1.dp, Color(0xFFA8875A))
            .padding(horizontal = 24.dp, vertical = 20.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Fragment ${roman(game.puzzleIndex + 1)} — 단서", style = Esc.smallCaps.copy(fontSize = 13.sp, color = Esc.inkLabel))
        if (hidden) {
            Text(
                "글씨가 어둠에 묻혀 읽히지 않습니다.\n차 안의 무언가가 이 종이를 밝혀 줄 겁니다.",
                style = Esc.title.copy(fontSize = 20.sp, color = Color(0x995A3E2A), lineHeight = 34.sp),
            )
            repeat(4) { i ->
                Box(Modifier.fillMaxWidth(listOf(0.92f, 0.78f, 0.86f, 0.6f)[i]).height(10.dp).background(Color(0x385A3E2A)))
            }
        } else {
            Text(puzzle.screenClue, style = Esc.title.copy(fontSize = 20.sp, color = Esc.ink, fontWeight = FontWeight.Bold, lineHeight = 36.sp))
            if (puzzle.needs == VehicleData.BATTERY_PERCENT) {
                game.lastVehicle.batteryPercent?.let { BatteryBar(it) }
            }
        }
    }
}

@Composable
private fun BatteryBar(percent: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("우리 차 에너지", style = Esc.body.copy(fontSize = 15.sp, color = Esc.inkSoft))
        Box(Modifier.fillMaxWidth().height(26.dp).border(1.dp, Esc.ink)) {
            Box(Modifier.fillMaxWidth(percent / 100f).fillMaxHeight().background(Color(0xFF6B5A43)))
            Box(Modifier.align(Alignment.Center).width(2.dp).fillMaxHeight().background(Esc.seal))
        }
    }
}

@Composable
private fun HintList(hints: List<String>) {
    Column(
        Modifier.fillMaxWidth().background(Esc.panel).border(1.dp, Esc.goldDim).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        hints.forEachIndexed { i, h ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("힌트 ${i + 1}", style = Esc.smallCaps.copy(fontSize = 14.sp, color = Esc.gold, letterSpacing = 1.sp))
                Text(h, style = Esc.body.copy(fontSize = 16.sp, lineHeight = 24.sp))
            }
        }
    }
}

@Composable
private fun LockTitle(script: String, instruction: String) {
    Text(script, style = Esc.script.copy(fontSize = 32.sp))
    Text(instruction, style = Esc.body.copy(fontSize = 17.sp, color = Esc.creamMuted), textAlign = TextAlign.Center)
}

/** 황동 원판 자물쇠 그림. */
@Composable
private fun BrassLock(modifier: Modifier) {
    Canvas(modifier) {
        val s = size.width / 300f
        drawArc(Color(0xFF9A9A9A), 180f, 180f, false, Offset(100 * s, 28 * s), Size(100 * s, 100 * s), style = Stroke(18 * s))
        drawArc(Color(0xFFD8D8D8), 180f, 180f, false, Offset(100 * s, 28 * s), Size(100 * s, 100 * s), style = Stroke(6 * s))
        drawLine(Color(0xFF9A9A9A), Offset(100 * s, 78 * s), Offset(100 * s, 120 * s), 18 * s)
        drawLine(Color(0xFF9A9A9A), Offset(200 * s, 78 * s), Offset(200 * s, 120 * s), 18 * s)
        val c = Offset(150 * s, 196 * s)
        drawCircle(androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFFE2C27E), Color(0xFFB8893F), Color(0xFF7E5A24)), Offset(54 * s, 100 * s), Offset(246 * s, 292 * s)), 96 * s, c)
        drawCircle(Color(0xFF6B4A1C), 84 * s, c, style = Stroke(2 * s))
        drawCircle(Color(0xFF6B4A1C), 34 * s, c)
        drawCircle(Color(0xFFC9A15A), 26 * s, c)
    }
}

@Composable
private fun TurnLock(answer: Answer.TurnSequence, progress: List<TurnSide>) {
    LockTitle("The Directional Lock", "방향지시등 레버로 순서를 입력하십시오")
    BrassLock(Modifier.size(220.dp))
    val desc = answer.sides.indices.joinToString { i ->
        when (progress.getOrNull(i)) { TurnSide.LEFT -> "왼쪽"; TurnSide.RIGHT -> "오른쪽"; null -> "비어 있음" }
    }
    Row(Modifier.semantics { contentDescription = "입력된 방향: $desc" }, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        answer.sides.indices.forEach { i ->
            val side = progress.getOrNull(i)
            Box(
                Modifier.size(58.dp).background(if (side != null) Esc.panel else Color.Transparent).border(1.dp, if (side != null) Esc.gold else Esc.goldDim),
                contentAlignment = Alignment.Center,
            ) {
                if (side != null) Text(if (side == TurnSide.LEFT) "←" else "→", style = Esc.title.copy(fontSize = 28.sp, color = Esc.gold))
            }
        }
    }
    Row(
        Modifier.background(Esc.panel).border(1.dp, Esc.goldDim).padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Text("←  왼쪽 깜빡이", style = Esc.body.copy(fontSize = 15.sp, color = Esc.creamMuted))
        Text("→  오른쪽 깜빡이", style = Esc.body.copy(fontSize = 15.sp, color = Esc.creamMuted))
    }
}

@Composable
private fun LightLock() {
    LockTitle("The Light Lock", "차의 눈을 밝히면 자물쇠가 풀립니다")
    BrassLock(Modifier.size(220.dp))
}

@Composable
private fun NumberLock(puzzleId: String, answer: Answer.Code, locked: Boolean, onSubmit: (String) -> Unit) {
    var input by remember(puzzleId) { mutableStateOf("") }
    val length = answer.code.length
    LockTitle("The Number Lock", if (locked) "단서가 드러나야 번호를 맞출 수 있습니다" else "번호 ${length}자리를 맞추십시오")
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.semantics { contentDescription = "입력한 번호 $input" }) {
        repeat(length) { i ->
            Box(Modifier.size(width = 58.dp, height = 70.dp).background(Esc.panel).border(1.dp, if (i < input.length) Esc.gold else Esc.goldDim), contentAlignment = Alignment.Center) {
                Text(input.getOrNull(i)?.toString() ?: "", style = Esc.title.copy(fontSize = 32.sp, color = Esc.gold))
            }
        }
    }
    val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "지움", "0", "확인")
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        keys.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { k ->
                    val enabled = !locked && when (k) {
                        "지움" -> input.isNotEmpty()
                        "확인" -> input.length == length
                        else -> input.length < length
                    }
                    val onClick = {
                        when (k) {
                            "지움" -> input = input.dropLast(1)
                            "확인" -> { onSubmit(input); input = "" }
                            else -> input += k
                        }
                    }
                    if (k == "확인") ParchmentButton(k, onClick, enabled = enabled, minWidth = 96.dp, modifier = Modifier.width(96.dp))
                    else OutlineButton(k, onClick, enabled = enabled, modifier = Modifier.width(96.dp))
                }
            }
        }
    }
}

@Composable
private fun ChoiceLock(game: GameState, puzzle: Puzzle, answer: Answer.Choice, onPick: (Int) -> Unit) {
    val locked = answer.revealWhen != null && !game.clueRevealed
    val undecidable = PuzzleEngine.correctChoice(answer, game.lastVehicle) == null
    LockTitle(
        "The Choice",
        when {
            locked -> "단서가 드러나야 고를 수 있습니다"
            undecidable -> "차량 값을 아직 읽지 못했습니다"
            else -> "알맞은 답을 하나 고르십시오"
        },
    )
    Column(Modifier.widthIn(max = 520.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        answer.options.forEachIndexed { i, option ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 60.dp)
                    .then(if (!locked) Modifier.parchment() else Modifier.background(Color(0x40EFE4CC)))
                    .border(1.dp, Esc.gold)
                    .clickable(enabled = !locked && !undecidable, role = Role.Button) { onPick(i) }
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text("${'A' + i}.  $option", style = Esc.title.copy(fontSize = 19.sp, color = if (locked) Esc.creamSoft else Esc.oxblood))
            }
        }
    }
    if (puzzle.needs == VehicleData.BATTERY_PERCENT && game.lastVehicle.batteryPercent != null) {
        Text("차량 값 사용: 배터리 ${game.lastVehicle.batteryPercent}%", style = Esc.body.copy(fontSize = 13.sp, color = Esc.creamSoft))
    }
}

@Composable
private fun FeedbackLine(feedback: Feedback?, ttsMessage: String?) {
    val text = ttsMessage ?: when (feedback) {
        Feedback.WRONG -> "자물쇠가 꿈쩍도 하지 않습니다. 다시 맞춰 보십시오."
        Feedback.PROGRESS -> "딸깍 — 무언가 반응했습니다."
        Feedback.LOCKED -> "아직 열 수 없습니다."
        Feedback.CORRECT, null -> null
    } ?: return
    val wrong = feedback == Feedback.WRONG && ttsMessage == null
    Text(
        text,
        Modifier.background(if (wrong) Color(0xCC5A1A1E) else Esc.panel).border(1.dp, Esc.goldDim).padding(horizontal = 18.dp, vertical = 10.dp),
        style = Esc.body.copy(fontSize = 16.sp),
    )
}

// ───────────────────────── 5. 단서 해금 (퍼즐 해결 직후) ─────────────────────────

@Composable
fun SolvedScreen(vm: GameViewModel) {
    val game = vm.game ?: return
    val solved = game.previousPuzzle ?: return
    val total = game.episode.puzzles.size
    GoldFrame {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 64.dp, vertical = 40.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(40.dp),
        ) {
            Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(440.dp)) {
                    val r = size.minDimension / 2
                    drawCircle(Esc.gold.copy(alpha = 0.55f), r * 0.95f, style = Stroke(1.dp.toPx()))
                    drawCircle(Esc.gold.copy(alpha = 0.4f), r * 0.82f, style = Stroke(1.dp.toPx()))
                }
                Column(
                    Modifier.width(290.dp).heightIn(min = 340.dp).rotate(-4f).shadow(20.dp).parchment().padding(28.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("Fragment ${roman(game.puzzleIndex)}", style = Esc.smallCaps.copy(fontSize = 13.sp, color = Esc.inkLabel))
                    Text(solved.title, style = Esc.title.copy(fontSize = 20.sp, color = Color(0xFF5A1A1E)))
                    Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFB9A882)))
                    Text(solved.successText, style = Esc.body.copy(fontSize = 16.sp, color = Esc.ink, lineHeight = 26.sp))
                }
                WaxSeal(Modifier.align(Alignment.Center).offset(x = 130.dp, y = 150.dp).size(90.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                ParkBadge("정차 중 · 단서 해금")
                Text(if (game.finished) "The Last Seal" else "The Seal is Broken", style = Esc.script.copy(fontSize = 42.sp))
                Text(
                    if (game.finished) "마지막 봉인이\n풀렸습니다" else "다음 단서가\n모습을 드러냈습니다",
                    style = Esc.title.copy(fontSize = 44.sp, lineHeight = 56.sp),
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("모은 조각", style = Esc.body.copy(fontSize = 15.sp, color = Esc.creamSoft))
                    repeat(total) { i ->
                        val got = i < game.puzzleIndex
                        Box(
                            Modifier.size(width = 22.dp, height = 28.dp).rotate(if (i % 2 == 0) -4f else 3f)
                                .then(if (got) Modifier.background(Esc.parchment) else Modifier.border(1.dp, Esc.goldDim)),
                        )
                    }
                    Text("${game.puzzleIndex} / $total", style = Esc.body.copy(fontSize = 15.sp, color = Esc.creamSoft))
                }
                ParchmentButton(if (game.finished) "진상 확인하기" else "다음 단서 펼치기", onClick = vm::continueAfterSolved, minWidth = 260.dp)
            }
        }
    }
}

// ───────────────────────── 탈출 성공 ─────────────────────────

@Composable
fun SuccessScreen(vm: GameViewModel) {
    val game = vm.game ?: return
    val minutes = ((System.currentTimeMillis() - vm.startedAtMs) / 60000).coerceAtLeast(0)
    GoldFrame {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 120.dp, vertical = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        ) {
            Text("Case Closed", style = Esc.script.copy(fontSize = 44.sp))
            Text("탈출 성공", style = Esc.title.copy(fontSize = 56.sp))
            Text("★".repeat(game.stars) + "☆".repeat(3 - game.stars), style = Esc.title.copy(fontSize = 36.sp, color = Esc.gold, letterSpacing = 8.sp))
            Text(
                "힌트 ${game.hintsUsedTotal}회 사용 · 약 ${minutes}분",
                style = Esc.body.copy(fontSize = 17.sp, color = Esc.creamSoft),
            )
            Text(
                game.episode.ending,
                Modifier.widthIn(max = 760.dp).parchment().padding(horizontal = 32.dp, vertical = 22.dp),
                style = Esc.body.copy(fontSize = 18.sp, color = Esc.ink, lineHeight = 30.sp),
                textAlign = TextAlign.Center,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                OutlineButton("다른 사건 고르기", onClick = { vm.abandon(); vm.goThemeSelect() }, modifier = Modifier.widthIn(min = 200.dp))
                ParchmentButton("로비로", onClick = vm::abandon)
            }
        }
    }
}
