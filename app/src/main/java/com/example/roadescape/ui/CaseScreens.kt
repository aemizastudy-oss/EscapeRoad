package com.example.roadescape.ui

import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
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
import com.example.roadescape.model.LobbyBlock
import com.example.roadescape.model.LobbyLayoutProfile

@Composable
fun LobbyScreen(
    state: GameUiState,
    onCases: () -> Unit,
    onArchive: () -> Unit,
    onCasebook: () -> Unit,
    onResume: () -> Unit,
    onTour: () -> Unit = {},
    onSdk: () -> Unit = {},
) {
    BoxWithConstraints(Modifier.fillMaxSize().background(CaseColors.DeepBurgundy).testTag("screen-lobby")) {
        val wide = maxWidth >= 900.dp
        Image(
            painter = painterResource(R.drawable.lobby_background), contentDescription = null,
            modifier = Modifier.matchParentSize(), contentScale = ContentScale.FillBounds,
        )
        // The supplied frame's upper edge is about 1/12 of its height.
        val innerTopPadding = 20.dp + 18.dp
        val contentTopPadding = maxOf(18.dp, maxHeight / 12f + 16.dp - innerTopPadding)
        val viewportHeight = maxHeight
        val editor = rememberLobbyLayoutEditor(
            profile = if (wide) LobbyLayoutProfile.WIDE else LobbyLayoutProfile.COMPACT,
            canEditSafely = state.canReadContent,
        )
        Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            LobbyLayoutCanvas(editor, wide, viewportHeight, contentTopPadding, state.session != null) { block ->
                when (block) {
                    LobbyBlock.WELCOME -> Text("Welcome, Detective", style = script(40))
                    LobbyBlock.LOGO -> Image(
                        painter = painterResource(R.drawable.pdf_logo),
                        contentDescription = "ESCAPE ROAD",
                        modifier = Modifier.widthIn(max = 380.dp).fillMaxWidth().height(if (wide) 220.dp else 180.dp),
                        contentScale = ContentScale.Fit,
                    )
                    LobbyBlock.TAGLINE -> CLabel("THE DETECTIVE'S DRIVE CLUB · MMXXVI", Modifier.padding(top = 4.dp))
                    LobbyBlock.TITLE -> CText("내 차로 떠나는 방탈출 여행", size = 23, weight = FontWeight.Bold)
                    LobbyBlock.DESCRIPTION -> CText("오늘 맡으실 사건을 고르시면, 첫 번째 단서를 건네드리겠습니다.", color = CaseColors.Cream.copy(alpha = .8f), modifier = Modifier.widthIn(max = 720.dp), align = TextAlign.Center)
                    LobbyBlock.ACTIONS -> FlowRow(Modifier.padding(top = 18.dp).widthIn(max = 600.dp), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        // 시안의 로비처럼 버튼 두 개만 둔다. SDK 연결 상태·사건 기록부·저장된 수사는 '더보기' 메뉴에 있다.
                        EscapeButton("수원 관광 코스 · 5챕터", "open-suwon-tour", { if (!editor.editing) onTour() }, modifier = Modifier.widthIn(min = 220.dp).heightIn(min = 58.dp))
                        EscapeButton("사건 고르기", "open-cases", { if (!editor.editing) onCases() }, secondary = true, modifier = Modifier.widthIn(min = 170.dp).heightIn(min = 58.dp))
                    }
                    LobbyBlock.RESUME -> state.session?.let { session ->
                        // 시안: 폭 236의 양피지 카드, -2도 기울임, 안쪽 6px 선.
                        Column(
                            Modifier.width(236.dp).rotate(-2f).shadow(14.dp).caseTexture(true).padding(6.dp).border(1.dp, CaseColors.ParchEdge).padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text("Case in Progress", style = smallCaps(14, CaseColors.InkLabel, .18f))
                            Text(state.content?.title.orEmpty(), fontFamily = CaseFonts.Heading, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, lineHeight = 24.sp, color = CaseColors.Ink, textAlign = TextAlign.Center)
                            // Detailed results remain behind the same parking gate as the casebook.
                            if (state.canReadContent) Text("장소 ${session.progress.count { it.status.isComplete }} / ${session.progress.size}", fontFamily = CaseFonts.Body, fontSize = 14.sp, color = Color(0xFF5A3E2A))
                            EscapeButton("수사 재개", "resume", { if (!editor.editing) onResume() }, enabled = !state.saving, modifier = Modifier.fillMaxWidth().heightIn(min = 46.dp))
                        }
                    }
                }
            }
        }
        LobbyLayoutEditorControls(editor, Modifier.fillMaxSize())
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
                Text("Dear Detective,", fontFamily = CaseFonts.Script, color = CaseColors.Red, fontSize = 28.sp)
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
                Text("The Route", fontFamily = CaseFonts.Script, fontSize = 30.sp, color = CaseColors.Gold)
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
                Text("The First Clue", fontFamily = CaseFonts.Script, fontSize = 32.sp, color = CaseColors.Cream)
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
    val story = content.story
    CasePage("screen-checkpoints", if (story != null) "The Route" else "Map of the First Clue", stringResource(R.string.nav_case_route), if (story != null) "${story.title}, ${story.subtitle}" else "CASE NO. 01") {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val map: @Composable () -> Unit = {
                PaperSheet(Modifier.fillMaxWidth()) {
                    if (story != null && content.checkpoints.size == 5) {
                        Text("Follow the Water", style = script(30, CaseColors.Wine))
                        StoryRouteMap(state)
                        CText("실제 지도가 아닌 물길 진행도입니다.", size = 13, color = CaseColors.Ink.copy(alpha = .6f))
                    } else {
                        CText("${content.checkpoints.size}개 경유지", size = 22, color = CaseColors.Ink, weight = FontWeight.Bold)
                        CText("실제 지도가 아닌 경유지 진행도입니다.", size = 14, color = CaseColors.Ink.copy(alpha = .6f))
                        RouteDiagram(state)
                    }
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

/** 캔버스 '로드트립 지도'(TripMap) 좌표 그대로(760x580). 다섯 장소의 시안 위치. */
private val storyNodes = listOf(Offset(120f, 500f), Offset(300f, 380f), Offset(300f, 230f), Offset(480f, 260f), Offset(610f, 90f))
private val storySolvedNotes = listOf("왕의 밀서 · 비방 첫 장", "재료 1 · 흐르는 물", "재료 2 · 곡식", "비방 마지막 장 · 백신", "백신 전달")
private val storyCurrentNotes = listOf("행궁 · 수사 중", "수문 · 수사 중", "만석거 · 수사 중", "박물관 · 수사 중", "원천호수 · 수사 중")
private fun svg(d: String) = androidx.compose.ui.graphics.vector.PathParser().parsePathString(d).toPath()
private val storyRoad by lazy { svg("M120 500 C 200 470, 220 420, 300 380 S 330 260, 300 230 S 420 180, 480 260 S 560 140, 610 90") }
private val storySegments by lazy { listOf("M120 500 C 200 470, 220 420, 300 380", "M300 380 C 380 340, 330 260, 300 230", "M300 230 C 270 200, 420 180, 480 260", "M480 260 C 540 340, 560 140, 610 90").map(::svg) }
private val storyWaters by lazy { listOf("M700 40 C 640 120, 600 160, 560 230 S 470 330, 420 380 S 330 470, 300 560", "M700 40 C 690 150, 650 220, 590 300").map(::svg) }

/** 시역 코스의 물길 지도. 해결=붉은 체크, 다음 목적지=금색 표식, 봉인=점선 물음표(이름도 가림). */
@Composable
private fun StoryRouteMap(state: GameUiState) {
    val content = state.content ?: return
    val session = state.session ?: return
    val currentId = GameController.currentCheckpoint(content, session)?.id
    val checkpoints = content.checkpoints.take(5)
    fun status(i: Int): Int {
        val cp = checkpoints[i]
        val p = session.progress.first { it.checkpointId == cp.id }
        return when { p.status.isComplete -> 0; cp.id == currentId -> 1; else -> 2 }
    }
    val seal = Color(0xFF7A2026); val brown = Color(0xFF5A4A36); val locked = Color(0xFF7E6A4E)
    BoxWithConstraints(Modifier.fillMaxWidth().height(600.dp)) {
        val scale = minOf(maxWidth.value / 760f, maxHeight.value / 580f)
        val left = (maxWidth.value - 760f * scale) / 2f
        val top = (maxHeight.value - 580f * scale) / 2f
        Canvas(Modifier.fillMaxSize()) {
            val d = this.density; val k = scale * d
            withTransform({ translate(left * d, top * d); scale(k, k, Offset.Zero) }) {
                storyWaters.forEach { drawPath(it, Color(0xFF6F8AA0).copy(alpha = .7f), style = Stroke(2f)) }
                listOf(Triple(610f, 90f, 46f to 22f), Triple(560f, 140f, 34f to 16f), Triple(300f, 230f, 36f to 20f)).forEach { (x, y, r) ->
                    drawOval(Color(0xFF9DB3C2).copy(alpha = .45f), Offset(x - r.first, y - r.second), Size(r.first * 2, r.second * 2))
                    drawOval(Color(0xFF6F8AA0), Offset(x - r.first, y - r.second), Size(r.first * 2, r.second * 2), style = Stroke(1.2f))
                }
                drawPath(storyRoad, Color(0xFFD8C9A6), style = Stroke(12f, cap = StrokeCap.Round))
                val reached = (0 until 5).count { status(it) == 0 }.coerceAtMost(storySegments.size)
                storySegments.take(reached).forEach {
                    drawPath(it, seal, style = Stroke(3.5f, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(2f, 9f))))
                }
                val c = Offset(110f, 110f)
                drawCircle(brown, 40f, c, style = Stroke(1.2f))
                drawPath(Path().apply { moveTo(c.x, c.y - 50); lineTo(c.x + 7, c.y); lineTo(c.x, c.y + 50); lineTo(c.x - 7, c.y); close() }, brown)
                drawPath(Path().apply { moveTo(c.x - 50, c.y); lineTo(c.x, c.y - 7); lineTo(c.x + 50, c.y); lineTo(c.x, c.y + 7); close() }, Color(0xFF8C7556))
                storyNodes.forEachIndexed { i, p ->
                    when (status(i)) {
                        0 -> {
                            drawCircle(seal, 16f, p)
                            drawPath(Path().apply { moveTo(p.x - 5, p.y); lineTo(p.x - 1, p.y + 4); lineTo(p.x + 6, p.y - 4) }, CaseColors.Cream, style = Stroke(2.4f, cap = StrokeCap.Round))
                        }
                        1 -> {
                            drawCircle(seal, 28f, p, style = Stroke(2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 5f))))
                            drawCircle(CaseColors.Gold, 16f, p)
                            drawCircle(Color(0xFF6B4A1C), 16f, p, style = Stroke(2f))
                            drawCircle(CaseColors.ButtonInk, 5f, p)
                        }
                        else -> drawCircle(locked, 14f, p, style = Stroke(2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f))))
                    }
                }
            }
        }
        fun at(x: Float, y: Float) = Modifier.offset((left + x * scale).dp, (top + y * scale).dp)
        Text("N", at(104f, 40f), style = smallCaps((16 * scale).toInt().coerceAtLeast(10), brown, 0f))
        Text("광교산", at(660f, 16f), fontFamily = CaseFonts.Heading, fontWeight = FontWeight.ExtraBold, fontSize = (14 * scale).sp, color = brown)
        storyNodes.forEachIndexed { i, p ->
            val st = status(i)
            if (st == 2) Text("?", at(p.x - 4f, p.y - 10f), style = smallCaps((15 * scale).toInt().coerceAtLeast(10), locked, 0f))
            val final = i == 4
            val name = when { st == 2 && final -> "???"; st == 2 -> "장소 ${i + 1}"; else -> checkpoints[i].title.replace(Regex("^CH\\.\\d+\\s*"), "") }
            val note = when (st) { 0 -> storySolvedNotes[i]; 1 -> storyCurrentNotes[i]; else -> if (final) "단서를 모으면 공개" else "봉인됨" }
            Column(at(p.x + 26f, p.y - 20f)) {
                Text(name, fontFamily = CaseFonts.Heading, fontWeight = FontWeight.ExtraBold, fontSize = (17 * scale).sp, lineHeight = (20 * scale).sp,
                    color = when (st) { 0 -> CaseColors.Ink; 1 -> seal; else -> Color(0xFF6A5A44) })
                Text(note, fontFamily = CaseFonts.Body, fontSize = (13 * scale).sp, lineHeight = (16 * scale).sp, color = brown)
            }
        }
    }
}

