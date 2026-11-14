package com.team.carescape.game

import com.team.carescape.model.TourClock
import com.team.carescape.model.TourLocation
import com.team.carescape.model.TourMode
import com.team.carescape.model.TourSafety
import com.team.carescape.model.TourStartRecord
import com.team.carescape.model.WaypointConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class TourVisitConfirmationTest {
    private val start = TourClock(1_800_000_000_000L, 1_000_000L, "boot-a")
    private val place = WaypointConfig("stop", "관광지", 0.0, 0.0)
    private val safe = TourSafety(true, true, true, true)
    private val record = TourStartRecord("session", place.id, TourMode.LIVE, start)
    private fun later(delta: Long) = start.copy(wallTimeMillis = start.wallTimeMillis + delta, elapsedRealtimeMillis = start.elapsedRealtimeMillis + delta)
    private fun fix(clock: TourClock = start) = TourLocation(TourMode.LIVE, 0.0, 0.0, 10.0, clock.wallTimeMillis, clock.elapsedRealtimeMillis)
    private fun observe(t: TourVisitConfirmation, location: TourLocation?, clock: TourClock, safety: TourSafety = safe,
                        departureAt: Long? = null, r: TourStartRecord = record) {
        t.observe(r, r.mode, place, location, safety, clock, departureAt)
    }
    private fun gate(t: TourVisitConfirmation, location: TourLocation?, clock: TourClock) =
        TourPolicy.unlockGate(record, record.mode, place, t.locationForGate(location), safe, clock, t.confirmation)

    @Test fun aConfirmedArrivalSurvivesOnlyAgeExpiryAfterFifteenMinutes() {
        val tracker = TourVisitConfirmation()
        val location = fix()
        observe(tracker, location, start)
        assertNotNull(tracker.confirmation)
        val done = later(TourPolicy.MIN_TOUR_MILLIS)
        observe(tracker, location.copy(error = "LOCATION_STALE"), done)
        assertEquals(TourGate.READY, gate(tracker, location.copy(error = "LOCATION_STALE"), done))
        assertEquals(TourGate.LOCATION_STALE, gate(tracker, location, later(899_999)))
    }

    @Test fun departureRejectsTheOldFixEvenIfItIsStillFreshAndParkingReturns() {
        val tracker = TourVisitConfirmation()
        val done = later(TourPolicy.MIN_TOUR_MILLIS)
        val location = fix(done)
        observe(tracker, location, done)
        val departed = later(TourPolicy.MIN_TOUR_MILLIS + 100)
        observe(tracker, location, departed, safe.copy(parked = false), departed.elapsedRealtimeMillis)
        assertNull(tracker.confirmation)
        observe(tracker, location, departed)
        assertEquals(TourGate.LOCATION_NOT_UPDATED, gate(tracker, location, departed))
        val returned = later(TourPolicy.MIN_TOUR_MILLIS + 101)
        val newLocation = fix(returned)
        observe(tracker, newLocation, returned)
        assertEquals(TourGate.READY, gate(tracker, newLocation, returned))
    }

    @Test fun aNewCallbackInTheSameMillisecondCanReconfirmButTheSameObjectCannot() {
        val tracker = TourVisitConfirmation()
        val done = later(TourPolicy.MIN_TOUR_MILLIS)
        val location = fix(done)
        observe(tracker, location, done)
        observe(tracker, location, done, safe.copy(parked = false), done.elapsedRealtimeMillis)
        observe(tracker, location, done)
        assertEquals(TourGate.LOCATION_NOT_UPDATED, gate(tracker, location, done))
        val newObservation = location.copy()
        observe(tracker, newObservation, done)
        assertEquals(TourGate.READY, gate(tracker, newObservation, done))
    }

    @Test fun outsideOrInvalidObservationsRevokeProofRatherThanPreserveAnOldSuccess() {
        listOf(fix().copy(longitude = 0.01), fix().copy(error = "LOCATION_SDK_ERROR"),
            fix().copy(accuracyMeters = null)).forEach { invalid ->
            val tracker = TourVisitConfirmation()
            observe(tracker, fix(), start)
            observe(tracker, invalid, start)
            assertNull(tracker.confirmation)
        }
    }

    @Test fun foregroundLossModeChangeAndDifferentTripCannotReuseAnOldConfirmation() {
        val tracker = TourVisitConfirmation()
        val location = fix()
        observe(tracker, location, start)
        observe(tracker, location, start, safe.copy(foreground = false))
        assertNull(tracker.confirmation)
        observe(tracker, location, start)
        observe(tracker, location, later(TourPolicy.MIN_TOUR_MILLIS), r = record.copy(sessionId = "new-session"))
        assertNull(tracker.confirmation)
        observe(tracker, location, start)
        tracker.observe(record.copy(mode = TourMode.MOCK), TourMode.MOCK, place, null, safe, start)
        assertNull(tracker.confirmation)
    }

    @Test fun changingOnlyDebugTimeBypassPreservesTheArrivalIdentity() {
        val tracker = TourVisitConfirmation()
        val r = record.copy(mode = TourMode.MOCK)
        val location = fix().copy(mode = TourMode.MOCK, simulatedInsideRadius = true)
        tracker.observe(r, r.mode, place, location, safe, start)
        val bypassed = r.copy(debugTimeBypass = true)
        val oldFix = later(30_001)
        tracker.observe(bypassed, r.mode, place, location, safe, oldFix)
        assertNotNull(tracker.confirmation)
        assertEquals(TourGate.READY, TourPolicy.unlockGate(bypassed, r.mode, place, location, safe, oldFix, tracker.confirmation))
    }

    @Test fun departureWatermarkSurvivesAChapterOrRecordChange() {
        val tracker = TourVisitConfirmation()
        val location = fix()
        observe(tracker, location, start, safe.copy(parked = false), start.elapsedRealtimeMillis)
        observe(tracker, location, start, r = record.copy(sessionId = "new-session"))
        assertNull(tracker.confirmation)
        assertEquals(TourGate.LOCATION_NOT_UPDATED, TourPolicy.startGate(record.mode, place, tracker.locationForGate(location), safe, start))
    }
}
