package com.example.roadescape.ui

import com.example.roadescape.R

import androidx.compose.ui.res.stringResource

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.roadescape.game.GameController
import com.example.roadescape.model.AcquisitionType
import com.example.roadescape.model.CheckpointStatus
import com.example.roadescape.model.GameSession

@Composable
fun LobbyScreen(
    state: GameUiState,
    onCases: () -> Unit,
    onArchive: () -> Unit,
    onCasebook: () -> Unit,
    onResume: () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize().testTag("screen-lobby")) {
        val wide = maxWidth >= 900.dp
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            CasePanel(Modifier.fillMaxWidth(if (wide) .76f else 1f).ornateCaseBorder()) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 18.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("Welcome, Detective", fontFamily = FontFamily.Cursive, fontSize = 30.sp, color = CaseColors.Gold)
                    Image(
                        painter = painterResource(R.drawable.pdf_logo),
                        contentDescription = "ESCAPE ROAD",
                        modifier = Modifier.widthIn(max = 620.dp).fillMaxWidth().height(if (wide) 260.dp else 200.dp),
                        contentScale = ContentScale.Fit,
                    )
                    CLabel("THE DETECTIVE'S DRIVE CLUB · MMXXVI", Modifier.padding(top = 4.dp))
                    CText("내 차로 떠나는 방탈출 여행", size = 23, weight = FontWeight.Bold)
                    CText("오늘 맡으실 사건을 고르시면, 첫 번째 단서를 건네드리겠습니다.", color = CaseColors.Cream.copy(alpha = .8f), modifier = Modifier.widthIn(max = 720.dp), align = TextAlign.Center)
                    FlowRow(Modifier.padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        EscapeButton("사건 고르기", "open-cases", onCases)
                        EscapeButton("사건 기록부", "lobby-casebook", onCasebook, secondary = true)
                        EscapeButton("저장된 수사", "lobby-archive", onArchive, secondary = true)
                    }
                }
            }
            state.session?.let { session ->
                PaperSheet(Modifier.widthIn(max = 440.dp).fillMaxWidth().align(if (wide) Alignment.End else Alignment.CenterHorizontally)) {
                    CLabel("CASE IN PROGRESS", dark = true)
                    CText(state.content?.title.orEmpty(), color = CaseColors.Ink, size = 20, weight = FontWeight.Bold)
                    // Detailed results remain behind the same parking gate as the casebook.
                    if (state.canReadContent) CText("경유지 ${session.progress.count { it.status.isComplete }} / ${session.progress.size}", color = CaseColors.Ink.copy(alpha = .7f))
                    EscapeButton("수사 재개", "resume", onResume, enabled = !state.saving, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
fun CaseBoardScreen(state: GameUiState, onBack: () -> Unit, onDetail: () -> Unit) {
    CasePage("screen-case-board", "The Case Board", "오늘 맡으실 사건을 고르십시오") {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                if (maxWidth >= 900.dp) Row(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    DemoCaseCard(state, onDetail, Modifier.weight(1f))
                    SealedCaseCard(Modifier.weight(1f))
                    SealedCaseCard(Modifier.weight(1f))
                } else Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    DemoCaseCard(state, onDetail, Modifier.fillMaxWidth())
                    SealedCaseCard(Modifier.fillMaxWidth())
                }
            }
            ScreenActions(onBack) { EscapeButton("이 사건을 맡겠습니다", "case-detail", onDetail, enabled = !state.saving && state.content != null) }
        }
    }
}

