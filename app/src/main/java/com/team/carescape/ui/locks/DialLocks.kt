package com.team.carescape.ui.locks

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.team.carescape.R
import com.team.carescape.game.DialColor
import com.team.carescape.game.LockDirection
import com.team.carescape.game.LockInput
import com.team.carescape.ui.Esc
import com.team.carescape.ui.OutlineButton
import com.team.carescape.ui.ParchmentButton
import kotlin.math.abs

private val WindowCream = Color(0xFFF2E6CB)
private const val ArtworkWidth = 440f
private const val ArtworkHeight = 480f
private const val DialSwipeDistanceDp = 20f
private const val DirectionSwipeDistanceDp = 28f

/** The owner keeps draft values and decides what a submitted answer means. */
@Composable
fun ColorDialLock(
    value: List<DialColor>,
    onValueChange: (List<DialColor>) -> Unit,
    onSubmit: (List<DialColor>) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    feedbackContent: (@Composable () -> Unit)? = null,
) {
    require(value.size == 4) { "A color lock has four dials." }
    val currentValue by rememberUpdatedState(value)
    val currentChange by rememberUpdatedState(onValueChange)
    val currentSubmit by rememberUpdatedState(onSubmit)
    val canEdit by rememberUpdatedState(enabled)
    fun step(index: Int, direction: Int): Boolean {
        if (!canEdit) return false
        currentChange(currentValue.mapIndexed { i, color ->
            if (i == index) DialColor.entries[Math.floorMod(color.ordinal + direction, DialColor.entries.size)] else color
        })
        return true
    }

    Column(modifier.width(320.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LockInstruction("다이얼을 위아래로 밀거나 눌러 색을 바꾸세요.")
        LockArtwork(R.drawable.color_lock) { scale ->
            repeat(4) { index ->
                var dragOffset by remember { mutableFloatStateOf(0f) }
                DialWindow(index, scale, dragOffset) { relative ->
                    val color = DialColor.entries[Math.floorMod(value[index].ordinal + relative, DialColor.entries.size)]
                    Canvas(Modifier.fillMaxSize()) {
                        drawCircle(color.paint, radius = size.width * 20f / 48f)
                    }
                }
                Box(
                    Modifier
                        .offset((84f + 68f * index).times(scale).dp, (264f * scale).dp)
                        .size((68f * scale).dp, (144f * scale).dp)
                        .dialInput("색상 다이얼 ${index + 1}", value[index].label, enabled, { step(index, it) }) { dragOffset = it },
                )
            }
        }
        ParchmentButton("제출", { if (canEdit) currentSubmit(currentValue.toList()) }, enabled = enabled, minHeight = 48.dp, minWidth = 180.dp)
        feedbackContent?.invoke()
    }
}

@Composable
fun NumberDialLock(
    value: List<Int>,
    onValueChange: (List<Int>) -> Unit,
    onSubmit: (List<Int>) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    feedbackContent: (@Composable () -> Unit)? = null,
) {
    require(value.size == 4 && value.all { it in 0..9 }) { "A numeric lock has four digits from zero to nine." }
    val currentValue by rememberUpdatedState(value)
    val currentChange by rememberUpdatedState(onValueChange)
    val currentSubmit by rememberUpdatedState(onSubmit)
    val canEdit by rememberUpdatedState(enabled)
    fun step(index: Int, direction: Int): Boolean {
        if (!canEdit) return false
        currentChange(currentValue.mapIndexed { i, digit -> if (i == index) Math.floorMod(digit + direction, 10) else digit })
        return true
    }

    Column(modifier.width(320.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LockInstruction("다이얼을 위아래로 밀거나 눌러 숫자를 바꾸세요.")
        LockArtwork(R.drawable.num_lock) { scale ->
            repeat(4) { index ->
                var dragOffset by remember { mutableFloatStateOf(0f) }
                DialWindow(index, scale, dragOffset) { relative ->
                    Text(
                        Math.floorMod(value[index] + relative, 10).toString(),
                        style = Esc.cormorant(44, Esc.ink).copy(fontSize = (44f * scale).sp),
                        textAlign = TextAlign.Center,
                    )
                }
                Box(
                    Modifier
                        .offset((84f + 68f * index).times(scale).dp, (264f * scale).dp)
                        .size((68f * scale).dp, (144f * scale).dp)
                        .dialInput("숫자 다이얼 ${index + 1}", value[index].toString(), enabled, { step(index, it) }) { dragOffset = it },
                )
            }
        }
        ParchmentButton("제출", { if (canEdit) currentSubmit(currentValue.toList()) }, enabled = enabled, minHeight = 48.dp, minWidth = 180.dp)
        feedbackContent?.invoke()
    }
}

@Composable
fun DirectionSwipeLock(
    value: List<LockDirection>,
    onValueChange: (List<LockDirection>) -> Unit,
    onSubmit: (List<LockDirection>) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    feedbackContent: (@Composable () -> Unit)? = null,
) {
    val currentValue by rememberUpdatedState(value)
    val currentChange by rememberUpdatedState(onValueChange)
    val currentSubmit by rememberUpdatedState(onSubmit)
    val canEdit by rememberUpdatedState(enabled)
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    val sequenceScroll = rememberLazyListState()
    LaunchedEffect(value.size) { if (value.isNotEmpty()) sequenceScroll.scrollToItem(value.lastIndex) }
    fun append(direction: LockDirection): Boolean {
        if (!canEdit) return false
        currentChange(currentValue + direction)
        return true
    }

    Column(modifier.width(320.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LockInstruction("가운데 원에서 원하는 방향으로 밀어 주세요.")
        LockArtwork(R.drawable.direction_lock) { scale ->
            Box(
                Modifier
                    .offset((172f * scale).dp, (272f * scale).dp)
                    .size((96f * scale).dp)
                    .semantics {
                        contentDescription = "방향 자물쇠 조작부"
                        stateDescription = value.joinToString(" ") { it.label }.ifEmpty { "입력 없음" }
                        if (enabled) {
                            customActions = LockDirection.entries.map { direction ->
                                CustomAccessibilityAction("${direction.label} 입력") { append(direction) }
                            }
                        } else disabled()
                    }
                    .pointerInput(enabled) {
                        if (!enabled) return@pointerInput
                        try {
                            awaitEachGesture {
                                val down = awaitFirstDown()
                                val center = Offset(size.width / 2f, size.height / 2f)
                                val diameter = minOf(size.width, size.height).toFloat()
                                if ((down.position - center).getDistance() > diameter / 2f) return@awaitEachGesture
                                val result = trackGesture(down.id, down.position) { displacement ->
                                    dragOffset = displacement.capLength(diameter / 5f)
                                }
                                dragOffset = Offset.Zero
                                if (result != null && result.dragged && canEdit) {
                                    LockInput.directionOf(result.displacement.x, result.displacement.y, DirectionSwipeDistanceDp.dp.toPx())?.let(::append)
                                }
                            }
                        } finally { dragOffset = Offset.Zero }
                    },
            )
            if (dragOffset != Offset.Zero) {
                Canvas(Modifier.offset((184f * scale).dp, (284f * scale).dp).size((72f * scale).dp)) {
                    drawCircle(Color(0xFFC9A15A), radius = size.minDimension / 2f, center = center + dragOffset)
                    drawCircle(Color(0xFF74521E), radius = size.minDimension / 2f, center = center + dragOffset, style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(48.dp).semantics {
            contentDescription = "입력된 방향"
            stateDescription = value.joinToString(" ") { it.label }.ifEmpty { "입력 없음" }
        }) {
            if (value.isEmpty()) {
                Text("입력한 방향이 여기에 표시됩니다.", Modifier.align(Alignment.Center), style = Esc.noto(13, Esc.creamMuted))
            } else {
                LazyRow(state = sequenceScroll, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(value) { index, direction ->
                        Column(Modifier.width(36.dp).height(46.dp).background(Esc.panel).border(1.dp, Esc.goldDim), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(direction.symbol, style = Esc.noto(22, Esc.gold))
                            Text("${index + 1}", style = Esc.noto(10, Esc.creamSoft))
                        }
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlineButton("초기화", { if (canEdit && currentValue.isNotEmpty()) currentChange(emptyList()) }, enabled = enabled && value.isNotEmpty(), minHeight = 48.dp, minWidth = 120.dp)
            ParchmentButton("제출", { if (canEdit && currentValue.isNotEmpty()) currentSubmit(currentValue.toList()) }, enabled = enabled && value.isNotEmpty(), minHeight = 48.dp, minWidth = 120.dp)
        }
        feedbackContent?.invoke()
    }
}

@Composable
private fun LockInstruction(text: String) {
    Text(text, style = Esc.noto(13, Esc.creamMuted), textAlign = TextAlign.Center)
}

/** Original PNG is retained; only its value windows receive controlled, dynamic content. */
@Composable
private fun LockArtwork(@DrawableRes image: Int, content: @Composable BoxScope.(Float) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth().aspectRatio(ArtworkWidth / ArtworkHeight)) {
        val scale = maxWidth.value / ArtworkWidth
        Image(painterResource(image), contentDescription = null, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize()) { content(scale) }
    }
}

@Composable
private fun BoxScope.DialWindow(index: Int, scale: Float, dragOffset: Float, content: @Composable BoxScope.(Int) -> Unit) {
    val heightPx = with(LocalDensity.current) { (72f * scale).dp.toPx() }
    val visibleOffset = dragOffset.coerceIn(-heightPx, heightPx)
    Box(
        Modifier
            .offset((94f + 68f * index).times(scale).dp, (300f * scale).dp)
            .size((48f * scale).dp, (72f * scale).dp)
            .background(WindowCream)
            .clipToBounds(),
    ) {
        for (relative in -1..1) {
            Box(Modifier.fillMaxSize().graphicsLayer { translationY = relative * heightPx + visibleOffset }, contentAlignment = Alignment.Center) { content(relative) }
        }
    }
}

/** A single pointer owns a gesture. A cancelled, consumed, or multi-touch gesture emits nothing. */
private data class CompletedLockGesture(val displacement: Offset, val dragged: Boolean)

private suspend fun androidx.compose.ui.input.pointer.AwaitPointerEventScope.trackGesture(
    pointerId: androidx.compose.ui.input.pointer.PointerId,
    start: Offset,
    onDrag: (Offset) -> Unit,
): CompletedLockGesture? {
    if (currentEvent.changes.any { it.id != pointerId && it.pressed }) return null
    var dragged = false
    while (true) {
        val event = awaitPointerEvent()
        if (event.changes.any { it.id != pointerId && it.pressed }) return null
        val change = event.changes.firstOrNull { it.id == pointerId } ?: return null
        if (change.isConsumed) return null
        val displacement = change.position - start
        if (displacement.getDistance() > viewConfiguration.touchSlop) dragged = true
        if (dragged) {
            change.consume()
            onDrag(displacement)
        }
        if (!change.pressed) return CompletedLockGesture(displacement, dragged)
    }
}

@Composable
private fun Modifier.dialInput(
    description: String,
    descriptionOfValue: String,
    enabled: Boolean,
    onStep: (Int) -> Boolean,
    onDrag: (Float) -> Unit,
): Modifier {
    val currentStep by rememberUpdatedState(onStep)
    val currentDrag by rememberUpdatedState(onDrag)
    val canEdit by rememberUpdatedState(enabled)
    return semantics {
        contentDescription = description
        stateDescription = descriptionOfValue
        role = Role.Button
        if (enabled) {
            onClick("다음 값") { if (canEdit) currentStep(1) else false }
            customActions = listOf(
                CustomAccessibilityAction("다음 값") { if (canEdit) currentStep(1) else false },
                CustomAccessibilityAction("이전 값") { if (canEdit) currentStep(-1) else false },
            )
        } else disabled()
    }.pointerInput(enabled) {
        if (!enabled) return@pointerInput
        try {
            awaitEachGesture {
                val down = awaitFirstDown()
                val result = trackGesture(down.id, down.position) { currentDrag(it.y) }
                currentDrag(0f)
                if (result != null && canEdit) {
                    val dy = result.displacement.y
                    if (!result.dragged) currentStep(1)
                    else if (abs(dy) >= DialSwipeDistanceDp.dp.toPx() && abs(dy) > abs(result.displacement.x)) currentStep(if (dy < 0f) 1 else -1)
                }
            }
        } finally { currentDrag(0f) }
    }
}

private fun Offset.capLength(maximum: Float): Offset {
    val length = getDistance()
    return if (length > maximum && length > 0f) this * (maximum / length) else this
}

private val DialColor.paint: Color get() = when (this) {
    DialColor.RED -> Color(0xFFB5372E)
    DialColor.YELLOW -> Color(0xFFE8C451)
    DialColor.GREEN -> Color(0xFF2D6C51)
    DialColor.BLUE -> Color(0xFF2C5FA2)
}
