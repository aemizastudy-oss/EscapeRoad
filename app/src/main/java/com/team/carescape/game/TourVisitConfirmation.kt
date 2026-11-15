package com.team.carescape.game

import com.team.carescape.model.TourClock
import com.team.carescape.model.TourKey
import com.team.carescape.model.TourLocation
import com.team.carescape.model.TourMode
import com.team.carescape.model.TourSafety
import com.team.carescape.model.TourStartRecord
import com.team.carescape.model.WaypointConfig

/** Initial-arrival location tracking. Return/submission permission does not consume this evidence. */
class TourVisitConfirmation {
    var confirmation: TourLocationConfirmation? = null
        private set
    private var mode: TourMode? = null
    private var bootId: String? = null
    private var recordKey: TourKey? = null
    private var startedAt: TourClock? = null
    private var target: WaypointConfig? = null
    private var departureAt: Long? = null
    private var departureSample: TourLocation? = null

    fun clear() {
        confirmation = null
        mode = null
        bootId = null
        recordKey = null
        startedAt = null
        target = null
        departureAt = null
        departureSample = null
    }

    /** A still-fresh pre-departure fix cannot silently requalify a parked vehicle. */
    fun locationForGate(location: TourLocation?): TourLocation? {
        val boundary = departureAt ?: return location
        val received = location?.receivedElapsedRealtimeMillis ?: return location
        // A distinct callback received in the same millisecond is a new observation too.
        // This changes eligibility only; timestamps, coordinates and accuracy stay untouched.
        return if (received < boundary || received == boundary && location === departureSample)
            location.copy(fromLiveUpdate = false) else location
    }

    fun observe(
        record: TourStartRecord?,
        mode: TourMode,
        target: WaypointConfig,
        location: TourLocation?,
        safety: TourSafety,
        now: TourClock,
        observedDepartureAt: Long? = null,
    ) {
        if (!safety.foreground) { clear(); return }
        if (this.mode != mode || bootId != now.bootId) clear()
        this.mode = mode
        bootId = now.bootId
        if (observedDepartureAt != null && observedDepartureAt in 0..now.elapsedRealtimeMillis &&
            (departureAt == null || observedDepartureAt > departureAt!!)) {
            departureAt = observedDepartureAt
            departureSample = location
            confirmation = null
        }
        // Scope changes discard proof, but must not discard the departure watermark.
        if (recordKey != record?.key || startedAt != record?.startedAt || this.target != target) {
            confirmation = null
            recordKey = record?.key
            startedAt = record?.startedAt
            this.target = target
        }
        if (record == null) { confirmation = null; return }
        val eligible = locationForGate(location)
        if (TourPolicy.locationGate(mode, target, eligible, now, allowStale = true) != TourGate.READY) {
            confirmation = null
            return
        }
        TourPolicy.confirmLocation(record, mode, target, eligible, safety, now)?.let { confirmation = it }
    }
}
