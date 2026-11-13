package com.team.carescape.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.team.carescape.game.CarControl
import com.team.carescape.game.CarVisual
import com.team.carescape.game.StoryLockDefinition

/** 오른쪽 입력 칸의 공통 틀. */
@Composable
private fun LockPanel(title: String, subtitle: String, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(Esc.panel).border(1.dp, Esc.goldDim).padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(title, style = Esc.pinyon(30))
        Text(subtitle, style = Esc.noto(14, Esc.creamSoft), textAlign = TextAlign.Center)
        content()
    }
}

@Composable
private fun SelectCard(label: String, selected: Boolean, enabled: Boolean, tag: String, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(52.dp)
            .background(if (selected) Esc.gold.copy(alpha = 0.22f) else Color.Transparent)
            .border(if (selected) 2.dp else 1.dp, if (selected) Esc.gold else Esc.goldDim)
            .semantics { this.selected = selected }
            .clickable(enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .testTag(tag)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(label, style = Esc.noto(17, if (selected) Esc.cream else Esc.creamMuted))
    }
}

/** 보기 하나 고르기. */
@Composable
fun ChoiceLock(
    lock: StoryLockDefinition.Choice,
    selected: String?,
    enabled: Boolean,
    onSelect: (String) -> Unit,
    onSubmit: () -> Unit,
    feedback: @Composable () -> Unit,
) {
    LockPanel("Choose", "보기 하나를 고른 뒤 제출하세요.") {
        lock.options.forEach { option ->
            SelectCard(option.label, option.id == selected, enabled, "choice-${option.id}") { onSelect(option.id) }
        }
        ParchmentButton("제출", onSubmit, Modifier.testTag("story-puzzle-submit"), enabled = enabled && selected != null, minWidth = 180.dp, minHeight = 48.dp)
        feedback()
    }
}

/** 보기를 차례로 눌러 순서를 만든다. 다시 누르면 그 뒤를 지운다. */
@Composable
fun OrderLock(
    lock: StoryLockDefinition.Order,
    order: List<String>,
    enabled: Boolean,
    onChange: (List<String>) -> Unit,
    onSubmit: () -> Unit,
    feedback: @Composable () -> Unit,
) {
    LockPanel("Arrange", "차례대로 누르세요. 고른 것을 다시 누르면 그 뒤가 지워집니다.") {
        lock.items.forEach { item ->
            val position = order.indexOf(item.id)
            val label = if (position >= 0) "${position + 1}.  ${item.label}" else item.label
            SelectCard(label, position >= 0, enabled, "order-${item.id}") {
                onChange(if (position >= 0) order.take(position) else order + item.id)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlineButton("처음부터", { onChange(emptyList()) }, Modifier.testTag("order-reset"), enabled = enabled && order.isNotEmpty(), minHeight = 48.dp)
            ParchmentButton("제출", onSubmit, Modifier.testTag("story-puzzle-submit"), enabled = enabled && order.isNotEmpty(), minWidth = 140.dp, minHeight = 48.dp)
        }
        feedback()
    }
}

/** 자릿수가 4가 아닌 숫자(예: 홍예 7칸). */
@Composable
fun DigitStepperLock(
    digits: List<Int>,
    enabled: Boolean,
    onChange: (List<Int>) -> Unit,
    onSubmit: () -> Unit,
    feedback: @Composable () -> Unit,
) {
    LockPanel("Count", "−·+로 숫자를 맞추세요.") {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            digits.forEachIndexed { i, d ->
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlineButton("+", { onChange(digits.mapIndexed { j, v -> if (j == i) (v + 1) % 10 else v }) }, Modifier.testTag("digit-$i-plus"), enabled = enabled, minWidth = 56.dp, minHeight = 48.dp)
                    Text(d.toString(), Modifier.testTag("digit-$i"), style = Esc.cormorant(52, Esc.cream, 0f))
                    OutlineButton("−", { onChange(digits.mapIndexed { j, v -> if (j == i) (v + 9) % 10 else v }) }, Modifier.testTag("digit-$i-minus"), enabled = enabled, minWidth = 56.dp, minHeight = 48.dp)
                }
            }
        }
        ParchmentButton("제출", onSubmit, Modifier.testTag("story-puzzle-submit"), enabled = enabled, minWidth = 180.dp, minHeight = 48.dp)
        feedback()
    }
}

