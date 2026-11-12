package com.example.roadescape.game

import com.example.roadescape.model.LockDefinition
import com.example.roadescape.model.LockInput
import com.example.roadescape.model.PuzzleDefinition

/** Pure comparison only: no rewards, storage, Android objects, or UI state. */
object LockEvaluator {
    sealed interface Result {
        data object InvalidInput : Result
        data object Incorrect : Result
        data object Correct : Result
    }

    fun evaluate(puzzle: PuzzleDefinition, input: LockInput): Result = evaluate(puzzle.lock, input)

    fun evaluate(lock: LockDefinition, input: LockInput): Result = when (lock) {
        is LockDefinition.Numeric -> {
            if (input !is LockInput.Numeric || input.value.length != lock.length ||
                input.value.any { it !in '0'..'9' }
            ) Result.InvalidInput else compare(input.value == lock.answer)
        }
        is LockDefinition.Sequence -> {
            if (input !is LockInput.Sequence || input.values.size != lock.answer.size) {
                Result.InvalidInput
            } else compare(input.values == lock.answer)
        }
        is LockDefinition.StateCombination -> {
            if (input !is LockInput.StateCombination || input.values.keys != setOf("channel", "level") ||
                input.values.values.any { it !in lock.min..lock.max }
            ) Result.InvalidInput else compare(
                input.values["channel"] == lock.channel && input.values["level"] == lock.level,
            )
        }
    }

    private fun compare(correct: Boolean): Result = if (correct) Result.Correct else Result.Incorrect
}