@Composable
private fun DemoCaseCard(state: GameUiState, onDetail: () -> Unit, modifier: Modifier) {
    PaperSheet(modifier.heightIn(min = 360.dp).clickable(enabled = state.content != null, onClick = onDetail)) {
        CaseArtwork(CaseArt.LIGHTHOUSE, Modifier.fillMaxWidth().height(150.dp).background(CaseColors.Gold.copy(alpha = .14f)))
        CLabel("CASE NO. 01 · 개발용 테스트", dark = true)
        CText(state.content?.title ?: "기본 기능 테스트 패키지", size = 22, color = CaseColors.Ink, weight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        PaperPair("자물쇠", "숫자 · 순서 · 가상 장치")
        PaperPair("경유지", "${state.content?.checkpoints?.size ?: 3}곳 · 순서대로 진행")
        PaperPair("이용 방식", "도착·주차 시뮬레이션")
        CText("SELECTED", size = 13, color = CaseColors.Red, weight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
    }
}

@Composable
private fun SealedCaseCard(modifier: Modifier) {
    CasePanel(modifier.heightIn(min = 360.dp)) {
        Column(Modifier.fillMaxWidth().padding(vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
            CaseArtwork(CaseArt.ENVELOPE, Modifier.size(78.dp))
            CText("봉인된 사건", size = 23, weight = FontWeight.Bold)
            CText("새로운 의뢰가 곧 도착합니다", color = CaseColors.Cream.copy(alpha = .65f), align = TextAlign.Center)
        }
    }
}

@Composable
fun CaseDetailScreen(state: GameUiState, onBack: () -> Unit, onBegin: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize().testTag("screen-case-detail")) {
        val letter: @Composable () -> Unit = {
            PaperSheet(Modifier.fillMaxWidth()) {
                CLabel("CASE FILE NO. 01", dark = true)
                CText(state.content?.title ?: "기본 기능 테스트 패키지", size = 30, color = CaseColors.Ink, weight = FontWeight.Bold)
                Text("Dear Detective,", fontFamily = FontFamily.Cursive, color = CaseColors.Red, fontSize = 28.sp)
                CText("탐정님께,", color = CaseColors.Ink, modifier = Modifier.padding(top = 10.dp))
                CText("세 개의 테스트 경유지에 작은 자물쇠가 기다리고 있습니다. 숫자를 이어 쓰고, 방향의 순서를 기억하고, 가상 장치의 두 값을 맞춰 보세요.", color = CaseColors.Ink, size = 18)
                CText("각 경유지에서 도착과 주차 상태를 확인하면 편지가 열립니다. 자물쇠를 해결하거나 건너뛰면 단서가 가방에 보관되고 다음 경유지로 나아갈 수 있습니다.", color = CaseColors.Ink, size = 18)
                CText("이것은 실제 장소나 차량 센서를 사용하지 않는 개발용 테스트 사건입니다. 서두르지 말고, 안전한 정차 상태에서 시작해 주세요.", color = CaseColors.Ink.copy(alpha = .8f))
                Spacer(Modifier.height(25.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                    CText("— Escape Road", color = CaseColors.Ink.copy(alpha = .65f))
                    CaseArtwork(CaseArt.WAX, Modifier.size(58.dp))
                }
            }
        }
        val route: @Composable () -> Unit = {
            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Text("The Route", fontFamily = FontFamily.Cursive, fontSize = 30.sp, color = CaseColors.Gold)
                CText(stringResource(R.string.nav_case_route), size = 24, weight = FontWeight.Bold)
                state.content?.checkpoints?.forEachIndexed { index, checkpoint ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        RomanBadge(index)
                        CText(checkpoint.title, modifier = Modifier.weight(1f))
                    }
                }
                HorizontalDivider(color = CaseColors.Gold.copy(alpha = .4f))
                CText("자물쇠 3종 · 경유지 3곳\n제한 시간 없이 천천히", color = CaseColors.Cream.copy(alpha = .8f))
                CText("단서는 차가 멈춰 있을 때만 드러납니다. 이번 테스트에서는 앱 안에서 도착과 주차를 확인합니다.", color = CaseColors.Cream.copy(alpha = .8f))
                ScreenActions(onBack) { EscapeButton("수사 시작", "begin-investigation", onBegin) }
            }
        }
        if (maxWidth >= 1000.dp) Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            Box(Modifier.weight(1.8f).verticalScroll(rememberScrollState())) { letter() }
            Box(Modifier.weight(1f).verticalScroll(rememberScrollState())) { route() }
        } else Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(22.dp)) { letter(); route() }
    }
}

