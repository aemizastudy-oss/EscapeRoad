package com.team.carescape.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.team.carescape.game.CarVisual
import com.team.carescape.game.CarControl
import com.team.carescape.game.StoryLockDefinition
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Pure UI contract; actual SDK and console observations live in PleosVehicleConsoleTest. */
class CarSequenceInputTest {
    @get:Rule val rule = createComposeRule()
    private val lock = StoryLockDefinition.CarSequence(listOf(CarControl.WINDOW), listOf(CarControl.WINDOW))

    @Test fun vehicleSourceHidesManualButtonsAndRequiresExplicitSubmit() {
        val answer = mutableStateOf<List<CarControl>>(emptyList())
        var submissions = 0
        rule.setContent {
            CarSequenceLock(lock, answer.value, true, true, {}, { submissions++ }, {},
                CarPuzzleInput.Vehicle(true, "실제 차량 입력"))
        }
        rule.onNodeWithTag("car-WINDOW").assertDoesNotExist()
        rule.onNodeWithTag("story-puzzle-submit").assertIsNotEnabled()
        rule.runOnIdle { answer.value = listOf(CarControl.WINDOW) }
        rule.onNodeWithTag("car-sequence").assertTextContains(CarControl.WINDOW.short)
        rule.runOnIdle { assertEquals(0, submissions) }
        rule.onNodeWithTag("story-puzzle-submit").performClick()
        rule.runOnIdle { assertEquals(1, submissions) }
    }

    @Test fun unavailableVehicleSourceBlocksSubmissionEvenWithAnOldDraft() {
        var submissions = 0
        rule.setContent {
            CarSequenceLock(lock, listOf(CarControl.WINDOW), true, true, {}, { submissions++ }, {},
                CarPuzzleInput.Vehicle(false, "권한 필요"))
        }
        rule.onNodeWithTag("car-WINDOW").assertDoesNotExist()
        rule.onNodeWithTag("story-puzzle-submit").assertIsNotEnabled()
        rule.runOnIdle { assertEquals(0, submissions) }
    }

    @Test fun mockButtonsStillCollectAnAnswerWithoutSubmittingIt() {
        val answer = mutableStateOf<List<CarControl>>(emptyList())
        var submissions = 0
        rule.setContent {
            CarSequenceLock(lock, answer.value, true, true, { answer.value = it }, { submissions++ }, {})
        }
        rule.onNodeWithTag("car-WINDOW").performClick()
        rule.runOnIdle {
            assertEquals(listOf(CarControl.WINDOW), answer.value)
            assertEquals(0, submissions)
        }
        rule.onNodeWithTag("car-reset").performClick()
        rule.runOnIdle { assertEquals(emptyList<CarControl>(), answer.value) }
    }
    @Test fun sluiceTurnSignalsShowClosingAndOpeningWithoutAutoSubmit() {
        val right = CarControl.TURN_RIGHT
        val left = CarControl.TURN_LEFT
        val answer = mutableStateOf<List<CarControl>>(emptyList())
        val gates = StoryLockDefinition.CarSequence(listOf(right, left), listOf(right, left), CarVisual.Sluice(2))
        var submissions = 0
        rule.setContent {
            CarSequenceLock(gates, answer.value, true, false, { answer.value = it }, { submissions++ }, {})
        }
        rule.onNodeWithTag("car-TURN_RIGHT").assertTextContains("오른쪽 깜빡이 · 수문 닫기").performClick()
        rule.onNodeWithContentDescription("1번 수문 닫힘").assertExists()
        rule.onNodeWithTag("car-TURN_LEFT").assertTextContains("왼쪽 깜빡이 · 수문 열기").performClick()
        rule.onNodeWithContentDescription("2번 수문 열림").assertExists()
        rule.runOnIdle { assertEquals(listOf(right, left), answer.value); assertEquals(0, submissions) }
        rule.onNodeWithTag("story-puzzle-submit").performClick()
        rule.runOnIdle { assertEquals(1, submissions) }
    }

}
