package com.team.carescape.game

import kotlin.math.abs
import kotlin.math.hypot

/** Screen dial choices, independent of physical vehicle signals. */
enum class DialColor(val label: String) {
    RED("빨강"), YELLOW("노랑"), GREEN("초록"), BLUE("파랑"),
}

enum class LockDirection(val label: String, val symbol: String) {
    UP("위", "↑"), DOWN("아래", "↓"), LEFT("왼쪽", "←"), RIGHT("오른쪽", "→"),
}

/** Normalizes an input draft without judging any puzzle answer. */
object LockInput {
    fun normalizeEnglish(input: String, length: Int = 5): String {
        require(length >= 0) { "The letter count must not be negative." }
        return input.asSequence()
            .filter { it in 'a'..'z' || it in 'A'..'Z' }
            .map { it.uppercaseChar() }
            .take(length)
            .joinToString("")
    }

    /** One direction per completed gesture. Exact diagonal ties use the horizontal axis. */
    fun directionOf(dx: Float, dy: Float, minDistance: Float): LockDirection? {
        if (!dx.isFinite() || !dy.isFinite() || !minDistance.isFinite() || minDistance < 0f) return null
        if (dx == 0f && dy == 0f || hypot(dx.toDouble(), dy.toDouble()) < minDistance.toDouble()) return null
        return if (abs(dx) >= abs(dy)) {
            if (dx > 0f) LockDirection.RIGHT else LockDirection.LEFT
        } else {
            if (dy > 0f) LockDirection.DOWN else LockDirection.UP
        }
    }
}