@Composable
fun DetectiveRulesScreen(onBack: () -> Unit, onContinue: () -> Unit) {
    var accepted by rememberSaveable { mutableStateOf(false) }
    CasePage("screen-rules", "The Detective's Code", "탐정 수칙", "BEFORE YOU BEGIN") {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            PaperSheet(Modifier.fillMaxWidth()) {
                RuleRow(0, "단서는 차가 멈춰 있을 때만", "도착과 PARKED가 확인되어야 퍼즐이 열립니다. DRIVING 또는 UNKNOWN에서는 내용을 가립니다.")
                RuleRow(1, "자물쇠는 화면으로 연다", "숫자 키패드와 방향 버튼, 가상 장치로 풉니다. 실제 차량 설정은 바뀌지 않습니다.")
                RuleRow(2, "힌트는 두 단계", "작은 실마리와 결정적 단서를 순서대로 열 수 있습니다. 정답 공개는 별도로 기록합니다.")
                RuleRow(3, "언제든 이어서", "제한 시간은 없습니다. 저장 완료된 진행은 다음 실행에서 이어갈 수 있습니다. 주차 상태는 다시 확인합니다.")
            }
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).toggleable(value = accepted, role = Role.Checkbox, onValueChange = { accepted = it }).testTag("rules-accepted"), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (accepted) "☑" else "□", color = CaseColors.Gold, fontSize = 26.sp)
                CText("수칙을 모두 확인했습니다")
            }
            ScreenActions(onBack) { EscapeButton("입장하기", "rules-continue", onContinue, enabled = accepted) }
        }
    }
}

@Composable
private fun RuleRow(index: Int, title: String, body: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        RomanBadge(index, dark = true)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CText(title, size = 20, color = CaseColors.Ink, weight = FontWeight.Bold)
            CText(body, color = CaseColors.Ink.copy(alpha = .75f))
        }
    }
    HorizontalDivider(color = CaseColors.Gold.copy(alpha = .35f))
}

@Composable
fun PartyScreen(onBack: () -> Unit, onContinue: () -> Unit) {
    var party by rememberSaveable { mutableIntStateOf(1) }
    var mode by rememberSaveable { mutableIntStateOf(0) }
    CasePage("screen-party", "Assemble Your Party", "함께하는 탐정", "CASE NO. 01") {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            CText("몇 명이 함께 수사하나요?", size = 20)
            ChoiceCards(listOf("혼자" to "나만의 속도로 천천히", "2인" to "서로의 생각을 나누며", "3~4인" to "함께 단서를 찾아서"), party, { party = it }, "party")
            CText("수사 분위기", size = 20)
            ChoiceCards(listOf("클래식" to "여유롭게 즐기기", "마스터" to "차분하게 도전하기"), mode, { mode = it }, "difficulty")
            CText("이번 테스트에서는 어떤 구성을 선택해도 같은 세 퍼즐과 2단계 힌트가 제공됩니다.", color = CaseColors.Cream.copy(alpha = .7f))
            ScreenActions(onBack) { EscapeButton("이 구성으로 시작", "party-continue", onContinue) }
        }
    }
}

@Composable
private fun ChoiceCards(items: List<Pair<String, String>>, selected: Int, onSelect: (Int) -> Unit, prefix: String) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val wide = maxWidth >= 680.dp
        if (wide) Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            items.forEachIndexed { index, (title, subtitle) ->
                ChoiceCard(title, subtitle, selected == index, { onSelect(index) }, "$prefix-$index", Modifier.weight(1f))
            }
        } else Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items.forEachIndexed { index, (title, subtitle) -> ChoiceCard(title, subtitle, selected == index, { onSelect(index) }, "$prefix-$index", Modifier.fillMaxWidth()) }
        }
    }
}

@Composable
private fun ChoiceCard(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit, tag: String, modifier: Modifier) {
    CasePanel(modifier.clickable(onClick = onClick).testTag(tag), selected = selected) {
        Column(Modifier.fillMaxWidth().heightIn(min = 104.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically)) {
            CText(title, size = 25, weight = FontWeight.Bold)
            CText(subtitle, color = CaseColors.Cream.copy(alpha = .75f), align = TextAlign.Center)
        }
    }
}

