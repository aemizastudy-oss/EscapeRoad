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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.drawscope.withTransform
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

/** 캔버스 다마스크 타일(120x168) - 업로드 SVG 경로 그대로, 짙은 색 32% 불투명도. */
private val damaskPaths: List<Path> by lazy {
    listOf(
        "M60 14c6 10 16 14 16 26 0 9-7 14-12 12 5-4 4-11-1-13-4 9-3 18 3 26 5 7 15 9 21 4-1 9-11 14-20 10 2 8 0 16-7 22-7-6-9-14-7-22-9 4-19-1-20-10 6 5 16 3 21-4 6-8 7-17 3-26-5 2-6 9-1 13-5 2-12-3-12-12 0-12 10-16 16-26z",
        "M60 104l5 10-5 10-5-10z",
        "M0 84c4 6 10 9 10 16 0 6-4 9-8 8 3-3 2-7-1-8-1 0-1 0-1 0zM120 84c-4 6-10 9-10 16 0 6 4 9 8 8-3-3-2-7 1-8 1 0 1 0 1 0z",
        "M0 0l4 8-4 8zM120 0l-4 8 4 8zM0 152l4 8-4 8zM120 152l-4 8 4 8z",
    ).map { androidx.compose.ui.graphics.vector.PathParser().parsePathString(it).toPath() }
}
private val damaskInk = Color(0x521A0306)

/**
 * 캔버스의 .wall(벨벳 사진 + 다마스크)과 .parch(양피지 사진 + 가장자리 그을림) 질감.
 * 그리기만 하며 카드의 측정 크기에 관여하지 않는다(스크롤 안에서도 안전).
 */
@Composable
fun Modifier.caseTexture(paper: Boolean = false): Modifier {
    val bitmap = ImageBitmap.imageResource(if (paper) R.drawable.canvas_parchment else R.drawable.canvas_velvet)
    return drawWithCache {
        val scale = max(size.width / bitmap.width, size.height / bitmap.height).coerceAtLeast(.001f)
        val width = (size.width / scale).roundToInt().coerceIn(1, bitmap.width)
        val height = (size.height / scale).roundToInt().coerceIn(1, bitmap.height)
        val vignette = Brush.radialGradient(
            .55f to Color.Transparent, 1f to Color(110, 70, 30).copy(alpha = .32f),
            center = Offset(size.width / 2, size.height / 2), radius = (max(size.width, size.height) * .71f).coerceAtLeast(1f),
        )
        onDrawBehind {
            clipRect {
                drawRect(if (paper) CaseColors.Cream else CaseColors.Burgundy)
                drawImage(bitmap, srcOffset = IntOffset((bitmap.width-width)/2, (bitmap.height-height)/2),
                    srcSize = IntSize(width, height),
                    dstSize = IntSize(size.width.roundToInt().coerceAtLeast(1), size.height.roundToInt().coerceAtLeast(1)),
                    filterQuality = FilterQuality.Medium)
                if (paper) drawRect(vignette)
                else {
                    val s = 1.dp.toPx(); val tw = 120 * s; val th = 168 * s
                    var y = 0f
                    while (y < size.height) {
                        var x = 0f
                        while (x < size.width) {
                            withTransform({ translate(x, y); scale(s, s, Offset.Zero) }) {
                                damaskPaths.forEach { drawPath(it, damaskInk) }
                                drawCircle(damaskInk, 3f, Offset(60f, 132f))
                            }
                            x += tw
                        }
                        y += th
                    }
                }
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
            val inset = 8.dp.toPx(); val outer = 2.dp.toPx()
            drawRect(CaseColors.Gold, Offset(inset + outer/2, inset + outer/2), Size((size.width - 2*inset - outer).coerceAtLeast(0f), (size.height - 2*inset - outer).coerceAtLeast(0f)), style = Stroke(outer))
            val inner = inset + 7.dp.toPx()
            drawRect(CaseColors.Gold, Offset(inner, inner), Size((size.width - 2*inner).coerceAtLeast(0f), (size.height - 2*inner).coerceAtLeast(0f)), style = Stroke(1.dp.toPx()))
        }
    } else Modifier), content = content)
    }
}

