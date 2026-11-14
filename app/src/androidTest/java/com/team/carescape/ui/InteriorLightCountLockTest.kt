package com.team.carescape.ui

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHasNoClickAction
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

/** Plain UI contracts: the current raw light count fills five beacons from the left. */
class InteriorLightCountLockTest {
    @get:Rule val rule = createComposeRule()

    @Test fun mockTogglesOnlyChangeStateUntilExplicitSubmission() {
        val lights = mutableStateOf<List<Boolean?>>(List(4) { false })
        val toggledIndices = mutableListOf<Int>()
        var submissions = 0
        rule.setContent {
            InteriorLightCountLock(
                lights = lights.value,
                enabled = true,
                sdkInput = false,
                message = "",
                onToggle = { index ->
                    toggledIndices += index
                    lights.value = lights.value.mapIndexed { i, value -> if (i == index) value != true else value }
                },
                onSubmit = { submissions++ },
                modifier = Modifier.width(360.dp),
            )
        }
        rule.onNodeWithText("MOCK 시연 · 꺼진 봉화 +1 / 켜진 봉화 −1").assertExists()
        listOf("앞\n왼쪽", "앞\n오른쪽", "뒤\n왼쪽", "뒤\n오른쪽").forEach { seat ->
            rule.onNodeWithText(seat).assertDoesNotExist()
        }
        assertRenderedBeacons(1)
        rule.onNodeWithTag("beacon-base")
            .assertHasNoClickAction()
            .performTouchInput { click() }
        rule.runOnIdle {
            assertEquals("The fixed beacon cannot change the raw light count", List(4) { false }, lights.value)
            assertEquals(emptyList<Int>(), toggledIndices)
            assertEquals(0, submissions)
        }

        // The far-right unlit beacon adds one at the left edge, even when tapped repeatedly.
        rule.onNodeWithTag("interior-light-3").performTouchInput { click() }
        assertRenderedBeacons(2)
        rule.onNodeWithTag("interior-light-3").performTouchInput { click() }
        assertRenderedBeacons(3)
        rule.runOnIdle {
            assertEquals(listOf(true, true, false, false), lights.value)
            assertEquals(listOf(0, 1), toggledIndices)
        }

        // Any lit, nonfixed beacon removes the last raw ON, not its displayed index.
        rule.onNodeWithTag("interior-light-0").performTouchInput { click() }
        assertRenderedBeacons(2)
        rule.runOnIdle { assertEquals(listOf(true, false, false, false), lights.value) }
        repeat(3) { step ->
            rule.onNodeWithTag("interior-light-3").performTouchInput { click() }
            assertRenderedBeacons(step + 3)
        }
        rule.runOnIdle {
            assertEquals(List(4) { true }, lights.value)
            assertEquals(0, submissions)
        }
        rule.onNodeWithTag("interior-light-0").performTouchInput { click() }
        assertRenderedBeacons(4)
        rule.runOnIdle {
            assertEquals(listOf(true, true, true, false), lights.value)
            assertEquals(listOf(0, 1, 1, 1, 2, 3, 3), toggledIndices)
            assertEquals(0, submissions)
        }
        rule.onNodeWithTag("story-puzzle-submit").assertIsEnabled().performTouchInput { click() }
        rule.runOnIdle { assertEquals(1, submissions) }
    }

    @Test fun sdkStateCannotBeToggledAndAnyUnknownStateBlocksSubmission() {
        val lights = mutableStateOf<List<Boolean?>>(listOf(false, false, false, true))
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
        // Rear-right alone must light beacon 2, not the beacon over its former seat position.
        assertRenderedBeacons(2)
        rule.onNodeWithTag("beacon-base").assertHasNoClickAction().performTouchInput { click() }
        repeat(4) { index ->
            rule.onNodeWithTag("interior-light-$index").assertHasNoClickAction().performTouchInput { click() }
        }
        rule.runOnIdle { assertEquals(0, toggles); assertEquals(0, submissions) }
        rule.onNodeWithTag("story-puzzle-submit").assertIsEnabled().performTouchInput { click() }
        rule.runOnIdle { assertEquals(1, submissions) }

        // Different raw seat combinations with the same count have the same visible fill.
        rule.runOnIdle { lights.value = listOf(true, false, false, false) }
        assertRenderedBeacons(2)
        rule.runOnIdle { lights.value = listOf(false, true, false, true) }
        assertRenderedBeacons(3)
        rule.runOnIdle { lights.value = listOf(true, false, true, false) }
        assertRenderedBeacons(3)
        rule.runOnIdle { lights.value = List(4) { true } }
        assertRenderedBeacons(5)
        // A known snapshot remains submittable, including a count the domain may reject.
        rule.onNodeWithTag("story-puzzle-submit").assertIsEnabled().performTouchInput { click() }
        rule.runOnIdle { assertEquals(2, submissions) }

        val incompleteSnapshots = listOf(
            listOf(true, true, null, true),
            emptyList(),
            listOf(true, true, true),
            List(5) { true },
        )
        incompleteSnapshots.forEach { snapshot ->
            rule.runOnIdle { lights.value = snapshot }
            // No partial count is rendered as a known number of beacons.
            assertRenderedBeacons(null)
            rule.onNodeWithText("켜진 봉화: 5 / 5").assertDoesNotExist()
            rule.onNodeWithTag("story-puzzle-submit").assertIsNotEnabled().performTouchInput { click() }
            rule.runOnIdle { assertEquals(0, toggles); assertEquals(2, submissions) }
        }
    }

