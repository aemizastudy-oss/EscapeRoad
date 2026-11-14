package com.team.carescape.game

/** Four independent seat reading lights. The global cabin switch is not another lamp. */
data class InteriorLightSnapshot(
    val states: Map<String, Boolean> = emptyMap(),
    val receivedElapsedMillis: Long = 0L,
    val ready: Boolean = false,
    val message: String = "실내등 상태를 확인하고 있습니다.",
) {
    fun validCount(now: Long): Int? = if (ready && states.keys == AREAS.toSet() &&
        receivedElapsedMillis > 0 && now - receivedElapsedMillis in 0..FRESH_MS)
        states.values.count { it } else null

    companion object {
        val AREAS = listOf("front_left", "front_right", "rear_left", "rear_right")
        const val FRESH_MS = 9_000L
    }
}