@Composable
fun CasePanel(modifier: Modifier = Modifier, paper: Boolean = false, selected: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    val fill = if (paper) Modifier.caseTexture(true) else Modifier.background(if (selected) CaseColors.Gold.copy(alpha=.16f) else CaseColors.Panel)
    CompositionLocalProvider(LocalContentColor provides if (paper) CaseColors.Ink else CaseColors.Cream) {
        Column(modifier.then(fill).border(if (selected) 2.dp else 1.dp, CaseColors.Gold.copy(alpha=if(selected)1f else .45f)).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
fun PaperSheet(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    CompositionLocalProvider(LocalContentColor provides CaseColors.Ink) {
        Box(modifier.shadow(18.dp).caseTexture(true).padding(10.dp)) {
            Column(Modifier.fillMaxWidth().border(1.dp, CaseColors.ParchEdge).padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
        }
    }
}

@Composable
fun CaseHeading(english: String, korean: String, modifier: Modifier = Modifier, side: String? = null) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(english, style = script(34))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            Text(korean, fontFamily = CaseFonts.Heading, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp, lineHeight = 39.sp, modifier = Modifier.weight(1f))
            if (side != null) Text(side, style = smallCaps(15), modifier = Modifier.padding(start=12.dp))
        }
        HorizontalDivider(color = CaseColors.Gold.copy(alpha=.45f), modifier = Modifier.padding(top=8.dp))
    }
}

@Composable
fun EscapeButton(text: String, tag: String, onClick: () -> Unit, enabled: Boolean = true, modifier: Modifier = Modifier, secondary: Boolean = false) {
    val parchment = !secondary && enabled
    val bounds = modifier.heightIn(min = 56.dp).widthIn(min = 56.dp).testTag(tag)
        .then(if (parchment) Modifier.caseTexture(true) else Modifier)
    val colors = ButtonDefaults.buttonColors(
        containerColor = if (secondary) CaseColors.Panel else Color.Transparent,
        contentColor = if (secondary) CaseColors.Cream else CaseColors.ButtonInk,
        disabledContainerColor = CaseColors.DeepBurgundy.copy(alpha=.3f), disabledContentColor = CaseColors.MutedGold.copy(alpha=.5f),
    )
    Button(onClick, bounds, enabled = enabled, colors = colors, shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, CaseColors.Gold.copy(alpha = if(enabled) 1f else .25f)), contentPadding=PaddingValues(horizontal=22.dp,vertical=12.dp)) {
        Text(text, fontFamily = CaseFonts.Heading, fontWeight = if (secondary) FontWeight.Bold else FontWeight.ExtraBold, fontSize = if (secondary) 17.sp else 18.sp)
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
        val content=state.content;val session=state.session
        val next=if(content?.story!=null&&session!=null)com.example.roadescape.game.GameController.currentCheckpoint(content,session) else null
        Text(if(gate==GateReason.DRIVING) "En Route · 이동 중" else "The Letter Remains Sealed",style=smallCaps(18,spacing=.3f))
        CaseArtwork(CaseArt.ENVELOPE,Modifier.size(132.dp,104.dp))
        if(next!=null){
            // 시안: 다음 목적지를 크게. 이야기 문구는 주행 중 화면에 쓰지 않는다.
            Text("다음 목적지",fontFamily=CaseFonts.Body,fontSize=22.sp,color=CaseColors.MutedGold)
            Text(next.title.replace(Regex("^CH\\.\\d+\\s*"),""),fontFamily=CaseFonts.Heading,fontWeight=FontWeight.ExtraBold,fontSize=58.sp,lineHeight=70.sp,modifier=Modifier.testTag("paused-next"))
        } else Text(stringResource(R.string.pause_title),style=MaterialTheme.typography.headlineMedium)
        Text(stringResource(gateLabel(gate)),color=CaseColors.Gold,style=MaterialTheme.typography.titleLarge)
        Text(if(next!=null)"도착해서 주차하면 그 시대로 건너갑니다" else stringResource(R.string.pause_description),fontFamily=CaseFonts.Body,fontSize=if(next!=null)26.sp else 16.sp,color=if(next!=null)CaseColors.Cream else CaseColors.MutedGold)
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)){
            Box(Modifier.width(120.dp).height(1.dp).background(CaseColors.Gold))
            Text(if(content?.story!=null)"Drive safely, Agent" else "Drive safely, Detective",style=script(30))
            Box(Modifier.width(120.dp).height(1.dp).background(CaseColors.Gold))
        }
    }
}

@StringRes fun parkingLabel(parking: ParkingState): Int = when(parking) {ParkingState.PARKED->R.string.parking_parked;ParkingState.DRIVING->R.string.parking_driving;ParkingState.UNKNOWN->R.string.parking_unknown}
@StringRes fun gateLabel(gate: GateReason): Int = when(gate) {
    GateReason.READY->R.string.gate_ready;GateReason.BACKGROUND->R.string.gate_background;GateReason.PARKING_UNKNOWN->R.string.gate_unknown
    GateReason.DRIVING->R.string.gate_driving;GateReason.STORAGE_ERROR->R.string.gate_storage;GateReason.BUSY->R.string.gate_busy
    GateReason.ARRIVAL_REQUIRED->R.string.gate_arrival;GateReason.COMPLETED->R.string.gate_completed;GateReason.NOT_CURRENT->R.string.gate_not_current;GateReason.STALE_EVENT->R.string.gate_stale
    GateReason.TOUR_REQUIRED->R.string.gate_tour;GateReason.UX_RESTRICTED->R.string.gate_ux;GateReason.MODE_MISMATCH->R.string.gate_mode
}
