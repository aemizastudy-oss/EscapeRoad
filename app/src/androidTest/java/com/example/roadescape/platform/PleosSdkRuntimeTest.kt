package com.example.roadescape.platform

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.roadescape.MainActivity
import com.example.roadescape.model.TourMode
import com.example.roadescape.model.TourSafety
import com.example.roadescape.platform.sdk.PleosSdkRuntime
import com.example.roadescape.platform.sdk.PleosSdkStatus
import com.example.roadescape.platform.sdk.SdkAvailability
import com.example.roadescape.platform.sdk.SdkFeatureState
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Actual AAR/AAOS initialization only: never request a route, speak, record audio, or grant permissions. */
@RunWith(AndroidJUnit4::class)
class PleosSdkRuntimeTest {
    @Test
    fun actualSdkObservationsFailClosedAndRespectLifecycle() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        scenario.moveToState(Lifecycle.State.RESUMED)
        val runtime = onMain { PleosSdkRuntime(context) }
        val report = JSONObject()
            .put("schemaVersion", 1)
            .put("observationType", "ACTUAL_SDK_INITIALIZATION_ONLY")
            .put("permissionsAutomaticallyGranted", false)
            .put("routeRequested", false)
            .put("speechRequested", false)
            .put("recognitionRequested", false)
        var failureCode: String? = null
        try {
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
            onMain { runtime.setForeground(true); runtime.start() }
            val startedAt = SystemClock.elapsedRealtime()
            var observation = onMain { observe(runtime) }
            var updatedLocationObserved = observation.locationCallbackReceived
            var confidenceObserved = observation.status.confidenceRaw != null
            while (SystemClock.elapsedRealtime() - startedAt < 15_000L) {
                // Observe SDK initialization callbacks on the instrumentation thread; never block the main looper.
                Thread.sleep(250)
                observation = onMain { observe(runtime) }
                updatedLocationObserved = updatedLocationObserved || observation.locationCallbackReceived
                confidenceObserved = confidenceObserved || observation.status.confidenceRaw != null
                assertUnrequestedAudioAndUnverifiedAccuracy(observation)
                val states = listOf(observation.status.fused, observation.status.navigation,
                    observation.status.carSafety, observation.status.vehicle, observation.status.tts, observation.status.stt)
                if (SystemClock.elapsedRealtime() - startedAt >= 3_000L &&
                    states.none { it.availability == SdkAvailability.CHECKING }
                ) break
            }
            report.put("observationDurationMillis", SystemClock.elapsedRealtime() - startedAt)
            report.put("foregroundObserved", observation.json())
            report.put("locationCallbackReceived", updatedLocationObserved)
            report.put("confidenceObserved", confidenceObserved)
            assertUnrequestedAudioAndUnverifiedAccuracy(observation)

            val activeRevision = observation.revision
            val background = onMain {
                runtime.setForeground(false)
                observe(runtime)
            }
            report.put("backgroundImmediate", background.json())
            assertInactive(background)
            assertTrue("Background invalidation must advance the command revision", background.revision > activeRevision)

            val restarted = onMain {
                runtime.stop()
                runtime.setForeground(true)
                runtime.start()
                observe(runtime)
            }
            report.put("restarted", restarted.json())
            assertTrue("A stopped runtime must accept a new foreground lifecycle", restarted.safety.foreground)
            assertTrue("Restart must create a new input revision", restarted.revision > background.revision)
            assertUnrequestedAudioAndUnverifiedAccuracy(restarted)

            val closed = onMain { runtime.close(); observe(runtime) }
            report.put("closedImmediate", closed.json())
            assertInactive(closed)
            Thread.sleep(250)
            val afterClose = onMain {
                // Closing is final; late callbacks and an accidental start must not resurrect this instance.
                runtime.start()
                runtime.setForeground(true)
                observe(runtime)
            }
            report.put("closedAfterCallbacks", afterClose.json())
            assertInactive(afterClose)
            assertEquals(closed.revision, afterClose.revision)
        } catch (error: Throwable) {
            failureCode = error.javaClass.simpleName
            throw error
        } finally {
            onMain { runtime.close() }
            report.put("testFailureCode", failureCode ?: JSONObject.NULL)
            // Deliberately serialize only the explicit allowlist below, never SDK/model toString().
            try {
                File(context.cacheDir, "pleos-sdk-smoke.json").writeText(report.toString(2), Charsets.UTF_8)
                Log.i("RoadEscapeSdkSmoke", report.toString())
            } finally { scenario.close() }
        }
    }

    private fun assertUnrequestedAudioAndUnverifiedAccuracy(observation: Observation) {
        assertFalse("Fused confidence must not be relabeled as verified meter accuracy", observation.status.locationAccuracyVerified)
        assertFalse("FusedLocation 1.1.3 does not provide verified accuracyMeters", observation.accuracyKnown)
        if (observation.locationMode != null) assertEquals(TourMode.LIVE, observation.locationMode)
        assertFalse("Initialization must never start TTS playback", observation.status.speaking)
        assertFalse("Initialization must never start microphone recognition", observation.status.listening)
    }

    private fun assertInactive(observation: Observation) {
        assertFalse(observation.safety.foreground)
        assertFalse(observation.safety.parked)
        assertFalse(observation.safety.stationary)
        assertFalse(observation.safety.carUxAllowed)
        assertFalse(observation.status.speaking)
        assertFalse(observation.status.listening)
    }

    private fun observe(runtime: PleosSdkRuntime): Observation {
        val location = runtime.locations.value
        return Observation(
            status = runtime.status.value,
            safety = runtime.safety.value,
            revision = runtime.revision,
            locationCallbackReceived = location?.fromLiveUpdate == true &&
                location.timestampEpochMillis != null && location.receivedElapsedRealtimeMillis != null,
            locationMode = location?.mode,
            locationError = location?.error,
            accuracyKnown = location?.accuracyMeters != null,
        )
    }

    private data class Observation(
        val status: PleosSdkStatus,
        val safety: TourSafety,
        val revision: Long,
        val locationCallbackReceived: Boolean,
        val locationMode: TourMode?,
        val locationError: String?,
        val accuracyKnown: Boolean,
    ) {
        fun json(): JSONObject = JSONObject()
            .put("fused", status.fused.json())
            .put("navigation", status.navigation.json())
            .put("carSafety", status.carSafety.json())
            .put("vehicle", status.vehicle.json())
            .put("tts", status.tts.json())
            .put("stt", status.stt.json())
            .put("routeState", safeCode(status.routeState))
            .put("locationCallbackReceived", locationCallbackReceived)
            .put("mode", locationMode?.name ?: JSONObject.NULL)
            .put("locationError", safeCode(locationError))
            .put("accuracyKnown", accuracyKnown)
            .put("locationAccuracyVerified", status.locationAccuracyVerified)
            .put("confidenceObserved", status.confidenceRaw != null)
            .put("brakeObservationPresent", status.brakePedal != null)
            .put("batteryObservationPresent", status.batteryPercent != null)
            .put("foreground", safety.foreground)
            .put("parked", safety.parked)
            .put("stationary", safety.stationary)
            .put("carUxAllowed", safety.carUxAllowed)
            .put("speaking", status.speaking)
            .put("listening", status.listening)
            .put("revision", revision)
    }

    private fun <T> onMain(action: () -> T): T {
        val result = AtomicReference<Result<T>>()
        InstrumentationRegistry.getInstrumentation().runOnMainSync { result.set(runCatching(action)) }
        return result.get().getOrThrow()
    }

    private companion object {
        fun SdkFeatureState.json(): JSONObject = JSONObject()
            .put("availability", availability.name)
            .put("detailCode", safeCode(detail))

        fun safeCode(value: String?): Any = when {
            value == null -> JSONObject.NULL
            value.length <= 120 && value.all { it.isLetterOrDigit() && it.code < 128 || it in "_:.\\-" } -> value
            else -> "REDACTED_NON_CODE"
        }
    }
}
