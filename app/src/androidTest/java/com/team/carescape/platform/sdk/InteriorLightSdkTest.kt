package com.team.carescape.platform.sdk

import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.team.carescape.game.InteriorLightSnapshot
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Opt-in Pleos emulator test: console switches are stimuli, READING_LIGHTS_STATE is the only oracle.
 * Does not launch MainActivity, submit a puzzle, or write user progress.
 * Requires -e vehicleConsoleControls true. Never changes gear, speed, or the global cabin switch.
 */
@RunWith(AndroidJUnit4::class)
class InteriorLightSdkTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    @Test fun actualReadingLightStatesReportZeroThreeFourThenThreeAndRestoreConsole(): Unit = runBlocking {
        assumeTrue(
            "Opt-in required: -e vehicleConsoleControls true",
            InstrumentationRegistry.getArguments().getString("vehicleConsoleControls") == "true",
        )
        withTimeout(150_000L) {
            coroutineScope {
                val console = ConsoleClient()
                var original: ConsoleSnapshot? = null
                var heartbeat: Job? = null
                var reader: PleosInteriorLightReader? = null
                var safetyReader: CarSafetyMonitor? = null
                // Every SDK callback and access to these observations belongs to Main.
                var latest = InteriorLightSnapshot()
                var safety = CarSafetyReading()
                var primaryFailure: Throwable? = null

                suspend fun assertSafe() = withContext(Dispatchers.Main.immediate) {
                    assertTrue(
                        "Light controls require actual fresh P/stationary/CarUx, never mock safety: ${safety.status}",
                        safety.status.availability == SdkAvailability.READY &&
                            safety.parked && safety.stationary && safety.uxAllowed,
                    )
                }

                suspend fun applyAndAwait(expected: Map<String, Boolean>) {
                    assertEquals(InteriorLightSnapshot.AREAS.toSet(), expected.keys)
                    assertSafe()
                    val after = SystemClock.elapsedRealtime()
                    for ((area, on) in expected) {
                        assertSafe()
                        console.putLight(area, on)
                    }
                    val consoleState = console.snapshot()
                    consoleState.requireParked()
                    assertEquals("The console must acknowledge all four switch inputs", expected, consoleState.lights)
                    val expectedCount = expected.values.count { it }
                    awaitMain(
                        label = "actual READING_LIGHTS_STATE count=$expectedCount and seats=$expected after console PUT",
                        diagnostic = {
                            "ready=${latest.ready}, states=${latest.states}, count=${latest.validCount(SystemClock.elapsedRealtime())}, " +
                                "oldestReceived=${latest.receivedElapsedMillis}, message=${latest.message}; " +
                                "console switch values were confirmed separately; no switch fallback is allowed"
                        },
                    ) {
                        latest.validCount(SystemClock.elapsedRealtime()) == expectedCount &&
                            latest.states == expected && latest.receivedElapsedMillis >= after
                    }
                    assertSafe()
                    Log.i(TAG, "OBSERVED_REAL_READING_LIGHTS_STATE_COUNT=$expectedCount; FOUR_SEAT_STATE_MATCH")
                }

                try {
                    original = console.snapshot().also { it.requireParked() }
                    // Establish this test's own session before writing any light input.
                    console.heartbeat(closing = false)
                    heartbeat = launch(Dispatchers.IO) {
                        while (isActive) {
                            console.heartbeat(closing = false)
                            delay(800L)
                        }
                    }
                    withContext(Dispatchers.Main.immediate) {
                        safetyReader = PleosCarSafety(instrumentation.targetContext) { safety = it }.also { it.start() }
                        reader = PleosInteriorLightReader(instrumentation.targetContext) { latest = it }.also { it.start() }
                    }
                    awaitMain(
                        "actual P/stationary/CarUx safety",
                        diagnostic = { "safety=${safety.status}, P=${safety.parked}, stationary=${safety.stationary}, UX=${safety.uxAllowed}" },
                    ) {
                        safety.status.availability == SdkAvailability.READY &&
                            safety.parked && safety.stationary && safety.uxAllowed
                    }
                    awaitMain(
                        "readable actual states for all four seats before controls",
                        diagnostic = { "ready=${latest.ready}, states=${latest.states}, message=${latest.message}" },
                    ) { latest.validCount(SystemClock.elapsedRealtime()) != null }

                    val allOff = InteriorLightSnapshot.AREAS.associateWith { false }
                    val firstThreeOn = InteriorLightSnapshot.AREAS.associateWith { it != "rear_right" }
                    val allOn = InteriorLightSnapshot.AREAS.associateWith { true }
                    // Use a different set of three on return, proving the first lamp is independently readable.
                    val differentThreeOn = InteriorLightSnapshot.AREAS.associateWith { it != "front_left" }
                    applyAndAwait(allOff)
                    applyAndAwait(firstThreeOn)
                    applyAndAwait(allOn)
                    applyAndAwait(differentThreeOn)
                    Log.i(TAG, "REAL_SDK_INTERIOR_LIGHT_COUNTS_0_3_4_3_PASS; NO_SWITCH_FALLBACK; LIVE_TOURISM_NOT_VERIFIED")
                    Unit
                } catch (failure: Throwable) {
                    primaryFailure = failure
                    throw failure
                } finally {
                    withContext(NonCancellable) {
                        val cleanupErrors = mutableListOf<Throwable>()
                        suspend fun cleanup(action: suspend () -> Unit) {
                            try { action() } catch (failure: Throwable) { cleanupErrors += failure }
                        }
                        cleanup { withContext(Dispatchers.Main.immediate) { reader?.close() } }
                        cleanup { withContext(Dispatchers.Main.immediate) { safetyReader?.close() } }
                        original?.let { snapshot ->
                            // Attempt each restoration independently even after a timeout/assertion/earlier failure.
                            cleanup { console.heartbeat(closing = false) }
                            for ((area, on) in snapshot.lights) cleanup { console.putLight(area, on) }
                            cleanup {
                                assertEquals("All original console reading-light switches must be restored",
                                    snapshot.lights, console.snapshot().lights)
                                Log.i(TAG, "ORIGINAL_FOUR_READING_LIGHT_SWITCHES_RESTORED")
                            }
                        }
                        cleanup { heartbeat?.cancelAndJoin() }
                        cleanup { console.heartbeat(closing = true) }
                        if (cleanupErrors.isNotEmpty()) {
                            val primary = primaryFailure ?: cleanupErrors.removeAt(0)
                            cleanupErrors.forEach(primary::addSuppressed)
                            if (primaryFailure == null) throw primary
                        }
                    }
                }
            }
        }
    }

    private suspend fun awaitMain(
        label: String,
        diagnostic: () -> String,
        condition: () -> Boolean,
    ) = withContext(Dispatchers.Main.immediate) {
        val deadline = SystemClock.elapsedRealtime() + 25_000L
        while (!condition()) {
            if (SystemClock.elapsedRealtime() >= deadline) {
                throw AssertionError("Not observed: $label. ${diagnostic()}")
            }
            delay(40L)
        }
        Log.i(TAG, "OBSERVED:$label")
    }

    private data class ConsoleSnapshot(val lights: Map<String, Boolean>, val gear: String, val speed: Double) {
        fun requireParked() {
            assertEquals("Console must already be in Park; this test never changes gear", "Park", gear)
            assertEquals("Console must already be stationary", 0.0, speed, 0.001)
        }
    }

    private class ConsoleClient {
        private val client = "carescape-interior-light-test-${UUID.randomUUID()}"
        // Existing successful SDK tests use the console inside the emulator, not the host alias 10.0.2.2.
        // Debug network security also permits this localhost endpoint specifically.
        private val base = "http://127.0.0.1:8088"

        suspend fun heartbeat(closing: Boolean) = request(
            "/browser/session?client=$client${if (closing) "&closing=1" else ""}", "POST",
        )

        suspend fun putLight(area: String, on: Boolean) {
            require(area in InteriorLightSnapshot.AREAS)
            // GET /vehicle/catalog declares area_field=area and boolean field=value.
            request("/vehicle/reading-lights?area=$area&value=$on", "PUT")
        }

        suspend fun snapshot(): ConsoleSnapshot {
            val state = JSONObject(request("/vehicle/state", "GET"))
            val lights = state.getJSONObject("body").getJSONObject("reading_lights")
            return ConsoleSnapshot(
                lights = InteriorLightSnapshot.AREAS.associateWith { lights.getBoolean(it) },
                gear = state.getJSONObject("gear").getJSONObject("actual").getString("value"),
                speed = state.getJSONObject("controls").getDouble("speed_kph"),
            )
        }

        private suspend fun request(path: String, method: String): String = withContext(Dispatchers.IO) {
            val connection = URL(base + path).openConnection() as HttpURLConnection
            try {
                connection.requestMethod = method
                connection.connectTimeout = 1_500
                connection.readTimeout = 1_500
                connection.useCaches = false
                if (method == "POST") {
                    connection.doOutput = true
                    connection.setFixedLengthStreamingMode(0)
                    connection.outputStream.close()
                }
                assertTrue("Console $method $path failed HTTP ${connection.responseCode}", connection.responseCode in 200..299)
                connection.inputStream.bufferedReader().use { it.readText() }
            } finally { connection.disconnect() }
        }
    }

    private companion object { const val TAG = "CarEscapeInteriorLightSdkTest" }
}
