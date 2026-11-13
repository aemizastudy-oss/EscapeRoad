package com.team.carescape.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StoryPuzzlesTest {
    private val episode get() = StoryCatalog.episodeFor(StoryCatalog.CURRENT_FLOW_VERSION)
    private fun definition(lock: StoryLockDefinition) = StoryPuzzleDefinition(
        "test", "테스트 자물쇠", "입력하세요", listOf("첫 힌트", "둘째 힌트"), "해설", lock,
    )
    private fun inputFor(lock: StoryLockDefinition): StoryPuzzleInput = when (lock) {
        is StoryLockDefinition.Numeric -> StoryPuzzleInput.Numeric(lock.answer)
        is StoryLockDefinition.Directions -> StoryPuzzleInput.Directions(lock.answer)
        is StoryLockDefinition.Device -> StoryPuzzleInput.Device(lock.channel, lock.level)
    }
    private fun atPuzzle(state: StoryState) = StoryEngine.returnToCar(
        StoryEngine.leaveCar(StoryEngine.verifiedArrival(state)),
    )

    @Test fun numericPreservesLeadingZeroAndSeparatesMalformedFromWrong() {
        val puzzle = definition(StoryLockDefinition.Numeric("0427"))
        assertEquals(StoryPuzzleResult.CORRECT, StoryPuzzles.evaluate(puzzle, StoryPuzzleInput.Numeric("0427")))
        assertEquals(StoryPuzzleResult.WRONG, StoryPuzzles.evaluate(puzzle, StoryPuzzleInput.Numeric("4270")))
        listOf("427", "00427", "０４２７", "04 27", "04a7", "").forEach {
            assertEquals(it, StoryPuzzleResult.INVALID_INPUT, StoryPuzzles.evaluate(puzzle, StoryPuzzleInput.Numeric(it)))
        }
        assertEquals(StoryPuzzleResult.INVALID_INPUT, StoryPuzzles.evaluate(puzzle, StoryPuzzleInput.Device(0, 0)))
    }

    @Test fun directionsRequireTheFullOrderedNativeSequence() {
        val answer = listOf(LockDirection.UP, LockDirection.RIGHT, LockDirection.DOWN, LockDirection.LEFT)
        val puzzle = definition(StoryLockDefinition.Directions(answer))
        assertEquals(StoryPuzzleResult.CORRECT, StoryPuzzles.evaluate(puzzle, StoryPuzzleInput.Directions(answer)))
        assertEquals(StoryPuzzleResult.WRONG, StoryPuzzles.evaluate(puzzle, StoryPuzzleInput.Directions(answer.reversed())))
        assertEquals(StoryPuzzleResult.INVALID_INPUT, StoryPuzzles.evaluate(puzzle, StoryPuzzleInput.Directions(answer.dropLast(1))))
    }

    @Test fun deviceValidatesBothValuesAndRejectsOutOfRangeInput() {
        val puzzle = definition(StoryLockDefinition.Device(3, 2))
        assertEquals(StoryPuzzleResult.CORRECT, StoryPuzzles.evaluate(puzzle, StoryPuzzleInput.Device(3, 2)))
        assertEquals(StoryPuzzleResult.WRONG, StoryPuzzles.evaluate(puzzle, StoryPuzzleInput.Device(2, 3)))
        listOf(StoryPuzzleInput.Device(-1, 2), StoryPuzzleInput.Device(3, 6)).forEach {
            assertEquals(StoryPuzzleResult.INVALID_INPUT, StoryPuzzles.evaluate(puzzle, it))
        }
    }

    @Test fun integratedDefinitionsContainTheFiveApprovedTemporaryAnswers() {
        val locks = episode.chapters.map { it.puzzle!!.lock }
        assertEquals(StoryLockDefinition.Numeric("0427"), locks[0])
        assertEquals(StoryLockDefinition.Directions(listOf(LockDirection.UP, LockDirection.RIGHT, LockDirection.DOWN, LockDirection.LEFT)), locks[1])
        assertEquals(StoryLockDefinition.Device(3, 2), locks[2])
        assertEquals(StoryLockDefinition.Numeric("1500"), locks[3])
        assertEquals(StoryLockDefinition.Directions(listOf(LockDirection.LEFT, LockDirection.UP, LockDirection.RIGHT, LockDirection.DOWN)), locks[4])
    }

    @Test fun tourReturnCreatesAnUnsolvedReceiptWithoutGrantingAnything() {
        val start = StoryEngine.start(episode, 2)
        val puzzle = atPuzzle(start)
        assertEquals(StoryPhase.PUZZLE, puzzle.phase)
        assertEquals(emptyList<String>(), puzzle.items)
        assertEquals(emptyList<String>(), puzzle.clues)
        assertEquals(StoryPuzzleProgress("suwon_01"), puzzle.puzzleProgressForCurrent)
        assertEquals(puzzle, StoryEngine.returnToCar(puzzle))
    }

    @Test fun wrongAnswersAndHintsPersistWhileOnlyCorrectAnswerGrantsOnce() {
        var state = atPuzzle(StoryEngine.start(episode, 2))
        val invalid = StoryEngine.submitPuzzle(state, StoryPuzzleInput.Numeric("42"))
        assertEquals(StoryPuzzleResult.INVALID_INPUT, invalid.result)
        assertEquals(state, invalid.state)
        repeat(2) { state = StoryEngine.submitPuzzle(state, StoryPuzzleInput.Numeric("9999")).state }
        assertEquals(2, state.puzzleProgressForCurrent!!.wrongAttempts)
        repeat(state.currentPuzzle!!.hints.size + 2) { state = StoryEngine.showPuzzleHint(state) }
        assertEquals(state.currentPuzzle!!.hints.size, state.puzzleProgressForCurrent!!.hintsShown)
        assertTrue(state.clues.isEmpty())
        val solved = StoryEngine.submitPuzzle(state, StoryPuzzleInput.Numeric("0427"))
        assertEquals(StoryPuzzleResult.CORRECT, solved.result)
        assertEquals(StoryPhase.REVEALED, solved.state.phase)
        assertTrue(solved.state.puzzleProgressForCurrent!!.solved)
        assertEquals(listOfNotNull(episode.chapters.first().clue), solved.state.clues)
        val duplicate = StoryEngine.submitPuzzle(solved.state, StoryPuzzleInput.Numeric("0427"))
        assertEquals(StoryPuzzleResult.LOCKED, duplicate.result)
        assertEquals(solved.state, duplicate.state)
    }

    @Test fun outOfPhaseMissingReceiptAndAlreadySolvedReceiptCannotAdvance() {
        val start = StoryEngine.start(episode, 2)
        listOf(start, start.copy(phase = StoryPhase.EXPLORING), start.copy(phase = StoryPhase.PUZZLE)).forEach {
            assertEquals(StorySubmission(it, StoryPuzzleResult.LOCKED), StoryEngine.submitPuzzle(it, StoryPuzzleInput.Numeric("0427")))
            assertEquals(it, StoryEngine.showPuzzleHint(it))
        }
        val forged = atPuzzle(start).copy(puzzleProgress = listOf(StoryPuzzleProgress("suwon_01", solved = true)))
        assertEquals(StorySubmission(forged, StoryPuzzleResult.LOCKED), StoryEngine.submitPuzzle(forged, StoryPuzzleInput.Numeric("0427")))
        assertEquals(start.copy(phase = StoryPhase.REVEALED), StoryEngine.next(start.copy(phase = StoryPhase.REVEALED)))
    }

    @Test fun allFiveChaptersNeedDistinctSolvedReceiptsIncludingTheFinalChapter() {
        var state = StoryEngine.start(episode, 2)
        episode.chapters.indices.forEach { index ->
            state = atPuzzle(state)
            assertEquals(index, state.chapterIndex)
            assertEquals(StoryPhase.PUZZLE, state.phase)
            val solved = StoryEngine.submitPuzzle(state, inputFor(state.currentPuzzle!!.lock))
            assertEquals(StoryPuzzleResult.CORRECT, solved.result)
            state = solved.state
            if (index < episode.finalChapterIndex) {
                state = StoryEngine.next(state)
                if (state.phase == StoryPhase.FINAL_REVEAL) state = StoryEngine.next(state)
            }
        }
        assertEquals(StoryPhase.ENDED, state.phase)
        assertEquals(5, state.puzzleProgress.size)
        assertTrue(state.puzzleProgress.all { it.solved })
        assertEquals(TourCatalog.checkpoints.map { it.id }, state.puzzleProgress.map { it.checkpointId })
        assertEquals(episode.chapters.mapNotNull { it.item }, state.items)
        assertEquals(episode.chapters.mapNotNull { it.clue }, state.clues)
        assertEquals(state, StoryEngine.next(state))
    }

    @Test fun legacyFlowRetainsItsOriginalNoPuzzleReturn() {
        val legacy = StoryEngine.returnToCar(StoryEngine.leaveCar(StoryEngine.verifiedArrival(StoryEngine.start(Siyeok.episode))))
        assertEquals(StoryPhase.REVEALED, legacy.phase)
        assertEquals(null, legacy.currentPuzzle)
        assertTrue(legacy.puzzleProgress.isEmpty())
        assertFalse(StoryCatalog.episodeFor(2).chapters.any { it.puzzle == null })
    }
}
