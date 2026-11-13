package com.example.roadescape.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.roadescape.BuildConfig
import com.example.roadescape.model.LobbyBlock

/** Keeps the content's ordinary layout and adds a sibling input shield only while editing. */
@Composable
fun LobbyEditableBlock(
    editor: LobbyLayoutEditorState,
    id: LobbyBlock,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val density = LocalDensity.current
    Box(modifier.testTag("lobby-block-${id.name}")) {
        content()
        if (BuildConfig.DEBUG && editor.editing) {
            val selected = editor.selectedBlock == id
            Box(
                Modifier.matchParentSize()
                    .background(CaseColors.Gold.copy(alpha = if (selected) .14f else .035f))
                    .border(if (selected) 2.dp else 1.dp, CaseColors.Gold.copy(alpha = if (selected) 1f else .55f))
                    .testTag("lobby-edit-handle-${id.name}")
                    .semantics { contentDescription = "${id.name} 위치 선택 및 드래그" }
                    .pointerInput(editor, id, density.density) {
                        detectDragGestures(
                            onDragStart = { editor.select(id) },
                            onDrag = { change, amount ->
                                change.consume()
                                editor.moveBy(id, amount.x / density.density, amount.y / density.density)
                            },
                        )
                    }
                    .clickable(enabled = !editor.saving) { editor.select(id) },
            )
        }
    }
}
