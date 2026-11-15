package com.team.carescape.game

import com.team.carescape.model.TourClock
import com.team.carescape.model.TourLocation
import com.team.carescape.model.TourMode
import com.team.carescape.model.WaypointConfig

/** Explicit synthetic coordinates, never a physical GPS observation or a tourism time grant. */
object ReviewTourLocation {
    fun input(target: WaypointConfig, clock: TourClock, insideRadius: Boolean): TourLocation? {
        val latitude = target.latitude ?: return null
        val longitude = target.longitude ?: return null
        // Move along a meridian, toward the equator, to produce a verifiably outside coordinate.
        val offsetDegrees = Math.toDegrees((target.radiusMeters + 500.0) / 6_371_000.0)
        val inputLatitude = if (insideRadius) latitude else
            latitude + if (latitude >= 0.0) -offsetDegrees else offsetDegrees
        if (!inputLatitude.isFinite() || inputLatitude !in -90.0..90.0) return null
        return TourLocation(
            mode = TourMode.REVIEW,
            latitude = inputLatitude,
            longitude = longitude,
            // Describes exact synthetic input, not a claimed GNSS measurement accuracy.
            accuracyMeters = 1.0,
            timestampEpochMillis = clock.wallTimeMillis,
            receivedElapsedRealtimeMillis = clock.elapsedRealtimeMillis,
            fromLiveUpdate = true,
            // Leave simulatedInsideRadius null: TourPolicy must calculate the real distance.
        )
    }
}