@Composable
fun PrologueScreen(state: GameUiState, onBack: () -> Unit, onStart: () -> Unit) {
    Column(Modifier.fillMaxSize().testTag("screen-prologue").verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                CLabel("PROLOGUE · I")
                Text("The First Clue", fontFamily = FontFamily.Cursive, fontSize = 32.sp, color = CaseColors.Cream)
            }
            EscapeButton("돌아가기", "prologue-back", onBack, secondary = true)
        }
        CaseArtwork(CaseArt.LIGHTHOUSE, Modifier.fillMaxWidth().height(210.dp))
        CasePanel(Modifier.fillMaxWidth()) {
            CText("세 개의 경유지, 세 개의 자물쇠.\n차가 멈춘 자리에서 첫 번째 단서를 만나세요.", size = 25, align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            CText("이야기 대신 기본 게임 기능을 확인하는 테스트 사건입니다.", color = CaseColors.Cream.copy(alpha = .7f), align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                EscapeButton("새 테스트 게임 시작", "start-new", onStart, enabled = !state.loading && !state.saving)
            }
        }
    }
}

@Composable
fun CaseCheckpointScreen(state: GameUiState, onOpen: (String) -> Unit, onCompletion: () -> Unit) {
    val content = state.content ?: return
    val session = state.session ?: return
    val current = GameController.currentCheckpoint(content, session)
    CasePage("screen-checkpoints", "Map of the First Clue", stringResource(R.string.nav_case_route), "CASE NO. 01") {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val map: @Composable () -> Unit = {
                PaperSheet(Modifier.fillMaxWidth()) {
                    CText("세 개의 테스트 경유지", size = 22, color = CaseColors.Ink, weight = FontWeight.Bold)
                    CText("실제 지도가 아닌 경유지 진행도입니다.", size = 14, color = CaseColors.Ink.copy(alpha = .6f))
                    RouteDiagram(state)
                }
            }
            val list: @Composable () -> Unit = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CLabel("THE ROUTE")
                    content.checkpoints.forEachIndexed { index, checkpoint ->
                        val progress = session.progress.first { it.checkpointId == checkpoint.id }
                        val accessible = checkpoint.id == current?.id || progress.status.isComplete
                        CasePanel(Modifier.fillMaxWidth(), selected = checkpoint.id == current?.id) {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                RomanBadge(index)
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    CText(checkpoint.title, size = 19, weight = FontWeight.Bold)
                                    CText(checkpointStateLabel(progress.status), size = 14, color = CaseColors.Cream.copy(alpha = .7f))
                                }
                            }
                            CText(if (!accessible) "이전 경유지 완료 필요" else if (progress.arrivedAt == null) "도착 확인 필요" else "방문 확인 완료", size = 14, color = CaseColors.Cream.copy(alpha = .7f))
                            EscapeButton(if (progress.status.isComplete) "기록 확인" else "퍼즐 열기", "checkpoint-${checkpoint.id}", { onOpen(checkpoint.puzzleId) }, enabled = accessible && !state.saving, modifier = Modifier.fillMaxWidth(), secondary = checkpoint.id != current?.id)
                        }
                    }
                    if (current == null) EscapeButton("완료 내역 보기", "view-completion", onCompletion, modifier = Modifier.fillMaxWidth())
                }
            }
            if (maxWidth >= 1050.dp) Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Box(Modifier.weight(1.7f).verticalScroll(rememberScrollState())) { map() }
                Box(Modifier.weight(1f).verticalScroll(rememberScrollState())) { list() }
            } else Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(18.dp)) { map(); list() }
        }
    }
}

