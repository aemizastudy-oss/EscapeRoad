package com.example.roadescape.platform

import com.example.roadescape.model.ParkingState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow

private class UnavailableParkingProvider : ParkingStateProvider {
    override val snapshot = MutableStateFlow(ParkingSnapshot()).asStateFlow()
    override fun invalidate() = Unit
}
private class UnavailableArrivalProvider : ArrivalProvider {
    override val arrivals = emptyFlow<ArrivalEvent>()
}

fun createPlatformSources() = PlatformSources(UnavailableParkingProvider(), UnavailableArrivalProvider())
