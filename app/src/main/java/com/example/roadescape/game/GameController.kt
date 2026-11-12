package com.example.roadescape.game

import com.example.roadescape.model.AcquiredClue
import com.example.roadescape.model.AcquisitionType
import com.example.roadescape.model.CheckpointDefinition
import com.example.roadescape.model.CheckpointProgress
import com.example.roadescape.model.CheckpointStatus
import com.example.roadescape.model.GamePackage
import com.example.roadescape.model.GameSession
import com.example.roadescape.model.LockInput
import com.example.roadescape.model.ParkingState

data class PlayEnvironment(
    val foreground: Boolean,
    val parking: ParkingState,
    val storageReady: Boolean = true,
    val busy: Boolean = false,
)

enum class GateReason {
    READY, BACKGROUND, PARKING_UNKNOWN, DRIVING, STORAGE_ERROR, BUSY,
    ARRIVAL_REQUIRED, COMPLETED, NOT_CURRENT, STALE_EVENT,
}

enum class GameFeedback {
    ARRIVED, STARTED, INVALID_INPUT, INCORRECT, CORRECT, HINT_REVEALED,
    ANSWER_REVEALED, SKIPPED, NO_CHANGE, BLOCKED,
}

sealed interface GameCommand {
    val sessionId: String

    data class Arrive(override val sessionId: String, val checkpointId: String, val arrivedAt: Long) : GameCommand
    data class StartPuzzle(override val sessionId: String, val puzzleId: String) : GameCommand
    data class Submit(override val sessionId: String, val puzzleId: String, val input: LockInput) : GameCommand
    data class RevealHint(override val sessionId: String, val puzzleId: String, val step: Int) : GameCommand
    data class RevealAnswer(override val sessionId: String, val puzzleId: String) : GameCommand
    data class Skip(override val sessionId: String, val puzzleId: String) : GameCommand
}

data class GameTransition(
    val session: GameSession,
    val feedback: GameFeedback,
    val gate: GateReason? = null,
)

/** A deterministic reducer. Callers serialize events and persist before publishing a transition. */
object GameController {
    fun newSession(content: GamePackage, sessionId: String): GameSession {
        require(sessionId.isNotBlank()) { "세션 ID가 필요합니다." }
        require(ContentValidator.validate(content).isEmpty()) { "검증되지 않은 콘텐츠입니다." }
        return GameSession(
            sessionId = sessionId,
            packageId = content.id,
            contentVersion = content.contentVersion,
            progress = content.checkpoints.map { CheckpointProgress(it.id) },
        )
    }

    fun currentCheckpoint(content: GamePackage, session: GameSession): CheckpointDefinition? =
        content.checkpoints.firstOrNull { checkpoint ->
            session.progress.firstOrNull { it.checkpointId == checkpoint.id }?.status?.isComplete != true
        }

    fun gate(
        content: GamePackage,
        session: GameSession,
        environment: PlayEnvironment,
        puzzleId: String? = null,
    ): GateReason {
        if (!environment.storageReady) return GateReason.STORAGE_ERROR
        if (environment.busy) return GateReason.BUSY
        if (!environment.foreground) return GateReason.BACKGROUND
        when (environment.parking) {
            ParkingState.DRIVING -> return GateReason.DRIVING
            ParkingState.UNKNOWN -> return GateReason.PARKING_UNKNOWN
            ParkingState.PARKED -> Unit
        }
        val checkpoint = currentCheckpoint(content, session) ?: return GateReason.COMPLETED
        if (puzzleId != null && checkpoint.puzzleId != puzzleId) return GateReason.NOT_CURRENT
        val progress = session.progress.firstOrNull { it.checkpointId == checkpoint.id }
            ?: return GateReason.STORAGE_ERROR
        if (progress.arrivedAt == null) return GateReason.ARRIVAL_REQUIRED
        return GateReason.READY
    }

