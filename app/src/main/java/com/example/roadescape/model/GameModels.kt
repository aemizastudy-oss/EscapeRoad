package com.example.roadescape.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GamePackage(
    val id: String,
    val contentVersion: Int,
    val title: String,
    val checkpoints: List<CheckpointDefinition>,
    val puzzles: List<PuzzleDefinition>,
    val clues: List<ClueDefinition>,
    val requiresTour: Boolean = false,
)

@Serializable
data class CheckpointDefinition(val id: String, val title: String, val puzzleId: String)

@Serializable
data class PuzzleDefinition(
    val id: String,
    val title: String,
    val prompt: String,
    val lock: LockDefinition,
    val hints: List<String>,
    val solution: String,
    val rewardClueIds: List<String>,
) {
    val type: PuzzleType
        get() = when (lock) {
            is LockDefinition.Numeric -> PuzzleType.NUMERIC
            is LockDefinition.Sequence -> PuzzleType.SEQUENCE
            is LockDefinition.StateCombination -> PuzzleType.STATE_COMBINATION
        }
}

enum class PuzzleType { NUMERIC, SEQUENCE, STATE_COMBINATION }

@Serializable
sealed interface LockDefinition {
    @Serializable
    @SerialName("NUMERIC")
    data class Numeric(val length: Int, val answer: String) : LockDefinition

    @Serializable
    @SerialName("SEQUENCE")
    data class Sequence(val answer: List<Direction>) : LockDefinition

    @Serializable
    @SerialName("STATE_COMBINATION")
    data class StateCombination(
        val channel: Int,
        val level: Int,
        val min: Int = 0,
        val max: Int = 5,
    ) : LockDefinition
}

@Serializable
enum class Direction { UP, RIGHT, DOWN, LEFT }

sealed interface LockInput {
    data class Numeric(val value: String) : LockInput
    data class Sequence(val values: List<Direction>) : LockInput
    data class StateCombination(val values: Map<String, Int>) : LockInput
}

@Serializable
data class ClueDefinition(val id: String, val title: String, val body: String)

@Serializable
data class GameSession(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val sessionId: String,
    val packageId: String,
    val contentVersion: Int,
    val progress: List<CheckpointProgress>,
    val acquiredClues: List<AcquiredClue> = emptyList(),
    val sourceMode: TourMode = TourMode.MOCK,
) {
    companion object { const val CURRENT_SCHEMA_VERSION = 1 }
}

@Serializable
data class CheckpointProgress(
    val checkpointId: String,
    val status: CheckpointStatus = CheckpointStatus.NOT_STARTED,
    val arrivedAt: Long? = null,
    val wrongAttempts: Int = 0,
    val revealedHintCount: Int = 0,
    val answerRevealed: Boolean = false,
)

@Serializable
enum class CheckpointStatus {
    NOT_STARTED, IN_PROGRESS, SOLVED, SKIPPED;

    val isComplete: Boolean get() = this == SOLVED || this == SKIPPED
}

@Serializable
data class AcquiredClue(
    val clueId: String,
    val sourceCheckpointId: String,
    val acquisitionType: AcquisitionType,
)

@Serializable
enum class AcquisitionType { SOLVED, SKIPPED }

enum class ParkingState { PARKED, DRIVING, UNKNOWN }