    @Test fun actionsCapturedBeforeSafetyOrInputModeChangesUseCurrentGuardsAndCallbacks() {
        val enabled = mutableStateOf(true)
        val sdkInput = mutableStateOf(false)
        val lights = mutableStateOf<List<Boolean?>>(listOf(false, false, false, true))
        val currentCallbacks = mutableStateOf(false)
        val oldToggles = mutableListOf<Int>()
        val newToggles = mutableListOf<Int>()
        var oldSubmissions = 0
        var newSubmissions = 0
        rule.setContent {
            val callbacksChanged = currentCallbacks.value
            InteriorLightCountLock(
                lights = lights.value,
                enabled = enabled.value,
                sdkInput = sdkInput.value,
                message = "",
                onToggle = { index ->
                    if (callbacksChanged) newToggles += index else oldToggles += index
                    lights.value = lights.value.mapIndexed { i, value -> if (i == index) value != true else value }
                },
                onSubmit = { if (callbacksChanged) newSubmissions++ else oldSubmissions++ },
                modifier = Modifier.width(360.dp),
            )
        }
        assertRenderedBeacons(2)
        val oldUnlitAction = rule.onNodeWithTag("interior-light-3")
            .fetchSemanticsNode().config[SemanticsActions.OnClick].action!!
        val oldLitAction = rule.onNodeWithTag("interior-light-0")
            .fetchSemanticsNode().config[SemanticsActions.OnClick].action!!
        val oldSubmitAction = rule.onNodeWithTag("story-puzzle-submit")
            .fetchSemanticsNode().config[SemanticsActions.OnClick].action!!
        rule.runOnIdle { enabled.value = false }
        rule.onNodeWithTag("story-puzzle-submit").assertIsNotEnabled()
        rule.runOnIdle {
            oldUnlitAction()
            oldLitAction()
            oldSubmitAction()
            assertEquals(emptyList<Int>(), oldToggles)
            assertEquals(0, oldSubmissions)
            enabled.value = true
            sdkInput.value = true
            currentCallbacks.value = true
        }
        rule.onNodeWithTag("story-puzzle-submit").assertIsEnabled()
        rule.runOnIdle {
            oldUnlitAction()
            oldLitAction()
            oldSubmitAction()
            assertEquals(emptyList<Int>(), oldToggles)
            assertEquals(emptyList<Int>(), newToggles)
            assertEquals(0, oldSubmissions)
            assertEquals(1, newSubmissions)
            sdkInput.value = false
            lights.value = listOf(true, false, true, false)
        }
        assertRenderedBeacons(3)
        // Retained actions must select a raw index from the latest snapshot on every click.
        rule.runOnIdle { oldUnlitAction() }
        assertRenderedBeacons(4)
        rule.runOnIdle { assertEquals(listOf(1), newToggles); oldUnlitAction() }
        assertRenderedBeacons(5)
        rule.runOnIdle { oldLitAction() }
        assertRenderedBeacons(4)
        rule.runOnIdle { oldLitAction() }
        assertRenderedBeacons(3)
        rule.runOnIdle {
            assertEquals(listOf(1, 3, 3, 2), newToggles)
            assertEquals(listOf(true, true, false, false), lights.value)
            assertEquals(emptyList<Int>(), oldToggles)
            assertEquals(0, oldSubmissions)
            assertEquals(1, newSubmissions)
            lights.value = listOf(true, null, false, false)
        }
        assertRenderedBeacons(null)
        rule.onNodeWithTag("story-puzzle-submit").assertIsNotEnabled()
        rule.runOnIdle {
            oldUnlitAction()
            oldLitAction()
            oldSubmitAction()
            assertEquals(listOf(1, 3, 3, 2), newToggles)
            assertEquals(listOf(true, null, false, false), lights.value)
            assertEquals(emptyList<Int>(), oldToggles)
            assertEquals(0, oldSubmissions)
            assertEquals(1, newSubmissions)
        }
    }

    /** The fixed first beacon survives unknown inputs; the other four form one count display. */
    private fun assertRenderedBeacons(litCount: Int?) {
        rule.onNodeWithTag("beacon-base")
            .assertContentDescriptionEquals("1번 봉화, 항상 켜짐, 고정")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "항상 켜짐 · 고정"))
        repeat(4) { index ->
            val state = when {
                litCount == null -> "미확인"
                index < litCount - 1 -> "켜짐"
                else -> "꺼짐"
            }
            rule.onNodeWithTag("interior-light-$index")
                .assertContentDescriptionEquals("${index + 2}번 봉화")
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, state))
        }
        rule.onNodeWithText(litCount?.let { "켜진 봉화: $it / 5" } ?: "봉화 수 확인 중").assertExists()
    }
}
