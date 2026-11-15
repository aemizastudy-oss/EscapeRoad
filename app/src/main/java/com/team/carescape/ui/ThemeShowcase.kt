package com.team.carescape.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.team.carescape.R
import com.team.carescape.game.ThemeCard
import com.team.carescape.game.ThemeCatalog
import com.team.carescape.game.ThemeTone
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

/**
 * 테마 선택: 포스터를 옆으로 넘기는 진열대. 가운데 포스터를 누르면 뒷면처럼 장르·난이도·공포도가 펼쳐진다.
 * 고를 수 없는 테마는 시연용 빈 포스터로만 보인다. 정차 중에만 이 화면에 온다(로비 게이트).
 */
@Composable
fun ThemeShowcaseScreen(vm: GameViewModel) {
    var sort by rememberSaveable { mutableStateOf(ShowcaseSort.RECOMMENDED) }
    var descending by rememberSaveable { mutableStateOf(true) }
    val cards = remember(sort, descending) { sortShowcase(ThemeCatalog.cards, sort, descending) }
    var focus by rememberSaveable { mutableIntStateOf(cards.indexOfFirst { it.playable }.coerceAtLeast(0)) }
    var opened by rememberSaveable { mutableStateOf(false) }
    val current = cards[focus.coerceIn(0, cards.lastIndex)]
    fun move(to: Int) {
        val next = to.coerceIn(0, cards.lastIndex)
        if (next != focus) { focus = next; opened = false }
    }
    /** 정렬을 바꿔도 보고 있던 포스터를 가운데에 둔다. 같은 정렬을 다시 누르면 높은 순·낮은 순이 바뀐다. */
    fun chooseSort(next: ShowcaseSort) {
        val keep = current
        if (next == sort && next != ShowcaseSort.RECOMMENDED) descending = !descending else { sort = next; descending = true }
        focus = sortShowcase(ThemeCatalog.cards, sort, descending).indexOf(keep).coerceAtLeast(0)
    }

    GoldFrame(padTop = 24.dp, padH = 40.dp, padBottom = 22.dp) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                BackButton(vm::goLobby, "로비로 돌아가기")
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Theme", style = Esc.cormorant(16, Esc.gold, 0.3f))
                    Text("오늘 맡으실 사건을 고르십시오", style = Esc.nanum(30))
                    Text(
                        when {
                            !opened -> "포스터를 누르면 장르·난이도·공포도를 볼 수 있어요"
                            current.playable -> "사건을 맡으면 첫 번째 목적지 안내가 시작됩니다"
                            else -> "준비 중인 테마예요. 시연용으로 포스터만 보여 드려요"
                        },
                        style = Esc.noto(14, Color(0xFFCFC3A8)),
                    )
                }
            }

            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                val posterH = (maxHeight - 8.dp).coerceAtMost(430.dp)
                val posterW = posterH * 0.7f
                val step = posterW * 0.82f
                val animated by animateFloatAsState(focus.toFloat(), tween(380), label = "showcase")
                var drag by remember { mutableFloatStateOf(0f) }
                val dragState = rememberDraggableState { drag += it }
                Box(
                    Modifier.fillMaxSize().draggable(
                        dragState, Orientation.Horizontal,
                        onDragStopped = {
                            val threshold = 60f
                            if (abs(drag) > threshold) move(focus - sign(drag).toInt() * (abs(drag) / 400f).roundToInt().coerceIn(1, 2))
                            drag = 0f
                        },
                    ),
                    contentAlignment = Alignment.Center,
                ) {
                    cards.forEachIndexed { i, card ->
                        val d = i - animated
                        val far = abs(d)
                        Poster(
                            card = card,
                            index = i,
                            width = posterW,
                            height = posterH,
                            opened = opened && i == focus,
                            modifier = Modifier
                                .zIndex(-far)
                                .graphicsLayer {
                                    translationX = d * step.toPx()
                                    val s = 1f - 0.16f * far.coerceAtMost(2f)
                                    scaleX = s; scaleY = s
                                    rotationY = (-d * 28f).coerceIn(-45f, 45f)
                                    cameraDistance = 14f * density
                                    alpha = (1f - 0.15f * far).coerceIn(0f, 1f)
                                },
                            onClick = { if (i == focus) opened = !opened else move(i) },
                        )
                    }
                    ArrowButton("◀", Modifier.align(Alignment.CenterStart), enabled = focus > 0) { move(focus - 1) }
                    ArrowButton("▶", Modifier.align(Alignment.CenterEnd), enabled = focus < cards.lastIndex) { move(focus + 1) }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("정렬", style = Esc.noto(14, Esc.gold))
                ShowcaseSort.entries.forEach { option ->
                    val active = option == sort
                    val arrow = if (option == ShowcaseSort.RECOMMENDED) "" else if (!active || descending) " ▼" else " ▲"
                    SortChip(option.label + arrow, active, Modifier.testTag("theme-sort-${option.name}")) { chooseSort(option) }
                }
                Spacer(Modifier.weight(1f))
                ParchmentButton(
                    if (current.playable) "이 사건을 맡겠습니다" else "준비 중",
                    { vm.select(current); vm.goBriefing() },
                    Modifier.testTag("theme-start"),
                    enabled = current.playable, minWidth = 250.dp, minHeight = 58.dp, fontSize = 20,
                )
            }
        }
    }
}

