package com.team.carescape.platform.sdk

enum class SdkAvailability {
    STOPPED, CHECKING, READY, NO_DATA, UNSUPPORTED, PERMISSION_DENIED, STALE, ERROR,
}

/** Initialization readiness is not a claim that an actual vehicle value/route/audio was observed. */
data class SdkFeatureState(
    val availability: SdkAvailability = SdkAvailability.STOPPED,
    val detail: String? = null,
)

data class PleosSdkStatus(
    val fused: SdkFeatureState = SdkFeatureState(),
    val navigation: SdkFeatureState = SdkFeatureState(),
    val carSafety: SdkFeatureState = SdkFeatureState(),
    val vehicle: SdkFeatureState = SdkFeatureState(),
    val tts: SdkFeatureState = SdkFeatureState(),
    val stt: SdkFeatureState = SdkFeatureState(),
    val routeState: String = "IDLE",
    val confidenceRaw: Float? = null,
    /** LocationData.confidence has no verified meter-accuracy contract. Never convert it. */
    val locationAccuracyVerified: Boolean = false,
    val brakePedal: Boolean? = null,
    val batteryPercent: Int? = null,
    val vehicleReceivedElapsedMillis: Long? = null,
    val speaking: Boolean = false,
    val listening: Boolean = false,
)

internal fun failureState(error: Throwable): SdkFeatureState = SdkFeatureState(
    when (error) {
        is SecurityException -> SdkAvailability.PERMISSION_DENIED
        is UnsupportedOperationException, is NoClassDefFoundError -> SdkAvailability.UNSUPPORTED
        else -> SdkAvailability.ERROR
    },
    error.javaClass.simpleName,
)
