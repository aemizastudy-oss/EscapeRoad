package com.team.carescape.game

import com.team.carescape.model.TourClock
import com.team.carescape.model.TourLocation
import com.team.carescape.model.TourMode
import com.team.carescape.model.TourSafety
import com.team.carescape.model.TourStartRecord
import com.team.carescape.model.WaypointConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TourPolicyTest {
    private val start = TourClock(1_800_000_000_000, 1_000_000, "boot-a")
    private val target = WaypointConfig("cp_01", "검증 지점", 0.0, 0.0)
    private val safe = TourSafety(foreground = true, parked = true, stationary = true, carUxAllowed = true)
    private val record = TourStartRecord("session-a", target.id, TourMode.LIVE, start)
    private fun later(millis: Long) = start.copy(wallTimeMillis = start.wallTimeMillis + millis, elapsedRealtimeMillis = start.elapsedRealtimeMillis + millis)
    private fun fresh(now: TourClock, mode: TourMode = TourMode.LIVE) = TourLocation(mode, 0.0, 0.0, 10.0, now.wallTimeMillis, now.elapsedRealtimeMillis, fromLiveUpdate = true)
    private fun unlock(now: TourClock, location: TourLocation? = fresh(now), safety: TourSafety = safe) =
        TourPolicy.unlockGate(record, TourMode.LIVE, target, location, safety, now)

    @Test
    fun fifteenMinuteBoundaryIsExactAndRequiresANewReturnLocation() {
        assertEquals(TourGate.TOUR_TIME_REMAINING, unlock(later(899_999)))
        assertEquals(TourGate.READY, unlock(later(900_000)))
        assertEquals(TourGate.READY, unlock(later(900_001)))
        assertEquals(TourGate.LOCATION_STALE, unlock(later(900_000), fresh(start)))
        assertEquals(TourGate.OUTSIDE_RADIUS, unlock(later(900_000), fresh(later(900_000)).copy(longitude = 0.01)))
    }

    @Test
    fun physicalParkingDoesNotReplaceForegroundStationaryAndCarUxPermission() {
        val now = later(900_000)
        assertEquals(TourGate.BACKGROUND, unlock(now, safety = safe.copy(foreground = false)))
        assertEquals(TourGate.NOT_PARKED, unlock(now, safety = safe.copy(parked = false)))
        assertEquals(TourGate.NOT_STATIONARY, unlock(now, safety = safe.copy(stationary = false)))
        assertEquals(TourGate.CAR_UX_RESTRICTED, unlock(now, safety = safe.copy(carUxAllowed = false)))
    }

    @Test
    fun missingStaleInaccurateAndLastKnownFixesRemainDifferentReasons() {
        assertEquals(TourGate.LOCATION_MISSING, TourPolicy.startGate(TourMode.LIVE, target, null, safe, start))
        assertEquals(TourGate.LOCATION_ERROR, TourPolicy.startGate(TourMode.LIVE, target, fresh(start).copy(error = "SDK unavailable"), safe, start))
        assertEquals(TourGate.LOCATION_NOT_UPDATED, TourPolicy.startGate(TourMode.LIVE, target, fresh(start).copy(fromLiveUpdate = false), safe, start))
        assertEquals(TourGate.LOCATION_ACCURACY_UNKNOWN, TourPolicy.startGate(TourMode.LIVE, target, fresh(start).copy(accuracyMeters = null), safe, start))
        assertEquals(TourGate.LOCATION_INACCURATE, TourPolicy.startGate(TourMode.LIVE, target, fresh(start).copy(accuracyMeters = 200.0), safe, start))
        assertEquals(TourGate.LOCATION_STALE, TourPolicy.startGate(TourMode.LIVE, target, fresh(start).copy(timestampEpochMillis = start.wallTimeMillis - 30_001), safe, start))
        assertEquals(TourGate.LOCATION_STALE, TourPolicy.startGate(TourMode.LIVE, target, fresh(start).copy(receivedElapsedRealtimeMillis = start.elapsedRealtimeMillis - 30_001), safe, start))
    }

    @Test
    fun futureOrInvalidCoordinatesAreNotCertified() {
        assertEquals(TourGate.LOCATION_TIMESTAMP_INVALID, TourPolicy.startGate(TourMode.LIVE, target, fresh(start).copy(timestampEpochMillis = start.wallTimeMillis + 5_001), safe, start))
        assertEquals(TourGate.LOCATION_TIMESTAMP_INVALID, TourPolicy.startGate(TourMode.LIVE, target, fresh(start).copy(receivedElapsedRealtimeMillis = start.elapsedRealtimeMillis + 1), safe, start))
        assertEquals(TourGate.LOCATION_INVALID, TourPolicy.startGate(TourMode.LIVE, target, fresh(start).copy(latitude = Double.NaN), safe, start))
        assertEquals(TourGate.LOCATION_INVALID, TourPolicy.startGate(TourMode.LIVE, target, fresh(start).copy(longitude = 181.0), safe, start))
        assertEquals(TourGate.READY, TourPolicy.startGate(TourMode.LIVE, target, fresh(start).copy(longitude = 0.0044), safe, start))
        assertEquals(TourGate.OUTSIDE_RADIUS, TourPolicy.startGate(TourMode.LIVE, target, fresh(start).copy(longitude = 0.0046), safe, start))
    }

    @Test
    fun actualAuthenticationCannotUseMissingCoordinatesOrMockEvidence() {
        val unconfigured = target.copy(latitude = null, longitude = null)
        assertEquals(TourGate.COORDINATES_NOT_CONFIGURED, TourPolicy.startGate(TourMode.LIVE, unconfigured, fresh(start).copy(simulatedInsideRadius = true), safe, start))
        assertEquals(TourGate.MODE_MISMATCH, TourPolicy.startGate(TourMode.LIVE, target, fresh(start, TourMode.MOCK).copy(simulatedInsideRadius = true), safe, start))
        assertEquals(TourGate.READY, TourPolicy.startGate(TourMode.MOCK, unconfigured, fresh(start, TourMode.MOCK).copy(simulatedInsideRadius = true), safe, start))
    }

    @Test
    fun wrongChapterOrModeRecordCannotUnlockTheCurrentChapter() {
        val now = later(900_000)
        assertEquals(TourGate.TOUR_NOT_STARTED, TourPolicy.unlockGate(null, TourMode.LIVE, target, fresh(now), safe, now))
        assertEquals(TourGate.RECORD_MISMATCH, TourPolicy.unlockGate(record.copy(checkpointId = "other"), TourMode.LIVE, target, fresh(now), safe, now))
        assertEquals(TourGate.MODE_MISMATCH, TourPolicy.unlockGate(record.copy(mode = TourMode.MOCK), TourMode.LIVE, target, fresh(now), safe, now))
    }

    @Test
    fun backwardAndUnreasonableClockChangesAreHeld() {
        assertEquals(TourTimeResult.Pending(TourGate.CLOCK_BACKWARD), TourPolicy.elapsed(record, start.copy(wallTimeMillis = start.wallTimeMillis - 1)))
        assertEquals(TourTimeResult.Pending(TourGate.CLOCK_BACKWARD), TourPolicy.elapsed(record, start.copy(elapsedRealtimeMillis = start.elapsedRealtimeMillis - 1)))
        assertEquals(TourTimeResult.Pending(TourGate.CLOCK_SUSPICIOUS), TourPolicy.elapsed(record, start.copy(wallTimeMillis = start.wallTimeMillis + 900_000, elapsedRealtimeMillis = start.elapsedRealtimeMillis + 60_000)))
        assertEquals(TourTimeResult.Pending(TourGate.CLOCK_SUSPICIOUS), TourPolicy.elapsed(record, later(TourPolicy.MAX_UNVERIFIED_REBOOT_GAP_MILLIS + 1).copy(bootId = "boot-b")))
    }

    @Test
    fun smallWallClockAdjustmentDoesNotUnlockEarlyAndRealRebootCanResume() {
        val fastWallClock = later(899_999).copy(wallTimeMillis = start.wallTimeMillis + 900_000)
        assertEquals(TourGate.TOUR_TIME_REMAINING, unlock(fastWallClock))
        val restarted = later(900_000).copy(bootId = "boot-b", elapsedRealtimeMillis = 30_000)
        assertEquals(TourTimeResult.Valid(900_000), TourPolicy.elapsed(record, restarted))
        assertEquals(TourGate.READY, unlock(restarted, fresh(restarted)))
    }

    @Test
    fun bypassIsOnlyForMockAndDoesNotOverrideLocationOrSafety() {
        val mock = record.copy(mode = TourMode.MOCK, debugTimeBypass = true)
        assertEquals(TourGate.READY, TourPolicy.unlockGate(mock, TourMode.MOCK, target, fresh(start, TourMode.MOCK), safe, start))
        assertEquals(TourGate.CAR_UX_RESTRICTED, TourPolicy.unlockGate(mock, TourMode.MOCK, target, fresh(start, TourMode.MOCK), safe.copy(carUxAllowed = false), start))
        assertEquals(TourGate.OUTSIDE_RADIUS, TourPolicy.unlockGate(mock, TourMode.MOCK, target, fresh(start, TourMode.MOCK).copy(longitude = 0.01), safe, start))
        assertEquals(TourGate.MODE_MISMATCH, TourPolicy.unlockGate(record.copy(debugTimeBypass = true), TourMode.LIVE, target, fresh(start), safe, start))
    }

    @Test
    fun allTourChaptersKeepFixedFifteenMinutePolicy() {
        assertEquals(500.0, WaypointConfig("cp", "초기값").radiusMeters, 0.0)
        assertTrue(runCatching { target.copy(minTourMinutes = 14) }.isFailure)
        assertEquals(0.0, TourPolicy.distanceMeters(0.0, 0.0, 0.0, 0.0), 0.001)
    }

    @Test
    fun everyCatalogStopIncludingFinalChapterNeedsFifteenMinutes() {
        assertEquals(5, TourCatalog.checkpoints.size)
        TourCatalog.checkpoints.forEach { waypoint ->
            val started = TourStartRecord("trip", waypoint.id, TourMode.LIVE, start)
            listOf(899_999L to TourGate.TOUR_TIME_REMAINING, 900_000L to TourGate.READY).forEach { (delta, expected) ->
                val now = later(delta)
                val position = fresh(now).copy(latitude = waypoint.latitude, longitude = waypoint.longitude)
                assertEquals(waypoint.name, expected, TourPolicy.unlockGate(started, TourMode.LIVE, waypoint, position, safe, now))
            }
        }
    }
}
