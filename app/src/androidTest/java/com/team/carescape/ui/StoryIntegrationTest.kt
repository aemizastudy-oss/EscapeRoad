package com.team.carescape.ui

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
import com.team.carescape.game.TourGate
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

    @Test
    fun firstPalacePuzzlePersistsWrongAttemptHintAndTourStartBeforeUiCorrectSubmission() {
        prepareFirstPuzzle()
        val puzzleStart = storedMock()
        val firstTourClock = puzzleStart.tourRecords.single().startedAt
        val session = puzzleStart.sessionId
        assertEquals(StoryCatalog.CURRENT_FLOW_VERSION, puzzleStart.flowVersion)
        assertTrue(puzzleStart.items.isEmpty())
        assertTrue(puzzleStart.clues.isEmpty())

        // flow3 첫 단계는 오디오 증언 보기 문제다. 오답 보기를 골라 UI 제출 버튼으로만 평가한다.
        assertEquals(StoryCatalog.ITEM_FLOW_VERSION, puzzleStart.flowVersion)
        clickTag("choice-gate")
        clickSubmit()
        awaitState { !it.saving && it.storyPuzzleProgress?.wrongAttempts == 1 }
        clickTag("story-puzzle-hint")
        awaitState { !it.saving && it.storyPuzzleProgress?.hintsShown == 1 }
        rule.onNodeWithTag("story-puzzle-hint-1").assertIsDisplayed()
        val attempted = storedMock()
        assertEquals(1, attempted.puzzleProgress.single().wrongAttempts)
        assertEquals(1, attempted.puzzleProgress.single().hintsShown)
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
        clickTag("choice-hall")
        rule.runOnIdle { assertEquals("hall", vm.storyPuzzleDraft.choice) }
        assertEquals("Selecting must not evaluate or grant anything", attempted, storedMock())
        clickSubmit()
        awaitState { !it.saving && it.story?.currentStepIndex == 1 }
        val stepped = storedMock()
        assertEquals(1, stepped.puzzleProgress.single().stepIndex)
        assertEquals(0, stepped.puzzleProgress.single().hintsShown)
        assertEquals(1, stepped.puzzleProgress.single().hintsUsed)
        assertTrue(stepped.clues.isEmpty())
        awaitTag("story-step-label")

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
    fun puzzleRejectsDrivingBackgroundAndMissingFreshLocationWithoutChangingReceipts() {
        prepareFirstPuzzle()
        rule.runOnUiThread { vm.changeStoryChoice("hall") }
        val before = storedMock()

        rule.runOnUiThread {
            vm.simSetParked(false)
            vm.changeStoryChoice("gate")
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
            vm.changeStoryChoice("hall")
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
        rule.onNodeWithTag("choice-hall").assertDoesNotExist()
        rule.runOnUiThread {
            vm.changeStoryChoice("hall")
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
            }
            vm.submitStoryPuzzle(input)
        }
        awaitState { !it.saving && (it.story?.phase != StoryPhase.PUZZLE || it.story?.currentStepIndex != before) }
    }

    private fun solveRemainingSteps() {
        while (vm.story?.phase == StoryPhase.PUZZLE) solveCurrentStep()
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
        awaitTag("story-puzzle")
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
