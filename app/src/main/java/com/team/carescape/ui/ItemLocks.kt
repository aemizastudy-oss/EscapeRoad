package com.team.carescape.ui

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.team.carescape.game.CaseItem
import com.team.carescape.game.StoryLockDefinition

@Composable
private fun Panel(title: String, subtitle: String, content: @Composable () -> Unit) {
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

/** 글자 바퀴 자물쇠. 바퀴마다 ▲·▼로 돌린다. */
@Composable
fun SymbolDialLock(
    lock: StoryLockDefinition.SymbolDial,
    value: List<Int>,
    enabled: Boolean,
    onChange: (List<Int>) -> Unit,
    onSubmit: () -> Unit,
    feedback: @Composable () -> Unit,
) {
    Panel("Turn the Dials", "바퀴를 돌려 글자를 맞춘 뒤 제출하세요.") {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            lock.wheels.forEachIndexed { w, wheel ->
                val i = value.getOrElse(w) { 0 }
                fun turn(d: Int) = onChange(lock.wheels.indices.map { j -> if (j == w) (i + d + wheel.size) % wheel.size else value.getOrElse(j) { 0 } })
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlineButton("▲", { turn(-1) }, Modifier.testTag("dial-$w-up"), enabled = enabled, minWidth = 110.dp, minHeight = 44.dp)
                    Text(wheel[(i - 1 + wheel.size) % wheel.size], style = Esc.noto(13, Esc.creamSoft.copy(alpha = 0.5f)))
                    Box(Modifier.width(110.dp).border(2.dp, Esc.gold).background(Esc.gold.copy(alpha = 0.14f)).padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                        Text(wheel[i], Modifier.testTag("dial-$w"), style = Esc.nanum(22))
                    }
                    Text(wheel[(i + 1) % wheel.size], style = Esc.noto(13, Esc.creamSoft.copy(alpha = 0.5f)))
                    OutlineButton("▼", { turn(1) }, Modifier.testTag("dial-$w-down"), enabled = enabled, minWidth = 110.dp, minHeight = 44.dp)
                }
            }
        }
        ParchmentButton("제출", onSubmit, Modifier.testTag("story-puzzle-submit"), enabled = enabled, minWidth = 180.dp, minHeight = 48.dp)
        feedback()
    }
}

/** 소지품 한 칸. 쓴 물건은 흐리게 보이고 고를 수 없다. */
@Composable
fun ItemTile(
    item: CaseItem, used: Boolean, selected: Boolean, enabled: Boolean, onClick: (() -> Unit)?,
    modifier: Modifier = Modifier, allowUsed: Boolean = false,
) {
    val on = selected && (allowUsed || !used)
    Column(
        modifier.width(104.dp)
            .border(if (on) 2.dp else 1.dp, if (on) Esc.gold else Esc.goldDim)
            .background(if (on) Esc.gold.copy(alpha = 0.2f) else Color.Transparent)
            .semantics { this.selected = on; contentDescription = "소지품 ${item.name}${if (used) " (사용함)" else ""}" }
            .let { if (onClick != null) it.clickable(enabled = enabled && (allowUsed || !used), role = Role.RadioButton, onClick = onClick) else it }
            .testTag("item-${item.id}")
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ItemArt(item.id, Modifier.size(64.dp), used)
        Text(item.name, style = Esc.noto(12, if (used) Esc.locked else Esc.cream, lineHeight = 1.3f), textAlign = TextAlign.Center)
        if (used) Text("사용함", style = Esc.noto(11, Esc.locked))
    }
}

/** 소지품에서 하나를 골라 쓴다. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UseItemLock(
    inventory: List<Pair<CaseItem, Boolean>>,
    selected: String?,
    enabled: Boolean,
    onSelect: (String) -> Unit,
    onSubmit: () -> Unit,
    feedback: @Composable () -> Unit,
) {
    Panel("Use an Item", "소지품에서 쓸 것을 고르세요.") {
        if (inventory.isEmpty()) Text("소지품이 비어 있습니다.", style = Esc.noto(14, Esc.creamSoft))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            inventory.forEach { (item, used) -> ItemTile(item, used, item.id == selected, enabled, { onSelect(item.id) }) }
        }
        inventory.firstOrNull { it.first.id == selected }?.let { (item, _) ->
            Text(item.description, style = Esc.noto(13, Esc.creamMuted, lineHeight = 1.5f), textAlign = TextAlign.Center)
        }
        ParchmentButton("쓰기", onSubmit, Modifier.testTag("story-puzzle-submit"), enabled = enabled && selected != null, minWidth = 180.dp, minHeight = 48.dp)
        feedback()
    }
}

/** 앱 안에서 누르는 순서(물길·봉화). 차량 신호를 쓰지 않는다. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SequenceLock(
    lock: StoryLockDefinition.Sequence,
    value: List<String>,
    enabled: Boolean,
    onChange: (List<String>) -> Unit,
    onSubmit: () -> Unit,
    feedback: @Composable () -> Unit,
) {
    Panel("In the Ledger", "화면을 눌러 차례대로 정하세요.") {
        LockVisual(lock.visual, value)
        Text(
            if (value.isEmpty()) "아직 고른 것이 없습니다" else value.joinToString("  →  ") { id -> lock.tokens.first { it.id == id }.label },
            Modifier.testTag("sequence-value"), style = Esc.noto(14, Esc.gold), textAlign = TextAlign.Center,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            lock.tokens.forEach { token ->
                OutlineButton(token.label, { if (value.size < lock.maxLength) onChange(value + token.id) },
                    Modifier.testTag("seq-${token.id}"), enabled = enabled && value.size < lock.maxLength, minHeight = 48.dp, fontSize = 15)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlineButton("처음부터", { onChange(emptyList()) }, Modifier.testTag("seq-reset"), enabled = enabled && value.isNotEmpty(), minHeight = 48.dp)
            ParchmentButton("제출", onSubmit, Modifier.testTag("story-puzzle-submit"), enabled = enabled && value.isNotEmpty(), minWidth = 140.dp, minHeight = 48.dp)
        }
        feedback()
    }
}