@Composable
private fun RouteDiagram(state: GameUiState) {
    val content = state.content ?: return
    val session = state.session ?: return
    val currentId = GameController.currentCheckpoint(content, session)?.id
    val checkpoints = content.checkpoints.take(5)
    val positions = if(checkpoints.size<=3)listOf(.12f to .80f,.44f to .48f,.75f to .18f).take(checkpoints.size)
        else listOf(.08f to .83f,.26f to .65f,.44f to .47f,.63f to .29f,.81f to .12f).take(checkpoints.size)
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
        CText("모든 경유지를 처리하면 완료 내역을 볼 수 있습니다.")
        return
    }
    val allSolved = session.progress.all { it.status == CheckpointStatus.SOLVED }
    Box(Modifier.fillMaxSize().testTag("screen-completion")) {
    Column(Modifier.fillMaxSize().testTag("completion").verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        val story = state.content?.story
        Text(if (story != null) "Mission Complete" else "Case Closed", style = script(44))
        if (story != null) Text("임무 완료 · 진 엔딩", fontFamily = CaseFonts.Heading, fontWeight = FontWeight.ExtraBold, fontSize = 44.sp, lineHeight = 57.sp)
        CText("${session.progress.size}개 경유지의 진행을 모두 마쳤어요", size = if (story != null) 20 else 34, weight = FontWeight.Bold)
        if (story != null) {
            CText("${story.title}, ${story.subtitle}", size = 22, color = CaseColors.Gold)
            CText(story.ending, color = CaseColors.Cream, modifier = Modifier.testTag("story-ending"))
        }
        CText("이번 사건의 진행을 기기에 저장했습니다. 해결한 퍼즐과 건너뛴 퍼즐은 각각의 기록으로 남습니다.", color = CaseColors.Cream.copy(alpha = .8f))
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
            // 시안의 붉은 이중 테두리 도장(-8도)
            val stampRed = Color(0xFFB83A33)
            Column(Modifier.padding(24.dp).rotate(-8f).border(2.dp, stampRed).padding(2.dp).border(2.dp, stampRed).padding(horizontal = 34.dp, vertical = 26.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(if (story != null) "CASE NO. 03" else "CASE NO. 01", style = smallCaps(22, stampRed, .3f))
                Text(if (allSolved) "SOLVED" else "COMPLETED", fontFamily = CaseFonts.SmallCaps, fontWeight = FontWeight.Bold, fontSize = 64.sp, lineHeight = 68.sp, color = stampRed)
                Text(if (story != null) "임무 완료" else if (allSolved) "해결 완료" else "테스트 완료", fontFamily = CaseFonts.Heading, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = stampRed)
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
        state.content?.story?.let { CText(it.disclaimer, size = 13, color = CaseColors.Cream.copy(alpha = .6f)) }
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
