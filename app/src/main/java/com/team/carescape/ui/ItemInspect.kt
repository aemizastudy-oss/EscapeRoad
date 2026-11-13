package com.team.carescape.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.team.carescape.game.CaseItem

private val pageColor = Color(0xFFEFE2C4)
private val pageInk = Color(0xFF3A2A20)

/** 소지품 자세히 보기. 수첩은 펼친 책장으로, 서찰·소식지는 펼친 종이로, 나머지는 큰 그림으로 보여 준다. */
@Composable
fun ItemInspect(item: CaseItem, used: Boolean, modifier: Modifier = Modifier) {
    Column(modifier.testTag("item-inspect-${item.id}"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(item.name, style = Esc.nanum(26))
            if (used) Text("사용함", Modifier.border(1.dp, Esc.locked).padding(horizontal = 8.dp, vertical = 2.dp), style = Esc.noto(13, Esc.locked))
        }
        when {
            item.id == "notebook" && item.pages.isNotEmpty() -> BookSpread(item.pages, Modifier.weight(1f).fillMaxWidth())
            item.pages.isNotEmpty() -> PaperSheet(item, Modifier.weight(1f).fillMaxWidth())
            else -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                ItemArt(item.id, Modifier.size(260.dp), used)
            }
        }
        Text(item.description, style = Esc.noto(15, Esc.creamMuted, lineHeight = 1.6f))
    }
}

/** 펼친 선장본 수첩. 두 쪽씩 넘긴다. */
@Composable
private fun BookSpread(pages: List<String>, modifier: Modifier) {
    var spread by rememberSaveable { mutableIntStateOf(0) }
    val last = (pages.size - 1) / 2
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.weight(1f).fillMaxWidth().shadow(14.dp)) {
            Row(Modifier.fillMaxSize()) {
                listOf(spread * 2, spread * 2 + 1).forEachIndexed { side, idx ->
                    Box(
                        Modifier.weight(1f).fillMaxHeight().background(
                            Brush.horizontalGradient(
                                if (side == 0) listOf(pageColor, pageColor, Color(0xFFD8C6A0)) else listOf(Color(0xFFD8C6A0), pageColor, pageColor),
                            ),
                        ).padding(horizontal = 26.dp, vertical = 22.dp),
                    ) {
                        Text(pages.getOrNull(idx) ?: "", Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).testTag("book-page-$idx"),
                            style = Esc.nanum(18, pageInk, lineHeight = 1.75f))
                        Text("${idx + 1}", Modifier.align(if (side == 0) Alignment.BottomStart else Alignment.BottomEnd), style = Esc.noto(12, Color(0xFF8A7050)))
                    }
                }
            }
            // 가운데 접힘과 매듭 실
            Canvas(Modifier.fillMaxSize()) {
                val x = size.width / 2
                drawLine(Color(0x553A2410), Offset(x, 0f), Offset(x, size.height), strokeWidth = 3.dp.toPx())
                listOf(0.12f, 0.31f, 0.5f, 0.69f, 0.88f).forEach { f ->
                    drawCircle(Color(0xFFB0302A), 3.dp.toPx(), Offset(x, size.height * f))
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            OutlineButton("◀ 앞장", { if (spread > 0) spread-- }, Modifier.testTag("book-prev"), enabled = spread > 0, minHeight = 44.dp, fontSize = 15)
            Text("${spread + 1} / ${last + 1}", style = Esc.noto(14, Esc.creamSoft))
            OutlineButton("뒷장 ▶", { if (spread < last) spread++ }, Modifier.testTag("book-next"), enabled = spread < last, minHeight = 44.dp, fontSize = 15)
        }
    }
}

/** 펼친 종이(밀서·비방·소식지). */
@Composable
private fun PaperSheet(item: CaseItem, modifier: Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Box(
            Modifier.fillMaxHeight().width(460.dp).rotate(-1.5f).shadow(14.dp).background(pageColor)
                .border(1.dp, Color(0xFFB89E70)).padding(horizontal = 36.dp, vertical = 28.dp),
        ) {
            Text(item.pages.first(), Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).testTag("paper-text"),
                style = Esc.nanum(19, pageInk, lineHeight = 1.8f), textAlign = TextAlign.Center)
            if (item.id == "secret_letter") ItemArt("secret_letter", Modifier.align(Alignment.BottomEnd).size(70.dp))
        }
    }
}

/** 새로 얻은 소지품을 보여 주는 창. */
@Composable
fun ItemAcquiredDialog(items: List<CaseItem>, onDismiss: () -> Unit) {
    val none = remember { MutableInteractionSource() }
    Box(
        Modifier.fillMaxSize().background(Color(0xCC0A0204)).clickable(none, indication = null) {}.testTag("item-acquired"),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.width(720.dp).background(Esc.wallDeep).border(1.dp, Esc.gold).padding(5.dp).border(1.dp, Esc.goldDim).padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("New Item", style = Esc.pinyon(34))
            Text("소지품에 들어왔습니다", style = Esc.nanum(22))
            Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                items.forEach { item ->
                    Column(Modifier.width(300.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            Modifier.size(170.dp).background(Brush.radialGradient(listOf(Esc.gold.copy(alpha = 0.22f), Color.Transparent))),
                            contentAlignment = Alignment.Center,
                        ) { ItemArt(item.id, Modifier.size(150.dp)) }
                        Text(item.name, style = Esc.nanum(20, Esc.gold))
                        Text(item.description, Modifier.height(72.dp), style = Esc.noto(14, Esc.creamMuted, lineHeight = 1.55f), textAlign = TextAlign.Center)
                    }
                }
            }
            ParchmentButton("소지품에 넣기", onDismiss, Modifier.testTag("item-acquired-ok"), minWidth = 240.dp, minHeight = 52.dp)
        }
    }
}
