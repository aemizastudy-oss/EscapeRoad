package com.team.carescape.game

import com.team.carescape.model.TourClock
import com.team.carescape.model.TourKey
import com.team.carescape.model.TourLocation
import com.team.carescape.model.TourMode
import com.team.carescape.model.TourSafety
import com.team.carescape.model.TourStartRecord
import com.team.carescape.model.WaypointConfig
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

enum class TourGate {
    READY, BACKGROUND, NOT_PARKED, NOT_STATIONARY, CAR_UX_RESTRICTED,
    COORDINATES_NOT_CONFIGURED, MODE_MISMATCH, RECORD_MISMATCH,
    LOCATION_MISSING, LOCATION_ERROR, LOCATION_NOT_UPDATED,
    LOCATION_TIMESTAMP_INVALID, LOCATION_STALE, LOCATION_INVALID,
    LOCATION_ACCURACY_UNKNOWN, LOCATION_INACCURATE, OUTSIDE_RADIUS,
    TOUR_NOT_STARTED, TOUR_TIME_REMAINING, CLOCK_INVALID, CLOCK_BACKWARD, CLOCK_SUSPICIOUS,
}

sealed interface TourTimeResult {
    data class Valid(val elapsedMillis: Long) : TourTimeResult
    data class Pending(val reason: TourGate) : TourTimeResult
}

/**
 * In-memory evidence of a valid arrival fix for one exact tour start and target.
 * This is not continuous dwell proof and must be discarded on lifecycle/movement invalidation.
 */
data class TourLocationConfirmation(
    val recordKey: TourKey,
    val startedAt: TourClock,
    val target: WaypointConfig,
    val confirmedAt: TourClock,
)

/** Pure policy. SDK capability, prior-chapter completion and persistence remain caller responsibilities. */
object TourPolicy {
    const val MIN_TOUR_MILLIS = 15L * 60L * 1000L
    const val INITIAL_RADIUS_METERS = 500.0

    // App validation defaults, not documented Pleos sampling guarantees or official requirements.
    const val MAX_LOCATION_AGE_MILLIS = 30_000L
    const val SDK_LOCATION_EXPIRY_MILLIS = 15_000L
    const val MAX_LOCATION_ACCURACY_METERS = 100.0
    const val MAX_FUTURE_LOCATION_SKEW_MILLIS = 5_000L
    const val CLOCK_DIFFERENCE_TOLERANCE_MILLIS = 120_000L
    const val MAX_UNVERIFIED_REBOOT_GAP_MILLIS = 30L * 24L * 60L * 60L * 1000L

    fun startGate(
        mode: TourMode,
        target: WaypointConfig,
        location: TourLocation?,
        safety: TourSafety,
        now: TourClock,
    ): TourGate {
        safetyGate(safety)?.let { return it }
        if (!validClock(now)) return TourGate.CLOCK_INVALID
        return locationGate(mode, target, location, now)
    }

    /** Return/submission checks use the saved tour start, time and current safety, not location. */
    fun unlockGate(
        record: TourStartRecord?,
        mode: TourMode,
        target: WaypointConfig,
        safety: TourSafety,
        now: TourClock,
    ): TourGate {
        safetyGate(safety)?.let { return it }
        if (!validClock(now)) return TourGate.CLOCK_INVALID
        if (record == null) return TourGate.TOUR_NOT_STARTED
        if (record.mode != mode) return TourGate.MODE_MISMATCH
        if (record.checkpointId != target.id || record.sessionId.isBlank()) return TourGate.RECORD_MISMATCH
        if (record.debugTimeBypass && mode != TourMode.MOCK) return TourGate.MODE_MISMATCH
        return when (val elapsed = elapsed(record, now)) {
            is TourTimeResult.Pending -> elapsed.reason
            is TourTimeResult.Valid -> if (
                elapsed.elapsedMillis >= MIN_TOUR_MILLIS || record.debugTimeBypass && mode == TourMode.MOCK
            ) TourGate.READY else TourGate.TOUR_TIME_REMAINING
        }
    }

    /** Only a full fresh arrival check can create evidence; a stored clock alone cannot. */
    fun confirmLocation(
        record: TourStartRecord?,
        mode: TourMode,
        target: WaypointConfig,
        location: TourLocation?,
        safety: TourSafety,
        now: TourClock,
    ): TourLocationConfirmation? {
        if (startGate(mode, target, location, safety, now) != TourGate.READY || record == null) return null
        if (record.mode != mode || record.checkpointId != target.id || record.sessionId.isBlank()) return null
        if (record.debugTimeBypass && mode != TourMode.MOCK) return null
        if (elapsed(record, now) !is TourTimeResult.Valid) return null
        return TourLocationConfirmation(record.key, record.startedAt, target, now)
    }

