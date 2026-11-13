package com.team.carescape.ui

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.team.carescape.MainActivity
import com.team.carescape.data.StoredTrip
import com.team.carescape.data.TripProgressStore
import com.team.carescape.game.StoryPhase
import com.team.carescape.game.TourGate
import com.team.carescape.model.TourMode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith

/** MOCK observations exercise the actual story UI; no LIVE trip is reset or fabricated. */
@RunWith(AndroidJUnit4::class)
class TourLifecycleTest {
    val rule = createAndroidComposeRule<MainActivity>()
    private lateinit var vm: GameViewModel
    private lateinit var store: TripProgressStore
    private var originalMock: StoredTrip? = null
    private var originalLive: StoredTrip? = null

    private val isolateMockTrip = object : ExternalResource() {
        override fun before() {
            store = TripProgressStore(InstrumentationRegistry.getInstrumentation().targetContext)
            val prior = runBlocking { store.load() }
            originalMock = prior.trips.firstOrNull { it.mode == TourMode.MOCK }
            originalLive = prior.trips.firstOrNull { it.mode == TourMode.LIVE }
            runBlocking { store.remove(TourMode.MOCK) }
        }

        override fun after() {
            val liveAfterTest = runBlocking { store.load().trips.firstOrNull { it.mode == TourMode.LIVE } }
            try {
                assertEquals("MOCK tests must preserve the LIVE record", originalLive, liveAfterTest)
            } finally {
                runBlocking {
                    val prior = originalMock
                    if (prior == null) store.remove(TourMode.MOCK) else store.save(prior)
                }
                val restored = runBlocking { store.load() }
                assertEquals(originalMock, restored.trips.firstOrNull { it.mode == TourMode.MOCK })
                assertEquals(originalLive, restored.trips.firstOrNull { it.mode == TourMode.LIVE })
            }
        }
    }

    @get:Rule
    val rules: TestRule = RuleChain.outerRule(isolateMockTrip).around(rule)

    @Test
    fun tourismStartSurvivesRecreationAndRequiresFreshLocationBeforeExplicitTimeBypass() {
        prepareExploring()
        val started = storedMock()
        val firstStart = started.tourRecords.single().startedAt
        awaitState { it.tourGate == TourGate.TOUR_TIME_REMAINING }
        rule.onNodeWithTag("tour-return").assertIsNotEnabled()
        assertTrue(vm.tourRemainingMillis!! > 0L)

        rule.activityRule.scenario.recreate()
        awaitTag("sdk-safety-paused")
        assertFalse(vm.parked)
        assertEquals(started, storedMock())
        openDevPanel()
        click("sdk-sim-parked")
        awaitState { it.parked && it.tourGate == TourGate.LOCATION_MISSING }
        rule.onNodeWithTag("tour-return").assertIsNotEnabled()

        click("sdk-sim-location")
        awaitState { it.tourGate == TourGate.TOUR_TIME_REMAINING }
        click("sdk-dev-toggle")
        click("sdk-demo-elapse")
        awaitState { !it.saving && it.tourGate == TourGate.READY }
        click("tour-return")
        awaitState { !it.saving && it.story?.phase == StoryPhase.REVEALED }

        val revealed = storedMock()
        assertEquals(firstStart, revealed.tourRecords.single().startedAt)
        assertTrue(revealed.tourRecords.single().debugTimeBypass)
        assertEquals(StoryPhase.REVEALED, revealed.phase)
        assertEquals(started.sessionId, revealed.sessionId)
        assertTrue(revealed.clues.isNotEmpty())
    }