@Composable
private fun RouteDiagram(state: GameUiState) {
    val content = state.content ?: return
    val session = state.session ?: return
    val currentId = GameController.currentCheckpoint(content, session)?.id
    val checkpoints = content.checkpoints.take(3)
    val positions = listOf(.12f to .80f, .44f to .48f, .75f to .18f).take(checkpoints.size)
    BoxWithConstraints(Modifier.fillMaxWidth().height(350.dp)) {
        Canvas(Modifier.fillMaxSize()) {
            val points = positions.map { Offset(size.width * it.first, size.height * it.second) }
            val path = Path().apply {
                moveTo(points[0].x, points[0].y)
                points.zipWithNext().forEach { (from, to) ->
                    cubicTo(from.x + size.width * .1f, to.y, to.x - size.width * .1f, to.y + size.height * .1f, to.x, to.y)
                }
            }
            drawPath(path, CaseColors.Gold.copy(alpha = .45f), style = Stroke(width = 6.dp.toPx()))
            points.forEachIndexed { index, point ->
                val checkpoint = checkpoints[index]
                val progress = session.progress.first { it.checkpointId == checkpoint.id }
                val color = when { progress.status.isComplete -> CaseColors.Red; checkpoint.id == currentId -> CaseColors.Gold; else -> CaseColors.Ink.copy(alpha = .25f) }
                drawCircle(CaseColors.Cream, 15.dp.toPx(), point)
                drawCircle(color, 11.dp.toPx(), point)
                drawCircle(color, 17.dp.toPx(), point, style = Stroke(1.dp.toPx()))
            }
        }
        checkpoints.forEachIndexed { index, checkpoint ->
            val progress = session.progress.first { it.checkpointId == checkpoint.id }
            Column(Modifier.offset(x = maxWidth * positions[index].first + 22.dp, y = maxHeight * positions[index].second - 15.dp).width((maxWidth * (1f - positions[index].first) - 28.dp).coerceAtLeast(50.dp))) {
                CText("경유지 ${index + 1}", color = CaseColors.Ink, weight = FontWeight.Bold)
                CText(checkpointStateLabel(progress.status), size = 13, color = CaseColors.Ink.copy(alpha = .6f))
            }
        }
        CaseArtwork(CaseArt.COMPASS, Modifier.size(90.dp).align(Alignment.BottomEnd))
    }
}

@Composable
fun SatchelScreen(state: GameUiState, onOpen: (String) -> Unit) {
    if (!state.canReadContent) { CasePausedScreen(state); return }
    val content = state.content ?: return
    val acquired = state.session?.acquiredClues.orEmpty()
    CasePage("screen-clues", "The Satchel", stringResource(R.string.nav_satchel), "${acquired.size} / ${content.clues.size} ITEMS") {
        if (acquired.isEmpty()) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
                CaseArtwork(CaseArt.ENVELOPE, Modifier.size(100.dp))
                CText("아직 모은 단서가 없어요", size = 26)
                CText("자물쇠를 해결하거나 건너뛰면 단서가 이곳에 보관됩니다.", color = CaseColors.Cream.copy(alpha = .7f), align = TextAlign.Center)
            }
            return@CasePage
        }
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val grid: @Composable () -> Unit = {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    acquired.chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            row.forEach { item ->
                                val clue = content.clues.first { it.id == item.clueId }
                                CasePanel(Modifier.weight(1f).clickable { onOpen(clue.id) }.testTag("clue-${clue.id}")) {
                                    Column(Modifier.fillMaxWidth().heightIn(min = 135.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)) {
                                        CaseArtwork(CaseArt.ENVELOPE, Modifier.size(52.dp))
                                        CText(clue.title, align = TextAlign.Center, weight = FontWeight.Bold)
                                        CText(if (item.acquisitionType == AcquisitionType.SKIPPED) "건너뛰어 획득" else "해결해 획득", size = 13, color = CaseColors.Cream.copy(alpha = .65f))
                                    }
                                }
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
            val first = acquired.first()
            val clue = content.clues.first { it.id == first.clueId }
            val preview: @Composable () -> Unit = {
                PaperSheet(Modifier.fillMaxWidth()) {
                    CLabel("COLLECTED FRAGMENT", dark = true)
                    CText(clue.title, size = 24, color = CaseColors.Ink, weight = FontWeight.Bold)
                    CText(clue.body, size = 18, color = CaseColors.Ink)
                    CText("획득 · ${content.checkpoints.first { it.id == first.sourceCheckpointId }.title}", size = 14, color = CaseColors.Ink.copy(alpha = .65f))
                    EscapeButton("크게 보기", "clue-preview-open", { onOpen(clue.id) }, modifier = Modifier.fillMaxWidth())
                }
            }
            if (maxWidth >= 1000.dp) Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Box(Modifier.weight(1.5f).verticalScroll(rememberScrollState())) { grid() }
                Box(Modifier.weight(1f).verticalScroll(rememberScrollState())) { preview() }
            } else Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(18.dp)) { grid(); preview() }
        }
    }
}