    fun reduce(
        content: GamePackage,
        session: GameSession,
        command: GameCommand,
        environment: PlayEnvironment,
    ): GameTransition {
        fun blocked(reason: GateReason) = GameTransition(session, GameFeedback.BLOCKED, reason)
        if (command.sessionId != session.sessionId) return blocked(GateReason.STALE_EVENT)
        if (session.schemaVersion != GameSession.CURRENT_SCHEMA_VERSION ||
            session.packageId != content.id || session.contentVersion != content.contentVersion
        ) {
            return blocked(GateReason.STORAGE_ERROR)
        }
        if (!environment.storageReady) return blocked(GateReason.STORAGE_ERROR)
        if (environment.busy) return blocked(GateReason.BUSY)
        if (!environment.foreground) return blocked(GateReason.BACKGROUND)
        val checkpoint = currentCheckpoint(content, session) ?: return blocked(GateReason.COMPLETED)
        val progress = session.progress.firstOrNull { it.checkpointId == checkpoint.id }
            ?: return blocked(GateReason.STORAGE_ERROR)
        if (command is GameCommand.Arrive) {
            if (command.checkpointId != checkpoint.id) return blocked(GateReason.NOT_CURRENT)
            if (command.arrivedAt < 0) return GameTransition(session, GameFeedback.INVALID_INPUT)
            if (progress.arrivedAt != null) return GameTransition(session, GameFeedback.NO_CHANGE)
            return changed(session, progress.copy(arrivedAt = command.arrivedAt), GameFeedback.ARRIVED)
        }
        val puzzleId = when (command) {
            is GameCommand.StartPuzzle -> command.puzzleId
            is GameCommand.Submit -> command.puzzleId
            is GameCommand.RevealHint -> command.puzzleId
            is GameCommand.RevealAnswer -> command.puzzleId
            is GameCommand.Skip -> command.puzzleId
            is GameCommand.Arrive -> error("도착 이벤트는 이미 처리됐습니다.")
        }
        val reason = gate(content, session, environment, puzzleId)
        if (reason != GateReason.READY) return blocked(reason)
        val puzzle = content.puzzles.firstOrNull { it.id == puzzleId }
            ?: return blocked(GateReason.STORAGE_ERROR)
        val started = progress.copy(status = CheckpointStatus.IN_PROGRESS)
        return when (command) {
            is GameCommand.StartPuzzle -> if (progress.status == CheckpointStatus.IN_PROGRESS) {
                GameTransition(session, GameFeedback.NO_CHANGE)
            } else changed(session, started, GameFeedback.STARTED)
            is GameCommand.Submit -> when (LockEvaluator.evaluate(puzzle, command.input)) {
                LockEvaluator.Result.InvalidInput -> GameTransition(session, GameFeedback.INVALID_INPUT)
                LockEvaluator.Result.Incorrect -> changed(
                    session,
                    started.copy(wrongAttempts = (started.wrongAttempts.toLong() + 1).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()),
                    GameFeedback.INCORRECT,
                )
                LockEvaluator.Result.Correct -> complete(content, session, started, CheckpointStatus.SOLVED)
            }
            is GameCommand.RevealHint -> when {
                command.step !in 1..puzzle.hints.size -> GameTransition(session, GameFeedback.INVALID_INPUT)
                command.step <= progress.revealedHintCount -> GameTransition(session, GameFeedback.NO_CHANGE)
                command.step != progress.revealedHintCount + 1 -> GameTransition(session, GameFeedback.INVALID_INPUT)
                else -> changed(session, started.copy(revealedHintCount = command.step), GameFeedback.HINT_REVEALED)
            }
            is GameCommand.RevealAnswer -> if (progress.answerRevealed) {
                GameTransition(session, GameFeedback.NO_CHANGE)
            } else changed(session, started.copy(answerRevealed = true), GameFeedback.ANSWER_REVEALED)
            is GameCommand.Skip -> complete(content, session, started, CheckpointStatus.SKIPPED)
            is GameCommand.Arrive -> error("도착 이벤트는 이미 처리됐습니다.")
        }
    }

