package com.team.carescape.ui

import com.team.carescape.game.TourGate
import com.team.carescape.game.TourPolicy
import com.team.carescape.model.TourClock
import com.team.carescape.model.TourLocation
import com.team.carescape.model.TourMode
import com.team.carescape.model.TourSafety
import com.team.carescape.model.TourStartRecord
import com.team.carescape.model.WaypointConfig
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/** Pure debug policy tests: no Context, SDK replacement, disk writes or fabricated app observations. */
class LiveTourTestSessionTest {
    private val token = "12345678-9abc-4def-8123-456789abcdef"
    private val now = TourClock(1_800_000_000_000L, 1_000_000L, "boot-a")
    private val target = WaypointConfig("cp_01", "Test checkpoint", 0.0, 0.0)
    private val safe = TourSafety(foreground = true, parked = true, stationary = true, carUxAllowed = true)
    private val record = TourStartRecord("trip-session", target.id, TourMode.LIVE, now)

    @After fun clearSession() {
        LiveTourTestSession.configure(null)
    }

    private fun session(): LiveTourTestSession {
        LiveTourTestSession.configure(token)
        return checkNotNull(LiveTourTestSession.capture())
    }

    @Test fun explicitCanonicalUuidIsRequiredAndRepeatedConfigurationKeepsTheInstance() {
        LiveTourTestSession.configure(null)
        assertNull(LiveTourTestSession.capture())
        for (invalid in listOf("", "../trip-progress.json", "1-1-1-1-1", "$token/..", " $token", token.dropLast(1))) {
            LiveTourTestSession.configure(invalid)
            assertNull("Invalid tokens must disable the test session", LiveTourTestSession.capture())
        }
        val first = session()
        assertEquals(token, first.id)
        LiveTourTestSession.configure(token)
        assertSame(first, LiveTourTestSession.capture())
        LiveTourTestSession.configure(token.uppercase())
        assertSame(first, LiveTourTestSession.capture())
        LiveTourTestSession.configure("aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee")
        assertNotSame(first, LiveTourTestSession.capture())
        LiveTourTestSession.configure("invalid")
        assertNull(LiveTourTestSession.capture())
    }

    @Test fun onlyLiveLocationChecksAreExcludedAndTheOriginalPolicyRemainsUnchanged() {
        val session = session()
        assertEquals(TourGate.LOCATION_MISSING, TourPolicy.startGate(TourMode.LIVE, target, null, safe, now))
        assertEquals(TourGate.READY, session.startGate(TourMode.LIVE, target, null, safe, now))
        assertEquals(TourGate.READY, session.startGate(TourMode.LIVE, WaypointConfig("unconfigured", "No coordinates"), null, safe, now))
        assertEquals(TourGate.LOCATION_MISSING, session.startGate(TourMode.MOCK, target, null, safe, now))
        assertEquals(TourGate.LOCATION_MISSING, session.unlockGate(record.copy(mode = TourMode.MOCK), TourMode.MOCK, target, null, safe, now, null))
        // This pure source-mode mismatch fixture must not become a LIVE observation.
        val wrongSource = TourLocation(mode = TourMode.MOCK)
        assertEquals(TourGate.MODE_MISMATCH, session.startGate(TourMode.LIVE, target, wrongSource, safe, now))
        assertEquals(TourGate.MODE_MISMATCH, session.unlockGate(record, TourMode.LIVE, target, wrongSource, safe, now, null))
        assertEquals(TourGate.LOCATION_MISSING, TourPolicy.startGate(TourMode.LIVE, target, null, safe, now))
    }

    @Test fun actualSafetyAndClockFailuresRemainBlockingAtStartAndSubmission() {
        val session = session()
        val unsafe = listOf(
            safe.copy(foreground = false) to TourGate.BACKGROUND,
            safe.copy(parked = false) to TourGate.NOT_PARKED,
            safe.copy(stationary = false) to TourGate.NOT_STATIONARY,
            safe.copy(carUxAllowed = false) to TourGate.CAR_UX_RESTRICTED,
        )
        for ((safety, expected) in unsafe) {
            assertEquals(expected, session.startGate(TourMode.LIVE, target, null, safety, now))
            assertEquals(expected, session.unlockGate(record, TourMode.LIVE, target, null, safety, now, null))
        }
        for (invalid in listOf(now.copy(wallTimeMillis = 0), now.copy(elapsedRealtimeMillis = -1), now.copy(bootId = ""))) {
            assertEquals(TourGate.CLOCK_INVALID, session.startGate(TourMode.LIVE, target, null, safe, invalid))
            assertEquals(TourGate.CLOCK_INVALID, session.unlockGate(record, TourMode.LIVE, target, null, safe, invalid, null))
        }
        assertEquals(TourGate.CLOCK_BACKWARD, session.unlockGate(record, TourMode.LIVE, target, null, safe,
            now.copy(wallTimeMillis = now.wallTimeMillis - 1), null))
        assertEquals(TourGate.CLOCK_SUSPICIOUS, session.unlockGate(record, TourMode.LIVE, target, null, safe,
            now.copy(wallTimeMillis = now.wallTimeMillis + 130_001, elapsedRealtimeMillis = now.elapsedRealtimeMillis + 1), null))
        assertEquals(TourGate.CLOCK_INVALID, session.unlockGate(record.copy(startedAt = now.copy(bootId = "")),
            TourMode.LIVE, target, null, safe, now, null))
    }

    @Test fun zeroWaitStillRequiresAnActualStartRecordWithMatchingModeAndCheckpoint() {
        val session = session()
        // Zero elapsed time is allowed without modifying the recorded start clock or a bypass flag.
        assertEquals(TourGate.READY, session.unlockGate(record, TourMode.LIVE, target, null, safe, now, null))
        assertEquals(now, record.startedAt)
        assertEquals(false, record.debugTimeBypass)
        assertEquals(TourGate.TOUR_NOT_STARTED, session.unlockGate(null, TourMode.LIVE, target, null, safe, now, null))
        assertEquals(TourGate.MODE_MISMATCH, session.unlockGate(record.copy(mode = TourMode.MOCK), TourMode.LIVE, target, null, safe, now, null))
        assertEquals(TourGate.RECORD_MISMATCH, session.unlockGate(record.copy(sessionId = " "), TourMode.LIVE, target, null, safe, now, null))
        assertEquals(TourGate.RECORD_MISMATCH, session.unlockGate(record.copy(checkpointId = "another-checkpoint"), TourMode.LIVE, target, null, safe, now, null))
        assertEquals(TourGate.MODE_MISMATCH, session.unlockGate(record.copy(debugTimeBypass = true), TourMode.LIVE, target, null, safe, now, null))
    }
}