@Composable
fun ClueDetailScreen(state: GameUiState, clueId: String, onBack: () -> Unit) {
    if (!state.canReadContent) { CasePausedScreen(state); return }
    val content = state.content ?: return
    val acquired = state.session?.acquiredClues?.firstOrNull { it.clueId == clueId }
    val clue = content.clues.firstOrNull { it.id == clueId }
    if (clue == null || acquired == null) {
        Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
            CText("요청한 단서를 찾을 수 없습니다")
            EscapeButton("단서 목록으로", "back-to-clues", onBack)
        }
        return
    }
    CasePage("screen-clue-detail", "The Collected Fragment", clue.title) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(22.dp)) {
            PaperSheet(Modifier.fillMaxWidth()) {
                CLabel("FRAGMENT · ${acquired.sourceCheckpointId.uppercase()}", dark = true)
                CText(clue.body, size = 24, color = CaseColors.Ink, modifier = Modifier.testTag("clue-body"))
                Spacer(Modifier.height(20.dp))
                CText("획득 · ${content.checkpoints.first { it.id == acquired.sourceCheckpointId }.title}", color = CaseColors.Ink.copy(alpha = .65f))
                CText(if (acquired.acquisitionType == AcquisitionType.SOLVED) "퍼즐을 해결해 획득" else "퍼즐을 건너뛰어 획득", color = CaseColors.Ink.copy(alpha = .65f))
            }
            EscapeButton("단서 목록으로", "back-to-clues", onBack, secondary = true)
        }
    }
}

@Composable
fun CaseArchiveScreen(state: GameUiState, onResume: () -> Unit, onCases: () -> Unit) {
    if (state.session != null && !state.canReadContent) { CasePausedScreen(state); return }
    CasePage("screen-archive", "The Case Archive", "저장된 수사", "AUTO-SAVED AT EVERY STEP") {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            CText("도착, 힌트와 정답 공개, 퍼즐 해결마다 진행을 저장합니다. 제한 시간 없이 다음에 이어서 풀어도 됩니다.", color = CaseColors.Cream.copy(alpha = .75f))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                state.session?.let { session ->
                    PaperSheet(Modifier.weight(1f)) {
                        CLabel("CASE NO. 01", dark = true)
                        CText(state.content?.title.orEmpty(), size = 25, color = CaseColors.Ink, weight = FontWeight.Bold)
                        ProgressSegments(session)
                        CText("경유지 ${session.progress.count { it.status.isComplete }} / ${session.progress.size} 완료", color = CaseColors.Ink)
                        CText("저장 완료된 진행에서 이어집니다.", color = CaseColors.Ink.copy(alpha = .7f))
                        Spacer(Modifier.height(30.dp))
                        EscapeButton("이어서 수사", "archive-resume", onResume, enabled = !state.saving, modifier = Modifier.fillMaxWidth())
                    }
                } ?: CasePanel(Modifier.weight(1f)) {
                    CText("빈 수사 기록", size = 25, weight = FontWeight.Bold)
                    CText("새 사건을 시작하면 이곳에 저장됩니다", color = CaseColors.Cream.copy(alpha = .7f))
                    EscapeButton("새 사건 고르기", "archive-cases", onCases, secondary = true)
                }
            }
            EscapeButton("새 사건 고르기", "archive-new-case", onCases, secondary = true)
        }
    }
}

