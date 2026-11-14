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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TruthRetryTest {
    private val episode = StoryCatalog.episodeFor(StoryCatalog.CASE_FLOW_VERSION)
    private val clock = TourClock(1_800_000_000_000L, 1_000_000L, "boot-one")

    private fun answer(lock: StoryLockDefinition): StoryPuzzleInput = when (lock) {
        is StoryLockDefinition.Numeric -> StoryPuzzleInput.Numeric(lock.answer)
        is StoryLockDefinition.Choice -> StoryPuzzleInput.Choice(lock.answer)
        is StoryLockDefinition.Order -> StoryPuzzleInput.Order(lock.answer)
        is StoryLockDefinition.CarSequence -> StoryPuzzleInput.CarSequence(lock.answer)
        else -> error("unexpected lock in flow3")
    }
    private fun wrong(lock: StoryLockDefinition) = StoryPuzzleInput.Choice((lock as StoryLockDefinition.Choice).options.first { it.id != lock.answer }.id)

    /** 진상 추리 하나를 틀린 채 끝까지 간 상태(일반 엔딩). [hints]만큼 단계마다 힌트를 연다. */
    private fun normalEnding(hints: Int = 0): StoryState {
        var state = StoryEngine.start(episode, StoryCatalog.CASE_FLOW_VERSION)
        episode.chapters.indices.forEach { index ->
            state = StoryEngine.returnToCar(StoryEngine.leaveCar(StoryEngine.verifiedArrival(state)))
            while (state.phase == StoryPhase.PUZZLE) {
                val step = state.currentPuzzle!!
                repeat(hints) { state = StoryEngine.showPuzzleHint(state) }
                state = StoryEngine.submitPuzzle(state, if (step.id == "c5_truth_why") wrong(step.lock) else answer(step.lock)).state
            }
            if (index < episode.finalChapterIndex) {
                state = StoryEngine.next(state)
                if (state.phase == StoryPhase.FINAL_REVEAL) state = StoryEngine.next(state)
            }
        }
        return state
    }

    private fun correct(state: StoryState) = state.truthSteps.associate { it.id to answer(it.lock) }

    @Test fun correctRetryTurnsTheNormalEndingIntoTheTrueEndingOnce() {
        val normal = normalEnding()
        assertEquals(EndingKind.NORMAL, normal.endingKind)
        assertTrue(normal.canRetryTruth)
        val (after, ok) = TruthRetry.submit(normal, correct(normal))!!
        assertTrue(ok)
        assertEquals(EndingKind.TRUE, after.endingKind)
        assertEquals(normal.awakening, after.awakening)
        assertEquals(normal.puzzleProgress.sumOf { it.wrongAttempts }, after.puzzleProgress.sumOf { it.wrongAttempts })
        assertFalse(after.canRetryTruth)
        assertNull(TruthRetry.submit(after, correct(after)))
    }

    @Test fun wrongRetryKeepsTheNormalEndingAndUsesTheOnlyChance() {
        val normal = normalEnding()
        val answers = correct(normal) + ("c5_truth_who" to wrong(normal.truthSteps.first { it.id == "c5_truth_who" }.lock))
        val (after, ok) = TruthRetry.submit(normal, answers)!!
        assertFalse(ok)
        assertEquals(EndingKind.NORMAL, after.endingKind)
        assertTrue(after.truthRetryUsed)
        assertFalse(after.canRetryTruth)
    }

    @Test fun missingAnswersTrueEndingsAndBadEndingsCannotRetry() {
        val normal = normalEnding()
        assertNull(TruthRetry.submit(normal, mapOf("c5_truth_who" to answer(normal.truthSteps.first().lock))))
        val bad = normalEnding(hints = 3)
        assertEquals(EndingKind.BAD, bad.endingKind)
        assertFalse(bad.canRetryTruth)
        assertNull(TruthRetry.submit(bad, correct(bad)))
        val notEnded = normal.copy(phase = StoryPhase.REVEALED)
        assertFalse(notEnded.canRetryTruth)
    }

    @Test fun storedRetryAndEndTimeValidateAndCannotBeUndone() {
        val normal = normalEnding()
        val records = TourCatalog.checkpoints.map { TourStartRecord("s", it.id, TourMode.MOCK, clock) }
        val base = StoredTrip(TourMode.MOCK, "s", chapterIndex = normal.chapterIndex, phase = normal.phase, items = normal.items,
            clues = normal.clues, startedAtEpochMillis = clock.wallTimeMillis, flowVersion = StoryCatalog.CASE_FLOW_VERSION,
            puzzleProgress = normal.puzzleProgress, tourRecords = records, endedAtEpochMillis = clock.wallTimeMillis + 3_600_000)
        validateStoredTrip(base)
        val store = upsertTrip(TripStoreState(), base)
        val retried = base.copy(puzzleProgress = TruthRetry.submit(normal, correct(normal))!!.first.puzzleProgress)
        validateStoredTrip(retried)
        val saved = upsertTrip(store, retried)
        // 결과를 되돌리거나 뒤집을 수 없고, 끝난 시각도 바꿀 수 없다.
        assertTrue(runCatching { upsertTrip(saved, base) }.isFailure)
        assertTrue(runCatching { upsertTrip(saved, retried.copy(puzzleProgress = retried.puzzleProgress.map { it.copy(truthRetrySolved = false) })) }.isFailure)
        assertTrue(runCatching { upsertTrip(saved, retried.copy(endedAtEpochMillis = clock.wallTimeMillis + 1)) }.isFailure)
        // 엔딩 전에는 끝난 시각이나 다시 풀기 기록이 있을 수 없다.
        assertTrue(runCatching { validateStoredTrip(base.copy(endedAtEpochMillis = clock.wallTimeMillis - 1)) }.isFailure)
        val forged = base.copy(puzzleProgress = base.puzzleProgress.map { it.copy(truthRetrySolved = true) })
        assertTrue(runCatching { validateStoredTrip(forged) }.isFailure)
    }
}

class ReportLineTest {
    @Test fun lastSentenceSkipsQuotedLinesInTheMiddle() {
        assertEquals("창밖으로 원천호수의 불빛이 물 위에 길게 번진다.", com.team.carescape.ui.lastSentence("학예사가 일지를 덮는다. \"이 이름… 아까까지는 없었는데.\" 창밖으로 원천호수의 불빛이 물 위에 길게 번진다."))
        assertEquals("\"아직 끝나지 않았다.\"", com.team.carescape.ui.lastSentence("일지 한 줄이 떠오른다. \"아직 끝나지 않았다.\""))
        StoryCatalog.episodeFor(StoryCatalog.CURRENT_FLOW_VERSION).endings.values.forEach { assertTrue(com.team.carescape.ui.lastSentence(it.epilogue).length in 5..80) }
    }
}