    fun elapsed(record: TourStartRecord, now: TourClock): TourTimeResult {
        val start = record.startedAt
        if (!validClock(start) || !validClock(now)) return TourTimeResult.Pending(TourGate.CLOCK_INVALID)
        if (now.wallTimeMillis < start.wallTimeMillis) return TourTimeResult.Pending(TourGate.CLOCK_BACKWARD)
        val wallDelta = now.wallTimeMillis - start.wallTimeMillis
        if (now.bootId == start.bootId) {
            if (now.elapsedRealtimeMillis < start.elapsedRealtimeMillis) return TourTimeResult.Pending(TourGate.CLOCK_BACKWARD)
            val elapsedDelta = now.elapsedRealtimeMillis - start.elapsedRealtimeMillis
            val difference = if (wallDelta >= elapsedDelta) wallDelta - elapsedDelta else elapsedDelta - wallDelta
            if (difference > CLOCK_DIFFERENCE_TOLERANCE_MILLIS) return TourTimeResult.Pending(TourGate.CLOCK_SUSPICIOUS)
            // A modest wall-clock adjustment must not unlock a chapter early.
            return TourTimeResult.Valid(minOf(wallDelta, elapsedDelta))
        }
        if (wallDelta > MAX_UNVERIFIED_REBOOT_GAP_MILLIS) return TourTimeResult.Pending(TourGate.CLOCK_SUSPICIOUS)
        // After a real OS reboot only the persisted wall clock remains comparable.
        // Offline storage cannot provide complete protection against deliberate clock manipulation.
        return TourTimeResult.Valid(wallDelta)
    }

    fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2.0) + cos(Math.toRadians(lat1)) *
            cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2.0)
        return 2.0 * 6_371_000.0 * asin(sqrt(a.coerceIn(0.0, 1.0)))
    }

    private fun safetyGate(safety: TourSafety): TourGate? = when {
        !safety.foreground -> TourGate.BACKGROUND
        !safety.carUxAllowed -> TourGate.CAR_UX_RESTRICTED
        !safety.parked -> TourGate.NOT_PARKED
        !safety.stationary -> TourGate.NOT_STATIONARY
        else -> null
    }

    private fun validClock(clock: TourClock) = clock.wallTimeMillis > 0 &&
        clock.elapsedRealtimeMillis >= 0 && clock.bootId.isNotBlank()

    /** Spatial/source validation; only a previously confirmed tour may opt out of age checks. */
    fun locationGate(
        mode: TourMode,
        target: WaypointConfig,
        location: TourLocation?,
        now: TourClock,
        allowStale: Boolean = false,
    ): TourGate {
        if (location != null && location.mode != mode) return TourGate.MODE_MISMATCH
        val simulated = mode == TourMode.MOCK && location?.simulatedInsideRadius != null
        if (!simulated && (target.latitude == null || target.longitude == null)) return TourGate.COORDINATES_NOT_CONFIGURED
        if (location == null) return TourGate.LOCATION_MISSING
        if (location.error != null && !(allowStale && location.error == "LOCATION_STALE")) return TourGate.LOCATION_ERROR
        if (!location.fromLiveUpdate) return TourGate.LOCATION_NOT_UPDATED
        val timestamp = location.timestampEpochMillis ?: return TourGate.LOCATION_TIMESTAMP_INVALID
        val receivedAt = location.receivedElapsedRealtimeMillis ?: return TourGate.LOCATION_TIMESTAMP_INVALID
        if (timestamp <= 0 || receivedAt < 0 || receivedAt > now.elapsedRealtimeMillis) return TourGate.LOCATION_TIMESTAMP_INVALID
        if (timestamp > now.wallTimeMillis && timestamp - now.wallTimeMillis > MAX_FUTURE_LOCATION_SKEW_MILLIS) return TourGate.LOCATION_TIMESTAMP_INVALID
        val stale = now.wallTimeMillis >= timestamp && now.wallTimeMillis - timestamp > MAX_LOCATION_AGE_MILLIS ||
            now.elapsedRealtimeMillis - receivedAt > MAX_LOCATION_AGE_MILLIS
        val sdkExpired = now.wallTimeMillis >= timestamp && now.wallTimeMillis - timestamp > SDK_LOCATION_EXPIRY_MILLIS ||
            now.elapsedRealtimeMillis - receivedAt > SDK_LOCATION_EXPIRY_MILLIS
        // The adapter expires sooner than the general policy. Its precise error must match that age.
        if (location.error != null && !sdkExpired) return TourGate.LOCATION_ERROR
        if (stale && !allowStale) return TourGate.LOCATION_STALE
        val accuracy = location.accuracyMeters
        if (accuracy == null || !accuracy.isFinite() || accuracy <= 0) return TourGate.LOCATION_ACCURACY_UNKNOWN
        if (accuracy > MAX_LOCATION_ACCURACY_METERS) return TourGate.LOCATION_INACCURATE
        if (simulated) return if (location.simulatedInsideRadius == true) TourGate.READY else TourGate.OUTSIDE_RADIUS
        val latitude = location.latitude ?: return TourGate.LOCATION_MISSING
        val longitude = location.longitude ?: return TourGate.LOCATION_MISSING
        if (!latitude.isFinite() || latitude !in -90.0..90.0 || !longitude.isFinite() || longitude !in -180.0..180.0) return TourGate.LOCATION_INVALID
        return if (distanceMeters(latitude, longitude, target.latitude!!, target.longitude!!) <= target.radiusMeters) TourGate.READY else TourGate.OUTSIDE_RADIUS
    }
}
