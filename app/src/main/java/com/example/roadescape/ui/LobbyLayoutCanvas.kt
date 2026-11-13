package com.example.roadescape.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.dp
import com.example.roadescape.model.LobbyBlock
import kotlin.math.max
import kotlin.math.roundToInt

/** Root placement keeps the moved controls' touch bounds aligned with their visible content. */
@Composable
internal fun LobbyLayoutCanvas(
    editor: LobbyLayoutEditorState,
    wide: Boolean,
    viewportHeight: Dp,
    contentTopPadding: Dp,
    hasResume: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable (LobbyBlock) -> Unit,
) {
    val blocks = listOf(
        LobbyBlock.WELCOME, LobbyBlock.LOGO, LobbyBlock.TAGLINE,
        LobbyBlock.TITLE, LobbyBlock.DESCRIPTION, LobbyBlock.ACTIONS,
    ) + if (hasResume) listOf(LobbyBlock.RESUME) else emptyList()
    Layout(
        modifier = modifier.fillMaxWidth(),
        content = { blocks.forEach { block -> LobbyEditableBlock(editor, block) { content(block) } } },
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val contentWidth = ((width * if (wide) .76f else 1f).roundToInt() - 60.dp.roundToPx())
            .coerceIn(1, max(1, width))
        val placeables = measurables.mapIndexed { index, measurable ->
            measurable.measure(Constraints(maxWidth = if (blocks[index] == LobbyBlock.RESUME) width else contentWidth))
        }
        val bases = mutableListOf<Pair<Int, Int>>()
        var nextY = contentTopPadding.roundToPx() + 38.dp.roundToPx()
        placeables.forEachIndexed { index, placeable ->
            val resume = blocks[index] == LobbyBlock.RESUME
            val x = if (resume && wide) {
                if (layoutDirection == LayoutDirection.Rtl) 0 else width - placeable.width
            } else (width - placeable.width) / 2
            bases += x to nextY
            nextY += placeable.height + when {
                blocks[index] == LobbyBlock.ACTIONS -> 56.dp.roundToPx()
                resume -> 18.dp.roundToPx()
                else -> 14.dp.roundToPx()
            }
        }
        val height = constraints.constrainHeight(max(viewportHeight.roundToPx(), nextY))
        val positions = placeables.mapIndexed { index, placeable ->
            val (baseX, baseY) = bases[index]
            val offset = editor.offsetFor(blocks[index])
            val x = (baseX + offset.xDp.dp.toPx()).roundToInt().coerceIn(0, max(0, width - placeable.width))
            val y = (baseY + offset.yDp.dp.toPx()).roundToInt().coerceIn(0, max(0, height - placeable.height))
            editor.recordBounds(blocks[index], LobbyBlockBounds(
                baseXDp = baseX.toDp().value, baseYDp = baseY.toDp().value,
                xDp = x.toDp().value, yDp = y.toDp().value,
                widthDp = placeable.width.toDp().value, heightDp = placeable.height.toDp().value,
                canvasWidthDp = width.toDp().value, canvasHeightDp = height.toDp().value,
            ))
            x to y
        }
        layout(width, height) {
            placeables.forEachIndexed { index, placeable ->
                val (x, y) = positions[index]
                val selected = editor.editing && editor.selectedBlock == blocks[index]
                placeable.place(x, y, zIndex = if (selected) 1f else 0f)
            }
        }
    }
}