    private fun changed(session: GameSession, progress: CheckpointProgress, feedback: GameFeedback) = GameTransition(
        session.copy(progress = session.progress.map { if (it.checkpointId == progress.checkpointId) progress else it }),
        feedback,
    )

    private fun complete(
        content: GamePackage,
        session: GameSession,
        progress: CheckpointProgress,
        status: CheckpointStatus,
    ): GameTransition {
        val checkpoint = content.checkpoints.first { it.id == progress.checkpointId }
        val puzzle = content.puzzles.first { it.id == checkpoint.puzzleId }
        val existingIds = session.acquiredClues.map { it.clueId }.toSet()
        val acquisitionType = if (status == CheckpointStatus.SOLVED) AcquisitionType.SOLVED else AcquisitionType.SKIPPED
        val rewards = puzzle.rewardClueIds.filterNot { it in existingIds }.distinct().map {
            AcquiredClue(it, checkpoint.id, acquisitionType)
        }
        val transition = changed(
            session, progress.copy(status = status),
            if (status == CheckpointStatus.SOLVED) GameFeedback.CORRECT else GameFeedback.SKIPPED,
        )
        return transition.copy(session = transition.session.copy(acquiredClues = session.acquiredClues + rewards))
    }

    fun validateSession(content: GamePackage, session: GameSession): List<String> = buildList {
        if (session.schemaVersion != GameSession.CURRENT_SCHEMA_VERSION) add("지원하지 않는 저장 버전입니다.")
        if (session.sessionId.isBlank()) add("저장 세션 ID가 없습니다.")
        if (session.packageId != content.id || session.contentVersion != content.contentVersion) {
            add("저장 데이터와 콘텐츠 버전이 일치하지 않습니다.")
        }
        val ids = content.checkpoints.map { it.id }
        if (session.progress.map { it.checkpointId } != ids) add("저장된 경유지 순서 또는 ID가 잘못됐습니다.")
        var foundIncomplete = false
        val expectedClues = mutableListOf<AcquiredClue>()
        content.checkpoints.forEach { checkpoint ->
            val progress = session.progress.firstOrNull { it.checkpointId == checkpoint.id } ?: return@forEach
            val puzzle = content.puzzles.firstOrNull { it.id == checkpoint.puzzleId } ?: return@forEach
            if (progress.arrivedAt != null && progress.arrivedAt < 0) add("도착 시각이 잘못됐습니다.")
            if (progress.wrongAttempts < 0 || progress.revealedHintCount !in 0..puzzle.hints.size) {
                add("저장된 오답 또는 힌트 횟수가 잘못됐습니다.")
            }
            if (progress.status != CheckpointStatus.NOT_STARTED && progress.arrivedAt == null) {
                add("방문 기록 없이 시작된 퍼즐이 있습니다.")
            }
            if (progress.status == CheckpointStatus.NOT_STARTED &&
                (progress.wrongAttempts != 0 || progress.revealedHintCount != 0 || progress.answerRevealed)
            ) add("시작하지 않은 퍼즐의 사용 이력이 있습니다.")
            if (foundIncomplete && progress != CheckpointProgress(checkpoint.id)) add("경유지 순서가 건너뛰어졌습니다.")
            if (!progress.status.isComplete) foundIncomplete = true
            if (progress.status.isComplete) {
                val type = if (progress.status == CheckpointStatus.SOLVED) AcquisitionType.SOLVED else AcquisitionType.SKIPPED
                puzzle.rewardClueIds.forEach { clueId ->
                    if (expectedClues.none { it.clueId == clueId }) expectedClues += AcquiredClue(clueId, checkpoint.id, type)
                }
            }
        }
        if (session.acquiredClues != expectedClues) add("완료 기록과 단서 보상이 일치하지 않습니다.")
    }
}
