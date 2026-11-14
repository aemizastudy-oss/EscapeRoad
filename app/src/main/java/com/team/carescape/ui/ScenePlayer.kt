package com.team.carescape.ui

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.team.carescape.game.Line
import com.team.carescape.game.SceneBg
import com.team.carescape.game.StoryEpisode

/**
 * 드라마처럼 한 줄씩 넘기는 장면. 배경은 [Line.bg]가 나올 때마다 바뀌고, 인물이 말하면 초상이 뜬다.
 * 정차 중 화면에서만 쓴다(주행 중에는 상위 화면이 이야기를 가린다).
 */
@Composable
fun ScenePlayer(
    episode: StoryEpisode,
    lines: List<Line>,
    index: Int,
    title: String,
    onNext: () -> Unit,
    onSkip: () -> Unit,
    onPrev: (() -> Unit)? = null,
    onLine: (String) -> Unit = {},
    lastLabel: String = "계속",
    fallbackBg: SceneBg = SceneBg.PALACE_NIGHT,
) {
    if (lines.isEmpty()) return
    val i = index.coerceIn(0, lines.lastIndex)
    val line = lines[i]
    val bg = lines.take(i + 1).lastOrNull { it.bg != null }?.bg ?: fallbackBg
    val who = episode.speakerFor(line.speaker)
    LaunchedEffect(i, lines) { onLine(line.text) }
    val tap = remember { MutableInteractionSource() }

    Box(Modifier.fillMaxSize().testTag("scene-player").clickable(tap, indication = null, role = Role.Button) { onNext() }) {
        Crossfade(bg, label = "scene-bg") { SceneBackdrop(it, Modifier.fillMaxSize().testTag("scene-bg-${it.name}")) }

        // 오른쪽 위는 시연 패널 자리라, 건너뛰기는 제목 옆(왼쪽 위)에 둔다.
        Row(Modifier.offset(48.dp, 30.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = Esc.cormorant(16))
                Text("${i + 1} / ${lines.size}", Modifier.testTag("scene-progress"), style = Esc.noto(13, Esc.creamSoft))
            }
            OutlineButton("건너뛰기", onSkip, Modifier.testTag("scene-skip"), minHeight = 44.dp, fontSize = 15)
        }

        // "— … —" 줄은 화면 가운데 큰 제목 카드로 보여 준다(엔딩 제목).
        if (who == null && line.text.startsWith("— ") && line.text.endsWith(" —")) {
            Column(
                Modifier.fillMaxSize().background(Color(0xB3100205)).padding(bottom = 140.dp).testTag("scene-title-card"),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically),
            ) {
                val parts = line.text.removePrefix("— ").removeSuffix(" —").split(" · ")
                Text("The End", style = Esc.pinyon(46))
                Box(Modifier.width(260.dp).height(1.dp).background(Esc.gold))
                Text(parts.first(), style = Esc.noto(22, Esc.gold), textAlign = TextAlign.Center)
                if (parts.size > 1) Text(parts.drop(1).joinToString(" · "), style = Esc.nanum(52), textAlign = TextAlign.Center)
            }
        }

        Crossfade(who?.id, label = "scene-portrait", modifier = Modifier.align(Alignment.BottomStart).padding(start = 70.dp, bottom = 150.dp)) { id ->
            if (id != null) Portrait(id, Modifier.size(270.dp, 378.dp).testTag("scene-portrait-$id"))
        }

        // 대사 상자
        Column(Modifier.align(Alignment.BottomCenter).padding(bottom = 30.dp, start = 60.dp, end = 60.dp)) {
            if (who != null) {
                Row(
                    Modifier.offset(x = 24.dp).background(Esc.wine).border(1.dp, Esc.gold).padding(horizontal = 18.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(who.name, Modifier.testTag("scene-speaker"), style = Esc.nanum(20))
                    Text(who.role, style = Esc.noto(13, Esc.gold))
                }
            }
            Column(
                Modifier.fillMaxWidth().heightIn(min = 150.dp).background(Color(0xE61E0508)).border(1.dp, Esc.gold)
                    .padding(horizontal = 36.dp, vertical = 22.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    if (who == null) line.text else "“${line.text}”",
                    Modifier.fillMaxWidth().testTag("scene-text"),
                    style = if (who == null) Esc.noto(20, Esc.creamMuted, lineHeight = 1.7f).copy(fontStyle = FontStyle.Italic)
                    else Esc.nanum(22, lineHeight = 1.6f),
                    textAlign = if (who == null) TextAlign.Center else TextAlign.Start,
                )
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        lines.indices.forEach { k ->
                            Box(Modifier.size(if (k == i) 22.dp else 12.dp, 4.dp).background(if (k <= i) Esc.gold else Esc.gold.copy(alpha = 0.3f)))
                        }
                    }
                    if (onPrev != null && i > 0) OutlineButton("◀ 이전", onPrev, Modifier.testTag("scene-prev"), minHeight = 44.dp, fontSize = 15)
                    ParchmentButton(if (i == lines.lastIndex) lastLabel else "다음 ▶", onNext, Modifier.width(170.dp).testTag("scene-next"), minWidth = 150.dp, minHeight = 48.dp)
                }
            }
        }
    }
}
