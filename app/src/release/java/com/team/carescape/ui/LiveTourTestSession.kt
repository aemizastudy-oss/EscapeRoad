package com.team.carescape.ui

import android.content.Context
import com.team.carescape.data.TripProgressStore
import com.team.carescape.game.TourGate
import com.team.carescape.game.TourPolicy
import com.team.carescape.model.TourClock
import com.team.carescape.model.TourLocation
import com.team.carescape.model.TourMode
import com.team.carescape.model.TourSafety
import com.team.carescape.model.TourStartRecord
import com.team.carescape.model.WaypointConfig

/** Release never creates or activates an SDK test session. */
internal class LiveTourTestSession private constructor(val id: String) {
    fun progressStore(context: Context): TripProgressStore = TripProgressStore(context)

    fun startGate(
        mode: TourMode,
        target: WaypointConfig,
        location: TourLocation?,
        safety: TourSafety,
        now: TourClock,
    ): TourGate = TourPolicy.startGate(mode, target, location, safety, now)

    fun unlockGate(
        record: TourStartRecord?,
        mode: TourMode,
        target: WaypointConfig,
        safety: TourSafety,
        now: TourClock,
    ): TourGate = TourPolicy.unlockGate(record, mode, target, safety, now)

    companion object {
        const val INTENT_EXTRA = "com.team.carescape.LIVE_SDK_TEST_SESSION"

        @Suppress("UNUSED_PARAMETER")
        fun configure(token: String?) = Unit

        fun capture(): LiveTourTestSession? = null
    }
}
