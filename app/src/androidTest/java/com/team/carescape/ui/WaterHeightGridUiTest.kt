package com.team.carescape.ui

import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.team.carescape.game.SiyeokFlow5
import com.team.carescape.game.StoryLockDefinition
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class WaterHeightGridUiTest {
    @get:Rule val rule = createComposeRule()

    @Test fun allTwentyFiveHeightsAreReadableAndOnlyTheCenterIsMarkedAsStart() {
        val definition = SiyeokFlow5.episode.chapters[2].puzzleSteps.single { it.id == "c3_channel" }
        val grid = checkNotNull((definition.lock as StoryLockDefinition.Directions).heightGrid)
        rule.setContent { WaterHeightGridBoard(grid, Modifier.width(320.dp)) }
        rule.onNodeWithTag("water-height-grid").assertIsDisplayed()
        for (height in 1..25) {
            rule.onNodeWithContentDescription("높이 $height").assertIsDisplayed()
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, if (height == 25) "시작" else "칸"))
        }
        // Check screen positions, not only the model: rows and columns must match the supplied clue.
        grid.rows.forEach { row ->
            row.zipWithNext().forEach { (left, right) ->
                val a = rule.onNodeWithContentDescription("높이 $left").fetchSemanticsNode().boundsInRoot
                val b = rule.onNodeWithContentDescription("높이 $right").fetchSemanticsNode().boundsInRoot
                assertTrue(a.right <= b.left && a.top == b.top)
            }
        }
        grid.rows.zipWithNext().forEach { (upper, lower) ->
            val a = rule.onNodeWithContentDescription("높이 ${upper.first()}").fetchSemanticsNode().boundsInRoot
            val b = rule.onNodeWithContentDescription("높이 ${lower.first()}").fetchSemanticsNode().boundsInRoot
            assertTrue(a.bottom <= b.top)
        }
    }
}