@Composable
private fun Poster(card: ThemeCard, index: Int, width: Dp, height: Dp, opened: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .size(width, height)
            .shadow(18.dp, RoundedCornerShape(4.dp))
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFF14100D))
            .border(1.dp, if (opened) Esc.gold else Color(0x55C9A15A), RoundedCornerShape(4.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .testTag("theme-poster-$index"),
    ) {
        PosterFace(card, index)
        AnimatedVisibility(opened, enter = fadeIn(tween(220)), exit = fadeOut(tween(160))) {
            PosterDetail(card)
        }
    }
}

@Composable
private fun PosterFace(card: ThemeCard, index: Int) {
    Box(Modifier.fillMaxSize()) {
        if (card.playable) {
            Image(painterResource(R.drawable.scene_palace_night), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Image(
                painterResource(R.drawable.portrait_yeoni), null,
                Modifier.align(Alignment.BottomCenter).fillMaxWidth(0.95f).fillMaxHeight(0.78f),
                contentScale = ContentScale.Fit, alpha = 0.92f,
            )
        } else {
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(toneColors(card.tone))))
            Text(
                card.mode.substringAfter("MODE ", "?"),
                Modifier.align(Alignment.Center),
                style = Esc.cormorant(120, Color(0x33EFE4CC), 0f),
            )
            Text(
                "COMING SOON", Modifier.align(Alignment.TopCenter).padding(top = 22.dp)
                    .border(1.dp, Color(0x66EFE4CC)).padding(horizontal = 10.dp, vertical = 4.dp),
                style = Esc.cormorant(13, Color(0xAAEFE4CC), 0.25f),
            )
        }
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(0.42f)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xE6100B08)))),
        )
        Column(
            Modifier.align(Alignment.BottomStart).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(card.mode, style = Esc.cormorant(12, Esc.gold, 0.25f))
            Text(card.title, style = Esc.nanum(24, Esc.cream, lineHeight = 1.2f))
            Text(card.subtitle, style = Esc.noto(13, Esc.creamSoft, lineHeight = 1.3f))
        }
    }
}

