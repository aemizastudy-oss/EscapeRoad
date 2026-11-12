package com.example.roadescape.platform

import kotlinx.coroutines.flow.Flow

data class ArrivalEvent(val sessionId: String, val checkpointId: String, val arrivedAt: Long)

interface ArrivalProvider { val arrivals: Flow<ArrivalEvent> }

data class PlatformSources(val parking: ParkingStateProvider, val arrival: ArrivalProvider)
