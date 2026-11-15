package com.team.carescape.model

import kotlinx.serialization.Serializable

/** MOCK and explicit REVIEW coordinates/records are never evidence for a LIVE trip. */
@Serializable
enum class TourMode { MOCK, LIVE, REVIEW }

/** Stores only the explicit review choice; no positions or trip clocks belong in preferences. */
object ReviewTourSettings {
    const val PREFERENCES = "review_tour"
    const val ENABLED = "enabled"
}

@Serializable
data class WaypointConfig(
    val id: String,
    val name: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val radiusMeters: Double = 500.0,
    val minTourMinutes: Int = 15,
) {
    init {
        require(id.isNotBlank() && name.isNotBlank())
        require(latitude == null || latitude.isFinite() && latitude in -90.0..90.0)
        require(longitude == null || longitude.isFinite() && longitude in -180.0..180.0)
        require(radiusMeters.isFinite() && radiusMeters > 0)
        require(minTourMinutes == 15) { "Every chapter requires a 15-minute tour" }
    }
}

/** bootId identifies an OS boot, not an app process or a tour session. */
@Serializable
data class TourClock(
    val wallTimeMillis: Long,
    val elapsedRealtimeMillis: Long,
    val bootId: String,
)

/** LIVE is updated SDK data; REVIEW is explicitly entered synthetic data. Never persist positions. */
data class TourLocation(
    val mode: TourMode,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val accuracyMeters: Double? = null,
    val timestampEpochMillis: Long? = null,
    val receivedElapsedRealtimeMillis: Long? = null,
    /** A new observation/input rather than a cached fix; REVIEW never authenticates LIVE. */
    val fromLiveUpdate: Boolean = true,
    val error: String? = null,
    /** Explicit debug simulation only; ignored in LIVE mode. */
    val simulatedInsideRadius: Boolean? = null,
)

data class TourSafety(
    val foreground: Boolean,
    val parked: Boolean,
    val stationary: Boolean,
    val carUxAllowed: Boolean,
)

@Serializable
data class TourKey(val sessionId: String, val checkpointId: String, val mode: TourMode)

@Serializable
data class TourStartRecord(
    val sessionId: String,
    val checkpointId: String,
    val mode: TourMode,
    val startedAt: TourClock,
    val debugTimeBypass: Boolean = false,
) {
    val key: TourKey get() = TourKey(sessionId, checkpointId, mode)
}

@Serializable
data class TourState(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val records: List<TourStartRecord> = emptyList(),
) {
    fun find(key: TourKey): TourStartRecord? = records.firstOrNull { it.key == key }
    companion object { const val CURRENT_SCHEMA_VERSION = 1 }
}