    @Test
    fun drivingAndForegroundLossHideStoryAndRejectProgressWithoutChangingTrip() {
        prepareExploring()
        click("sdk-demo-elapse")
        awaitState { !it.saving && it.tourGate == TourGate.READY }
        val before = storedMock()

        rule.runOnUiThread {
            vm.simSetParked(false)
            vm.returnToCar()
            vm.leaveCar()
            vm.storyNext()
            assertFalse(vm.speakNarration("안전 조건 시험"))
        }
        awaitTag("sdk-safety-paused")
        rule.onNodeWithTag("tour-return").assertDoesNotExist()
        rule.onNodeWithTag("tour-remaining").assertDoesNotExist()
        assertEquals(before, storedMock())

        rule.runOnUiThread { vm.simSetParked(true); vm.demoArrive() }
        awaitState { it.parked && it.tourGate == TourGate.READY }
        rule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            assertFalse(vm.foreground)
            vm.returnToCar()
            vm.storyNext()
            assertFalse(vm.speakNarration("백그라운드 시험"))
        }
        assertEquals(before, storedMock())
        rule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        awaitTag("sdk-safety-paused")
        assertFalse(vm.parked)
        assertEquals(StoryPhase.EXPLORING, vm.story?.phase)

        openDevPanel()
        click("sdk-sim-parked")
        awaitState { it.parked && it.tourGate == TourGate.LOCATION_MISSING }
        rule.onNodeWithTag("tour-return").assertIsNotEnabled()
        assertEquals(before, storedMock())
    }

    @Test
    fun modeSelectionKeepsLiveRecordsSeparateAndRequiresNewMockConfirmation() {
        prepareExploring()
        val mockBefore = storedMock()
        rule.runOnUiThread {
            vm.goLobby()
            vm.chooseMode(TourMode.LIVE)
        }
        awaitState { it.mode == TourMode.LIVE && !it.loading && !it.saving }
        assertEquals(originalLive, runBlocking { store.load().trips.firstOrNull { it.mode == TourMode.LIVE } })

        // SDK callbacks run on the same main thread, so compare immediately around ignored inputs.
        rule.runOnUiThread {
            val liveVehicle = vm.vehicle
            vm.simSetParked(true)
            vm.demoArrive()
            assertEquals("LIVE must ignore explicit mock controls", liveVehicle, vm.vehicle)
            vm.goLobby()
            vm.chooseMode(TourMode.MOCK)
        }
        awaitState {
            it.mode == TourMode.MOCK && !it.loading && !it.saving && it.story?.phase == StoryPhase.EXPLORING
        }
        assertFalse("Returning to MOCK requires a new P confirmation", vm.parked)
        assertEquals(mockBefore, storedMock())

        openDevPanel()
        click("sdk-sim-parked")
        awaitState { it.parked && it.tourGate == TourGate.LOCATION_MISSING }
        rule.runOnUiThread { vm.resumeStory() }
        awaitTag("tour-return")
        rule.onNodeWithTag("tour-return").assertIsNotEnabled()
        click("sdk-sim-location")
        awaitState { it.tourStartGate == TourGate.READY }
        assertEquals(mockBefore, storedMock())
        assertEquals(originalLive, runBlocking { store.load().trips.firstOrNull { it.mode == TourMode.LIVE } })
    }

    private fun prepareExploring() {
        awaitTag("sdk-status-open")
        rule.runOnIdle { vm = ViewModelProvider(rule.activity)[GameViewModel::class.java] }
        awaitState { !it.loading && it.foreground }
        assertEquals(TourMode.MOCK, vm.mode)
        openDevPanel()
        click("sdk-sim-parked")
        awaitState { it.parked }
        click("sdk-dev-toggle")
        rule.runOnUiThread { vm.startStory() }
        awaitState { !it.saving && it.story?.phase == StoryPhase.EN_ROUTE && it.showMap }
        click("story-start-guidance")
        awaitState { !it.saving && !it.showMap }
        openDevPanel()
        click("sdk-sim-location")
        awaitState { !it.saving && it.story?.phase == StoryPhase.ARRIVED }
        click("sdk-dev-toggle")
        click("tour-start")
        awaitState { !it.saving && it.story?.phase == StoryPhase.EXPLORING }
        awaitTag("tour-return")
    }

    private fun openDevPanel() {
        if (!exists("sdk-sim-parked")) click("sdk-dev-toggle")
        awaitTag("sdk-sim-parked")
    }

    private fun storedMock(): StoredTrip = runBlocking {
        checkNotNull(store.load().trips.firstOrNull { it.mode == TourMode.MOCK })
    }

    private fun awaitState(predicate: (GameViewModel) -> Boolean) {
        rule.waitUntil(30_000) { predicate(vm) }
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
}
