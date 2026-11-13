package com.team.carescape.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.team.carescape.MainActivity
import com.team.carescape.game.DialColor
import com.team.carescape.model.TourMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The workshop is an isolated input exercise, never a story progression shortcut. */
@RunWith(AndroidJUnit4::class)
class LockWorkshopLifecycleTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun lobbyWorkshopSubmitsOnlyExplicitAnswersAndRejectsUnsafeCallbacks() {
        lateinit var vm: GameViewModel
        rule.runOnUiThread { vm = ViewModelProvider(rule.activity)[GameViewModel::class.java] }
        rule.waitUntil(10_000) { !vm.loading }
        rule.runOnUiThread {
            vm.goLobby()
            vm.chooseMode(TourMode.MOCK)
        }
        rule.waitUntil(10_000) { !vm.loading && vm.mode == TourMode.MOCK }
        rule.runOnUiThread { vm.simSetParked(true) }
        rule.waitUntil(5_000) { vm.parked }
        val storyBefore = rule.runOnIdle { vm.story }
        rule.onNodeWithText("자물쇠 체험").performClick()
        rule.onNodeWithTag("lock-workshop").assertIsDisplayed()
        rule.onNodeWithContentDescription("색상 다이얼 1").performClick()
        rule.runOnIdle {
            assertEquals(DialColor.YELLOW, vm.lockWorkshop.colors.first())
            assertNull(vm.lockWorkshop.submitted)
        }
        rule.onNodeWithText("제출").performClick()
        rule.runOnIdle { assertEquals("노랑 · 빨강 · 빨강 · 빨강", vm.lockWorkshop.submitted) }
        val submitted = rule.runOnIdle { vm.lockWorkshop }
        rule.runOnUiThread {
            vm.simSetParked(false)
            vm.changeWorkshopColors(List(4) { DialColor.BLUE })
            vm.submitWorkshopColors(List(4) { DialColor.BLUE })
        }
        rule.onNodeWithTag("sdk-safety-paused").assertIsDisplayed()
        rule.onNodeWithTag("lock-workshop").assertDoesNotExist()
        rule.runOnIdle {
            assertEquals(submitted, vm.lockWorkshop)
            assertEquals(storyBefore, vm.story)
        }
        rule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        rule.runOnUiThread {
            vm.submitWorkshopColors(submitted.colors)
            assertEquals(submitted, vm.lockWorkshop)
        }
        rule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        rule.onNodeWithTag("sdk-safety-paused").assertIsDisplayed()
        rule.runOnUiThread {
            vm.simSetParked(true)
            vm.goLobby()
            vm.submitWorkshopColors(submitted.colors)
            assertEquals(submitted, vm.lockWorkshop)
            assertEquals(storyBefore, vm.story)
        }
    }
}
