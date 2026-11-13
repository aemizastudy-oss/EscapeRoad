package com.team.carescape.game

import kotlinx.serialization.Serializable

data class StoryPuzzleDefinition(
    val id: String,
    val title: String,
    val prompt: String,
    val hints: List<String>,
    val solution: String,
    val lock: StoryLockDefinition,
)

sealed interface StoryLockDefinition {
    data class Numeric(val answer: String) : StoryLockDefinition {
        init { require(answer.isNotEmpty() && answer.all { it in '0'..'9' }) }
    }
    data class Directions(val answer: List<LockDirection>) : StoryLockDefinition {
        init { require(answer.isNotEmpty()) }
    }
    data class Device(val channel: Int, val level: Int, val min: Int = 0, val max: Int = 5) : StoryLockDefinition {
        init { require(min <= max && channel in min..max && level in min..max) }
    }
}

sealed interface StoryPuzzleInput {
    data class Numeric(val value: String) : StoryPuzzleInput
    data class Directions(val value: List<LockDirection>) : StoryPuzzleInput
    data class Device(val channel: Int, val level: Int) : StoryPuzzleInput
}

enum class StoryPuzzleResult { CORRECT, WRONG, INVALID_INPUT, LOCKED }

/** A receipt belongs to one checkpoint of the containing trip, never to a different mode/session. */
@Serializable
data class StoryPuzzleProgress(
    val checkpointId: String,
    val wrongAttempts: Int = 0,
    val hintsShown: Int = 0,
    val solved: Boolean = false,
)

data class StorySubmission(val state: StoryState, val result: StoryPuzzleResult)

/** Pure native-lock evaluation. No actual vehicle controls or voice recognition are involved. */
object StoryPuzzles {
    fun evaluate(puzzle: StoryPuzzleDefinition, input: StoryPuzzleInput): StoryPuzzleResult {
        val matches = when (val lock = puzzle.lock) {
            is StoryLockDefinition.Numeric -> {
                val value = (input as? StoryPuzzleInput.Numeric)?.value
                    ?: return StoryPuzzleResult.INVALID_INPUT
                if (value.length != lock.answer.length || value.any { it !in '0'..'9' }) {
                    return StoryPuzzleResult.INVALID_INPUT
                }
                value == lock.answer
            }
            is StoryLockDefinition.Directions -> {
                val value = (input as? StoryPuzzleInput.Directions)?.value
                    ?: return StoryPuzzleResult.INVALID_INPUT
                if (value.size != lock.answer.size) return StoryPuzzleResult.INVALID_INPUT
                value == lock.answer
            }
            is StoryLockDefinition.Device -> {
                val value = input as? StoryPuzzleInput.Device ?: return StoryPuzzleResult.INVALID_INPUT
                if (value.channel !in lock.min..lock.max || value.level !in lock.min..lock.max) {
                    return StoryPuzzleResult.INVALID_INPUT
                }
                value.channel == lock.channel && value.level == lock.level
            }
        }
        return if (matches) StoryPuzzleResult.CORRECT else StoryPuzzleResult.WRONG
    }
}
