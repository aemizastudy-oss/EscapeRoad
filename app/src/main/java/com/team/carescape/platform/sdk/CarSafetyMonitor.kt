package com.team.carescape.platform.sdk

/** No android.car types here: the runtime itself must remain loadable without that optional library. */
internal interface CarSafetyMonitor : AutoCloseable {
    fun start()
}

internal data class CarSafetyReading(
    val parked: Boolean = false,
    val stationary: Boolean = false,
    val uxAllowed: Boolean = false,
    val status: SdkFeatureState = SdkFeatureState(),
)
