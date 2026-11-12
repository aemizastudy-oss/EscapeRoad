package com.example.roadescape.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import com.example.roadescape.R
import com.example.roadescape.game.GateReason
import com.example.roadescape.model.ParkingState
import kotlin.math.max
import kotlin.math.roundToInt

enum class CaseArt { LIGHTHOUSE, CLOCK, ENVELOPE, KEY, BOOK, LOCK, COMPASS, WAX }

fun Modifier.ornateCaseBorder(): Modifier = drawWithCache {
    onDrawWithContent {
        drawContent()
        fun outline(inset: Float) {
            val edge = 24.dp.toPx()
            val w = size.width; val h = size.height
            val path = Path().apply {
                moveTo(inset + edge, inset)
                lineTo(w - inset - edge, inset)
                quadraticTo(w - inset - edge, inset + edge, w - inset, inset + edge)
                lineTo(w - inset, h - inset - edge)
                quadraticTo(w - inset - edge, h - inset - edge, w - inset - edge, h - inset)
                lineTo(inset + edge, h - inset)
                quadraticTo(inset + edge, h - inset - edge, inset, h - inset - edge)
                lineTo(inset, inset + edge)
                quadraticTo(inset + edge, inset + edge, inset + edge, inset)
                close()
            }
            drawPath(path, CaseColors.Gold.copy(alpha=.8f), style=Stroke(.8.dp.toPx()))
        }
        outline(8.dp.toPx()); outline(15.dp.toPx())
        for (y in listOf(8.dp.toPx(), size.height-8.dp.toPx())) {
            val x = size.width/2; val r = 7.dp.toPx()
            drawPath(Path().apply { moveTo(x,y-r); lineTo(x+r*.65f,y); lineTo(x,y+r); lineTo(x-r*.65f,y);close() },CaseColors.Gold)
        }
    }
}

/** Textures cropped from the supplied PDF. Native text and controls remain independent. */
@Composable
fun Modifier.caseTexture(paper: Boolean = false): Modifier {
    val bitmap = ImageBitmap.imageResource(if (paper) R.drawable.pdf_paper else R.drawable.pdf_wallpaper)
    // Draw only: the texture must not affect a card's measurement inside a scrolling column.
    return drawWithCache {
        val scale = max(size.width / bitmap.width, size.height / bitmap.height).coerceAtLeast(.001f)
        val width = (size.width / scale).roundToInt().coerceIn(1, bitmap.width)
        val height = (size.height / scale).roundToInt().coerceIn(1, bitmap.height)
        onDrawBehind {
            clipRect {
                drawImage(bitmap, srcOffset = IntOffset((bitmap.width-width)/2, (bitmap.height-height)/2),
                    srcSize = IntSize(width, height),
                    dstSize = IntSize(size.width.roundToInt().coerceAtLeast(1), size.height.roundToInt().coerceAtLeast(1)),
                    filterQuality = FilterQuality.Medium)
            }
        }
    }
}

@Composable
fun CaseFrame(modifier: Modifier = Modifier, framed: Boolean = true, content: @Composable BoxScope.() -> Unit) {
    CompositionLocalProvider(LocalContentColor provides CaseColors.Cream) {
    Box(modifier.caseTexture().then(if (framed) Modifier.drawWithCache {
        onDrawWithContent {
            drawContent()
            val inset = 10.dp.toPx()
            drawRect(CaseColors.Gold, Offset(inset, inset), Size((size.width - 2*inset).coerceAtLeast(0f), (size.height - 2*inset).coerceAtLeast(0f)), style = Stroke(1.3.dp.toPx()))
            drawRect(CaseColors.Gold.copy(alpha = .7f), Offset(inset+4.dp.toPx(), inset+4.dp.toPx()), Size((size.width-2*inset-8.dp.toPx()).coerceAtLeast(0f), (size.height-2*inset-8.dp.toPx()).coerceAtLeast(0f)), style = Stroke(.5.dp.toPx()))
        }
    } else Modifier), content = content)
    }
}

