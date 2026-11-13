package com.example.roadescape.ui

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.roadescape.MainActivity
import com.example.roadescape.RoadEscapeApplication
import com.example.roadescape.model.GameSession
import com.example.roadescape.model.ParkingState
import com.example.roadescape.model.TourMode
import com.example.roadescape.platform.SimulatedParkingProvider
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith

/** These tests change only local layout preferences; the existing game is never reset. */
@RunWith(AndroidJUnit4::class)
class LobbyLayoutEditorTest {
    val rule = createAndroidComposeRule<MainActivity>()

    private lateinit var app: RoadEscapeApplication
    private lateinit var preferences: SharedPreferences
    private lateinit var viewModel: GameViewModel
    private var originalPreferences: Map<String, Any?> = emptyMap()
    private var originalPreferenceFilePresent = false
    private var originalGame: GameSession? = null

    private val isolateLayoutPreferences = object : ExternalResource() {
        override fun before() {
            app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as RoadEscapeApplication
            originalPreferenceFilePresent = File(app.dataDir, "shared_prefs/$PREFERENCES.xml").isFile
            preferences = app.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            originalPreferences = preferenceSnapshot()
            originalGame = savedGame()
            check(preferences.edit().clear().commit())
        }

        override fun after() {
            try {
                assertEquals("Layout editing must not change the game snapshot", originalGame, savedGame())
            } finally {
                val editor = preferences.edit().clear()
                originalPreferences.forEach { (key, value) ->
                    when (value) {
                        is String -> editor.putString(key, value)
                        is Boolean -> editor.putBoolean(key, value)
                        is Int -> editor.putInt(key, value)
                        is Long -> editor.putLong(key, value)
                        is Float -> editor.putFloat(key, value)
                        is Set<*> -> editor.putStringSet(key, value.map { it as String }.toSet())
                        null -> editor.remove(key)
                        else -> error("Unsupported preference type: ${value.javaClass.name}")
                    }
                }
                check(editor.commit())
                assertEquals(originalPreferences, preferenceSnapshot())
                // Do not leave a new empty editor preference file when none existed before.
                if (!originalPreferenceFilePresent) check(app.deleteSharedPreferences(PREFERENCES))
            }
        }
    }

    @get:Rule
    val rules: TestRule = RuleChain.outerRule(isolateLayoutPreferences).around(rule)

    @Test
    fun draggedLogoPersistsAcrossRecreationWithoutChangingGame() {
        enterParkedLobby()
        openEditor()
        val before = bounds("lobby-block-LOGO")
        val handle = bounds("lobby-edit-handle-LOGO")
        assertSamePosition(before, handle)
        assertEquals("The drag shield must cover the full logo width", before.width, handle.width, 2f)
        assertEquals("The drag shield must cover the full logo height", before.height, handle.height, 2f)

        rule.onNodeWithTag("lobby-edit-handle-LOGO", useUnmergedTree = true).performTouchInput {
            swipe(center, center + Offset(72f, 40f), durationMillis = 350)
        }
        rule.waitForIdle()
        val dragged = bounds("lobby-block-LOGO")
        assertTrue("Dragging must move the measured block", moved(before, dragged))
        rule.onNodeWithTag("lobby-layout-status").assertTextContains("미저장 변경", substring = true)
        assertTrue(preferenceSnapshot().isEmpty())

        saveAndAwaitClosed()
        val persisted = preferenceSnapshot()
        val savedBounds = bounds("lobby-block-LOGO")
        assertTrue(persisted[SETTINGS_KEY] is String)
        assertEquals(originalGame, savedGame())

        rule.activityRule.scenario.recreate()
        awaitTag("lobby-block-LOGO")
        simulateParking(ParkingState.PARKED)
        awaitReadable()
        assertSamePosition(savedBounds, bounds("lobby-block-LOGO"))
        assertEquals(persisted, preferenceSnapshot())
        assertEquals(originalGame, savedGame())
    }

    @Test
    fun editingActionsInterceptsTouchesAndSavedButtonsUseMovedHitboxes() {
        enterParkedLobby()
        val beforeButton = bounds("open-cases")
        openEditor()
        click("lobby-layout-panel-toggle")

        // Accessibility actions must obey the editing gate as well as pointer input.
        rule.onNodeWithTag("open-cases").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("screen-lobby").assertExists()
        rule.onNodeWithTag("screen-case-board").assertDoesNotExist()

        // Also verify that the visible input shield intercepts a real touch.
        rule.onNodeWithTag("open-cases").performTouchInput { click(center) }
        rule.waitForIdle()
        rule.onNodeWithTag("screen-lobby").assertExists()
        rule.onNodeWithTag("screen-case-board").assertDoesNotExist()
        assertTrue(preferenceSnapshot().isEmpty())

        click("lobby-layout-panel-toggle")
        selectBlock("ACTIONS")
        click("lobby-layout-step")
        rule.onNodeWithTag("lobby-layout-step").assertTextEquals("이동 단위: 10 dp")
        repeat(6) { click("lobby-layout-nudge-right") }
        repeat(10) { click("lobby-layout-nudge-down") }
        saveAndAwaitClosed()

        val movedButton = bounds("open-cases")
        assertTrue("The native button bounds must move with ACTIONS", moved(beforeButton, movedButton))
        assertFalse("The new touch must be outside the button's old bounds", beforeButton.contains(movedButton.center))
        rule.onNodeWithTag("open-cases").performTouchInput { click(center) }
        awaitTag("screen-case-board")
        assertEquals(originalGame, savedGame())
    }

