package com.team.carescape.game

import com.team.carescape.data.StoredTrip
import com.team.carescape.data.TripStoreState
import com.team.carescape.data.upsertTrip
import com.team.carescape.data.validateStoredTrip
import com.team.carescape.model.TourClock
import com.team.carescape.model.TourMode
import com.team.carescape.model.TourStartRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SiyeokCaseTest {
    private val episode = StoryCatalog.episodeFor(StoryCatalog.CASE_FLOW_VERSION)
    private val clock = TourClock(1_800_000_000_000L, 1_000_000L, "boot-one")

    private fun answer(lock: StoryLockDefinition): StoryPuzzleInput = when (lock) {
        is StoryLockDefinition.Numeric -> StoryPuzzleInput.Numeric(lock.answer)
        is StoryLockDefinition.Directions -> StoryPuzzleInput.Directions(lock.answer)
        is StoryLockDefinition.Device -> StoryPuzzleInput.Device(lock.channel, lock.level)
        is StoryLockDefinition.Choice -> StoryPuzzleInput.Choice(lock.answer)
        is StoryLockDefinition.Order -> StoryPuzzleInput.Order(lock.answer)
        is StoryLockDefinition.CarSequence -> StoryPuzzleInput.CarSequence(lock.answer)
    }
    private fun wrong(lock: StoryLockDefinition): StoryPuzzleInput = when (lock) {
        is StoryLockDefinition.Choice -> StoryPuzzleInput.Choice(lock.options.first { it.id != lock.answer }.id)
        else -> error("choice only")
    }
    private fun atPuzzle(state: StoryState) = StoryEngine.returnToCar(StoryEngine.leaveCar(StoryEngine.verifiedArrival(state)))

    /** 모든 장소를 플레이한다. [onStep]이 입력을 바꿀 수 있다. */
    private fun play(choose: (StoryState, StoryPuzzleDefinition) -> StoryPuzzleInput = { _, p -> answer(p.lock) },
                     hintsPerStep: Int = 0): StoryState {
        var state = StoryEngine.start(episode, StoryCatalog.CASE_FLOW_VERSION)
        episode.chapters.indices.forEach { index ->
            state = atPuzzle(state)
            assertEquals(StoryPhase.PUZZLE, state.phase)
            while (state.phase == StoryPhase.PUZZLE) {
                val step = state.currentPuzzle!!
                repeat(hintsPerStep) { state = StoryEngine.showPuzzleHint(state) }
                val result = StoryEngine.submitPuzzle(state, choose(state, step))
                assertEquals(step.id, StoryPuzzleResult.CORRECT, result.result)
                state = result.state
            }
            if (index < episode.finalChapterIndex) {
                assertEquals(StoryPhase.REVEALED, state.phase)
                state = StoryEngine.next(state)
                if (state.phase == StoryPhase.FINAL_REVEAL) state = StoryEngine.next(state)
            }
        }
        return state
    }

    @Test fun everyChapterHasStepsAndEveryStepHasAValidUniqueAnswer() {
        val ids = episode.chapters.flatMap { c -> c.puzzleSteps.map { it.id } }
        assertEquals(ids.distinct(), ids)
        assertTrue(episode.chapters.all { it.puzzleSteps.size >= 4 && it.puzzle == null })
        episode.chapters.flatMap { it.puzzleSteps }.forEach { step ->
            assertEquals(step.id, StoryPuzzleResult.CORRECT, StoryPuzzles.evaluate(step, answer(step.lock)))
            assertTrue(step.id, step.hints.size == 3 || step.kind == StepKind.SCENE)
        }
        // 기획 확정 정답
        fun lock(id: String) = episode.chapters.flatMap { it.puzzleSteps }.single { it.id == id }.lock
        assertEquals(StoryLockDefinition.Numeric("0213"), lock("c1_diary_lock"))
        assertEquals(StoryLockDefinition.Numeric("7"), lock("c2_arches"))
        assertEquals(listOf(CarControl.WIPER, CarControl.WIPER, CarControl.WIPER, CarControl.HAZARD, CarControl.WIPER, CarControl.WIPER, CarControl.WIPER),
            (lock("c2_gates") as StoryLockDefinition.CarSequence).answer)
        assertEquals(listOf(CarControl.TURN_LEFT, CarControl.TURN_RIGHT, CarControl.TURN_RIGHT), (lock("c3_channel") as StoryLockDefinition.CarSequence).answer)
        assertEquals(StoryLockDefinition.Numeric("1799"), lock("c3_year"))
        assertEquals(4, (lock("c4_beacon") as StoryLockDefinition.CarSequence).answer.size)
        assertEquals(listOf("water", "grain", "licorice"), (lock("c4_vaccine") as StoryLockDefinition.Order).answer)
        assertEquals("b4172", (lock("c5_bottle") as StoryLockDefinition.Choice).answer)
    }

    @Test fun perfectRunEndsWithTheTrueEndingAndAllDocuments() {
        val end = play()
        assertEquals(StoryPhase.ENDED, end.phase)
        assertEquals(0, end.awakening)
        assertTrue(end.truthSolved)
        assertEquals(EndingKind.TRUE, end.endingKind)
        assertEquals(episode.chapters.mapNotNull { it.clue }, end.clues)
        assertEquals(episode.chapters.mapNotNull { it.item }, end.items)
        assertEquals(episode.documents.map { it.id }, end.visibleDocuments.map { it.id })
        assertEquals(4, end.clues.size)
    }

    @Test fun wrongVerdictStillFinishesButGivesTheNormalEnding() {
        val end = play({ _, step -> if (step.id == "c5_truth_why") wrong(step.lock) else answer(step.lock) })
        assertEquals(StoryPhase.ENDED, end.phase)
        assertFalse(end.truthSolved)
        assertEquals(EndingKind.NORMAL, end.endingKind)
        assertEquals(listOf("c5_truth_why"), end.puzzleProgress.last().verdictMisses)
    }

    @Test fun hintCostsRaiseAwakeningAndHeavyHintUseGivesTheBadEnding() {
        var state = atPuzzle(StoryEngine.start(episode, StoryCatalog.CASE_FLOW_VERSION))
        state = StoryEngine.showPuzzleHint(state); assertEquals(0, state.awakening)
        state = StoryEngine.showPuzzleHint(state); assertEquals(1, state.awakening)
        state = StoryEngine.showPuzzleHint(state); assertEquals(3, state.awakening)
        assertEquals(state, StoryEngine.showPuzzleHint(state))
        assertEquals(3, state.hintsUsed)
        val end = play(hintsPerStep = 3)
        assertTrue(end.awakening >= SiyeokCase.BAD_THRESHOLD)
        assertEquals(EndingKind.BAD, end.endingKind)
    }

    @Test fun stepAdvanceResetsHintsAndRejectsRepeatedSubmission() {
        var state = atPuzzle(StoryEngine.start(episode, StoryCatalog.CASE_FLOW_VERSION))
        state = StoryEngine.showPuzzleHint(state)
        val first = state.currentPuzzle!!
        val next = StoryEngine.submitPuzzle(state, answer(first.lock))
        assertEquals(StoryPuzzleResult.CORRECT, next.result)
        assertEquals(1, next.state.currentStepIndex)
        assertEquals(0, next.state.puzzleProgressForCurrent!!.hintsShown)
        assertEquals(1, next.state.puzzleProgressForCurrent!!.hintsUsed)
        assertEquals(StoryPhase.PUZZLE, next.state.phase)
        assertTrue(next.state.clues.isEmpty())
        // 이전 단계 답은 다음 단계에서 오답이거나 형식 오류다.
        assertTrue(StoryEngine.submitPuzzle(next.state, answer(first.lock)).result != StoryPuzzleResult.CORRECT)
    }

    @Test fun trapAddsAwakeningAndSpecificFeedback() {
        val gates = episode.chapters[1].puzzleSteps.single { it.id == "c2_gates" }
        var state = atPuzzle(StoryEngine.start(episode, StoryCatalog.CASE_FLOW_VERSION).copy(chapterIndex = 1))
        while (state.currentPuzzle!!.id != gates.id) state = StoryEngine.submitPuzzle(state, answer(state.currentPuzzle!!.lock)).state
        val trap = StoryEngine.submitPuzzle(state, StoryPuzzleInput.CarSequence(List(7) { CarControl.WIPER }))
        assertEquals(StoryPuzzleResult.WRONG, trap.result)
        assertEquals(2, trap.state.awakening)
        assertTrue(trap.note!!.contains("물이 멎었다"))
        val shallow = StoryEngine.submitPuzzle(trap.state, StoryPuzzleInput.CarSequence(listOf(CarControl.HAZARD) + List(6) { CarControl.WIPER }))
        assertEquals(2, shallow.state.awakening)
        assertTrue(shallow.note!!.contains("얕아"))
        val arches = episode.chapters[1].puzzleSteps.single { it.id == "c2_arches" }
        assertEquals("그건 남쪽 수문 이야기다. 지금 네 앞의 문을 세라.", arches.feedback("9")?.message)
    }

    @Test fun documentsUnlockOnlyAfterTheirStep() {
        var state = atPuzzle(StoryEngine.start(episode, StoryCatalog.CASE_FLOW_VERSION))
        assertEquals(listOf("doc_physician_1"), state.visibleDocuments.map { it.id })
        while (state.currentPuzzle!!.id != "c1_diary_lock") state = StoryEngine.submitPuzzle(state, answer(state.currentPuzzle!!.lock)).state
        assertFalse(state.visibleDocuments.any { it.id == "doc_yeoni" })
        state = StoryEngine.submitPuzzle(state, StoryPuzzleInput.Numeric("0213")).state
        assertTrue(state.visibleDocuments.any { it.id == "doc_yeoni" })
        assertEquals(StoryPhase.REVEALED, state.phase)
        assertNull(StoryEngine.start(episode, 3).endingKind)
    }

    @Test fun hiddenLettersSpellTheBeaconClue() {
        val body = episode.documents.single { it.id == "doc_physician_3" }.body
        val marked = Regex("【(.)】").findAll(body).joinToString("") { it.groupValues[1] }
        assertEquals("경계를넘었다", marked)
    }

    @Test fun storedCaseTripsValidateAcrossEveryStepAndCannotRollBack() {
        var state = StoryEngine.start(episode, StoryCatalog.CASE_FLOW_VERSION)
        var trip = StoredTrip(TourMode.MOCK, "case-session", chapterIndex = 0, phase = StoryPhase.EN_ROUTE,
            startedAtEpochMillis = clock.wallTimeMillis, flowVersion = StoryCatalog.CASE_FLOW_VERSION)
        var store = upsertTrip(TripStoreState(), trip)
        fun save(next: StoryState) {
            val records = TourCatalog.checkpoints.take(next.chapterIndex + if (next.phase.ordinal >= StoryPhase.EXPLORING.ordinal) 1 else 0)
                .map { TourStartRecord("case-session", it.id, TourMode.MOCK, clock) }
            trip = trip.copy(chapterIndex = next.chapterIndex, phase = next.phase, items = next.items, clues = next.clues,
                puzzleProgress = next.puzzleProgress, tourRecords = records)
            validateStoredTrip(trip)
            store = upsertTrip(store, trip)
            state = next
        }
        episode.chapters.indices.forEach { index ->
            save(StoryEngine.verifiedArrival(state)); save(StoryEngine.leaveCar(state)); save(StoryEngine.returnToCar(state))
            while (state.phase == StoryPhase.PUZZLE) {
                if (state.currentPuzzle!!.hints.isNotEmpty()) save(StoryEngine.showPuzzleHint(state))
                val before = trip
                save(StoryEngine.submitPuzzle(state, answer(state.currentPuzzle!!.lock)).state)
                assertTrue(runCatching { upsertTrip(store, before) }.isFailure)
            }
            if (index < episode.finalChapterIndex) {
                save(StoryEngine.next(state))
                if (state.phase == StoryPhase.FINAL_REVEAL) save(StoryEngine.next(state))
            }
        }
        assertEquals(StoryPhase.ENDED, trip.phase)
        assertNotNull(state.endingKind)
        val forged = trip.copy(puzzleProgress = trip.puzzleProgress.map { it.copy(verdictMisses = listOf("c1_plaque")) })
        assertTrue(runCatching { validateStoredTrip(forged) }.isFailure)
    }
}