@Composable
fun CasebookScreen(state: GameUiState, onResume: () -> Unit, onSolution: () -> Unit, onCertificate: () -> Unit) {
    if (state.session != null && !state.canReadContent) { CasePausedScreen(state); return }
    val session = state.session
    val complete = session?.progress?.all { it.status.isComplete } == true
    if (session == null) {
        CasePage("screen-casebook", "The Casebook", "사건 기록부") {
            CasePanel(Modifier.fillMaxWidth()) { CText("아직 기록된 사건이 없습니다", size = 24); CText("새 테스트 게임을 시작하면 기록이 남습니다.") }
        }
        return
    }
    CasePage("screen-casebook", "The Casebook", "사건 기록부") {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val summary: @Composable () -> Unit = {
                    PaperSheet(Modifier.fillMaxWidth()) {
                        CLabel("CASE NO. 01", dark = true)
                        CText(state.content?.title.orEmpty(), size = 28, color = CaseColors.Ink, weight = FontWeight.Bold)
                        CText(if (complete) "COMPLETED" else "IN PROGRESS", size = 20, color = CaseColors.Red, weight = FontWeight.Bold)
                        ProgressSegments(session)
                        CText("경유지 ${session.progress.count { it.status.isComplete }} / ${session.progress.size}", color = CaseColors.Ink)
                    }
                }
                val detail: @Composable () -> Unit = {
                    Column(verticalArrangement = Arrangement.spacedBy(15.dp)) {
                        CLabel("CASE DETAILS")
                        CasePair("해결한 경유지", session.progress.count { it.status == CheckpointStatus.SOLVED }.toString())
                        CasePair("건너뛴 경유지", session.progress.count { it.status == CheckpointStatus.SKIPPED }.toString())
                        CasePair("공개한 힌트", "${session.progress.sumOf { it.revealedHintCount }}개")
                        CasePair("정답 공개", "${session.progress.count { it.answerRevealed }}회")
                        CText("다녀온 길", size = 20, weight = FontWeight.Bold)
                        state.content?.checkpoints?.forEach { checkpoint ->
                            val progress = session.progress.first { it.checkpointId == checkpoint.id }
                            if (progress.arrivedAt != null) CText("•  ${checkpoint.title} · ${checkpointStateLabel(progress.status)}", color = CaseColors.Cream.copy(alpha = .8f))
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (complete) {
                                EscapeButton("해설 다시 보기", "open-solution", onSolution, secondary = true)
                                EscapeButton("기념 카드", "open-certificate", onCertificate)
                            } else EscapeButton("수사 재개", "casebook-resume", onResume)
                        }
                    }
                }
                if (maxWidth >= 900.dp) Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                    Box(Modifier.weight(1.2f)) { summary() }
                    Box(Modifier.weight(1f)) { detail() }
                } else Column(verticalArrangement = Arrangement.spacedBy(20.dp)) { summary(); detail() }
            }
        }
    }
}

@Composable
fun CaseCompletionScreen(state: GameUiState, onNew: () -> Unit, onSolution: () -> Unit, onCertificate: () -> Unit, onRating: () -> Unit) {
    if (!state.canReadContent) { CasePausedScreen(state); return }
    val session = state.session ?: return
    if (session.progress.any { !it.status.isComplete }) {
        CText("세 경유지를 모두 처리하면 완료 내역을 볼 수 있습니다.")
        return
    }
    val allSolved = session.progress.all { it.status == CheckpointStatus.SOLVED }
    Box(Modifier.fillMaxSize().testTag("screen-completion")) {
    Column(Modifier.fillMaxSize().testTag("completion").verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        Text("Case Closed", fontFamily = FontFamily.Cursive, fontSize = 37.sp, color = CaseColors.Gold)
        CText("세 개의 경유지를 모두 지났어요", size = 34, weight = FontWeight.Bold)
        CText("이번 테스트 사건의 진행을 기기에 저장했습니다. 해결한 퍼즐과 건너뛴 퍼즐은 각각의 기록으로 남습니다.", color = CaseColors.Cream.copy(alpha = .8f))
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val results: @Composable () -> Unit = {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ResultCard("해결", session.progress.count { it.status == CheckpointStatus.SOLVED }, "summary-solved")
                    ResultCard("건너뜀", session.progress.count { it.status == CheckpointStatus.SKIPPED }, "summary-skipped")
                    ResultCard("공개한 힌트", session.progress.sumOf { it.revealedHintCount }, "summary-hints")
                    ResultCard("정답 공개", session.progress.count { it.answerRevealed }, "summary-answers")
                }
            }
            val stamp: @Composable () -> Unit = {
            Column(Modifier.widthIn(min = 150.dp).border(2.dp, CaseColors.Red).padding(horizontal = 20.dp, vertical = 26.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                CLabel("CASE NO. 01")
                CText(if (allSolved) "SOLVED" else "COMPLETED", size = 27, color = CaseColors.Red, weight = FontWeight.Bold)
                CText(if (allSolved) "해결 완료" else "테스트 완료", color = CaseColors.Cream.copy(alpha = .7f))
            }
            }
            if (maxWidth >= 800.dp) Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { results() }
                stamp()
            } else Column(verticalArrangement = Arrangement.spacedBy(18.dp)) { results(); stamp() }
        }
        state.content?.checkpoints?.forEach { checkpoint ->
            val progress = session.progress.first { it.checkpointId == checkpoint.id }
            CText("${checkpoint.title} · ${checkpointStateLabel(progress.status)} · 힌트 ${progress.revealedHintCount}개 · 정답 공개 ${if (progress.answerRevealed) "있음" else "없음"}", color = CaseColors.Cream.copy(alpha = .8f))
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            EscapeButton("사건 해설", "open-solution", onSolution, secondary = true)
            EscapeButton("기념 카드 만들기", "open-certificate", onCertificate)
            EscapeButton("테마 평가", "open-rating", onRating, secondary = true)
            EscapeButton(stringResource(R.string.new_game), "start-new", onNew, enabled = !state.saving, secondary = true)
        }
    }
    }
}

