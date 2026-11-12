package com.example.roadescape.platform

import com.example.roadescape.model.ParkingState
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update

class SimulatedParkingProvider : ParkingStateProvider {
    private val mutable = MutableStateFlow(ParkingSnapshot())
    override val snapshot = mutable.asStateFlow()
    fun simulate(value: ParkingState) { mutable.update { ParkingSnapshot(value, it.revision + 1) } }
    override fun invalidate() { simulate(ParkingState.UNKNOWN) }
}

class SimulatedArrivalProvider : ArrivalProvider {
    private val events = Channel<ArrivalEvent>(Channel.UNLIMITED)
    override val arrivals = events.receiveAsFlow()
    fun simulate(event: ArrivalEvent) { events.trySend(event) }
}

fun createPlatformSources() = PlatformSources(SimulatedParkingProvider(), SimulatedArrivalProvider())
