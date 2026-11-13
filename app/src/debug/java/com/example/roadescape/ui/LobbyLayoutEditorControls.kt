package com.example.roadescape.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.roadescape.model.LobbyBlock
import java.util.Locale

@Composable
fun LobbyLayoutEditorControls(editor: LobbyLayoutEditorState, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var choosingBlock by remember(editor) { mutableStateOf(false) }
    BoxWithConstraints(modifier) {
        if (!editor.editing) {
            EscapeButton(
                text = if (editor.canEdit) "로비 배치 편집" else "배치 편집 · 안전 정차 필요",
                tag = "lobby-layout-edit",
                onClick = editor::beginEditing,
                enabled = editor.canEdit,
                secondary = true,
                modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
            )
        } else {
            Surface(
                modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp)
                    .widthIn(max = 700.dp).fillMaxWidth().heightIn(max = maxHeight * .57f),
                color = CaseColors.DeepBurgundy.copy(alpha = .98f),
                contentColor = CaseColors.Cream,
                border = BorderStroke(1.dp, CaseColors.Gold),
                shadowElevation = 10.dp,
            ) {
                Column(Modifier.padding(12.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("로비 배치 편집 · ${editor.profile.name}",
                            modifier = Modifier.weight(1f), color = CaseColors.Gold,
                            fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        EscapeButton(if (editor.panelExpanded) "접기" else "펼치기",
                            "lobby-layout-panel-toggle", editor::togglePanel, secondary = true)
                        EscapeButton("취소", "lobby-layout-cancel", editor::cancel, secondary = true)
                    }
                    Text(editor.statusText, modifier = Modifier.testTag("lobby-layout-status"),
                        color = CaseColors.Cream, fontSize = 14.sp)
                    if (editor.panelExpanded) {
                        editor.warning?.let { Text(it, color = CaseColors.Gold, fontSize = 13.sp) }
                        Text("블록을 드래그하거나 화살표로 이동하세요. 안전 상태가 풀리면 미저장 변경을 취소합니다.",
                            color = CaseColors.Cream.copy(alpha = .8f), fontSize = 13.sp)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.weight(1f)) {
                                EscapeButton("선택: ${editor.selectedBlock.label()}", "lobby-layout-select",
                                    { choosingBlock = true }, enabled = !editor.saving,
                                    secondary = true, modifier = Modifier.fillMaxWidth())
                                DropdownMenu(expanded = choosingBlock, onDismissRequest = { choosingBlock = false }) {
                                    LobbyBlock.entries.forEach { block ->
                                        DropdownMenuItem(
                                            text = { Text(block.label()) },
                                            onClick = { editor.select(block); choosingBlock = false },
                                            modifier = Modifier.heightIn(min = 56.dp)
                                                .testTag("lobby-layout-select-${block.name}"),
                                        )
                                    }
                                }
                            }
                            EscapeButton("이동 단위: ${editor.stepDp.toInt()} dp", "lobby-layout-step",
                                editor::toggleStep, enabled = !editor.saving, secondary = true)
                        }
                        val offset = editor.offsetFor(editor.selectedBlock)
                        Text(String.format(Locale.ROOT, "%s · x %.1f dp / y %.1f dp",
                            editor.selectedBlock.name, offset.xDp, offset.yDp),
                            modifier = Modifier.testTag("lobby-layout-selected-info"),
                            color = CaseColors.Cream, fontSize = 14.sp)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            EscapeButton("← 왼쪽", "lobby-layout-nudge-left", { editor.nudge(-1, 0) }, enabled = !editor.saving, secondary = true)
                            EscapeButton("→ 오른쪽", "lobby-layout-nudge-right", { editor.nudge(1, 0) }, enabled = !editor.saving, secondary = true)
                            EscapeButton("↑ 위", "lobby-layout-nudge-up", { editor.nudge(0, -1) }, enabled = !editor.saving, secondary = true)
                            EscapeButton("↓ 아래", "lobby-layout-nudge-down", { editor.nudge(0, 1) }, enabled = !editor.saving, secondary = true)
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            EscapeButton("저장", "lobby-layout-save", editor::save, enabled = !editor.saving)
                            EscapeButton("기본값", "lobby-layout-reset", editor::resetDraftToDefaults,
                                enabled = !editor.saving, secondary = true)
                            EscapeButton("레이아웃 JSON 복사", "lobby-layout-copy", {
                                editor.exportJson()?.let { json ->
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                    if (clipboard != null) {
                                        clipboard.setPrimaryClip(ClipData.newPlainText("Escape Road lobby layout", json))
                                        editor.copiedToClipboard()
                                    }
                                }
                            }, enabled = !editor.saving, secondary = true)
                        }
                        Text("기본값은 임시 적용 후 저장합니다. JSON은 복사해 asset에 반영하고 재빌드하면 다른 기기와 공유할 수 있습니다.",
                            color = CaseColors.Cream.copy(alpha = .7f), fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

private fun LobbyBlock.label(): String = when (this) {
    LobbyBlock.WELCOME -> "환영 문구"
    LobbyBlock.LOGO -> "로고"
    LobbyBlock.TAGLINE -> "태그라인"
    LobbyBlock.TITLE -> "제목"
    LobbyBlock.DESCRIPTION -> "설명"
    LobbyBlock.ACTIONS -> "버튼 묶음"
    LobbyBlock.RESUME -> "진행 중인 사건"
}
