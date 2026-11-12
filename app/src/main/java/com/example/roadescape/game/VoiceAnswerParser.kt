package com.example.roadescape.game

import com.example.roadescape.model.*

/** Produces a proposal only. The user must confirm it before any game command is sent. */
object VoiceAnswerParser {
    fun parse(lock: LockDefinition, text: String): LockInput? = when (lock) {
        is LockDefinition.Numeric -> {
            val digits = text.replace(Regex("[\\s,]"), "").map { c -> when (c) {
                '공','영' -> '0'; '일' -> '1'; '이' -> '2'; '삼' -> '3'; '사' -> '4'; '오' -> '5'
                '육' -> '6'; '칠' -> '7'; '팔' -> '8'; '구' -> '9'; else -> c
            } }.joinToString("")
            if (digits.length == lock.length && digits.all { it in '0'..'9' }) LockInput.Numeric(digits) else null
        }
        is LockDefinition.Sequence -> {
            val values = text.uppercase().trim().split(Regex("[\\s,→]+")).map { when (it) {
                "위","위쪽","UP" -> Direction.UP; "오른쪽","RIGHT" -> Direction.RIGHT
                "아래","아래쪽","DOWN" -> Direction.DOWN; "왼쪽","LEFT" -> Direction.LEFT; else -> null
            } }
            if (values.size == lock.answer.size && values.all { it != null }) LockInput.Sequence(values.filterNotNull()) else null
        }
        is LockDefinition.StateCombination -> null
    }
}
