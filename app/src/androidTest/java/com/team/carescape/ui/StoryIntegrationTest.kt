package com.team.carescape.ui

import java.io.File
import com.team.carescape.game.LockDirection
import androidx.compose.ui.test.swipe
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import com.team.carescape.game.CarControl
import com.team.carescape.game.DemoTarget
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.team.carescape.MainActivity
import com.team.carescape.data.StoredTrip
import com.team.carescape.data.TripProgressStore
import com.team.carescape.data.TripStoreState
import com.team.carescape.game.EndingKind
import com.team.carescape.game.StoryLockDefinition
import com.team.carescape.game.StoryCatalog
import com.team.carescape.game.StoryPhase
import com.team.carescape.game.StoryPuzzleInput
import com.team.carescape.game.TourCatalog
import com.team.carescape.game.TourGate
import com.team.carescape.game.TourPolicy
import com.team.carescape.model.TourMode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith

/** MOCK tourism with native answer submission. These tests do not establish LIVE SDK qualification. */
@RunWith(AndroidJUnit4::class)
class StoryIntegrationTest {
    val rule = createAndroidComposeRule<MainActivity>()
    private lateinit var vm: GameViewModel
    private lateinit var store: TripProgressStore
    private var originalMock: StoredTrip? = null
    private var originalLive: StoredTrip? = null
    private var originalMetadata: TripStoreState? = null

    /** Restore active MOCK data through DataStore; never replace a live DataStore's backing file. */
    private val isolateMockTrip = object : ExternalResource() {
        override fun before() {
            store = TripProgressStore(InstrumentationRegistry.getInstrumentation().targetContext)
            val before = runBlocking { store.load() }
            originalMock = before.trips.firstOrNull { it.mode == TourMode.MOCK }
            originalLive = before.trips.firstOrNull { it.mode == TourMode.LIVE }
            originalMetadata = before.copy(trips = emptyList())
            runBlocking { store.remove(TourMode.MOCK) }
        }

        override fun after() {
            try {
                val after = runBlocking { store.load() }
                assertEquals("MOCK fixture must preserve LIVE", originalLive, after.trips.firstOrNull { it.mode == TourMode.LIVE })
                assertEquals("Archives and schema metadata must remain unchanged", originalMetadata, after.copy(trips = emptyList()))
            } finally {
                runBlocking {
                    store.remove(TourMode.MOCK)
                    originalMock?.let { store.save(it) }
                }
                val restored = runBlocking { store.load() }
                assertEquals(originalMock, restored.trips.firstOrNull { it.mode == TourMode.MOCK })
                assertEquals(originalLive, restored.trips.firstOrNull { it.mode == TourMode.LIVE })
                assertEquals(originalMetadata, restored.copy(trips = emptyList()))
            }
        }
    }

    @get:Rule
    val rules: TestRule = RuleChain.outerRule(isolateMockTrip).around(rule)



