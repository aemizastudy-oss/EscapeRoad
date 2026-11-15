package com.team.carescape.ui

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import com.team.carescape.data.TripProgressStore
import com.team.carescape.data.TripStateSerializer
import com.team.carescape.data.TripStoreState
import com.team.carescape.game.TourGate
import com.team.carescape.game.TourPolicy
import com.team.carescape.model.TourClock
import com.team.carescape.model.TourLocation
import com.team.carescape.model.TourMode
import com.team.carescape.model.TourSafety
import com.team.carescape.model.TourStartRecord
import com.team.carescape.model.WaypointConfig
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Isolated debug LIVE SDK exercise; excluded location/time checks are never tourism evidence. */
internal class LiveTourTestSession private constructor(val id: String) {
    private var cachedStore: TripProgressStore? = null

    @Synchronized
    fun progressStore(context: Context): TripProgressStore {
        cachedStore?.let { return it }
        val directory = File(context.applicationContext.filesDir, "sdk-live-tests").canonicalFile
        val sessionDirectory = File(directory, id).canonicalFile
        check(sessionDirectory.parentFile == directory) { "Invalid LIVE SDK test directory" }
        val file = File(sessionDirectory, TripProgressStore.FILE_NAME).canonicalFile
        check(file.parentFile == sessionDirectory) { "Invalid LIVE SDK test progress path" }
        return TripProgressStore(LiveTestStoreRegistry.forFile(file)).also { cachedStore = it }
    }

    fun startGate(
        mode: TourMode,
        target: WaypointConfig,
        location: TourLocation?,
        safety: TourSafety,
        now: TourClock,
    ): TourGate {
        // Evaluate the unmodified policy first: its safety, clock and source-mode checks still apply.
        val gate = TourPolicy.startGate(mode, target, location, safety, now)
        return if (mode == TourMode.LIVE && gate in EXCLUDED_LOCATION_GATES) TourGate.READY else gate
    }

    fun unlockGate(
        record: TourStartRecord?,
        mode: TourMode,
        target: WaypointConfig,
        safety: TourSafety,
        now: TourClock,
    ): TourGate {
        val gate = TourPolicy.unlockGate(record, mode, target, safety, now)
        // The isolated LIVE exercise excludes only the wait; all other return checks remain shared.
        return if (mode == TourMode.LIVE && gate == TourGate.TOUR_TIME_REMAINING) TourGate.READY else gate
    }

    companion object {
        const val INTENT_EXTRA = "com.team.carescape.LIVE_SDK_TEST_SESSION"

        @Volatile private var current: LiveTourTestSession? = null

        @Synchronized
        fun configure(token: String?) {
            val canonicalId = token?.takeIf { it.length == 36 }?.let { candidate ->
                runCatching { UUID.fromString(candidate).toString() }.getOrNull()
                    ?.takeIf { it.equals(candidate, ignoreCase = true) }
            }
            current = when {
                canonicalId == null -> null
                current?.id == canonicalId -> current
                else -> LiveTourTestSession(canonicalId)
            }
        }

        fun capture(): LiveTourTestSession? = current

        private val EXCLUDED_LOCATION_GATES = setOf(
            TourGate.COORDINATES_NOT_CONFIGURED,
            TourGate.LOCATION_MISSING,
            TourGate.LOCATION_ERROR,
            TourGate.LOCATION_NOT_UPDATED,
            TourGate.LOCATION_TIMESTAMP_INVALID,
            TourGate.LOCATION_STALE,
            TourGate.LOCATION_INVALID,
            TourGate.LOCATION_ACCURACY_UNKNOWN,
            TourGate.LOCATION_INACCURATE,
            TourGate.OUTSIDE_RADIUS,
        )
    }
}

/** Reconfiguration may revisit a UUID while an earlier Activity still owns its session. */
private object LiveTestStoreRegistry {
    private val stores = mutableMapOf<String, DataStore<TripStoreState>>()

    @Synchronized
    fun forFile(file: File): DataStore<TripStoreState> = stores.getOrPut(file.canonicalPath) {
        DataStoreFactory.create(
            serializer = TripStateSerializer,
            scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
            produceFile = { file },
        )
    }
}
