package com.team.carescape.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.team.carescape.game.StoryState

private enum class CaseTab(val label: String) { DOCS("문서"), PEOPLE("인물"), ITEMS("소지품"), CLUES("단서") }

/** 사건 기록부. 정차 중에만 열리며, 지금까지 모은 일지·인물·단서를 다시 읽는다. */
@Composable
fun CasebookScreen(s: StoryState, onClose: () -> Unit) {
    var tab by rememberSaveable { mutableStateOf(CaseTab.DOCS) }
    val docs = s.visibleDocuments
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = docs.firstOrNull { it.id == selectedId } ?: docs.lastOrNull()
    GoldFrame(padTop = 24.dp, padH = 36.dp, padBottom = 22.dp) {
        Column(Modifier.fillMaxSize().testTag("casebook"), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("Case Book", style = Esc.pinyon(34))
                    Text("사건 기록부", style = Esc.nanum(28))
                }
                CaseTab.entries.filter { it != CaseTab.ITEMS || s.episode.caseItems.isNotEmpty() }.forEach { t ->
                    val on = t == tab
                    Box(
                        Modifier.border(if (on) 2.dp else 1.dp, if (on) Esc.gold else Esc.goldDim)
                            .background(if (on) Esc.gold.copy(alpha = 0.18f) else Esc.panel)
                            .clickable(role = Role.Tab) { tab = t }.testTag("casebook-tab-${t.name}")
                            .padding(horizontal = 18.dp, vertical = 10.dp),
                    ) { Text(t.label, style = Esc.nanum(17, if (on) Esc.cream else Esc.creamSoft)) }
                }
                ParchmentButton("닫기", onClose, Modifier.testTag("casebook-close"), minWidth = 120.dp, minHeight = 48.dp)
            }
            when (tab) {
                CaseTab.DOCS -> Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    Column(Modifier.width(300.dp).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (docs.isEmpty()) Text("아직 모은 문서가 없습니다.", style = Esc.noto(15, Esc.creamSoft))
                        docs.forEach { d ->
                            val on = d.id == selected?.id
                            Column(
                                Modifier.fillMaxWidth().border(if (on) 2.dp else 1.dp, if (on) Esc.gold else Esc.goldDim)
                                    .background(if (on) Esc.gold.copy(alpha = 0.14f) else Color.Transparent)
                                    .clickable(role = Role.Button) { selectedId = d.id }.testTag("casebook-doc-${d.id}")
                                    .padding(12.dp),
                            ) {
                                Text(d.title, style = Esc.nanum(17, Esc.cream))
                                Text(d.owner, style = Esc.noto(13, Esc.creamSoft))
                            }
                        }
                    }
                    Column(
                        Modifier.weight(1f).fillMaxHeight().parchment().border(1.dp, Esc.goldDim).padding(26.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (selected != null) {
                            Text(selected.owner, style = Esc.cormorant(15, Esc.inkLabel))
                            Text(selected.title, style = Esc.nanum(26, Esc.wine))
                            Text(documentAnnotated(selected.body), Modifier.testTag("casebook-doc-body"), style = Esc.noto(18, Esc.inkBody, lineHeight = 1.8f))
                        }
                    }
                }
                CaseTab.PEOPLE -> Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val people = s.visiblePeople
                    val center = people.firstOrNull { it.id == "physician" }
                    val linked = people.mapNotNull { p -> s.personRelation(p)?.let { p to it } }
                    if (center != null && linked.isNotEmpty()) RelationMap(center, linked)
                    people.forEach { p ->
                        Row(Modifier.fillMaxWidth().background(Esc.panel).border(1.dp, Esc.goldDim).padding(14.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                            SpeakerSeal(p, Esc.gold, 44)
                            Column(Modifier.width(180.dp)) {
                                Text(p.name, style = Esc.nanum(20))
                                Text(p.role, style = Esc.noto(14, Esc.gold))
                            }
                            Column(Modifier.weight(1f).testTag("casebook-person-${p.id}"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                s.personNotes(p).forEachIndexed { i, note ->
                                    Text(if (i == 0) note else "· $note", style = Esc.noto(16, if (i == 0) Esc.creamMuted else Esc.cream, lineHeight = 1.6f))
                                }
                            }
                        }
                    }
                    Text("이 이야기의 인물과 기록은 허구입니다.", style = Esc.noto(13, Esc.label))
                }
                CaseTab.ITEMS -> Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val inv = s.inventory
                    if (inv.isEmpty()) Text("아직 얻은 소지품이 없습니다.", style = Esc.noto(15, Esc.creamSoft))
                    inv.forEach { item ->
                        val used = s.isItemUsed(item)
                        Row(Modifier.fillMaxWidth().background(Esc.panel).border(1.dp, Esc.goldDim).padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                            ItemTile(item, used, selected = false, enabled = false, onClick = null)
                            Text(item.description, Modifier.weight(1f).testTag("casebook-item-${item.id}"),
                                style = Esc.noto(16, if (used) Esc.locked else Esc.creamMuted, lineHeight = 1.6f))
                        }
                    }
                    Text("소지품은 퍼즐을 풀면 들어오고, 자물쇠에서 ‘쓰기’로 사용합니다.", style = Esc.noto(13, Esc.label))
                }
                CaseTab.CLUES -> Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (s.clues.isEmpty()) Text("아직 확정된 단서가 없습니다.", style = Esc.noto(15, Esc.creamSoft))
                    s.clues.forEachIndexed { i, clue ->
                        Row(Modifier.fillMaxWidth().background(Esc.panel).border(1.dp, Esc.goldDim).padding(14.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                            Text("단서 ${i + 1}", Modifier.width(80.dp), style = Esc.noto(14, Esc.gold))
                            Text(clue, Modifier.weight(1f), style = Esc.nanum(19))
                            Text(s.episode.chapters[i].place, style = Esc.noto(14, Esc.creamSoft))
                        }
                    }
                    if (s.items.isNotEmpty()) Text("얻은 것 · ${s.items.joinToString(" · ")}", style = Esc.noto(16, Esc.gold))
                }
            }
        }
    }
}
