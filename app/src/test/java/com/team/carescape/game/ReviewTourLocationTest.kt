package com.team.carescape.game

import com.team.carescape.model.TourClock
import com.team.carescape.model.TourMode
import com.team.carescape.model.TourSafety
import com.team.carescape.model.TourStartRecord
import com.team.carescape.model.WaypointConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewTourLocationTest {
    private val now = TourClock(1_800_000_000_000, 1_000_000, "review-boot")
    private val safety = TourSafety(true, true, true, true)
    private val place = TourCatalog.checkpoints.first()
    private val record = TourStartRecord("review-session", place.id, TourMode.REVIEW, now)
    private fun later(millis: Long) = now.copy(
        wallTimeMillis = now.wallTimeMillis + millis,
        elapsedRealtimeMillis = now.elapsedRealtimeMillis + millis,
    )

    @Test fun reviewInputsUseActualCoordinateDistanceAtEveryCatalogStop() {
        TourCatalog.checkpoints.forEach { target ->
            val inside = ReviewTourLocation.input(target, now, true)!!
            val outside = ReviewTourLocation.input(target, now, false)!!
            assertEquals(TourMode.REVIEW, inside.mode)
            assertNull(inside.simulatedInsideRadius)
            assertNull(outside.simulatedInsideRadius)
            assertEquals(TourGate.READY, TourPolicy.startGate(TourMode.REVIEW, target, inside, safety, now))
            assertEquals(TourGate.OUTSIDE_RADIUS, TourPolicy.startGate(TourMode.REVIEW, target, outside, safety, now))
            assertTrue(TourPolicy.distanceMeters(outside.latitude!!, outside.longitude!!,
                target.latitude!!, target.longitude!!) > target.radiusMeters)
        }
    }

    @Test fun reviewDoesNotUseTheOldInsideRadiusFlagOrInventMissingTargetCoordinates() {
        val outside = ReviewTourLocation.input(place, now, false)!!
        assertEquals(TourGate.OUTSIDE_RADIUS, TourPolicy.startGate(TourMode.REVIEW, place,
            outside.copy(simulatedInsideRadius = true), safety, now))
        assertNull(ReviewTourLocation.input(place.copy(latitude = null), now, true))
        assertNull(ReviewTourLocation.input(place.copy(longitude = null), now, false))
    }

    @Test fun syntheticReviewInputCannotAuthenticateLiveOrMockTrips() {
        val input = ReviewTourLocation.input(place, now, true)!!
        assertEquals(TourGate.MODE_MISMATCH, TourPolicy.startGate(TourMode.LIVE, place, input, safety, now))
        assertEquals(TourGate.MODE_MISMATCH, TourPolicy.startGate(TourMode.MOCK, place, input, safety, now))
        assertEquals(TourGate.MODE_MISMATCH, TourPolicy.unlockGate(record, TourMode.LIVE, place, safety, later(900_000)))
    }

    @Test fun reviewRequiresExplicitFreshPositionBeforeStarting() {
        assertEquals(TourGate.LOCATION_MISSING, TourPolicy.startGate(TourMode.REVIEW, place, null, safety, now))
        val input = ReviewTourLocation.input(place, now, true)!!
        assertEquals(TourGate.LOCATION_STALE,
            TourPolicy.startGate(TourMode.REVIEW, place, input, safety, later(30_001)))
    }

    @Test fun reviewWaitsTheRealFifteenMinuteBoundaryAndRejectsDebugTimeBypass() {
        assertEquals(TourGate.TOUR_NOT_STARTED, TourPolicy.unlockGate(null, TourMode.REVIEW, place, safety, later(900_000)))
        assertEquals(TourGate.TOUR_TIME_REMAINING, TourPolicy.unlockGate(record, TourMode.REVIEW, place, safety, later(899_999)))
        assertEquals(TourGate.READY, TourPolicy.unlockGate(record, TourMode.REVIEW, place, safety, later(900_000)))
        assertEquals(TourGate.MODE_MISMATCH,
            TourPolicy.unlockGate(record.copy(debugTimeBypass = true), TourMode.REVIEW, place, safety, later(900_000)))
    }

    @Test fun reviewStillRejectsBackgroundDrivingRestrictedUxAndClockChanges() {
        val input = ReviewTourLocation.input(place, now, true)!!
        listOf(
            safety.copy(foreground = false) to TourGate.BACKGROUND,
            safety.copy(parked = false) to TourGate.NOT_PARKED,
            safety.copy(stationary = false) to TourGate.NOT_STATIONARY,
            safety.copy(carUxAllowed = false) to TourGate.CAR_UX_RESTRICTED,
        ).forEach { (unsafe, expected) ->
            assertEquals(expected, TourPolicy.startGate(TourMode.REVIEW, place, input, unsafe, now))
            assertEquals(expected, TourPolicy.unlockGate(record, TourMode.REVIEW, place, unsafe, later(900_000)))
        }
        val altered = later(900_000).copy(elapsedRealtimeMillis = now.elapsedRealtimeMillis + 60_000)
        assertEquals(TourGate.CLOCK_SUSPICIOUS, TourPolicy.unlockGate(record, TourMode.REVIEW, place, safety, altered))
    }

    @Test fun reviewKeepsSavedStartAcrossAppOrRealBootBoundariesWithoutReturnGps() {
        val restored = record.copy()
        assertEquals(TourGate.READY, TourPolicy.unlockGate(restored, TourMode.REVIEW, place, safety, later(900_000)))
        val rebooted = later(900_000).copy(bootId = "new-boot", elapsedRealtimeMillis = 12_000)
        assertEquals(TourGate.READY, TourPolicy.unlockGate(restored, TourMode.REVIEW, place, safety, rebooted))
        assertEquals(TourGate.RECORD_MISMATCH,
            TourPolicy.unlockGate(restored, TourMode.REVIEW, TourCatalog.checkpoints[1], safety, later(900_000)))
    }

    @Test fun meridianOffsetHandlesPolesAndTheDateLine() {
        listOf(90.0, -90.0, 0.0).forEach { latitude ->
            listOf(-180.0, 180.0).forEach { longitude ->
                val target = WaypointConfig("edge", "edge", latitude, longitude)
                val outside = ReviewTourLocation.input(target, now, false)
                assertNotNull(outside)
                assertEquals(TourGate.OUTSIDE_RADIUS,
                    TourPolicy.startGate(TourMode.REVIEW, target, outside, safety, now))
            }
        }
    }
}
