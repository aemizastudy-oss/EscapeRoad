package com.example.roadescape.ui

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.roadescape.MainActivity
import com.example.roadescape.game.GameCommand
import com.example.roadescape.game.GameFeedback
import com.example.roadescape.game.TourCatalog
import com.example.roadescape.game.TourGate
import com.example.roadescape.model.CheckpointStatus
import com.example.roadescape.model.LockDefinition
import com.example.roadescape.model.LockInput
import com.example.roadescape.model.TourMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Explicit mock observations verify the tour flow without claiming an actual visit or SDK signal. */
@RunWith(AndroidJUnit4::class)
class TourFlowTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private lateinit var viewModel: GameViewModel

    @Test
    fun fiveChaptersRequireTheirOwnTourStartAndCompleteInOrder() {
        startMockTour()
        TourCatalog.checkpoints.forEachIndexed { index, waypoint ->
            if (index > 0) click("tab-checkpoints")
            click("parking-PARKED")
            click("tour-location-inside")
            awaitState { it.tourStartGate == TourGate.READY }
            click("tour-start")
            awaitState {
                it.tourRecord?.checkpointId == waypoint.id &&
                    it.session?.progress?.first { progress -> progress.checkpointId == waypoint.id }?.arrivedAt != null
            }
            click("checkpoint-${waypoint.id}")
            rule.waitUntil(30_000) { exists("arrival-open") || exists("paused") || exists("puzzle-submit") }
            if (exists("arrival-open")) click("arrival-open")
            awaitTag("paused")
            rule.onNodeWithTag("puzzle-submit").assertDoesNotExist()

            click("tab-checkpoints")
            click("tour-debug-elapsed")
            awaitState { it.tourGate == TourGate.READY && it.tourRecord?.debugTimeBypass == true }
            openCurrentPuzzle()
            click("skip")
            click("confirmation-confirm")
            awaitState {
                it.session?.progress?.first { progress -> progress.checkpointId == waypoint.id }?.status == CheckpointStatus.SKIPPED
            }
        }
        awaitTag("completion")
        rule.onNodeWithTag("summary-solved").assertTextEquals("0")
        rule.onNodeWithTag("summary-skipped").assertTextEquals("5")
        val complete = viewModel.state.value.session!!
        assertEquals(5, complete.acquiredClues.size)
        assertEquals(TourMode.MOCK, complete.sourceMode)
    }

    @Test
    fun recreationRetainsTourRecordButRequiresFreshParkingAndLocation() {
        startMockTour()
        prepareFirstUnlockedPuzzle()
        val before = viewModel.state.value.tourRecord!!
        rule.activityRule.scenario.recreate()
        awaitState { !it.canReadContent }
        click("parking-PARKED")
        awaitState { it.canReadContent && it.tourGate == TourGate.LOCATION_MISSING }
        rule.onNodeWithTag("puzzle-submit").assertDoesNotExist()
        assertEquals(before, viewModel.state.value.tourRecord)

        click("tab-checkpoints")
        click("tour-location-inside")
        awaitState { it.tourGate == TourGate.READY }
        click("tour-start") // Reconfirmation must preserve the original saved start time.
        awaitState { !it.saving && it.tourGate == TourGate.READY && it.feedback == GameFeedback.NO_CHANGE }
        assertEquals(before, viewModel.state.value.tourRecord)
        openCurrentPuzzle()
        awaitEnabled("puzzle-submit")
        assertTrue(viewModel.state.value.tourRecord!!.debugTimeBypass)
    }

    @Test
    fun outsidePositionHidesPuzzleAndCannotSubmitEvenAfterTourTimePassed() {
        startMockTour()
        prepareFirstUnlockedPuzzle()
        val before = viewModel.state.value.session!!
        val record = viewModel.state.value.tourRecord!!
        val content = viewModel.state.value.content!!
        val checkpoint = content.checkpoints.first()
        val puzzle = content.puzzles.first { it.id == checkpoint.puzzleId }
        val correct = when (val lock = puzzle.lock) {
            is LockDefinition.Numeric -> LockInput.Numeric(lock.answer)
            is LockDefinition.Sequence -> LockInput.Sequence(lock.answer)
            is LockDefinition.StateCombination -> LockInput.StateCombination(mapOf("channel" to lock.channel, "level" to lock.level))
        }

        click("tab-checkpoints")
        click("tour-location-outside")
        awaitState { it.tourGate == TourGate.OUTSIDE_RADIUS }
        click("checkpoint-${checkpoint.id}")
        awaitTag("paused")
        rule.onNodeWithTag("puzzle-submit").assertDoesNotExist()
        rule.onNodeWithTag("open-hints").assertDoesNotExist()
        rule.onNodeWithTag("reveal-answer").assertDoesNotExist()
        rule.onNodeWithTag("skip").assertDoesNotExist()
        rule.runOnUiThread {
            viewModel.command(GameCommand.Submit(before.sessionId, puzzle.id, correct))
        }
        awaitState { it.feedback == GameFeedback.BLOCKED }
        assertEquals(before, viewModel.state.value.session)
        assertEquals(record, viewModel.state.value.tourRecord)

        click("tab-checkpoints")
        click("tour-location-inside")
        awaitState { it.tourGate == TourGate.READY }
        openCurrentPuzzle()
        assertEquals(before, viewModel.state.value.session)
    }

    private fun startMockTour() {
        rule.waitUntil(30_000) { exists("open-suwon-tour") || exists("open-menu") || exists("nav-home") }
        if (!exists("open-suwon-tour") && exists("nav-home")) click("nav-home")
        if (exists("open-suwon-tour")) {
            click("open-suwon-tour")
        } else {
            click("open-menu")
            click("menu-tour-setup")
        }
        awaitTag("screen-tour-setup")
        rule.runOnIdle { viewModel = ViewModelProvider(rule.activity)[GameViewModel::class.java] }
        val previousId = viewModel.state.value.session?.sessionId
        click("tour-start-mock")
        rule.waitUntil(30_000) {
            exists("confirmation-confirm") ||
                viewModel.state.value.session?.let { it.sessionId != previousId && it.sourceMode == TourMode.MOCK } == true
        }
        if (exists("confirmation-confirm")) click("confirmation-confirm")
        awaitState {
            !it.loading && !it.saving && it.content?.requiresTour == true &&
                it.session?.sessionId != previousId && it.mode == TourMode.MOCK
        }
        assertNotNull(viewModel.state.value.session)
        click("tab-checkpoints")
        awaitTag("checkpoint-${TourCatalog.checkpoints.first().id}")
    }

    private fun prepareFirstUnlockedPuzzle() {
        val first = TourCatalog.checkpoints.first().id
        click("parking-PARKED")
        click("tour-location-inside")
        click("tour-start")
        awaitState {
            it.tourRecord?.checkpointId == first &&
                it.session?.progress?.first()?.arrivedAt != null
        }
        click("tour-debug-elapsed")
        awaitState { it.tourGate == TourGate.READY }
        openCurrentPuzzle()
        awaitState { it.session?.progress?.first()?.status == CheckpointStatus.IN_PROGRESS }
    }

    private fun openCurrentPuzzle() {
        val current = viewModel.state.value.session!!.progress.first { !it.status.isComplete }.checkpointId
        if (!exists("checkpoint-$current")) click("tab-checkpoints")
        click("checkpoint-$current")
        rule.waitUntil(30_000) { exists("arrival-open") || exists("puzzle-submit") || exists("paused") }
        if (exists("arrival-open")) click("arrival-open")
        awaitEnabled("puzzle-submit")
    }

    private fun click(tag: String) {
        awaitEnabled(tag)
        val node = rule.onNodeWithTag(tag)
        runCatching { node.performScrollTo() }
        node.performClick()
        rule.waitForIdle()
    }

    private fun awaitState(predicate: (GameUiState) -> Boolean) {
        rule.waitUntil(30_000) { predicate(viewModel.state.value) }
    }

    private fun awaitTag(tag: String) {
        rule.waitUntil(30_000) { exists(tag) }
    }

    private fun awaitEnabled(tag: String) {
        rule.waitUntil(30_000) {
            runCatching { rule.onNodeWithTag(tag).assertIsEnabled(); true }.getOrDefault(false)
        }
    }

    private fun exists(tag: String): Boolean = try {
        rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    } catch (error: IllegalStateException) {
        if (error.message?.startsWith("No compose hierarchies found") == true) false else throw error
    }
}
