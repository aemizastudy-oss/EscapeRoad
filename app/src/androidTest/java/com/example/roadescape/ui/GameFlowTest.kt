package com.example.roadescape.ui

import android.content.Context
import android.view.inputmethod.InputMethodManager
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.roadescape.MainActivity
import com.example.roadescape.R
import com.example.roadescape.RoadEscapeApplication
import com.example.roadescape.model.CheckpointStatus
import com.example.roadescape.model.GameSession
import com.example.roadescape.model.ParkingState
import com.example.roadescape.platform.SimulatedParkingProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        click("digit-8")
        click("puzzle-clear")
        click("digit-9")
        click("puzzle-delete")
        listOf(0, 4, 2, 7).forEach { click("digit-$it") }
        click("puzzle-submit")
        click("tab-checkpoints")
        awaitTag("checkpoint-cp_02")
        click("checkpoint-cp_02")
        awaitTag("paused")
        rule.onNodeWithTag("puzzle-submit").assertDoesNotExist()

        click("arrive")
        awaitEnabled("puzzle-submit")
        click("sequence-LEFT")
        click("puzzle-clear")
        click("sequence-DOWN")
        click("puzzle-delete")
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
        revealNextHint()

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
        revealNextHint()
        click("reveal-answer")
        click("confirmation-confirm")
        awaitTag("answer-content")

        rule.activityRule.scenario.recreate()
        rule.waitUntil(30_000) { exists("resume") || exists("paused") || exists("checkpoint-cp_01") }
        if (exists("resume")) click("resume")
        if (exists("checkpoint-cp_01")) click("checkpoint-cp_01")
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

    @Test
    fun menuPagesAndLobbyReturnWithoutChangingSavedSessionAndResetCanBeCancelled() {
        startNewGame()
        click("parking-PARKED")
        val original = savedSession()
        listOf(
            "notebook", "archive", "casebook", "locks", "save", "rules",
        ).forEach { destination ->
            click("open-menu")
            click("menu-$destination")
            awaitTag("screen-$destination")
            if (destination == "notebook") {
                val note = "테스트 경유지의 단서를 수첩에 기록했습니다."
                click("notebook-page-cp_01")
                rule.onNodeWithTag("notebook-input").performTextReplacement(note)
                closeSoftKeyboard()
                click("notebook-save")
                awaitText("notebook-status", "이 기기에 메모를 저장했습니다.")
                assertEquals(original, savedSession())
                click("nav-back")
                awaitTag("checkpoint-cp_01")
                click("open-menu")
                click("menu-notebook")
                awaitTag("notebook-input")
                rule.onNodeWithTag("notebook-input").assertTextEquals(note)
            }
            click("nav-back")
            awaitTag("checkpoint-cp_01")
            assertEquals(original, savedSession())
        }

        click("nav-home")
        awaitTag("screen-lobby")
        click("resume")
        awaitTag("checkpoint-cp_01")
        assertEquals(original, savedSession())

        openNewGamePreview()
        click("start-new")
        awaitTag("confirmation-confirm")
        click("confirmation-cancel")
        rule.onNodeWithTag("confirmation-confirm").assertDoesNotExist()
        assertEquals(original, savedSession())
        click("nav-home")
        click("resume")
        awaitTag("checkpoint-cp_01")
        assertEquals(original, savedSession())
    }

    @Test
    fun reopeningHintsAndCancellingDialogsPreserveProgressAndDrivingDismissesAnswerDialog() {
        startNewGame()
        click("parking-PARKED")
        arriveAndOpen("cp_01")
        awaitProgress { it.progress.first().status == CheckpointStatus.IN_PROGRESS }
        val original = savedSession()

        click("open-hints")
        awaitTag("screen-hints")
        assertEquals(original, savedSession())
        click("nav-back")
        awaitEnabled("puzzle-submit")
        assertEquals(original, savedSession())

        revealNextHint()
        val oneHint = savedSession()
        assertEquals(1, oneHint.progress.first().revealedHintCount)
        repeat(2) {
            click("open-hints")
            awaitTag("screen-hints")
            click("nav-back")
            awaitEnabled("puzzle-submit")
            assertEquals(oneHint, savedSession())
        }

        listOf("reveal-answer", "skip").forEach { action ->
            click(action)
            awaitTag("confirmation-confirm")
            click("confirmation-cancel")
            awaitEnabled("puzzle-submit")
            assertEquals(oneHint, savedSession())
        }
        assertFalse(savedSession().progress.first().answerRevealed)

        click("reveal-answer")
        awaitTag("confirmation-confirm")
        simulateParkingWhileDialogIsOpen(ParkingState.DRIVING)
        awaitTag("paused")
        rule.onNodeWithTag("confirmation-confirm").assertDoesNotExist()
        rule.onNodeWithTag("answer-content").assertDoesNotExist()
        assertEquals(oneHint, savedSession())
        click("parking-PARKED")
        awaitEnabled("puzzle-submit")
        rule.onNodeWithTag("hint-count")
            .assertTextEquals(rule.activity.getString(R.string.hint_count, 1, 2))
    }

    @Test
    fun acquiredClueDetailAndCompletedSolutionAreHiddenWhileMoving() {
        startNewGame()
        click("parking-PARKED")
        arriveAndOpen("cp_01")
        skipCurrentPuzzle()
        click("tab-clues")
        click("clue-clue_01")
        awaitTag("screen-clue-detail")
        val expectedBody = runBlocking {
            application().container.packageRepository.load().clues.first { it.id == "clue_01" }.body
        }
        rule.onNodeWithTag("clue-body").assertTextEquals(expectedBody)
        val afterFirstClue = savedSession()
        click("parking-DRIVING")
        awaitTag("paused")
        rule.onNodeWithTag("clue-body").assertDoesNotExist()
        click("parking-UNKNOWN")
        awaitTag("paused")
        rule.onNodeWithTag("clue-body").assertDoesNotExist()
        click("parking-PARKED")
        awaitTag("clue-body")
        assertEquals(afterFirstClue, savedSession())
        click("nav-back")
        awaitTag("clue-clue_01")

        click("tab-checkpoints")
        arriveAndOpen("cp_02")
        skipCurrentPuzzle()
        click("tab-checkpoints")
        arriveAndOpen("cp_03")
        skipCurrentPuzzle()
        awaitTag("completion")
        rule.onNodeWithTag("summary-solved").assertTextEquals("0")
        rule.onNodeWithTag("summary-skipped").assertTextEquals("3")
        click("open-solution")
        awaitTag("solution-content")
        val complete = savedSession()
        click("parking-DRIVING")
        awaitTag("paused")
        rule.onNodeWithTag("solution-content").assertDoesNotExist()
        click("parking-PARKED")
        awaitTag("solution-content")
        assertEquals(complete, savedSession())
        click("nav-back")
        awaitTag("completion")
        click("open-certificate")
        awaitTag("screen-certificate")
        click("nav-back")
        awaitTag("completion")
        click("open-rating")
        awaitTag("screen-rating")
        val review = "주차 상태에서 세 가지 자물쇠를 차분하게 확인했습니다."
        rule.onNodeWithTag("rating-review").performTextReplacement(review)
        closeSoftKeyboard()
        click("rating-satisfaction-4")
        click("rating-save")
        awaitText("rating-status", "이 기기에 평가를 저장했습니다.")
        assertEquals(complete, savedSession())
        click("nav-back")
        awaitTag("completion")
        click("open-rating")
        awaitTag("rating-review")
        rule.onNodeWithTag("rating-review").assertTextEquals(review)
        rule.onNodeWithTag("rating-satisfaction-4").assertTextEquals("◆ 4")
        rule.onNodeWithTag("rating-satisfaction-5").assertTextEquals("◇ 5")
        click("nav-back")
        awaitTag("completion")
        assertEquals(complete, savedSession())
    }

    private fun startNewGame() {
        openNewGamePreview()
        click("start-new")
        rule.waitUntil(30_000) {
            exists("confirmation-confirm") || exists("checkpoint-cp_01")
        }
        if (exists("confirmation-confirm")) click("confirmation-confirm")
        awaitTag("tab-checkpoints")
        click("tab-checkpoints")
        awaitTag("checkpoint-cp_01")
    }

    private fun openNewGamePreview() {
        rule.waitUntil(30_000) { exists("open-cases") || exists("nav-home") }
        if (!exists("open-cases")) click("nav-home")
        click("open-cases")
        click("case-detail")
        click("begin-investigation")
        awaitTag("rules-accepted")
        if (!runCatching { rule.onNodeWithTag("rules-accepted").assertIsOn() }.isSuccess) {
            click("rules-accepted")
        }
        click("rules-continue")
        click("party-continue")
        awaitTag("start-new")
    }

    private fun arriveAndOpen(checkpointId: String) {
        click("arrive")
        awaitEnabled("checkpoint-$checkpointId")
        click("checkpoint-$checkpointId")
        rule.waitUntil(30_000) { exists("arrival-open") || exists("puzzle-submit") }
        if (exists("arrival-open")) click("arrival-open")
        awaitEnabled("puzzle-submit")
    }

    private fun revealNextHint() {
        click("open-hints")
        awaitTag("screen-hints")
        click("hint-next")
        awaitProgress { it.progress.first().revealedHintCount == 1 }
        click("nav-back")
        awaitEnabled("puzzle-submit")
    }

    private fun skipCurrentPuzzle() {
        val checkpointId = savedSession().progress.first { !it.status.isComplete }.checkpointId
        click("skip")
        click("confirmation-confirm")
        awaitProgress { session -> session.progress.first { it.checkpointId == checkpointId }.status == CheckpointStatus.SKIPPED }
    }

    private fun awaitProgress(predicate: (GameSession) -> Boolean) {
        rule.waitUntil(30_000) { runCatching { predicate(savedSession()) }.getOrDefault(false) }
    }

    private fun application() = rule.activity.application as RoadEscapeApplication

    private fun savedSession(): GameSession = runBlocking {
        checkNotNull(application().container.gameRepository.load())
    }

    private fun simulateParkingWhileDialogIsOpen(state: ParkingState) {
        // This models an external state update while the modal owns touch focus.
        rule.runOnUiThread {
            (application().container.sources.parking as SimulatedParkingProvider).simulate(state)
        }
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

    private fun awaitText(tag: String, expected: String) {
        rule.waitUntil(30_000) {
            runCatching {
                rule.onNodeWithTag(tag).assertTextEquals(expected)
                true
            }.getOrDefault(false)
        }
    }

    private fun closeSoftKeyboard() {
        rule.runOnUiThread {
            val activity = rule.activity
            val input = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            input.hideSoftInputFromWindow(activity.window.decorView.windowToken, 0)
            activity.currentFocus?.clearFocus()
        }
        rule.waitForIdle()
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
