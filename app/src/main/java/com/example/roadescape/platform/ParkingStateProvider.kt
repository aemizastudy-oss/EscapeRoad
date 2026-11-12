package com.example.roadescape.platform

import com.example.roadescape.model.ParkingState
import kotlinx.coroutines.flow.StateFlow

/** App-owned boundary. No vehicle signals are read by this prototype. */
data class ParkingSnapshot(val state: ParkingState = ParkingState.UNKNOWN, val revision: Long = 0)

interface ParkingStateProvider {
    val snapshot: StateFlow<ParkingSnapshot>
    fun invalidate()
}
