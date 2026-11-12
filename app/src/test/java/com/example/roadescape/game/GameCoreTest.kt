package com.example.roadescape.game

import com.example.roadescape.model.AcquisitionType
import com.example.roadescape.model.CheckpointStatus
import com.example.roadescape.model.Direction
import com.example.roadescape.model.GamePackage
import com.example.roadescape.model.GameSession
import com.example.roadescape.model.LockDefinition
import com.example.roadescape.model.LockInput
import com.example.roadescape.model.ParkingState
import java.io.File
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GameCoreTest {
    private val content: GamePackage = Json.decodeFromString(File("src/main/assets/demo_package.json").readText())
    private val parked = PlayEnvironment(foreground = true, parking = ParkingState.PARKED)

    private fun initial(id: String = "session-1") = GameController.newSession(content, id)
    private fun arrive(session: GameSession): GameSession {
        val checkpoint = checkNotNull(GameController.currentCheckpoint(content, session))
        return GameController.reduce(content, session, GameCommand.Arrive(session.sessionId, checkpoint.id, 123L), parked).session
    }
    private fun submit(session: GameSession, puzzleId: String, input: LockInput, environment: PlayEnvironment = parked) =
        GameController.reduce(content, session, GameCommand.Submit(session.sessionId, puzzleId, input), environment)
    private fun skip(session: GameSession): GameSession {
        val checkpoint = checkNotNull(GameController.currentCheckpoint(content, session))
        return GameController.reduce(content, arrive(session), GameCommand.Skip(session.sessionId, checkpoint.puzzleId), parked).session
    }

    @Test fun suppliedAssetIsValidAndPreservesLeadingZero() {
        assertTrue(ContentValidator.validate(content).isEmpty())
        assertEquals("0427", (content.puzzles.first().lock as LockDefinition.Numeric).answer)
        assertTrue(GameController.validateSession(content, initial()).isEmpty())
    }

    @Test fun numericSeparatesCorrectIncorrectAndFormatErrors() {
        val definition = content.puzzles[0]
        assertEquals(LockEvaluator.Result.Correct, LockEvaluator.evaluate(definition, LockInput.Numeric("0427")))
        assertEquals(LockEvaluator.Result.InvalidInput, LockEvaluator.evaluate(definition, LockInput.Numeric("427")))
        assertEquals(LockEvaluator.Result.Incorrect, LockEvaluator.evaluate(definition, LockInput.Numeric("0428")))
        assertEquals(LockEvaluator.Result.InvalidInput, LockEvaluator.evaluate(definition, LockInput.Numeric("０４２７")))
        assertEquals(LockEvaluator.Result.InvalidInput, LockEvaluator.evaluate(definition, LockInput.Numeric(" 427")))
        val session = arrive(initial())
        assertEquals(session, submit(session, "p_01", LockInput.Numeric("427")).session)
        val incorrect = submit(session, "p_01", LockInput.Numeric("0428"))
        assertEquals(GameFeedback.INCORRECT, incorrect.feedback)
        assertEquals(1, incorrect.session.progress[0].wrongAttempts)
        assertTrue(incorrect.session.acquiredClues.isEmpty())
    }

    @Test fun sequenceComparesFullOrderAndSupportsRepeatedDirections() {
        val definition = content.puzzles[1]
        val answer = listOf(Direction.UP, Direction.RIGHT, Direction.DOWN, Direction.LEFT)
        assertEquals(LockEvaluator.Result.Correct, LockEvaluator.evaluate(definition, LockInput.Sequence(answer)))
        assertEquals(LockEvaluator.Result.Incorrect, LockEvaluator.evaluate(definition, LockInput.Sequence(answer.reversed())))
        assertEquals(LockEvaluator.Result.InvalidInput, LockEvaluator.evaluate(definition, LockInput.Sequence(answer.take(3))))
        assertEquals(
            LockEvaluator.Result.Correct,
            LockEvaluator.evaluate(LockDefinition.Sequence(listOf(Direction.UP, Direction.UP)), LockInput.Sequence(listOf(Direction.UP, Direction.UP))),
        )
    }

    @Test fun stateCombinationRequiresBothKeysAndValidRange() {
        val definition = content.puzzles[2]
        fun evaluate(values: Map<String, Int>) = LockEvaluator.evaluate(definition, LockInput.StateCombination(values))
        assertEquals(LockEvaluator.Result.Correct, evaluate(mapOf("channel" to 3, "level" to 2)))
        assertEquals(LockEvaluator.Result.Incorrect, evaluate(mapOf("channel" to 3, "level" to 1)))
        assertEquals(LockEvaluator.Result.InvalidInput, evaluate(mapOf("channel" to 3)))
        assertEquals(LockEvaluator.Result.InvalidInput, evaluate(mapOf("channel" to 3, "level" to 6)))
        assertEquals(LockEvaluator.Result.InvalidInput, evaluate(mapOf("channel" to -1, "level" to 2)))
        assertEquals(LockEvaluator.Result.InvalidInput, evaluate(mapOf("channel" to 3, "level" to 2, "extra" to 1)))
        assertEquals(LockEvaluator.Result.InvalidInput, LockEvaluator.evaluate(definition, LockInput.Numeric("32")))
    }

    @Test fun visitAndParkingGateProtectProgressAndFutureCheckpoints() {
        val session = initial()
        assertEquals(GateReason.ARRIVAL_REQUIRED, submit(session, "p_01", LockInput.Numeric("0427")).gate)
        val visited = arrive(session)
        assertEquals(GateReason.READY, GameController.gate(content, visited, parked, "p_01"))
        assertEquals(GateReason.NOT_CURRENT, GameController.gate(content, visited, parked, "p_02"))
        for (parking in listOf(ParkingState.UNKNOWN, ParkingState.DRIVING)) {
            val result = submit(visited, "p_01", LockInput.Numeric("0427"), parked.copy(parking = parking))
            assertEquals(visited, result.session)
            assertEquals(GameFeedback.BLOCKED, result.feedback)
        }
        assertEquals(GateReason.BACKGROUND, submit(visited, "p_01", LockInput.Numeric("0427"), parked.copy(foreground = false)).gate)
        assertEquals(GateReason.STORAGE_ERROR, submit(visited, "p_01", LockInput.Numeric("0427"), parked.copy(storageReady = false)).gate)
        assertEquals(GateReason.BUSY, submit(visited, "p_01", LockInput.Numeric("0427"), parked.copy(busy = true)).gate)
        assertEquals(session, GameController.reduce(content, session, GameCommand.Arrive(session.sessionId, "cp_02", 1), parked).session)
    }

    @Test fun completionIsAtomicAndRepeatedOrStaleCommandsCannotGrantAgain() {
        val visited = arrive(initial())
        val solved = submit(visited, "p_01", LockInput.Numeric("0427")).session
        assertEquals(CheckpointStatus.SOLVED, solved.progress[0].status)
        assertEquals(listOf("clue_01"), solved.acquiredClues.map { it.clueId })
        assertEquals(solved, submit(solved, "p_01", LockInput.Numeric("0427")).session)
        assertEquals(solved, submit(solved, "p_01", LockInput.Numeric("9999")).session)
        assertEquals(0, solved.progress[0].wrongAttempts)
        assertEquals("cp_02", GameController.currentCheckpoint(content, solved)?.id)
        assertEquals(GateReason.ARRIVAL_REQUIRED, submit(solved, "p_02", LockInput.Sequence(emptyList())).gate)
        assertNull(solved.progress[1].arrivedAt)
        val newGame = initial("session-2")
        val oldEvent = GameCommand.Submit(visited.sessionId, "p_01", LockInput.Numeric("0427"))
        val result = GameController.reduce(content, newGame, oldEvent, parked)
        assertEquals(GateReason.STALE_EVENT, result.gate)
        assertEquals(newGame, result.session)
    }

    @Test fun duplicateArrivalAndStartAreIdempotent() {
        val visited = arrive(initial())
        assertEquals(visited, arrive(visited))
        val command = GameCommand.StartPuzzle(visited.sessionId, "p_01")
        val started = GameController.reduce(content, visited, command, parked).session
        assertEquals(CheckpointStatus.IN_PROGRESS, started.progress[0].status)
        assertEquals(started, GameController.reduce(content, started, command, parked).session)
    }

    @Test fun hintStepsAndAnswerHistorySurviveRoundTripWithoutSolving() {
        var session = arrive(initial())
        val hint = GameCommand.RevealHint(session.sessionId, "p_01", 1)
        session = GameController.reduce(content, session, hint, parked).session
        assertEquals(1, session.progress[0].revealedHintCount)
        assertEquals(session, GameController.reduce(content, session, hint, parked).session)
        session = GameController.reduce(content, session, GameCommand.RevealHint(session.sessionId, "p_01", 2), parked).session
        session = GameController.reduce(content, session, GameCommand.RevealAnswer(session.sessionId, "p_01"), parked).session
        val restored = Json.decodeFromString<GameSession>(Json.encodeToString(GameSession.serializer(), session))
        assertEquals(session, restored)
        assertEquals(2, restored.progress[0].revealedHintCount)
        assertTrue(restored.progress[0].answerRevealed)
        assertFalse(restored.progress[0].status.isComplete)
        assertTrue(restored.acquiredClues.isEmpty())
        val skipped = GameController.reduce(content, restored, GameCommand.Skip(restored.sessionId, "p_01"), parked).session
        assertEquals(CheckpointStatus.SKIPPED, skipped.progress[0].status)
        assertTrue(skipped.progress[0].answerRevealed)
        assertEquals(AcquisitionType.SKIPPED, skipped.acquiredClues.single().acquisitionType)
        assertTrue(GameController.validateSession(content, skipped).isEmpty())
    }

    @Test fun revealingAnswerStillAllowsDirectSolveAndRetainsDisclosure() {
        val session = arrive(initial())
        val revealed = GameController.reduce(content, session, GameCommand.RevealAnswer(session.sessionId, "p_01"), parked).session
        val solved = submit(revealed, "p_01", LockInput.Numeric("0427")).session
        assertEquals(CheckpointStatus.SOLVED, solved.progress[0].status)
        assertTrue(solved.progress[0].answerRevealed)
        assertEquals(AcquisitionType.SOLVED, solved.acquiredClues.single().acquisitionType)
    }

    @Test fun interruptedStateBlocksAllPuzzleMutationsThenResumes() {
        val session = arrive(initial())
        val commands = listOf(
            GameCommand.StartPuzzle(session.sessionId, "p_01"),
            GameCommand.Submit(session.sessionId, "p_01", LockInput.Numeric("0427")),
            GameCommand.RevealHint(session.sessionId, "p_01", 1),
            GameCommand.RevealAnswer(session.sessionId, "p_01"),
            GameCommand.Skip(session.sessionId, "p_01"),
        )
        for (parking in listOf(ParkingState.DRIVING, ParkingState.UNKNOWN)) {
            commands.forEach { command ->
                val result = GameController.reduce(content, session, command, parked.copy(parking = parking))
                assertEquals(GameFeedback.BLOCKED, result.feedback)
                assertEquals(session, result.session)
            }
        }
        assertEquals(GameFeedback.CORRECT, submit(session, "p_01", LockInput.Numeric("0427")).feedback)
    }

    @Test fun threeCheckpointsCompleteInOrderWithDistinctOutcomesAndRewards() {
        var session = arrive(initial())
        session = submit(session, "p_01", LockInput.Numeric("0427")).session
        session = skip(session)
        session = arrive(session)
        session = submit(session, "p_03", LockInput.StateCombination(mapOf("channel" to 3, "level" to 2))).session
        assertNull(GameController.currentCheckpoint(content, session))
        assertEquals(listOf(CheckpointStatus.SOLVED, CheckpointStatus.SKIPPED, CheckpointStatus.SOLVED), session.progress.map { it.status })
        assertEquals(3, session.acquiredClues.size)
        assertEquals(GateReason.COMPLETED, GameController.gate(content, session, parked))
        assertTrue(GameController.validateSession(content, session).isEmpty())
        assertNotEquals(initial().sessionId, initial("another-session").sessionId)
    }

    @Test fun contentValidationRejectsDuplicatesReferencesAndMalformedAnswers() {
        assertTrue(ContentValidator.validate(content.copy(checkpoints = content.checkpoints + content.checkpoints.first())).isNotEmpty())
        assertTrue(ContentValidator.validate(content.copy(puzzles = content.puzzles + content.puzzles.first())).isNotEmpty())
        assertTrue(ContentValidator.validate(content.copy(clues = content.clues.dropLast(1))).isNotEmpty())
        val wrongNumber = content.puzzles[0].copy(lock = LockDefinition.Numeric(4, "427"))
        assertTrue(ContentValidator.validate(content.copy(puzzles = listOf(wrongNumber) + content.puzzles.drop(1))).isNotEmpty())
        val invalidRange = content.puzzles[2].copy(lock = LockDefinition.StateCombination(7, 2))
        assertTrue(ContentValidator.validate(content.copy(puzzles = content.puzzles.take(2) + invalidRange)).isNotEmpty())
        val missingReference = content.checkpoints[0].copy(puzzleId = "missing")
        assertTrue(ContentValidator.validate(content.copy(checkpoints = listOf(missingReference) + content.checkpoints.drop(1))).isNotEmpty())
    }

    @Test fun sharedClueRewardRetainsFirstSourceWithoutDuplicateGrant() {
        val shared = content.copy(puzzles = content.puzzles.mapIndexed { index, puzzle ->
            if (index == 1) puzzle.copy(rewardClueIds = listOf("clue_01", "clue_02")) else puzzle
        })
        assertTrue(ContentValidator.validate(shared).isEmpty())
        var session = GameController.newSession(shared, "shared-clue-session")
        for (checkpoint in shared.checkpoints.take(2)) {
            session = GameController.reduce(shared, session, GameCommand.Arrive(session.sessionId, checkpoint.id, 123), parked).session
            session = GameController.reduce(shared, session, GameCommand.Skip(session.sessionId, checkpoint.puzzleId), parked).session
        }
        assertEquals(listOf("clue_01", "clue_02"), session.acquiredClues.map { it.clueId })
        assertEquals("cp_01", session.acquiredClues.first().sourceCheckpointId)
        assertTrue(GameController.validateSession(shared, session).isEmpty())
    }

    @Test(expected = SerializationException::class)
    fun unknownPuzzleTypeIsRejectedDuringDecode() {
        Json.decodeFromString<GamePackage>(File("src/main/assets/demo_package.json").readText().replace("NUMERIC", "UNKNOWN_TYPE"))
    }

    @Test fun invalidSavedProgressIsRejectedWithoutInventingRewards() {
        val initial = initial()
        assertTrue(GameController.validateSession(content, initial.copy(schemaVersion = 99)).isNotEmpty())
        assertTrue(GameController.validateSession(content, initial.copy(contentVersion = 999)).isNotEmpty())
        val forged = initial.copy(progress = initial.progress.mapIndexed { index, p ->
            if (index == 1) p.copy(status = CheckpointStatus.SOLVED, arrivedAt = 1L) else p
        })
        assertTrue(GameController.validateSession(content, forged).isNotEmpty())
        val solved = submit(arrive(initial), "p_01", LockInput.Numeric("0427")).session
        assertTrue(GameController.validateSession(content, solved.copy(acquiredClues = emptyList())).isNotEmpty())
        val wrongProvenance = solved.copy(acquiredClues = solved.acquiredClues.map { it.copy(acquisitionType = AcquisitionType.SKIPPED) })
        assertTrue(GameController.validateSession(content, wrongProvenance).isNotEmpty())
        val futureSchema = arrive(initial).copy(schemaVersion = 99)
        assertEquals(GateReason.STORAGE_ERROR, submit(futureSchema, "p_01", LockInput.Numeric("0427")).gate)
    }
}
