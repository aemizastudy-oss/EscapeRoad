package com.example.roadescape.ui

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.roadescape.MainActivity
import com.example.roadescape.R
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Tests exercise the same debug panel and touch controls available in the APK. */
@RunWith(AndroidJUnit4::class)
class GameFlowTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun threeLocksCompleteInOrderAndAwardThreeClues() {
        startNewGame()
        click("parking-PARKED")
        rule.onNodeWithTag("checkpoint-cp_02").assertIsNotEnabled()

        arriveAndOpen("cp_01")
        listOf(0, 4, 2, 7).forEach { click("digit-$it") }
        click("puzzle-submit")
        click("tab-checkpoints")
        awaitTag("checkpoint-cp_02")
        click("checkpoint-cp_02")
        awaitTag("paused")
        rule.onNodeWithTag("puzzle-submit").assertDoesNotExist()

        click("arrive")
        awaitEnabled("puzzle-submit")
        listOf("UP", "RIGHT", "DOWN", "LEFT").forEach { click("sequence-$it") }
        click("puzzle-submit")
        click("tab-checkpoints")
        arriveAndOpen("cp_03")
        click("channel-3")
        click("level-2")
        click("puzzle-submit")
        awaitTag("completion")
        rule.onNodeWithTag("summary-solved").assertTextContains("3")
        rule.onNodeWithTag("summary-skipped").assertTextContains("0")
        rule.onNodeWithTag("summary-hints").assertTextContains("0")
        rule.onNodeWithTag("summary-answers").assertTextContains("0")

        click("tab-clues")
        listOf("clue_01", "clue_02", "clue_03").forEach { clueId ->
            awaitTag("clue-$clueId")
        }
    }

    @Test
    fun drivingAndUnknownHidePuzzleAndRequireParkingAgain() {
        startNewGame()
        click("checkpoint-cp_01")
        awaitTag("paused")
        rule.onNodeWithTag("puzzle-submit").assertDoesNotExist()
        click("parking-PARKED")
        awaitTag("paused")
        rule.onNodeWithTag("puzzle-submit").assertDoesNotExist()
        click("arrive")
        awaitEnabled("puzzle-submit")
        click("digit-0")
        click("hint-next")

        click("parking-DRIVING")
        awaitTag("paused")
        rule.onNodeWithTag("puzzle-submit").assertDoesNotExist()
        rule.onNodeWithTag("digit-0").assertDoesNotExist()
        rule.onNodeWithTag("hint-next").assertDoesNotExist()
        rule.onNodeWithTag("reveal-answer").assertDoesNotExist()
        rule.onNodeWithTag("skip").assertDoesNotExist()

        click("parking-UNKNOWN")
        awaitTag("paused")
        rule.onNodeWithTag("puzzle-submit").assertDoesNotExist()

        click("parking-PARKED")
        awaitEnabled("puzzle-submit")
        rule.onNodeWithTag("paused").assertDoesNotExist()
        rule.onNodeWithTag("digit-0").assertExists()
    }

    @Test
    fun revealedAnswerSurvivesActivityRecreationAndSkippingAwardsClue() {
        startNewGame()
        click("parking-PARKED")
        arriveAndOpen("cp_01")
        click("hint-next")
        click("reveal-answer")
        click("confirmation-confirm")
        awaitTag("answer-content")

        rule.activityRule.scenario.recreate()
        rule.waitUntil(30_000) { exists("resume") || exists("paused") }
        if (exists("resume")) click("resume")
        awaitTag("paused")
        click("parking-PARKED")
        rule.waitUntil(30_000) { exists("checkpoint-cp_01") || exists("puzzle-submit") }
        if (exists("checkpoint-cp_01")) click("checkpoint-cp_01")
        awaitTag("answer-content")
        rule.onNodeWithTag("hint-count")
            .assertTextEquals(rule.activity.getString(R.string.hint_count, 1, 2))
        awaitEnabled("skip")
        click("skip")
        click("confirmation-confirm")
        click("tab-clues")
        awaitTag("clue-clue_01")
    }

    private fun startNewGame() {
        awaitTag("start-new")
        click("start-new")
        rule.waitUntil(30_000) {
            exists("confirmation-confirm") || exists("checkpoint-cp_01")
        }
        if (exists("confirmation-confirm")) click("confirmation-confirm")
        awaitTag("tab-checkpoints")
        click("tab-checkpoints")
        awaitTag("checkpoint-cp_01")
    }

    private fun arriveAndOpen(checkpointId: String) {
        click("arrive")
        awaitEnabled("checkpoint-$checkpointId")
        click("checkpoint-$checkpointId")
        awaitEnabled("puzzle-submit")
    }

    private fun click(tag: String) {
        awaitEnabled(tag)
        val node = rule.onNodeWithTag(tag)
        // Wide and compact layouts may use different scroll containers.
        runCatching { node.performScrollTo() }
        node.performClick()
        rule.waitForIdle()
    }

    private fun awaitTag(tag: String) {
        rule.waitUntil(30_000) { exists(tag) }
    }

    private fun awaitEnabled(tag: String) {
        rule.waitUntil(30_000) {
            runCatching {
                rule.onNodeWithTag(tag).assertExists().assertIsEnabled()
                true
            }.getOrDefault(false)
        }
    }

    private fun exists(tag: String): Boolean =
        rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
}
