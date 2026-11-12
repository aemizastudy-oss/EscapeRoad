package com.example.roadescape.game

import com.example.roadescape.model.CheckpointDefinition
import com.example.roadescape.model.CheckpointStatus
import com.example.roadescape.model.ClueDefinition
import com.example.roadescape.model.GamePackage
import com.example.roadescape.model.GameSession
import com.example.roadescape.model.LockDefinition
import com.example.roadescape.model.LockInput
import com.example.roadescape.model.ParkingState
import com.example.roadescape.model.PuzzleDefinition
import com.example.roadescape.model.TourMode
import java.io.File
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameTourGateTest {
    private val content = GamePackage(
        id = "tour-gate-fixture", contentVersion = 1, title = "관광 게이트 검증",
        checkpoints = (1..5).map { CheckpointDefinition("cp_$it", "검증 경유지 $it", "p_$it") },
        puzzles = (1..5).map { PuzzleDefinition("p_$it", "검증 자물쇠 $it", "0, 4, 2, 7을 입력하세요.", LockDefinition.Numeric(4, "0427"), listOf("첫 힌트", "다음 힌트"), "정답은 0427입니다.", listOf("clue_$it")) },
        clues = (1..5).map { ClueDefinition("clue_$it", "검증 단서 $it", "보상 기록 검증") },
        requiresTour = true,
    )
    private val ready = PlayEnvironment(
        foreground = true, parking = ParkingState.PARKED, mode = TourMode.LIVE,
        carUxAllowed = true, stationary = true, tourGate = GateReason.READY, arrivalVerified = true,
    )

    private fun initial() = GameController.newSession(content, "live-tour-session", TourMode.LIVE)
    private fun commands(session: GameSession, puzzleId: String = "p_1") = listOf(
        GameCommand.StartPuzzle(session.sessionId, puzzleId),
        GameCommand.Submit(session.sessionId, puzzleId, LockInput.Numeric("0427")),
        GameCommand.Submit(session.sessionId, puzzleId, LockInput.Numeric("9999")),
        GameCommand.RevealHint(session.sessionId, puzzleId, 1),
        GameCommand.RevealAnswer(session.sessionId, puzzleId),
        GameCommand.Skip(session.sessionId, puzzleId),
    )
    private fun arrived(session: GameSession): GameSession {
        val checkpoint = GameController.currentCheckpoint(content, session)!!
        return GameController.reduce(content, session, GameCommand.Arrive(session.sessionId, checkpoint.id, 1234L), ready).session
    }

    @Test
    fun tourismArrivalRequiresVerifiedLocationPhysicalStopAndUxPermission() {
        val session = initial()
        val arrive = GameCommand.Arrive(session.sessionId, "cp_1", 1234L)
        val blocked = listOf(
            ready.copy(arrivalVerified = false), ready.copy(parking = ParkingState.UNKNOWN),
            ready.copy(parking = ParkingState.DRIVING), ready.copy(stationary = false),
            ready.copy(carUxAllowed = false),
        )
        blocked.forEach { environment ->
            val result = GameController.reduce(content, session, arrive, environment)
            assertEquals(session, result.session)
            assertEquals(GameFeedback.BLOCKED, result.feedback)
            assertEquals(GateReason.TOUR_REQUIRED, result.gate)
        }
        assertEquals(GateReason.BACKGROUND, GameController.reduce(content, session, arrive, ready.copy(foreground = false)).gate)
        assertEquals(GameFeedback.ARRIVED, GameController.reduce(content, session, arrive, ready.copy(tourGate = GateReason.TOUR_REQUIRED)).feedback)
    }

    @Test
    fun everyPuzzleMutationIsRejectedUntilTheTourGateIsReady() {
        val session = arrived(initial())
        for (reason in listOf(null, GateReason.TOUR_REQUIRED, GateReason.STORAGE_ERROR, GateReason.ARRIVAL_REQUIRED)) {
            commands(session).forEach { command ->
                val result = GameController.reduce(content, session, command, ready.copy(tourGate = reason))
                assertEquals("$command must not change progress for $reason", session, result.session)
                assertEquals(GameFeedback.BLOCKED, result.feedback)
                assertEquals(reason ?: GateReason.TOUR_REQUIRED, result.gate)
            }
        }
        assertTrue(session.acquiredClues.isEmpty())
        assertEquals(0, session.progress.first().wrongAttempts)
        assertEquals(0, session.progress.first().revealedHintCount)
        assertFalse(session.progress.first().answerRevealed)
    }

    @Test
    fun modeMismatchBlocksArrivalAndEveryPuzzleCommand() {
        val session = initial()
        val mismatch = ready.copy(mode = TourMode.MOCK)
        val arrival = GameController.reduce(content, session, GameCommand.Arrive(session.sessionId, "cp_1", 1234L), mismatch)
        assertEquals(session, arrival.session)
        assertEquals(GateReason.MODE_MISMATCH, arrival.gate)
        val visited = arrived(session)
        commands(visited).forEach { command ->
            val result = GameController.reduce(content, visited, command, mismatch)
            assertEquals(visited, result.session)
            assertEquals(GateReason.MODE_MISMATCH, result.gate)
        }
    }

    @Test
    fun touringDoesNotBypassPreviousChapterAndNextChapterNeedsItsOwnGate() {
        val session = arrived(initial())
        val futureArrival = GameController.reduce(content, session, GameCommand.Arrive(session.sessionId, "cp_2", 1234L), ready)
        assertEquals(session, futureArrival.session)
        assertEquals(GateReason.NOT_CURRENT, futureArrival.gate)
        commands(session, "p_2").forEach { command ->
            assertEquals(GateReason.NOT_CURRENT, GameController.reduce(content, session, command, ready).gate)
        }
        val completedFirst = GameController.reduce(content, session, GameCommand.Submit(session.sessionId, "p_1", LockInput.Numeric("0427")), ready).session
        assertEquals(CheckpointStatus.SOLVED, completedFirst.progress.first().status)
        assertEquals(null, completedFirst.progress[1].arrivedAt)
        assertEquals(GateReason.TOUR_REQUIRED, GameController.gate(content, completedFirst, ready.copy(tourGate = GateReason.TOUR_REQUIRED), "p_2"))
        assertEquals(GateReason.ARRIVAL_REQUIRED, GameController.gate(content, completedFirst, ready, "p_2"))
    }

    @Test
    fun fiveChaptersCanCompleteWithoutDuplicateRewardsWhenAllGatesAreMet() {
        var session = initial()
        content.checkpoints.forEachIndexed { index, checkpoint ->
            session = arrived(session)
            val command = GameCommand.Submit(session.sessionId, checkpoint.puzzleId, LockInput.Numeric("0427"))
            session = GameController.reduce(content, session, command, ready).session
            assertEquals(index + 1, session.acquiredClues.size)
            assertEquals(session, GameController.reduce(content, session, command, ready).session)
            assertTrue(GameController.validateSession(content, session).isEmpty())
        }
        assertTrue(session.progress.all { it.status == CheckpointStatus.SOLVED })
        assertEquals(null, GameController.currentCheckpoint(content, session))
    }

    @Test
    fun legacyThreeChapterJsonDefaultsToMockAndKeepsItsExistingFlow() {
        val legacyContent = Json.decodeFromString<GamePackage>(File("src/main/assets/demo_package.json").readText())
        assertFalse(legacyContent.requiresTour)
        val original = GameController.newSession(legacyContent, "legacy-session")
        val legacyJson = Json.encodeToString(original)
        assertFalse(legacyJson.contains("sourceMode"))
        val restored = Json.decodeFromString<GameSession>(legacyJson)
        assertEquals(1, restored.schemaVersion)
        assertEquals(TourMode.MOCK, restored.sourceMode)
        val unknown = PlayEnvironment(foreground = true, parking = ParkingState.UNKNOWN)
        val checkpoint = legacyContent.checkpoints.first()
        val visited = GameController.reduce(legacyContent, restored, GameCommand.Arrive(restored.sessionId, checkpoint.id, 1234L), unknown).session
        assertEquals(1234L, visited.progress.first().arrivedAt)
        val parked = unknown.copy(parking = ParkingState.PARKED)
        assertEquals(GateReason.READY, GameController.gate(legacyContent, visited, parked, checkpoint.puzzleId))
        val solved = GameController.reduce(legacyContent, visited, GameCommand.Submit(visited.sessionId, checkpoint.puzzleId, LockInput.Numeric("0427")), parked).session
        assertEquals(CheckpointStatus.SOLVED, solved.progress.first().status)
        assertEquals(1, solved.acquiredClues.size)
    }
}