    @Test
    fun cancelResetAndSafetyLossDiscardDraftWithoutWriting() {
        enterParkedLobby()
        openEditor()
        selectBlock("LOGO")
        click("lobby-layout-step")
        click("lobby-layout-nudge-down")
        saveAndAwaitClosed()
        val persisted = preferenceSnapshot()
        val savedBounds = bounds("lobby-block-LOGO")

        openEditor()
        selectBlock("LOGO")
        click("lobby-layout-nudge-right")
        click("lobby-layout-cancel")
        awaitEditorClosed()
        assertSamePosition(savedBounds, bounds("lobby-block-LOGO"))
        assertEquals(persisted, preferenceSnapshot())

        openEditor()
        click("lobby-layout-reset")
        rule.onNodeWithTag("lobby-layout-status").assertTextContains("기본값 적용", substring = true)
        assertEquals("Reset is only a draft until Save", persisted, preferenceSnapshot())
        click("lobby-layout-cancel")
        awaitEditorClosed()
        assertSamePosition(savedBounds, bounds("lobby-block-LOGO"))

        openEditor()
        selectBlock("LOGO")
        click("lobby-layout-nudge-right")
        simulateParking(ParkingState.DRIVING)
        awaitEditorClosed()
        assertEquals(persisted, preferenceSnapshot())
        simulateParking(ParkingState.PARKED)
        awaitReadable()
        assertSamePosition(savedBounds, bounds("lobby-block-LOGO"))

        openEditor()
        selectBlock("LOGO")
        click("lobby-layout-nudge-down")
        rule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        rule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        awaitEditorClosed()
        assertEquals(persisted, preferenceSnapshot())
        simulateParking(ParkingState.PARKED)
        awaitReadable()
        assertSamePosition(savedBounds, bounds("lobby-block-LOGO"))
        assertEquals(originalGame, savedGame())
    }

    private fun enterParkedLobby() {
        // Tests must never convert an existing LIVE session into a mock game.
        assumeTrue("Run with the existing MOCK game; do not reset user progress", originalGame?.sourceMode != TourMode.LIVE)
        rule.waitUntil(30_000) { exists("screen-lobby") || exists("nav-home") }
        if (!exists("screen-lobby")) click("nav-home")
        rule.runOnIdle { viewModel = ViewModelProvider(rule.activity)[GameViewModel::class.java] }
        rule.waitUntil(30_000) { !viewModel.state.value.loading }
        simulateParking(ParkingState.PARKED)
        awaitReadable()
        awaitTag("lobby-layout-edit")
    }

    private fun simulateParking(parking: ParkingState) {
        rule.runOnUiThread { (app.container.sources.parking as SimulatedParkingProvider).simulate(parking) }
        rule.waitForIdle()
    }

    private fun awaitReadable() {
        rule.waitUntil(30_000) { viewModel.state.value.canReadContent }
    }

    private fun openEditor() {
        click("lobby-layout-edit")
        awaitTag("lobby-layout-save")
    }

    private fun selectBlock(id: String) {
        click("lobby-layout-select")
        click("lobby-layout-select-$id")
    }

    private fun saveAndAwaitClosed() {
        click("lobby-layout-save")
        rule.waitUntil(30_000) {
            preferences.contains(SETTINGS_KEY) && exists("screen-lobby") && !exists("lobby-layout-save")
        }
        rule.waitForIdle()
    }

    private fun awaitEditorClosed() {
        rule.waitUntil(30_000) {
            exists("screen-lobby") && !exists("lobby-layout-save") && !exists("lobby-edit-handle-LOGO")
        }
        rule.waitForIdle()
    }

    private fun bounds(tag: String): Rect =
        rule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    private fun moved(before: Rect, after: Rect): Boolean =
        kotlin.math.abs(before.left - after.left) > 2f || kotlin.math.abs(before.top - after.top) > 2f

    private fun assertSamePosition(expected: Rect, actual: Rect) {
        assertEquals("Measured left", expected.left, actual.left, 2f)
        assertEquals("Measured top", expected.top, actual.top, 2f)
    }

    private fun savedGame(): GameSession? = runBlocking { app.container.gameRepository.load() }

    private fun preferenceSnapshot(): Map<String, Any?> = preferences.all.mapValues { (_, value) ->
        if (value is Set<*>) value.map { it as String }.toSet() else value
    }

    private fun click(tag: String) {
        rule.waitUntil(30_000) {
            runCatching { rule.onNodeWithTag(tag).assertIsEnabled(); true }.getOrDefault(false)
        }
        val node = rule.onNodeWithTag(tag)
        runCatching { node.performScrollTo() }
        node.performClick()
        rule.waitForIdle()
    }

    private fun awaitTag(tag: String) {
        rule.waitUntil(30_000) { exists(tag) }
    }

    private fun exists(tag: String): Boolean = try {
        rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    } catch (error: IllegalStateException) {
        if (error.message?.startsWith("No compose hierarchies found") == true) false else throw error
    }

    private companion object {
        const val PREFERENCES = "roadescape-lobby-layout"
        const val SETTINGS_KEY = "settings_json"
    }
}
