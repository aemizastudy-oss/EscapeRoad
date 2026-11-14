package com.team.carescape.game

import com.team.carescape.data.StoredTrip
import com.team.carescape.data.validateStoredTrip
import com.team.carescape.model.TourClock
import com.team.carescape.model.TourMode
import com.team.carescape.model.TourStartRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SiyeokFlow4Test {
    private val episode = StoryCatalog.episodeFor(StoryCatalog.ITEM_FLOW_VERSION)
    private val steps = episode.chapters.flatMap { it.puzzleSteps }
    private val clock = TourClock(1_800_000_000_000L, 1_000_000L, "boot-four")

    private fun answer(lock: StoryLockDefinition): StoryPuzzleInput = when (lock) {
        is StoryLockDefinition.Numeric -> StoryPuzzleInput.Numeric(lock.answer)
        is StoryLockDefinition.Directions -> StoryPuzzleInput.Directions(lock.answer)
        is StoryLockDefinition.Device -> StoryPuzzleInput.Device(lock.channel, lock.level)
        is StoryLockDefinition.Choice -> StoryPuzzleInput.Choice(lock.answer)
        is StoryLockDefinition.Order -> StoryPuzzleInput.Order(lock.answer)
        is StoryLockDefinition.CarSequence -> StoryPuzzleInput.CarSequence(lock.answer)
        is StoryLockDefinition.SymbolDial -> StoryPuzzleInput.Dial(lock.answer)
        is StoryLockDefinition.UseItem -> StoryPuzzleInput.UseItem(lock.answer)
        is StoryLockDefinition.Sequence -> StoryPuzzleInput.Sequence(lock.answer)
        is StoryLockDefinition.InteriorLightCount -> StoryPuzzleInput.InteriorLightCount(lock.answer)
    }
    private fun atPuzzle(state: StoryState) = StoryEngine.returnToCar(StoryEngine.leaveCar(StoryEngine.verifiedArrival(state)))
    private fun step(id: String) = steps.single { it.id == id }

    private fun play(onState: (StoryState) -> Unit = {}): StoryState {
        var state = StoryEngine.start(episode, StoryCatalog.ITEM_FLOW_VERSION)
        episode.chapters.indices.forEach { index ->
            state = atPuzzle(state)
            while (state.phase == StoryPhase.PUZZLE) {
                val current = state.currentPuzzle!!
                if (current.lock is StoryLockDefinition.UseItem) {
                    val lock = current.lock as StoryLockDefinition.UseItem
                    val owned = state.inventory.firstOrNull { it.id == lock.answer }
                    assertTrue("${current.id}: 쓸 소지품을 이미 가지고 있어야 한다", owned != null && !state.isItemUsed(owned))
                }
                val result = StoryEngine.submitPuzzle(state, answer(current.lock))
                assertEquals(current.id, StoryPuzzleResult.CORRECT, result.result)
                state = result.state
                onState(state)
            }
            if (index < episode.finalChapterIndex) {
                state = StoryEngine.next(state)
                if (state.phase == StoryPhase.FINAL_REVEAL) state = StoryEngine.next(state)
            }
        }
        return state
    }

    @Test fun newTripsStartOnFlowFourAndFlowThreeStaysReadable() {
        assertTrue(StoryCatalog.CURRENT_FLOW_VERSION >= StoryCatalog.ITEM_FLOW_VERSION)
        assertTrue(StoryCatalog.episodeFor(StoryCatalog.CASE_FLOW_VERSION).chapters[2].puzzleSteps.any { it.id == "c3_channel" && it.lock is StoryLockDefinition.CarSequence })
    }

    @Test fun carControlsAreCutToTheSluiceAndTheWindow() {
        val car = steps.filter { it.lock is StoryLockDefinition.CarSequence }.map { it.id }
        assertEquals(listOf("c2_gates", "c5_window"), car)
        assertTrue(step("c3_channel").lock is StoryLockDefinition.Sequence)
        assertTrue(step("c4_beacon").lock is StoryLockDefinition.Sequence)
        assertEquals(listOf("c2_drawer", "c4_flint", "c5_release"), steps.filter { it.lock is StoryLockDefinition.UseItem }.map { it.id })
        assertEquals(listOf(11, 0), (step("c2_timebox").lock as StoryLockDefinition.SymbolDial).answer)
    }

    @Test fun everyStepHasAUniqueAnswerAndThreeHints() {
        assertEquals(steps.map { it.id }.distinct(), steps.map { it.id })
        steps.forEach { s ->
            assertEquals(s.id, StoryPuzzleResult.CORRECT, StoryPuzzles.evaluate(s, answer(s.lock)))
            assertTrue(s.id, s.hints.size == 3 || s.kind == StepKind.SCENE)
            assertTrue(s.id, s.speaker == null || episode.speakerFor(s.speaker) != null)
        }
        val ids = episode.caseItems.map { it.id }
        episode.caseItems.forEach { item ->
            assertTrue(item.id, steps.any { it.id == item.fromStep })
            assertTrue(item.id, item.usedBy == null || steps.any { it.id == item.usedBy })
        }
        assertEquals(ids.distinct(), ids)
    }

    @Test fun perfectRunCollectsAndUsesItemsAndReachesTheTrueEnding() {
        val end = play()
        assertEquals(StoryPhase.ENDED, end.phase)
        assertEquals(EndingKind.TRUE, end.endingKind)
        assertEquals(episode.caseItems.map { it.id }, end.inventory.map { it.id })
        assertEquals(setOf("key_eul", "strong_water", "grain", "flint", "licorice", "vaccine"),
            end.inventory.filter { end.isItemUsed(it) }.map { it.id }.toSet())
        assertEquals(episode.documents.map { it.id }, end.visibleDocuments.map { it.id })
    }

    @Test fun wrongItemsAndDialsGiveSpecificNotesWithoutPenalty() {
        val drawer = step("c2_drawer")
        assertEquals(StoryPuzzleResult.WRONG, StoryPuzzles.evaluate(drawer, StoryPuzzleInput.UseItem("secret_letter")))
        assertEquals(StoryPuzzleResult.INVALID_INPUT, StoryPuzzles.evaluate(drawer, StoryPuzzleInput.UseItem("not-an-item")))
        assertEquals("밀서로는 서랍이 열리지 않는다.", drawer.feedback("secret_letter")?.message)
        val box = step("c2_timebox")
        assertEquals(StoryPuzzleResult.WRONG, StoryPuzzles.evaluate(box, StoryPuzzleInput.Dial(listOf(0, 11))))
        assertEquals(StoryPuzzleResult.INVALID_INPUT, StoryPuzzles.evaluate(box, StoryPuzzleInput.Dial(listOf(12, 0))))
        assertEquals(0, box.feedback("0,11")?.penalty)
        val beacon = step("c4_beacon")
        assertEquals(StoryPuzzleResult.WRONG, StoryPuzzles.evaluate(beacon, StoryPuzzleInput.Sequence(List(5) { "FIRE" })))
        assertTrue(beacon.feedback("FIRE,FIRE,FIRE,FIRE,FIRE")!!.message.contains("너무 많이"))
        val channel = step("c3_channel")
        assertTrue(channel.feedback("R,R,R")!!.message.contains("거꾸로"))
    }

    @Test fun drawerDocumentWaitsForTheKey() {
        var state = atPuzzle(StoryEngine.start(episode, StoryCatalog.ITEM_FLOW_VERSION).copy(chapterIndex = 1))
        while (state.currentPuzzle!!.id != "c2_drawer") state = StoryEngine.submitPuzzle(state, answer(state.currentPuzzle!!.lock)).state
        assertFalse(state.visibleDocuments.any { it.id == "doc_e04" })
        assertTrue(state.visibleDocuments.any { it.id == "doc_sluice" })
    }

    @Test fun scenesHaveBackgroundsAndKnownSpeakers() {
        val prologue = StoryCatalog.episodeFor(StoryCatalog.INTEGRATED_FLOW_VERSION).prologueScene
        assertTrue(prologue.size >= 12)
        assertEquals(SceneBg.AGENCY, prologue.first().bg)
        prologue.forEach { assertTrue(it.speaker ?: "-", it.speaker == null || episode.speakerFor(it.speaker) != null) }
        episode.chapters.forEachIndexed { i, c ->
            assertTrue("arrival bg $i", c.arrivalScene.first().bg != null)
            if (i < episode.finalChapterIndex) assertTrue("reveal bg $i", c.revealScene.first().bg != null)
        }
    }

    @Test fun storedFlowFourTripsValidateAtEveryStep() {
        var trip = StoredTrip(TourMode.MOCK, "flow4", chapterIndex = 0, phase = StoryPhase.EN_ROUTE,
            startedAtEpochMillis = clock.wallTimeMillis, flowVersion = StoryCatalog.ITEM_FLOW_VERSION)
        validateStoredTrip(trip)
        play { next ->
            val records = TourCatalog.checkpoints.take(next.chapterIndex + 1).map { TourStartRecord("flow4", it.id, TourMode.MOCK, clock) }
            trip = trip.copy(chapterIndex = next.chapterIndex, phase = next.phase, items = next.items, clues = next.clues,
                puzzleProgress = next.puzzleProgress, tourRecords = records)
            validateStoredTrip(trip)
        }
    }
}