@Composable
fun CasePanel(modifier: Modifier = Modifier, paper: Boolean = false, selected: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    val fill = if (paper) Modifier.caseTexture(true) else Modifier.background(if (selected) CaseColors.Gold.copy(alpha=.13f) else CaseColors.DeepBurgundy.copy(alpha=.55f))
    CompositionLocalProvider(LocalContentColor provides if (paper) CaseColors.Ink else CaseColors.Cream) {
        Column(modifier.then(fill).border(if (selected) 2.dp else .7.dp, CaseColors.Gold.copy(alpha=if(selected)1f else .55f)).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
fun PaperSheet(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    CompositionLocalProvider(LocalContentColor provides CaseColors.Ink) {
        Box(modifier.caseTexture(true).border(1.dp, CaseColors.Gold.copy(alpha=.9f)).padding(8.dp)) {
            Column(Modifier.fillMaxWidth().border(.5.dp, CaseColors.Ink.copy(alpha=.23f)).padding(22.dp), verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
        }
    }
}

@Composable
fun CaseHeading(english: String, korean: String, modifier: Modifier = Modifier, side: String? = null) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(english, color = CaseColors.Gold, fontFamily = FontFamily.Cursive, fontStyle = FontStyle.Italic, fontSize = 27.sp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            Text(korean, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
            if (side != null) Text(side, style = MaterialTheme.typography.labelLarge, color = CaseColors.MutedGold, modifier = Modifier.padding(start=12.dp))
        }
        HorizontalDivider(color = CaseColors.Gold.copy(alpha=.45f), modifier = Modifier.padding(top=8.dp))
    }
}

@Composable
fun EscapeButton(text: String, tag: String, onClick: () -> Unit, enabled: Boolean = true, modifier: Modifier = Modifier, secondary: Boolean = false) {
    val bounds = modifier.heightIn(min = 56.dp).widthIn(min = 56.dp).testTag(tag)
    val colors = ButtonDefaults.buttonColors(
        containerColor = if (secondary) CaseColors.DeepBurgundy.copy(alpha=.5f) else CaseColors.Cream,
        contentColor = if (secondary) CaseColors.Cream else CaseColors.Ink,
        disabledContainerColor = CaseColors.DeepBurgundy.copy(alpha=.3f), disabledContentColor = CaseColors.MutedGold.copy(alpha=.5f),
    )
    Button(onClick, bounds, enabled = enabled, colors = colors, shape = MaterialTheme.shapes.small,
        border = BorderStroke(.8.dp, CaseColors.Gold.copy(alpha = if(enabled) .85f else .25f)), contentPadding=PaddingValues(horizontal=20.dp,vertical=12.dp)) {
        Text(text, fontWeight = FontWeight.Medium, fontSize = 16.sp)
    }
}

@Composable
fun CaseArtwork(kind: CaseArt, modifier: Modifier = Modifier) {
    val resource = when (kind) {
        CaseArt.LIGHTHOUSE -> R.drawable.pdf_lighthouse
        CaseArt.CLOCK -> R.drawable.pdf_clock
        CaseArt.ENVELOPE -> R.drawable.pdf_envelope
        CaseArt.KEY -> R.drawable.pdf_key
        CaseArt.BOOK -> R.drawable.pdf_book
        CaseArt.LOCK -> R.drawable.pdf_lock
        CaseArt.COMPASS -> R.drawable.pdf_compass
        CaseArt.WAX -> R.drawable.pdf_wax
    }
    Image(painterResource(resource), contentDescription = null,
        modifier = modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp), contentScale = ContentScale.Fit)
}

@Composable
fun CasePausedScreen(state: GameUiState, reason: GateReason? = null) {
    val gate = reason ?: when {
        state.error != null -> GateReason.STORAGE_ERROR
        !state.foreground -> GateReason.BACKGROUND
        state.parking == ParkingState.DRIVING -> GateReason.DRIVING
        state.parking == ParkingState.UNKNOWN -> GateReason.PARKING_UNKNOWN
        else -> GateReason.ARRIVAL_REQUIRED
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("paused").padding(24.dp),
        horizontalAlignment=Alignment.CenterHorizontally, verticalArrangement=Arrangement.spacedBy(22.dp,Alignment.CenterVertically)) {
        Text(if(gate==GateReason.DRIVING) "En Route · 이동 중" else "The Letter Remains Sealed",fontFamily=FontFamily.Cursive,fontStyle=FontStyle.Italic,color=CaseColors.Gold,fontSize=30.sp)
        CaseArtwork(CaseArt.ENVELOPE,Modifier.size(130.dp))
        Text(stringResource(R.string.pause_title),style=MaterialTheme.typography.headlineMedium)
        Text(stringResource(gateLabel(gate)),color=CaseColors.Gold,style=MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.pause_description),color=CaseColors.MutedGold)
        Text("Drive safely, Detective",fontFamily=FontFamily.Cursive,fontStyle=FontStyle.Italic,color=CaseColors.Gold,fontSize=22.sp)
    }
}

@StringRes fun parkingLabel(parking: ParkingState): Int = when(parking) {ParkingState.PARKED->R.string.parking_parked;ParkingState.DRIVING->R.string.parking_driving;ParkingState.UNKNOWN->R.string.parking_unknown}
@StringRes fun gateLabel(gate: GateReason): Int = when(gate) {
    GateReason.READY->R.string.gate_ready;GateReason.BACKGROUND->R.string.gate_background;GateReason.PARKING_UNKNOWN->R.string.gate_unknown
    GateReason.DRIVING->R.string.gate_driving;GateReason.STORAGE_ERROR->R.string.gate_storage;GateReason.BUSY->R.string.gate_busy
    GateReason.ARRIVAL_REQUIRED->R.string.gate_arrival;GateReason.COMPLETED->R.string.gate_completed;GateReason.NOT_CURRENT->R.string.gate_not_current;GateReason.STALE_EVENT->R.string.gate_stale
}