    /** MOCK tourism only: real central swipes, explicit submit and durable chapter progress. */
    @Test
    fun reservoirHeightGridCentralSwipesSubmitSaveAndResumeCompletedChannel() {
        prepareStory()
        assertEquals(TourMode.MOCK, vm.mode)
        assertFalse("This test uses ordinary MOCK controls, never the hybrid SDK source", vm.mockVehiclePuzzleSdk)
        rule.runOnUiThread { vm.demoJump(DemoTarget.Scene(2)) }
        awaitState { !it.saving && it.story?.chapterIndex == 2 && it.story?.phase == StoryPhase.PUZZLE && it.tourGate == TourGate.READY }
        awaitTag("explore")
        clickTag("hotspot-x3_farmbook")
        awaitState { !it.saving && it.story?.puzzleProgressForCurrent?.found?.contains("x3_farmbook") == true }
        clickTag("hotspot-x3_sluice")
        clickTag("hotspot-open")
        awaitState { it.storyPuzzle?.id == "c3_channel" && !it.saving }
        assertTrue(vm.storyPuzzle!!.lock is StoryLockDefinition.Directions)
        awaitTag("water-height-grid")
        runCatching { rule.onNodeWithTag("water-height-grid").performScrollTo() }
        (1..25).forEach { height ->
            rule.onNodeWithContentDescription("높이 $height").assertIsDisplayed()
        }

        val expected = listOf(LockDirection.UP, LockDirection.UP, LockDirection.RIGHT, LockDirection.RIGHT,
            LockDirection.DOWN, LockDirection.DOWN, LockDirection.DOWN, LockDirection.LEFT,
            LockDirection.DOWN, LockDirection.LEFT, LockDirection.LEFT)
        assertEquals(expected, (vm.storyPuzzle!!.lock as StoryLockDefinition.Directions).answer)
        val beforeDraft = storedMock()
        fun swipeDirection(direction: LockDirection) {
            val pad = rule.onNodeWithContentDescription("방향 자물쇠 조작부")
            runCatching { pad.performScrollTo() }
            pad.assertIsDisplayed().performTouchInput {
                val end = when (direction) {
                    LockDirection.UP -> Offset(center.x, height * 0.05f)
                    LockDirection.DOWN -> Offset(center.x, height * 0.95f)
                    LockDirection.RIGHT -> Offset(width * 0.95f, center.y)
                    LockDirection.LEFT -> Offset(width * 0.05f, center.y)
                }
                swipe(start = center, end = end, durationMillis = 300)
            }
            rule.waitForIdle()
        }
        fun resetDirections() {
            val reset = rule.onNodeWithText("초기화")
            runCatching { reset.performScrollTo() }
            reset.assertIsEnabled().performClick()
            rule.runOnIdle { assertTrue(vm.storyPuzzleDraft.directions.isEmpty()) }
        }

        // Draft and reset are reversible UI operations; they must not write any result.
        expected.take(2).forEach(::swipeDirection)
        rule.runOnIdle { assertEquals(expected.take(2), vm.storyPuzzleDraft.directions) }
        assertEquals(beforeDraft, storedMock())
        resetDirections()
        assertEquals(beforeDraft, storedMock())

        // A complete but wrong route records exactly one attempt and unlocks no clue.
        val wrong = expected.dropLast(1) + LockDirection.RIGHT
        wrong.forEach(::swipeDirection)
        rule.runOnIdle { assertEquals(wrong, vm.storyPuzzleDraft.directions) }
        assertEquals(beforeDraft, storedMock())
        clickSubmit()
        val originalAttempts = beforeDraft.puzzleProgress.single { it.checkpointId == TourCatalog.checkpoints[2].id }.wrongAttempts
        awaitState { !it.saving && it.storyPuzzleProgress?.wrongAttempts == originalAttempts + 1 }
        val afterWrong = storedMock()
        assertFalse(vm.story!!.isStepDone("c3_channel"))
        assertEquals(beforeDraft.items, afterWrong.items)
        assertEquals(beforeDraft.clues, afterWrong.clues)
        assertEquals(beforeDraft.tourRecords, afterWrong.tourRecords)
        assertEquals(beforeDraft.puzzleProgress.single { it.checkpointId == TourCatalog.checkpoints[2].id }.solvedSteps,
            afterWrong.puzzleProgress.single { it.checkpointId == TourCatalog.checkpoints[2].id }.solvedSteps)
        resetDirections()

        expected.forEachIndexed { index, direction ->
            swipeDirection(direction)
            rule.runOnIdle { assertEquals(expected.take(index + 1), vm.storyPuzzleDraft.directions) }
            assertEquals("Swipes must not submit or persist puzzle results", afterWrong, storedMock())
        }
        runCatching { rule.onNodeWithTag("water-height-grid").performScrollTo() }
        (1..25).forEach { rule.onNodeWithContentDescription("높이 $it").assertIsDisplayed() }
        rule.onNodeWithContentDescription("방향 자물쇠 조작부").assertIsDisplayed()
        rule.waitForIdle()
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        if (bitmap != null) {
            try {
                val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "verification")
                check(directory.isDirectory || directory.mkdirs())
                val file = File(directory, "water-grid-before-submit.png")
                file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                Log.i("CarEscapeWaterGridTest", "MOCK_UI_SCREENSHOT:${file.absolutePath}")
            } finally { bitmap.recycle() }
        } else {
            Log.w("CarEscapeWaterGridTest", "MOCK_UI_SCREENSHOT_UNAVAILABLE")
        }
        assertEquals("Screenshot capture must not submit", afterWrong, storedMock())
        clickSubmit()
        awaitState { !it.saving && it.story?.isStepDone("c3_channel") == true }
        awaitTag("explore")
        rule.onNodeWithTag("hotspot-x3_diary").assertIsDisplayed()
        val submitted = storedMock()
        assertEquals(afterWrong.sessionId, submitted.sessionId)
        assertEquals(afterWrong.tourRecords, submitted.tourRecords)
        assertTrue(submitted.puzzleProgress.single { it.checkpointId == TourCatalog.checkpoints[2].id }.solvedSteps.contains("c3_channel"))
        clickTag("hotspot-x3_diary")
        awaitState { !it.saving && it.story?.puzzleProgressForCurrent?.found?.contains("x3_diary") == true }
        val completed = storedMock()

