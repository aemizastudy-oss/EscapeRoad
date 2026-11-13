package com.team.carescape.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.team.carescape.game.CasePerson
import com.team.carescape.game.Line
import com.team.carescape.game.StoryEpisode

/** 대사 장면. [onParchment]가 true면 양피지 위(먹색), 아니면 어두운 배경(크림색)에 그린다. */
@Composable
fun DialogueBlock(episode: StoryEpisode, lines: List<Line>, onParchment: Boolean = false, modifier: Modifier = Modifier) {
    val body = if (onParchment) Esc.inkBody else Esc.creamMuted
    val soft = if (onParchment) Esc.inkSoft else Esc.creamSoft
    val nameColor = if (onParchment) Esc.wine else Esc.gold
    Column(modifier.testTag("dialogue"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        lines.forEach { line ->
            val who = episode.speakerFor(line.speaker)
            if (who == null) {
                Text(line.text, style = Esc.noto(15, soft, lineHeight = 1.7f).copy(fontStyle = FontStyle.Italic))
            } else {
                Row(Modifier.fillMaxWidth().testTag("dialogue-${who.id}"), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SpeakerSeal(who, nameColor)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("${who.name} · ${who.role}", style = Esc.noto(13, nameColor))
                        Text("“${line.text}”", style = Esc.noto(16, body, lineHeight = 1.7f))
                    }
                }
            }
        }
    }
}

/** 인물의 첫 글자를 새긴 작은 원형 인장. */
@Composable
fun SpeakerSeal(who: CasePerson, color: Color = Esc.gold, size: Int = 36) {
    Box(
        Modifier.size(size.dp).border(1.dp, color, CircleShape).background(color.copy(alpha = 0.12f), CircleShape),
        contentAlignment = Alignment.Center,
    ) { Text(who.name.take(1), style = Esc.nanum(size / 2, color)) }
}

/** 퍼즐 단계를 건네는 인물 표시. */
@Composable
fun SpeakerTag(who: CasePerson, modifier: Modifier = Modifier) {
    Row(modifier.testTag("story-step-speaker"), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SpeakerSeal(who, Esc.wine, 32)
        Column(Modifier.width(260.dp)) {
            Text(who.name, style = Esc.nanum(17, Esc.wine))
            Text(who.role, style = Esc.noto(12, Esc.inkLabel))
        }
    }
}

/** 관계도: 서도윤을 가운데 두고, 기록부에 오른 인물을 관계 말과 함께 잇는다. */
@Composable
fun RelationMap(center: CasePerson, others: List<Pair<CasePerson, String>>, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().background(Esc.panel).border(1.dp, Esc.goldDim).padding(16.dp).testTag("relation-map"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("관계도", style = Esc.nanum(18))
        others.forEach { (p, relation) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SpeakerSeal(center, Esc.gold, 30)
                Text(center.name, Modifier.width(56.dp), style = Esc.noto(14, Esc.cream))
                Box(Modifier.width(40.dp).padding(vertical = 1.dp).background(Esc.goldDim).size(40.dp, 1.dp))
                Text(relation, Modifier.width(150.dp).testTag("relation-${p.id}"), style = Esc.noto(14, if (relation.endsWith("?")) Esc.creamSoft else Esc.gold))
                Box(Modifier.width(40.dp).background(Esc.goldDim).size(40.dp, 1.dp))
                SpeakerSeal(p, Esc.gold, 30)
                Text("${p.name} · ${p.role}", style = Esc.noto(14, Esc.cream))
            }
        }
    }
}
