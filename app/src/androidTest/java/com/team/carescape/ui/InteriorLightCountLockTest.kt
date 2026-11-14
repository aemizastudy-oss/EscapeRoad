package com.team.carescape.ui

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Plain UI contracts; no vehicle service, ViewModel, or real vehicle controls. */
class InteriorLightCountLockTest {
    @get:Rule val rule = createComposeRule()

    @Test fun mockTogglesOnlyChangeStateUntilExplicitSubmission() {
        val lights = mutableStateOf<List<Boolean?>>(List(4) { false })
        var submissions = 0
        rule.setContent {
            InteriorLightCountLock(
                lights = lights.value,
                enabled = true,
                sdkInput = false,
                message = "",
                onToggle = { index ->
                    lights.value = lights.value.mapIndexed { i, value -> if (i == index) value != true else value }
                },
                onSubmit = { submissions++ },
                modifier = Modifier.width(360.dp),
            )
        }
        rule.onNodeWithText("MOCK 시연 · 조명 카드를 눌러 바꿔 보세요").assertExists()
        repeat(4) { index -> rule.onNodeWithTag("interior-light-$index").performTouchInput { click() } }
        rule.onNodeWithText("켜진 실내 조명: 4개").assertExists()
        rule.runOnIdle {
            assertEquals(List(4) { true }, lights.value)
            assertEquals(0, submissions)
        }
        rule.onNodeWithTag("story-puzzle-submit").assertIsEnabled().performTouchInput { click() }
        rule.runOnIdle { assertEquals(1, submissions) }
    }

    @Test fun sdkStateCannotBeToggledAndAnyUnknownStateBlocksSubmission() {
        val lights = mutableStateOf<List<Boolean?>>(List(4) { true })
        var toggles = 0
        var submissions = 0
        rule.setContent {
            InteriorLightCountLock(
                lights = lights.value,
                enabled = true,
                sdkInput = true,
                message = "실제 차량 입력",
                onToggle = { toggles++ },
                onSubmit = { submissions++ },
                modifier = Modifier.width(360.dp),
            )
        }
        repeat(4) { index -> rule.onNodeWithTag("interior-light-$index").performTouchInput { click() } }
        rule.runOnIdle { assertEquals(0, toggles); assertEquals(0, submissions) }
        // A current initial snapshot of four lit lights is enough; no artificial off/on history.
        rule.onNodeWithTag("story-puzzle-submit").assertIsEnabled().performTouchInput { click() }
        rule.runOnIdle {
            assertEquals(1, submissions)
            lights.value = listOf(true, true, null, true)
        }
        rule.onNodeWithText("미확인").assertExists()
        rule.onNodeWithText("켜진 실내 조명 수 확인 중").assertExists()
        rule.onNodeWithText("켜진 실내 조명: 4개").assertDoesNotExist()
        rule.onNodeWithTag("story-puzzle-submit").assertIsNotEnabled().performTouchInput { click() }
        rule.runOnIdle {
            assertEquals(1, submissions)
            lights.value = emptyList()
        }
        rule.onNodeWithTag("story-puzzle-submit").assertIsNotEnabled()
    }

    @Test fun actionsCapturedBeforeSafetyOrInputModeChangesUseCurrentGuardsAndCallbacks() {
        val enabled = mutableStateOf(true)
        val sdkInput = mutableStateOf(false)
        val currentCallbacks = mutableStateOf(false)
        var oldToggles = 0
        var newToggles = 0
        var oldSubmissions = 0
        var newSubmissions = 0
        rule.setContent {
            val callbacksChanged = currentCallbacks.value
            InteriorLightCountLock(
                lights = List(4) { true },
                enabled = enabled.value,
                sdkInput = sdkInput.value,
                message = "",
                onToggle = { if (callbacksChanged) newToggles++ else oldToggles++ },
                onSubmit = { if (callbacksChanged) newSubmissions++ else oldSubmissions++ },
                modifier = Modifier.width(360.dp),
            )
        }
        val oldToggleAction = rule.onNodeWithTag("interior-light-0")
            .fetchSemanticsNode().config[SemanticsActions.OnClick].action!!
        val oldSubmitAction = rule.onNodeWithTag("story-puzzle-submit")
            .fetchSemanticsNode().config[SemanticsActions.OnClick].action!!
        rule.runOnIdle { enabled.value = false }
        rule.onNodeWithTag("story-puzzle-submit").assertIsNotEnabled()
        rule.runOnIdle {
            oldToggleAction()
            oldSubmitAction()
            assertEquals(0, oldToggles)
            assertEquals(0, oldSubmissions)
            enabled.value = true
            sdkInput.value = true
            currentCallbacks.value = true
        }
        rule.onNodeWithTag("story-puzzle-submit").assertIsEnabled()
        rule.runOnIdle {
            oldToggleAction()
            oldSubmitAction()
            assertEquals(0, oldToggles)
            assertEquals(0, newToggles)
            assertEquals(0, oldSubmissions)
            assertEquals(1, newSubmissions)
        }
    }
}