        rule.activityRule.scenario.recreate()
        rule.runOnIdle { vm = ViewModelProvider(rule.activity)[GameViewModel::class.java] }
        awaitState { !it.loading && it.foreground }
        assertEquals("Activity recreation must preserve the solved channel and acquired note", completed, storedMock())
        awaitTag("sdk-safety-paused")
        openDevPanel()
        clickTag("sdk-sim-parked")
        clickTag("sdk-sim-location")
        awaitState { it.parked && it.tourGate == TourGate.READY && !it.saving }
        closeDevPanel()
        awaitTag("explore")
        assertTrue(vm.story!!.isStepDone("c3_channel"))
        assertTrue(vm.story!!.puzzleProgressForCurrent!!.found.contains("x3_diary"))
        assertEquals(completed, storedMock())
        rule.onNodeWithTag("hotspot-x3_diary").assertIsDisplayed()
        Log.i("CarEscapeWaterGridTest", "MOCK_WATER_GRID_11_REAL_SWIPES_UI_SUBMIT_DATASTORE_RECREATE_PASS; LIVE_SDK_NOT_USED")
    }

    /** Explicit hybrid: MOCK tourism fixture, real SDK safety + controls, actual UI submit + DataStore. */
    @Test
    fun consoleSdkControlsReachHybridMockStoryUiSubmissionAndStoredProgress(): Unit = runBlocking {
        assumeTrue("Opt-in required: -e vehicleConsoleControls true",
            InstrumentationRegistry.getArguments().getString("vehicleConsoleControls") == "true")
        val client = "carescape-hybrid-${UUID.randomUUID()}"
        suspend fun http(path: String, method: String): String = withContext(Dispatchers.IO) {
            val c = URL("http://127.0.0.1:8088$path").openConnection() as HttpURLConnection
            try {
                c.requestMethod = method
                c.connectTimeout = 1500
                c.readTimeout = 1500
                c.useCaches = false
                if (method == "POST") {
                    c.doOutput = true
                    c.setFixedLengthStreamingMode(0)
                    c.outputStream.close()
                }
                assertTrue("Console HTTP ${c.responseCode}: $path", c.responseCode in 200..299)
                c.inputStream.bufferedReader().use { it.readText() }
            } finally { c.disconnect() }
        }
        val heartbeat = launch(Dispatchers.IO) {
            while (isActive) {
                http("/browser/session?client=$client", "POST")
                delay(800)
            }
        }
        var initialWiper: String? = null
        var initialWindow: Int? = null
        var initialTurnSignal: String? = null
        var originalFailure: Throwable? = null
        try {
            val initial = JSONObject(http("/vehicle/state", "GET"))
            assertEquals("Park", initial.getJSONObject("gear").getJSONObject("actual").getString("value"))
            assertEquals(0.0, initial.getJSONObject("controls").getDouble("speed_kph"), 0.001)
            initialWiper = initial.getJSONObject("body").getJSONObject("wipers").getJSONObject("front").getString("mode")
            initialWindow = initial.getJSONObject("body").getJSONObject("windows").getInt("front_left")
            initialTurnSignal = initial.getJSONObject("lights").getString("turn_signal_mode")
            prepareStory()
            awaitState { it.sdkStatus.carSafety.availability == com.team.carescape.platform.sdk.SdkAvailability.READY }
            rule.runOnUiThread { vm.goLobby(); vm.setMockVehiclePuzzleSdk(true) }
            awaitState { it.mockVehiclePuzzleSdk && it.parked && !it.saving }
            assertEquals(TourMode.MOCK, vm.mode)
            http("/vehicle/turn-signal?mode=Off", "PUT")
            awaitState { it.vehiclePuzzleValues[CarControl.TURN_LEFT] == false && it.vehiclePuzzleValues[CarControl.TURN_RIGHT] == false }
            rule.runOnUiThread { vm.demoJump(DemoTarget.Scene(1)) }
            awaitState { !it.saving && it.story?.chapterIndex == 1 && it.story?.phase == StoryPhase.PUZZLE && it.tourGate == TourGate.READY }
            awaitTag("explore")
            clickTag("hotspot-x2_arch")
            clickTag("hotspot-open")
            awaitState { it.storyPuzzle?.id == "c2_arches" }
            rule.runOnUiThread { vm.changeStoryNumbers(listOf(7)) }
            clickSubmit()
            awaitState { !it.saving && it.story?.isStepDone("c2_arches") == true }
            awaitTag("explore")
            clickTag("hotspot-x2_lever")
            clickTag("hotspot-open")
            awaitState { it.storyPuzzle?.id == "c2_gates" && (it.carPuzzleInput as? CarPuzzleInput.Vehicle)?.ready == true }
            val expected = listOf(CarControl.TURN_RIGHT, CarControl.TURN_RIGHT, CarControl.TURN_RIGHT, CarControl.TURN_LEFT,
                CarControl.TURN_RIGHT, CarControl.TURN_RIGHT, CarControl.TURN_RIGHT)
            val beforeInputs = storedMock()
            // Even in hybrid mode a screen callback must not forge the SDK answer.
            rule.runOnUiThread { vm.changeStoryCar(expected) }
            assertTrue(vm.storyPuzzleDraft.car.isEmpty())
            suspend fun actual(control: CarControl, active: Boolean, path: String) {
                assertTrue("Real safety must be valid before console input", vm.parked)
                http(path, "PUT")
                awaitState { it.vehiclePuzzleValues[control] == active }
                assertTrue("Real safety must remain valid", vm.parked)
                assertTrue(vm.mockVehiclePuzzleSdk)
            }
            suspend fun pulse(control: CarControl) {
                val mode = if (control == CarControl.TURN_RIGHT) "BlinkRight" else "BlinkLeft"
                val beforeCount = vm.storyPuzzleDraft.car.size
                actual(control, true, "/vehicle/turn-signal?mode=$mode")
                awaitState { it.storyPuzzleDraft.car.size >= beforeCount + 1 }
                assertEquals(beforeCount + 1, vm.storyPuzzleDraft.car.size)
                // Repeating the active level must not fill another gate.
                http("/vehicle/turn-signal?mode=$mode", "PUT")
                delay(600)
                assertEquals("Repeated active turn signal must not add an answer", beforeCount + 1, vm.storyPuzzleDraft.car.size)
                actual(control, false, "/vehicle/turn-signal?mode=Off")
                awaitState { it.vehiclePuzzleValues[CarControl.TURN_LEFT] == false && it.vehiclePuzzleValues[CarControl.TURN_RIGHT] == false }
                assertEquals("Returning to Off must not add an answer", beforeCount + 1, vm.storyPuzzleDraft.car.size)
            }
            repeat(3) { pulse(CarControl.TURN_RIGHT) }
            pulse(CarControl.TURN_LEFT)
            repeat(3) { pulse(CarControl.TURN_RIGHT) }
            awaitState { it.storyPuzzleDraft.car.size >= expected.size }
            assertEquals(expected, vm.storyPuzzleDraft.car)
            assertEquals("SDK inputs must not save or submit automatically", beforeInputs, storedMock())
            assertFalse(vm.story!!.isStepDone("c2_gates"))
            clickTag("story-puzzle-submit")
            awaitState { !it.saving && it.story?.isStepDone("c2_gates") == true }
            val saved = storedMock()
            assertEquals(beforeInputs.sessionId, saved.sessionId)
            assertEquals(beforeInputs.tourRecords, saved.tourRecords)
            assertTrue(saved.puzzleProgress.single { it.checkpointId == TourCatalog.checkpoints[1].id }.solvedSteps.contains("c2_gates"))
            Log.i("CarEscapeVehicleSdkTest", "HYBRID_MOCK_TOUR_REAL_P_SDK_RRRLRRR_UI_SUBMIT_DATASTORE_PASS; LIVE_LOCATION_NOT_VERIFIED")
            Unit
        } catch (failure: Throwable) {
            originalFailure = failure
            throw failure
        } finally {
            withContext(NonCancellable) {
                val errors = mutableListOf<Throwable>()
                suspend fun clean(action: suspend () -> Unit) {
                    try { action() } catch (error: Throwable) { errors += error }
                }
                clean {
                    if (this@StoryIntegrationTest::vm.isInitialized) {
                        awaitState { it.foreground && !it.loading && !it.saving }
                        rule.runOnUiThread { vm.goLobby(); vm.setMockVehiclePuzzleSdk(false) }
                        assertFalse(vm.mockVehiclePuzzleSdk)
                    }
                }
                initialWiper?.let { clean { http("/vehicle/body?front_wiper=$it", "PUT") } }
                initialWindow?.let { clean { http("/vehicle/body?window_fl=$it", "PUT") } }
                initialTurnSignal?.let { clean { http("/vehicle/turn-signal?mode=$it", "PUT") } }
                clean {
                    if (initialWiper != null && initialWindow != null) {
                        val restoredState = JSONObject(http("/vehicle/state", "GET"))
                        val restored = restoredState.getJSONObject("body")
                        assertEquals(initialWiper, restored.getJSONObject("wipers").getJSONObject("front").getString("mode"))
                        assertEquals(initialWindow, restored.getJSONObject("windows").getInt("front_left"))
                        assertEquals(initialTurnSignal, restoredState.getJSONObject("lights").getString("turn_signal_mode"))
                    }
                }
                clean { heartbeat.cancelAndJoin() }
                clean { http("/browser/session?client=$client&closing=1", "POST") }
                if (errors.isNotEmpty()) {
                    val primary = originalFailure ?: errors.removeAt(0)
                    errors.forEach(primary::addSuppressed)
                    if (originalFailure == null) throw primary
                }
            }
        }
    }

    @Test
    fun firstPalacePuzzlePersistsWrongAttemptHintAndTourStartBeforeUiCorrectSubmission() {
        prepareFirstPuzzle()
        val puzzleStart = storedMock()
        val firstTourClock = puzzleStart.tourRecords.single().startedAt
        val session = puzzleStart.sessionId
        assertEquals(StoryCatalog.CURRENT_FLOW_VERSION, puzzleStart.flowVersion)
        assertTrue(puzzleStart.items.isEmpty())
        assertTrue(puzzleStart.clues.isEmpty())

        // flow5 화성행궁은 탐색 장면이다. 장면에서 현판을 눌러 자물쇠를 열고, 오답은 UI 제출 버튼으로만 평가한다.
        assertEquals(StoryCatalog.EXPLORE_FLOW_VERSION, puzzleStart.flowVersion)
        awaitTag("explore")
        clickTag("hotspot-x_plaque")
        clickTag("hotspot-open")
        awaitTag("story-puzzle")
        clickTag("choice-feng")
        clickSubmit()
        awaitState { !it.saving && it.storyPuzzleProgress?.wrongAttempts == 1 }
        clickTag("story-puzzle-hint")
        awaitState { !it.saving && it.storyPuzzleProgress?.hintsShown == 1 }
        rule.onNodeWithTag("story-puzzle-hint-1").assertIsDisplayed()
        val attempted = storedMock()
        assertEquals(1, attempted.puzzleProgress.single().wrongAttempts)
        assertEquals(mapOf("c1_plaque" to 1), attempted.puzzleProgress.single().stepHints)
        assertEquals(0, attempted.puzzleProgress.single().hintsShown)
        assertEquals(0, attempted.puzzleProgress.single().stepIndex)
        assertFalse(attempted.puzzleProgress.single().solved)
        assertTrue(attempted.items.isEmpty())
        assertTrue(attempted.clues.isEmpty())

        rule.activityRule.scenario.recreate()
        awaitTag("sdk-safety-paused")
        assertFalse(vm.parked)
        assertEquals(attempted, storedMock())
        openDevPanel()
        clickTag("sdk-sim-parked")
        awaitState { it.parked && it.tourGate == TourGate.LOCATION_MISSING }
        awaitTag("story-puzzle-waiting")
        rule.onNodeWithTag("story-puzzle-prompt").assertDoesNotExist()
        rule.runOnUiThread {
            vm.submitStoryPuzzle(StoryPuzzleInput.Choice(vm.storyPuzzleDraft.choice ?: ""))
        }
        assertEquals("Fresh P alone must not unlock the question", attempted, storedMock())

        clickTag("sdk-sim-location")
        awaitState { it.tourGate == TourGate.READY && !it.saving }
        closeDevPanel()
        awaitTag("story-puzzle")
        rule.onNodeWithTag("story-puzzle-hint-1").assertIsDisplayed()
        assertEquals(firstTourClock, storedMock().tourRecords.single().startedAt)
        assertEquals(1, vm.storyPuzzleProgress?.wrongAttempts)
        assertEquals(1, vm.storyPuzzleProgress?.hintsShown)

        // 보기를 고르는 것만으로는 평가·지급하지 않는다.
        clickTag("choice-shou")
        rule.runOnIdle { assertEquals("shou", vm.storyPuzzleDraft.choice) }
        assertEquals("Selecting must not evaluate or grant anything", attempted, storedMock())
        clickSubmit()
        awaitState { !it.saving && it.story?.currentStepIndex == 1 }
        val stepped = storedMock()
        assertEquals(listOf("c1_plaque"), stepped.puzzleProgress.single().solvedSteps)
        assertEquals(0, stepped.puzzleProgress.single().stepIndex)
        assertEquals(1, stepped.puzzleProgress.single().hintsUsed)
        assertTrue(stepped.clues.isEmpty())
        // 푼 자물쇠는 닫히고 장면으로 돌아온다. 현판 뒤 틈이 새로 보인다.
        awaitTag("explore")
        awaitTag("hotspot-x_behind")

        solveRemainingSteps()
        awaitState { !it.saving && it.story?.phase == StoryPhase.REVEALED }
        val solved = storedMock()
        assertEquals(session, solved.sessionId)
        assertEquals(firstTourClock, solved.tourRecords.single().startedAt)
        assertEquals(1, solved.puzzleProgress.single().wrongAttempts)
        assertTrue(solved.puzzleProgress.single().solved)
        assertEquals(listOfNotNull(vm.story!!.episode.chapters[0].clue), solved.clues)
        assertEquals(listOfNotNull(vm.story!!.episode.chapters[0].item), solved.items)
        assertEquals(1, solved.clues.size)

        rule.runOnUiThread {
            vm.submitStoryPuzzle(StoryPuzzleInput.Numeric("0213"))
            vm.returnToCar()
            vm.submitStoryPuzzle(StoryPuzzleInput.Choice("hall"))
        }
        rule.waitForIdle()
        assertEquals("A solved checkpoint must never grant twice", solved, storedMock())
    }

    @Test
    fun confirmedTourRemainsUsableAfterLocationExpiryButDepartureNeedsANewFix() {
        prepareFirstPuzzle()
        val beforeExpiry = storedMock()
        val observedAt = SystemClock.elapsedRealtime()
        // Real elapsed time: Compose's animation clock does not age an SDK/location sample.
        rule.waitUntil(45_000) {
            SystemClock.elapsedRealtime() - observedAt > TourPolicy.MAX_LOCATION_AGE_MILLIS + 250
        }
        awaitState { it.tourGate == TourGate.READY && !it.saving }
        assertEquals(beforeExpiry, storedMock())

        rule.runOnUiThread { vm.openHotspotLock("x_plaque") }
        awaitTag("story-puzzle")
        clickTag("choice-shou")
        clickSubmit()
        awaitState { !it.saving && it.story?.puzzleProgress?.single()?.solvedSteps?.contains("c1_plaque") == true }
        val afterSubmission = storedMock()
        assertTrue(afterSubmission.puzzleProgress.single().solvedSteps.contains("c1_plaque"))
        assertEquals(beforeExpiry.tourRecords.single().startedAt, afterSubmission.tourRecords.single().startedAt)

        rule.runOnUiThread { vm.simSetParked(false); vm.simSetParked(true) }
        awaitState { it.parked && it.tourGate == TourGate.LOCATION_NOT_UPDATED }
        assertEquals(afterSubmission, storedMock())
        rule.runOnUiThread { vm.demoArrive() }
        awaitState { it.parked && it.tourGate == TourGate.READY }
        assertEquals(afterSubmission, storedMock())
    }

    @Test
    fun puzzleRejectsDrivingBackgroundAndMissingFreshLocationWithoutChangingReceipts() {
        prepareFirstPuzzle()
        // 화성행궁은 탐색 장면이다. 현판 자물쇠를 연 상태에서 같은 안전 조건을 확인한다.
        rule.runOnUiThread { vm.openHotspotLock("x_plaque") }
        awaitTag("story-puzzle")
        rule.runOnUiThread { vm.changeStoryChoice("shou") }
        val before = storedMock()

        rule.runOnUiThread {
            vm.simSetParked(false)
            vm.changeStoryChoice("feng")
            vm.showStoryPuzzleHint()
            vm.submitStoryPuzzle(StoryPuzzleInput.Choice(vm.storyPuzzleDraft.choice ?: ""))
            vm.storyNext()
        }
        awaitTag("sdk-safety-paused")
        rule.onNodeWithTag("story-puzzle").assertDoesNotExist()
        rule.onNodeWithTag("story-puzzle-prompt").assertDoesNotExist()
        assertEquals(before, storedMock())

        rule.runOnUiThread { vm.simSetParked(true); vm.demoArrive() }
        awaitState { it.parked && it.tourGate == TourGate.READY }
        awaitTag("story-puzzle")
        rule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            assertFalse(vm.foreground)
            vm.changeStoryChoice("shou")
            vm.showStoryPuzzleHint()
            vm.submitStoryPuzzle(StoryPuzzleInput.Choice(vm.storyPuzzleDraft.choice ?: ""))
            vm.storyNext()
        }
        assertEquals(before, storedMock())
        rule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        awaitTag("sdk-safety-paused")
        openDevPanel()
        clickTag("sdk-sim-parked")
        awaitState { it.parked && it.tourGate == TourGate.LOCATION_MISSING }
        awaitTag("story-puzzle-waiting")
        rule.onNodeWithTag("story-puzzle").assertDoesNotExist()
        rule.onNodeWithTag("choice-shou").assertDoesNotExist()
        rule.runOnUiThread {
            vm.changeStoryChoice("shou")
            vm.showStoryPuzzleHint()
            vm.submitStoryPuzzle(StoryPuzzleInput.Choice(vm.storyPuzzleDraft.choice ?: ""))
        }
        assertEquals("A foreground return requires a fresh position before any puzzle command", before, storedMock())
        clickTag("sdk-sim-location")
        awaitState { it.tourGate == TourGate.READY }
        closeDevPanel()
        awaitTag("story-puzzle")
        assertEquals(before, storedMock())
    }

    @Test
    fun allFiveNativePuzzlesGrantOnceInOrderAndKeepTourStartClocksThroughResume() {
        prepareStory()
        val firstClocks = mutableMapOf<String, com.team.carescape.model.TourClock>()
        val session = storedMock().sessionId
        val chapters = vm.story!!.episode.chapters
        for (index in chapters.indices) {
            enterPuzzleAtCurrentStop(index)
            val pending = storedMock()
            pending.tourRecords.forEach { firstClocks.putIfAbsent(it.checkpointId, it.startedAt) }
            assertEquals(index, pending.puzzleProgress.count { it.solved })
            assertEquals(index + 1, pending.tourRecords.size)
            rule.runOnUiThread { vm.storyNext() }
            rule.waitForIdle()
            assertEquals("An unanswered puzzle cannot skip its checkpoint", pending, storedMock())
            val lastInput = inputFor(chapters[index].puzzleSteps.last().lock)
            solveRemainingSteps()
            val expectedPhase = if (index == chapters.lastIndex) StoryPhase.ENDED else StoryPhase.REVEALED
            awaitState { !it.saving && it.story?.phase == expectedPhase }
            val granted = storedMock()
            assertEquals(session, granted.sessionId)
            assertEquals(index + 1, granted.puzzleProgress.count { it.solved })
            assertEquals(granted.clues.distinct(), granted.clues)
            assertEquals(granted.items.distinct(), granted.items)
            assertEquals(chapters.take(index + 1).mapNotNull { it.clue }, granted.clues)
            assertEquals(chapters.take(index + 1).mapNotNull { it.item }, granted.items)
            granted.tourRecords.forEach { assertEquals(firstClocks[it.checkpointId], it.startedAt) }

            rule.runOnUiThread { vm.submitStoryPuzzle(lastInput) }
            rule.waitForIdle()
            assertEquals(granted, storedMock())
            if (index == 1) {
                rule.runOnUiThread { vm.goLobby(); vm.resumeStory() }
                awaitState { it.screen == Screen.STORY && it.story?.phase == StoryPhase.REVEALED }
                assertEquals("Resume must not restart clocks or grant again", granted, storedMock())
            }
            if (index < chapters.lastIndex) advanceToNextStop(index + 1)
        }
        val completed = storedMock()
        assertEquals(StoryPhase.ENDED, completed.phase)
        assertEquals(4, completed.chapterIndex)
        assertEquals(5, completed.puzzleProgress.size)
        assertTrue(completed.puzzleProgress.all { it.solved })
        assertEquals(5, completed.tourRecords.size)
        completed.tourRecords.forEach { assertEquals(firstClocks[it.checkpointId], it.startedAt) }
        assertEquals(EndingKind.TRUE, vm.story?.endingKind)
        // 엔딩 장면이 먼저 나온다. 진 엔딩 장면에는 2026년 마무리(박물관)가 있다.
        awaitTag("scene-player")
        clickTag("scene-skip")
        awaitTag("ending-title")
    }

    @OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
    @Test
    fun museumBeaconUsesCurrentMockCountAndPreservesCompletionAfterRecreation() {
        prepareMuseumBeacon()
        if (exists("item-acquired-ok")) clickTag("item-acquired-ok")
        if (exists("explore-guide-close")) clickTag("explore-guide-close")
        val before = storedMock()
        val chapterId = TourCatalog.checkpoints[3].id
        val beforeAttempts = before.puzzleProgress.single { it.checkpointId == chapterId }.wrongAttempts

        fun captureBeacon(name: String) {
            val lock = rule.onNodeWithTag("interior-light-lock")
            runCatching { lock.performScrollTo() }
            rule.waitForIdle()
            val bitmap = lock.captureToImage().asAndroidBitmap()
            try {
                val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "beacon-five")
                assertTrue("Beacon screenshot directory must exist", directory.isDirectory || directory.mkdirs())
                val file = File(directory, "$name.png")
                file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                assertTrue("Beacon screenshot must be written", file.isFile && file.length() > 0L)
                Log.i("CarEscapeBeaconTest", "MOCK_UI_SCREENSHOT:${file.absolutePath}")
            } finally { bitmap.recycle() }
        }

        rule.onNodeWithText("켜진 봉화: 1 / 5").assertExists()
        rule.onNodeWithTag("beacon-base").performTouchInput { click() }
        rule.runOnIdle { assertEquals(List(4) { false }, vm.storyPuzzleDraft.interiorLights) }
        assertEquals("The fixed base beacon must not change saved progress", before, storedMock())
        captureBeacon("beacon-1")

        repeat(4) { clickTag("interior-light-$it") }
        rule.onNodeWithText("켜진 봉화: 5 / 5").assertExists()
        rule.runOnIdle { assertEquals(List(4) { true }, vm.storyPuzzleDraft.interiorLights) }
        assertEquals("Changing four current lights must not persist an attempt", before, storedMock())
        captureBeacon("beacon-5")
        clickSubmit()
        awaitState { !it.saving && it.storyPuzzleProgress?.wrongAttempts == beforeAttempts + 1 }
        val afterWrong = storedMock()
        assertFalse(vm.story!!.isStepDone("c4_beacon"))
        assertEquals(before.items, afterWrong.items)
        assertEquals(before.clues, afterWrong.clues)
        assertEquals(before.tourRecords, afterWrong.tourRecords)

        clickTag("interior-light-3")
        rule.onNodeWithText("켜진 봉화: 4 / 5").assertExists()
        rule.runOnIdle { assertEquals(listOf(true, true, true, false), vm.storyPuzzleDraft.interiorLights) }
        assertEquals("Three raw lights plus the base still require explicit submission", afterWrong, storedMock())
        captureBeacon("beacon-4")
        clickSubmit()
        awaitState { !it.saving && it.story?.isStepDone("c4_beacon") == true }
        val completed = storedMock()
        assertEquals(before.sessionId, completed.sessionId)
        assertEquals(before.tourRecords, completed.tourRecords)
        assertEquals(1, completed.puzzleProgress.single { it.checkpointId == chapterId }.solvedSteps.count { it == "c4_beacon" })
        rule.runOnUiThread { vm.submitStoryPuzzle(StoryPuzzleInput.InteriorLightCount(3)) }
        rule.waitForIdle()
        assertEquals("A completed beacon cannot grant twice", completed, storedMock())

        rule.activityRule.scenario.recreate()
        rule.runOnIdle { vm = ViewModelProvider(rule.activity)[GameViewModel::class.java] }
        awaitState { !it.loading && it.foreground }
        assertTrue(vm.story!!.isStepDone("c4_beacon"))
        assertEquals("Recreation must preserve the beacon completion and receipts", completed, storedMock())
    }

    private fun prepareMuseumBeacon() {
        prepareStory()
        assertFalse(vm.mockVehiclePuzzleSdk)
        rule.runOnUiThread { vm.demoJump(DemoTarget.Scene(3)) }
        awaitState { !it.saving && it.story?.chapterIndex == 3 && it.story?.phase == StoryPhase.PUZZLE && it.tourGate == TourGate.READY }
        rule.runOnUiThread { vm.touchHotspot("x4_diary") }
        awaitState { !it.saving && it.story?.puzzleProgressForCurrent?.found?.contains("x4_diary") == true }
        rule.runOnUiThread { vm.openHotspotLock("x4_drawer") }
        awaitState { !it.saving && it.storyPuzzle?.id == "c4_index" }
        solveCurrentStep()
        rule.runOnUiThread { vm.openHotspotLock("x4_beaconbox") }
        awaitState { !it.saving && it.storyPuzzle?.id == "c4_flint" }
        solveCurrentStep()
        rule.runOnUiThread { vm.openHotspotLock("x4_beaconbox") }
        awaitState { !it.saving && it.storyPuzzle?.id == "c4_beacon" }
        assertTrue(vm.storyPuzzle!!.lock is StoryLockDefinition.InteriorLightCount)
        awaitTag("interior-light-lock")
    }


    private fun inputFor(lock: StoryLockDefinition): StoryPuzzleInput = when (lock) {
        is StoryLockDefinition.Numeric -> StoryPuzzleInput.Numeric(lock.answer)
        is StoryLockDefinition.Directions -> StoryPuzzleInput.Directions(lock.answer)
        is StoryLockDefinition.Device -> StoryPuzzleInput.Device(lock.channel, lock.level)
        is StoryLockDefinition.Choice -> StoryPuzzleInput.Choice(lock.answer)
        is StoryLockDefinition.Order -> StoryPuzzleInput.Order(lock.answer)
        is StoryLockDefinition.CarSequence -> StoryPuzzleInput.CarSequence(lock.answer)
        is StoryLockDefinition.SymbolDial -> StoryPuzzleInput.Dial(lock.answer)
        is StoryLockDefinition.UseItem -> StoryPuzzleInput.UseItem(lock.answer)
        is StoryLockDefinition.Sequence -> StoryPuzzleInput.Sequence(lock.answer)
        is StoryLockDefinition.InteriorLightCount -> StoryPuzzleInput.InteriorLightCount(lock.requiredLights)
    }

    /** 현재 단계의 정답을 화면 초안에 넣고 제출한다(평가는 ViewModel·엔진이 한다). */
    private fun solveCurrentStep() {
        val before = vm.story!!.currentStepIndex
        val input = inputFor(vm.storyPuzzle!!.lock)
        rule.runOnUiThread {
            when (input) {
                is StoryPuzzleInput.Numeric -> vm.changeStoryNumbers(input.value.map { it.digitToInt() })
                is StoryPuzzleInput.Directions -> vm.changeStoryDirections(input.value)
                is StoryPuzzleInput.Device -> vm.changeStoryDevice(input.channel, input.level)
                is StoryPuzzleInput.Choice -> vm.changeStoryChoice(input.id)
                is StoryPuzzleInput.Order -> vm.changeStoryOrder(input.ids)
                is StoryPuzzleInput.CarSequence -> vm.changeStoryCar(input.value)
                is StoryPuzzleInput.Dial -> vm.changeStoryDial(input.value)
                is StoryPuzzleInput.UseItem -> vm.changeStoryItem(input.id)
                is StoryPuzzleInput.Sequence -> vm.changeStorySequence(input.ids)
                is StoryPuzzleInput.InteriorLightCount -> repeat(input.count) { vm.changeStoryInteriorLight(it) }
            }
            vm.submitStoryPuzzle(input)
        }
        awaitState { !it.saving && (it.story?.phase != StoryPhase.PUZZLE || it.story?.currentStepIndex != before) }
    }

    private fun solveRemainingSteps() {
        while (vm.story?.phase == StoryPhase.PUZZLE) {
            val s = vm.story!!
            if (s.isExplore && s.currentPuzzle == null) {
                // 탐색 장면: 지금 손댈 수 있는 곳을 살펴보거나 그 자물쇠를 연다.
                val spot = s.actionableHotspots.first()
                if (spot.locks.isEmpty()) {
                    val before = s.puzzleProgressForCurrent!!.found.size
                    rule.runOnUiThread { vm.touchHotspot(spot.id) }
                    awaitState { !it.saving && (it.story?.puzzleProgressForCurrent?.found?.size ?: 0) > before }
                } else {
                    rule.runOnUiThread { vm.openHotspotLock(spot.id) }
                    awaitState { it.story?.currentPuzzle != null }
                }
            } else solveCurrentStep()
        }
    }

    private fun prepareFirstPuzzle() {
        prepareStory()
        enterPuzzleAtCurrentStop(0)
    }

    private fun prepareStory() {
        awaitTag("sdk-status-open")
        rule.runOnIdle { vm = ViewModelProvider(rule.activity)[GameViewModel::class.java] }
        awaitState { !it.loading && it.foreground }
        assertEquals(TourMode.MOCK, vm.mode)
        openDevPanel()
        clickTag("sdk-sim-parked")
        awaitState { it.parked }
        closeDevPanel()
        rule.runOnUiThread { vm.startStory() }
        awaitState { !it.saving && it.story?.phase == StoryPhase.EN_ROUTE && it.showMap }
        assertEquals(StoryCatalog.CURRENT_FLOW_VERSION, storedMock().flowVersion)
    }

    private fun enterPuzzleAtCurrentStop(index: Int) {
        assertEquals(index, vm.story?.chapterIndex)
        clickTag("story-start-guidance")
        awaitState { !it.saving && !it.showMap }
        openDevPanel()
        clickTag("sdk-sim-location")
        awaitState { !it.saving && it.story?.phase == StoryPhase.ARRIVED }
        closeDevPanel()
        // 도착 장면(드라마)을 건너뛰면 관광 시작 화면이 나온다.
        clickTag("scene-skip")
        clickTag("tour-start")
        awaitState { !it.saving && it.story?.phase == StoryPhase.EXPLORING }
        val exploring = storedMock()
        assertNotNull(exploring.tourRecords.firstOrNull { it.checkpointId == vm.currentWaypoint?.id })
        assertTrue(vm.tourRemainingMillis!! > 0)
        clickTag("sdk-demo-elapse")
        awaitState { !it.saving && it.tourGate == TourGate.READY }
        clickTag("tour-return")
        awaitState { !it.saving && it.story?.phase == StoryPhase.PUZZLE }
        // 탐색 장면 장소는 퀴즈 대신 장면이 먼저 나온다.
        awaitTag(if (vm.story!!.isExplore) "explore" else "story-puzzle")
        assertEquals(index, vm.story?.chapterIndex)
        assertFalse(vm.storyPuzzleProgress!!.solved)
    }

    private fun advanceToNextStop(expectedIndex: Int) {
        rule.runOnUiThread { vm.storyNext() }
        awaitState { !it.saving && (it.story?.phase == StoryPhase.FINAL_REVEAL || it.story?.chapterIndex == expectedIndex) }
        if (vm.story?.phase == StoryPhase.FINAL_REVEAL) {
            rule.runOnUiThread { vm.storyNext() }
        }
        awaitState { !it.saving && it.story?.chapterIndex == expectedIndex && it.story?.phase == StoryPhase.EN_ROUTE && it.showMap }
    }

    private fun storedMock(): StoredTrip = runBlocking {
        checkNotNull(store.load().trips.firstOrNull { it.mode == TourMode.MOCK })
    }

    private fun awaitState(predicate: (GameViewModel) -> Boolean) = rule.waitUntil(30_000) { predicate(vm) }
    private fun awaitTag(tag: String) = rule.waitUntil(30_000) { exists(tag) }

    private fun openDevPanel() {
        if (!exists("sdk-sim-parked")) clickTag("sdk-dev-toggle")
        awaitTag("sdk-sim-parked")
    }

    private fun closeDevPanel() {
        if (exists("sdk-sim-parked")) clickTag("sdk-dev-toggle")
    }

    private fun clickSubmit() {
        rule.onNodeWithText("제출").assertIsEnabled().performClick()
        rule.waitForIdle()
    }

    private fun clickTag(tag: String) {
        // 새 소지품 창이 떠 있으면 아래 화면을 누를 수 없으므로 먼저 닫는다.
        if (tag != "item-acquired-ok" && exists("item-acquired-ok")) {
            rule.onNodeWithTag("item-acquired-ok").performClick()
            rule.waitForIdle()
        }
        // 첫 장면 도움말도 아래 화면을 가리므로 먼저 닫는다.
        if (tag != "explore-guide-close" && exists("explore-guide-close")) {
            rule.onNodeWithTag("explore-guide-close").performClick()
            rule.waitForIdle()
        }
        rule.waitUntil(30_000) { runCatching { rule.onNodeWithTag(tag).assertIsEnabled(); true }.getOrDefault(false) }
        val node = rule.onNodeWithTag(tag)
        runCatching { node.performScrollTo() }
        node.performClick()
        rule.waitForIdle()
    }

    private fun exists(tag: String): Boolean = try {
        rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    } catch (error: IllegalStateException) {
        if (error.message?.startsWith("No compose hierarchies found") == true) false else throw error
    }
}