@Composable
private fun PosterDetail(card: ThemeCard) {
    Column(
        Modifier.fillMaxSize().background(Color(0xF70F0B09)).padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(card.mode, style = Esc.cormorant(12, Esc.gold, 0.25f))
        Text(card.title, style = Esc.nanum(22, Esc.cream, lineHeight = 1.2f))
        Text(card.description, style = Esc.noto(13, Esc.creamSoft, lineHeight = 1.45f), maxLines = 4)
        Spacer(Modifier.height(4.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0x55C9A15A)))
        StatRow("장르", card.genre.ifBlank { "미정" })
        MeterRow("난이도", card.difficulty)
        MeterRow("공포도", card.fear)
        StatRow("플레이", card.playTime)
        StatRow("대상", card.audience)
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.width(62.dp), style = Esc.noto(13, Esc.gold, lineHeight = 1.3f))
        Text(value, style = Esc.noto(13, Esc.cream, lineHeight = 1.3f))
    }
}

@Composable
private fun MeterRow(label: String, value: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.width(62.dp), style = Esc.noto(13, Esc.gold, lineHeight = 1.3f))
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
            repeat(5) { k ->
                Box(Modifier.size(width = 16.dp, height = 7.dp).background(if (k < value) Esc.gold else Color(0x33C9A15A)))
            }
        }
        Text("  ${value * 20}%", style = Esc.noto(12, Esc.creamSoft, lineHeight = 1.3f))
    }
}

@Composable
private fun SortChip(text: String, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.height(48.dp).clip(RoundedCornerShape(24.dp))
            .background(if (active) Esc.gold else Color(0x66140F0C))
            .border(1.dp, if (active) Esc.gold else Color(0x88C9A15A), RoundedCornerShape(24.dp))
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = Esc.noto(15, if (active) Color(0xFF1C130C) else Esc.cream, lineHeight = 1.2f)) }
}

@Composable
private fun ArrowButton(text: String, modifier: Modifier, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier.zIndex(10f).size(52.dp).clip(RoundedCornerShape(26.dp))
            .background(Color(if (enabled) 0x99140F0C else 0x33140F0C))
            .border(1.dp, Color(if (enabled) 0xAAC9A15A else 0x33C9A15A), RoundedCornerShape(26.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = Esc.noto(18, if (enabled) Esc.gold else Color(0x55C9A15A)), textAlign = TextAlign.Center) }
}

enum class ShowcaseSort(val label: String) { RECOMMENDED("추천순"), DIFFICULTY("난이도"), FEAR("공포도") }

/**
 * 진열 순서. 추천순은 고를 수 있는 테마를 가운데에 둔다.
 * 난이도·공포도 순은 값으로 줄 세우되, 아직 공개하지 않은 테마는 늘 맨 끝에 둔다. 같은 값이면 원래 순서를 지킨다.
 */
internal fun sortShowcase(cards: List<ThemeCard>, sort: ShowcaseSort, descending: Boolean): List<ThemeCard> {
    if (sort == ShowcaseSort.RECOMMENDED) return showcaseOrder(cards)
    val key: (ThemeCard) -> Int = if (sort == ShowcaseSort.DIFFICULTY) ThemeCard::difficulty else ThemeCard::fear
    val (sealed, open) = cards.partition { it.mode == "SEALED" }
    val ordered = if (descending) open.sortedByDescending(key) else open.sortedBy(key)
    return ordered + sealed
}

/** 고를 수 있는 테마를 가운데에 두고, 나머지를 양옆에 번갈아 둔다. */
internal fun showcaseOrder(cards: List<ThemeCard>): List<ThemeCard> {
    val main = cards.filter { it.playable }
    val rest = cards.filterNot { it.playable }
    val left = rest.filterIndexed { i, _ -> i % 2 == 1 }.reversed()
    val right = rest.filterIndexed { i, _ -> i % 2 == 0 }
    return left + main + right
}

private fun toneColors(tone: ThemeTone): List<Color> = when (tone) {
    ThemeTone.NOIR -> listOf(Color(0xFF2A2D36), Color(0xFF0E0F13))
    ThemeTone.KIDS -> listOf(Color(0xFF3E6B5A), Color(0xFF16261F))
    ThemeTone.ADVENTURE -> listOf(Color(0xFF6B4E2E), Color(0xFF241A10))
    ThemeTone.HISTORY -> listOf(Color(0xFF5A2320), Color(0xFF1C0B0A))
}