/** 차량 조작 대체 입력. 버튼 하나가 차량 조작 한 번이다. 실제 차량 설정을 읽거나 바꾸지 않는다. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CarSequenceLock(
    lock: StoryLockDefinition.CarSequence,
    value: List<CarControl>,
    enabled: Boolean,
    scene: Boolean,
    onChange: (List<CarControl>) -> Unit,
    onSubmit: () -> Unit,
    feedback: @Composable () -> Unit,
) {
    LockPanel(if (scene) "In the Car" else "Car Controls", "차량 신호 연동 전 · 화면 버튼으로 입력") {
        LockVisual(lock.visual, value.map { it.name })
        if (!scene) Text(
            if (value.isEmpty()) "아직 입력이 없습니다" else value.joinToString("  →  ") { it.short },
            Modifier.testTag("car-sequence"), style = Esc.noto(15, Esc.gold), textAlign = TextAlign.Center,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            lock.controls.forEach { control ->
                OutlineButton(control.label, { if (value.size < lock.maxLength) onChange(value + control) },
                    Modifier.testTag("car-${control.name}").semantics { contentDescription = "차량 조작 ${control.label}" },
                    enabled = enabled && value.size < lock.maxLength && !(scene && value.isNotEmpty()), minHeight = 48.dp, fontSize = 15)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (!scene) OutlineButton("처음부터", { onChange(emptyList()) }, Modifier.testTag("car-reset"), enabled = enabled && value.isNotEmpty(), minHeight = 48.dp)
            ParchmentButton(if (scene) "했어요" else "제출", onSubmit, Modifier.testTag("story-puzzle-submit"), enabled = enabled && value.isNotEmpty(), minWidth = 140.dp, minHeight = 48.dp)
        }
        feedback()
    }
}

@Composable
internal fun LockVisual(visual: CarVisual, value: List<String>) {
    fun isLeft(k: String?) = k == CarControl.TURN_LEFT.name || k == "L"
    fun isRight(k: String?) = k == CarControl.TURN_RIGHT.name || k == "R"
    when (visual) {
        CarVisual.None -> Unit
        is CarVisual.Sluice -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
            repeat(visual.gates) { i ->
                val state = value.getOrNull(i)
                val current = i == value.size
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(
                        Modifier.size(32.dp, 58.dp)
                            .border(if (current) 2.dp else 1.dp, if (current) Esc.gold else Esc.goldDim)
                            .background(when (state) {
                                CarControl.WIPER.name -> Esc.inkSoft
                                CarControl.HAZARD.name -> Color(0xFF6F8AA0)
                                else -> Color.Transparent
                            }),
                    )
                    Text("${i + 1}", style = Esc.noto(12, if (current) Esc.gold else Esc.creamSoft))
                }
            }
        }
        is CarVisual.WaterMap -> Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("출발 높이 ${visual.start}", Modifier.fillMaxWidth(), style = Esc.noto(14, Esc.gold), textAlign = TextAlign.Center)
            visual.forks.forEachIndexed { i, fork ->
                val pick = value.getOrNull(i)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    ForkCell("${fork.left}${if (fork.leftNote.isNotEmpty()) " · ${fork.leftNote}" else ""}", isLeft(pick), Modifier.weight(1f))
                    Text("갈림 ${i + 1}", style = Esc.noto(12, if (i == value.size) Esc.gold else Esc.creamSoft))
                    ForkCell("${fork.right}${if (fork.rightNote.isNotEmpty()) " · ${fork.rightNote}" else ""}", isRight(pick), Modifier.weight(1f))
                }
            }
        }
        is CarVisual.Beacon -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val lit = value.count { it == CarControl.HEADLIGHT.name || it == "FIRE" }
            repeat(visual.pits) { i ->
                Canvas(Modifier.size(40.dp, 52.dp)) {
                    drawRect(Esc.goldDim, Offset(4f, size.height * 0.55f), Size(size.width - 8f, size.height * 0.45f), style = Stroke(2f))
                    if (i < lit) drawCircle(Color(0xFFE08A3C), size.width * 0.32f, Offset(size.width / 2, size.height * 0.38f))
                }
            }
        }
    }
}

@Composable
private fun ForkCell(text: String, chosen: Boolean, modifier: Modifier) {
    Box(
        modifier.height(36.dp).border(if (chosen) 2.dp else 1.dp, if (chosen) Color(0xFF6F8AA0) else Esc.goldDim)
            .background(if (chosen) Color(0x556F8AA0) else Color.Transparent),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = Esc.noto(13, Esc.creamMuted)) }
}

/** 문서 본문의 【 】 글자를 붉은 먹점으로 강조한다. */
fun documentAnnotated(body: String): androidx.compose.ui.text.AnnotatedString = androidx.compose.ui.text.buildAnnotatedString {
    var rest = body
    while (true) {
        val open = rest.indexOf('【')
        val close = if (open >= 0) rest.indexOf('】', open) else -1
        if (open < 0 || close < 0) { append(rest); break }
        append(rest.substring(0, open))
        pushStyle(androidx.compose.ui.text.SpanStyle(color = Esc.seal, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            background = Esc.seal.copy(alpha = 0.12f)))
        append(rest.substring(open + 1, close))
        pop()
        rest = rest.substring(close + 1)
    }
}