@Composable
private fun ResultCard(label: String, count: Int, tag: String) {
    CasePanel(Modifier.widthIn(min = 145.dp)) {
        CText(label, size = 14, color = CaseColors.Cream.copy(alpha = .7f))
        CText(count.toString(), size = 32, weight = FontWeight.Bold, modifier = Modifier.testTag(tag))
    }
}

@Composable
private fun ProgressSegments(session: GameSession) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        session.progress.forEach { progress ->
            Box(Modifier.weight(1f).height(4.dp).background(if (progress.status.isComplete) CaseColors.Red else CaseColors.Ink.copy(alpha = .2f)))
        }
    }
}

@Composable
private fun CasePage(tag: String, english: String, korean: String, side: String? = null, content: @Composable BoxScope.() -> Unit) {
    Column(Modifier.fillMaxSize().testTag(tag), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        CaseHeading(english, korean, side = side)
        Box(Modifier.weight(1f).fillMaxWidth(), content = content)
    }
}

@Composable
private fun ScreenActions(onBack: () -> Unit, content: @Composable RowScope.() -> Unit) {
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        EscapeButton("돌아가기", "screen-back", onBack, secondary = true)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
private fun RomanBadge(index: Int, dark: Boolean = false) {
    Box(Modifier.size(40.dp).border(1.dp, if (dark) CaseColors.Red else CaseColors.Gold), contentAlignment = Alignment.Center) {
        CText(listOf("I", "II", "III", "IV").getOrElse(index) { (index + 1).toString() }, color = if (dark) CaseColors.Red else CaseColors.Gold)
    }
}

@Composable
private fun CText(text: String, modifier: Modifier = Modifier, size: Int = 16, color: androidx.compose.ui.graphics.Color = CaseColors.Cream, weight: FontWeight = FontWeight.Normal, align: TextAlign = TextAlign.Start) {
    Text(text, modifier, color = color, fontFamily = CaseFonts.Serif, fontSize = size.sp, fontWeight = weight, textAlign = align, lineHeight = (size * 1.55f).sp)
}

@Composable
private fun CLabel(text: String, modifier: Modifier = Modifier, dark: Boolean = false) {
    Text(text, modifier, fontFamily = CaseFonts.Serif, fontSize = 11.sp, letterSpacing = 2.sp, color = if (dark) CaseColors.Ink.copy(alpha = .7f) else CaseColors.Gold)
}

@Composable
private fun PaperPair(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        CText(label, color = CaseColors.Ink.copy(alpha = .6f), size = 14)
        CText(value, color = CaseColors.Ink, size = 14)
    }
}

@Composable
private fun CasePair(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        CText(label, color = CaseColors.Cream.copy(alpha = .7f))
        CText(value)
    }
    HorizontalDivider(color = CaseColors.Gold.copy(alpha = .3f))
}

private fun checkpointStateLabel(status: CheckpointStatus): String = when (status) {
    CheckpointStatus.NOT_STARTED -> "시작 전"
    CheckpointStatus.IN_PROGRESS -> "수사 중"
    CheckpointStatus.SOLVED -> "해결"
    CheckpointStatus.SKIPPED -> "건너뜀"
}
