package com.team.carescape.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.team.carescape.game.WaterHeightGrid

/** A fixed clue, never a route preview or an alternate input control. */
@Composable
internal fun WaterHeightGridBoard(grid: WaterHeightGrid, modifier: Modifier = Modifier) {
    val board = Color(0xFF0C0C0C)
    val edge = Color(0xFF45413A)
    val startFill = Color(0xFFE9CF91)
    Column(
        modifier.testTag("water-height-grid")
            .background(board, RoundedCornerShape(10.dp)).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        grid.rows.forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { height ->
                    val start = height == grid.start
                    Box(
                        Modifier.weight(1f).aspectRatio(1f)
                            .background(if (start) startFill else board, RoundedCornerShape(6.dp))
                            .border(1.dp, if (start) Esc.gold else edge, RoundedCornerShape(6.dp))
                            .clearAndSetSemantics {
                                contentDescription = "높이 $height"
                                stateDescription = if (start) "시작" else "칸"
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(height.toString(), style = Esc.noto(22, if (start) Esc.ink else Esc.cream))
                            if (start) Text("START", style = Esc.noto(9, Esc.inkBody))
                        }
                    }
                }
            }
        }
    }
}
