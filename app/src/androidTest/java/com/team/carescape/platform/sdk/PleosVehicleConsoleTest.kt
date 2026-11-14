package com.team.carescape.platform.sdk

import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.team.carescape.data.TripProgressStore
import com.team.carescape.game.CarControl
import com.team.carescape.game.StoryCatalog
import com.team.carescape.game.StoryEngine
import com.team.carescape.game.StoryPhase
import com.team.carescape.game.StoryPuzzleInput
import com.team.carescape.game.StoryPuzzleProgress
import com.team.carescape.game.StoryPuzzleResult
import com.team.carescape.game.StoryState
import com.team.carescape.game.TourCatalog
import com.team.carescape.game.VehiclePuzzleInputContext
import com.team.carescape.game.VehiclePuzzleInputMapper
import com.team.carescape.game.VehiclePuzzleSample
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import kotlinx.coroutines.Dispatchers
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Opt-in emulator test. Reads real Vehicle SDK samples and sends console controls.
 * Story/foreground fixtures are controlled in memory: this does NOT qualify LIVE tourism/location gates.
 * No MainActivity is launched, so this runtime owns its only puzzleSamples consumer.
 *
 * Run only on the Pleos emulator with -e vehicleConsoleControls true.
 */
@RunWith(AndroidJUnit4::class)
class PleosVehicleConsoleTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    @Test
    fun actualWindowAndWiperSamplesReachMapperAndWindowNeedsExplicitSubmission() = runBlocking {
        withRealConsole { harness ->
            harness.prepareBaselines()
            harness.arm("console-window")
            val windowStory = fixture(4, "c5_window")
            val before = windowStory.copy()

            harness.setAndAwait(CarControl.WINDOW, true, "/vehicle/body?window_fl=75")
            harness.awaitEvents(listOf(CarControl.WINDOW))
            harness.putSafely("/vehicle/body?window_fl=100")
            harness.assertEventsRemain(listOf(CarControl.WINDOW))
            assertEquals("SDK input alone must not solve a story", before, windowStory)
            assertFalse(windowStory.isStepDone("c5_window"))

            // This explicit call models submit, rather than treating a sensor as submit.
            val submitted = StoryEngine.submitPuzzle(windowStory, StoryPuzzleInput.CarSequence(harness.events()))
            assertEquals(StoryPuzzleResult.CORRECT, submitted.result)
            assertTrue(submitted.state.isStepDone("c5_window"))
            assertEquals("Immutable pre-submit fixture remains unchanged", before, windowStory)

            harness.setAndAwait(CarControl.WINDOW, false, "/vehicle/body?window_fl=0")
            harness.arm("console-wiper-three")
            repeat(3) { harness.wiperPulse() }
            val expected = List(3) { CarControl.WIPER }
            harness.awaitEvents(expected)
            harness.putSafely("/vehicle/body?front_wiper=Off")
            harness.assertEventsRemain(expected)

            // Three observed Off->Low transitions are three inputs, not three blade sweeps.
            // The current directional gate must reject these legacy wiper controls.
            val gates = fixture(1, "c2_gates")
            assertFalse(gates.isStepDone("c2_gates"))
            val incomplete = StoryEngine.submitPuzzle(gates, StoryPuzzleInput.CarSequence(harness.events()))
            assertEquals(StoryPuzzleResult.INVALID_INPUT, incomplete.result)
            assertFalse(incomplete.state.isStepDone("c2_gates"))
            Log.i(TAG, "REAL_SDK_WINDOW_SUBMIT_CORRECT; REAL_SDK_WIPER_OFF_TO_LOW_COUNT=3; SYNTHETIC_IN_MEMORY_STORY")
        }
    }

    @Test
    fun sevenGateAnswerUsesActualTurnSignalsBeforeExplicitSubmission() = runBlocking {
        withRealConsole { harness ->
            harness.prepareTurnBaselines()
            harness.arm("console-seven-gates")
            val gates = fixture(1, "c2_gates")
            val before = gates.copy()
            repeat(3) { harness.turnPulse(CarControl.TURN_RIGHT) }
            harness.turnPulse(CarControl.TURN_LEFT)
            repeat(3) { harness.turnPulse(CarControl.TURN_RIGHT) }
            val expected = listOf(CarControl.TURN_RIGHT, CarControl.TURN_RIGHT, CarControl.TURN_RIGHT, CarControl.TURN_LEFT,
                CarControl.TURN_RIGHT, CarControl.TURN_RIGHT, CarControl.TURN_RIGHT)
            harness.awaitEvents(expected)
            assertEquals("Answer collection must not submit", before, gates)
            assertFalse(gates.isStepDone("c2_gates"))
            val submitted = StoryEngine.submitPuzzle(gates, StoryPuzzleInput.CarSequence(harness.events()))
            assertEquals(StoryPuzzleResult.CORRECT, submitted.result)
            assertTrue(submitted.state.isStepDone("c2_gates"))
            Log.i(TAG, "REAL_SDK_TURN_RRRLRRR_SEVEN_GATE_SUBMIT_CORRECT; SYNTHETIC_IN_MEMORY_STORY; LIVE_TOURISM_NOT_VERIFIED")
        }
    }

    private suspend fun withRealConsole(block: suspend (Harness) -> Unit) {
        assumeTrue("Opt-in required: -e vehicleConsoleControls true",
            InstrumentationRegistry.getArguments().getString("vehicleConsoleControls") == "true")
        withTimeout(150_000) {
            coroutineScope {
                val console = ConsoleClient()
                // Reads only durable progress; no removals, fixture writes, migrations or reset.
                val store = TripProgressStore(instrumentation.targetContext)
                val originalProgress = store.load()
                val runtime = withContext(Dispatchers.Main.immediate) { PleosSdkRuntime(instrumentation.targetContext) }
                val harness = Harness(runtime, console)
                var original: ConsoleSnapshot? = null
                var originalFailure: Throwable? = null
                val heartbeat = launch(Dispatchers.IO) {
                    while (isActive) {
                        console.heartbeat(closing = false)
                        delay(800)
                    }
                }
                val collector = launch(Dispatchers.Main.immediate) {
                    runtime.puzzleSamples.collect { harness.onSample(it) }
                }
                try {
                    original = console.snapshot()
                    original.requireParked()
                    withContext(Dispatchers.Main.immediate) {
                        runtime.setForeground(true)
                        runtime.start()
                    }
                    harness.awaitRealSafety()
                    block(harness)
                } catch (failure: Throwable) {
                    originalFailure = failure
                    throw failure
                } finally {
                    withContext(NonCancellable) {
                        val cleanupErrors = mutableListOf<Throwable>()
                        suspend fun cleanup(action: suspend () -> Unit) {
                            try { action() } catch (failure: Throwable) { cleanupErrors += failure }
                        }
                        cleanup { withContext(Dispatchers.Main.immediate) { runtime.close() } }
                        cleanup { collector.cancelAndJoin() }
                        // Restore every control independently, even if an earlier restore fails.
                        original?.let { value ->
                            cleanup { console.request("/vehicle/body?front_wiper=${value.frontWiper}", "PUT") }
                            cleanup { console.request("/vehicle/body?window_fl=${value.windowFl}", "PUT") }
                            cleanup { console.request("/vehicle/turn-signal?mode=${value.turnSignal}", "PUT") }
                            cleanup {
                                val restored = console.snapshot()
                                assertEquals(value.frontWiper, restored.frontWiper)
                                assertEquals(value.windowFl, restored.windowFl)
                                assertEquals(value.hazard, restored.hazard)
                                assertEquals(value.turnSignal, restored.turnSignal)
                            }
                        }
                        cleanup { heartbeat.cancelAndJoin() }
                        cleanup { console.heartbeat(closing = true) }
                        cleanup { assertEquals("SDK test must leave all user progress unchanged", originalProgress, store.load()) }
                        if (cleanupErrors.isNotEmpty()) {
                            val primary = originalFailure ?: cleanupErrors.removeAt(0)
                            cleanupErrors.forEach(primary::addSuppressed)
                            if (originalFailure == null) throw primary
                        }
                    }
                }
            }
        }
    }

    private class Harness(val runtime: PleosSdkRuntime, private val console: ConsoleClient) {
        private val mapper = VehiclePuzzleInputMapper()
        private val accepted = mutableListOf<CarControl>()
        private var armed = false

        fun onSample(sample: VehiclePuzzleSample) {
            check(android.os.Looper.myLooper() == android.os.Looper.getMainLooper())
            if (armed) mapper.accept(sample, SystemClock.elapsedRealtime())?.let(accepted::add)
        }

        suspend fun awaitRealSafety() = awaitMain("real P, stationary and CarUx; harness foreground flag") {
            runtime.safety.value.let { it.foreground && it.parked && it.stationary && it.carUxAllowed }
        }

        private suspend fun assertSafe() = withContext(Dispatchers.Main.immediate) {
            assertTrue("Console controls require real SDK P/stationary/CarUx, never mock safety",
                runtime.safety.value.let { it.foreground && it.parked && it.stationary && it.carUxAllowed })
        }

        suspend fun putSafely(path: String) {
            assertSafe()
            console.request(path, "PUT")
            assertSafe()
        }

        suspend fun prepareBaselines() {
            putSafely("/vehicle/body?front_wiper=Off")
            putSafely("/vehicle/body?window_fl=0")
            assertEquals(SdkAvailability.READY, awaitFeature(CarControl.WIPER).availability)
            assertEquals(SdkAvailability.READY, awaitFeature(CarControl.WINDOW).availability)
            awaitLatest(CarControl.WIPER, false)
            awaitLatest(CarControl.WINDOW, false)
        }

        suspend fun prepareTurnBaselines() {
            putSafely("/vehicle/turn-signal?mode=Off")
            assertEquals(SdkAvailability.READY, awaitFeature(CarControl.TURN_LEFT).availability)
            assertEquals(SdkAvailability.READY, awaitFeature(CarControl.TURN_RIGHT).availability)
            awaitLatest(CarControl.TURN_LEFT, false)
            awaitLatest(CarControl.TURN_RIGHT, false)
        }

        suspend fun turnPulse(control: CarControl) {
            require(control == CarControl.TURN_LEFT || control == CarControl.TURN_RIGHT)
            val mode = if (control == CarControl.TURN_RIGHT) "BlinkRight" else "BlinkLeft"
            val expected = events() + control
            setAndAwait(control, true, "/vehicle/turn-signal?mode=$mode")
            awaitEvents(expected)
            // An already active signal, including its repeated SDK snapshots, adds no gate.
            putSafely("/vehicle/turn-signal?mode=$mode")
            if (expected.size == 1) delay(3_500) // Hold across polling and several visible flashes.
            assertEventsRemain(expected)
            setAndAwait(control, false, "/vehicle/turn-signal?mode=Off")
            assertEventsRemain(expected)
        }

        suspend fun arm(scope: String) = withContext(Dispatchers.Main.immediate) {
            accepted.clear()
            mapper.reset(VehiclePuzzleInputContext(scopeKey = scope, eligible = true,
                activatedAtElapsedMillis = SystemClock.elapsedRealtime(), generation = runtime.puzzleGeneration,
                sequenceFloor = runtime.puzzleSequence))
            runtime.puzzleLatest.values.forEach { mapper.seedBaseline(it) }
            armed = true
        }

        suspend fun setAndAwait(control: CarControl, active: Boolean, path: String) {
            val floor = withContext(Dispatchers.Main.immediate) { runtime.puzzleSequence }
            putSafely(path)
            awaitLatest(control, active, floor)
        }

        suspend fun wiperPulse() {
            setAndAwait(CarControl.WIPER, true, "/vehicle/body?front_wiper=Low")
            setAndAwait(CarControl.WIPER, false, "/vehicle/body?front_wiper=Off")
        }

        suspend fun awaitLatest(control: CarControl, active: Boolean, afterSequence: Long = -1L) =
            awaitMain("fresh actual SDK $control=$active after $afterSequence") {
                runtime.puzzleLatest[control]?.let {
                    it.active == active && it.generation == runtime.puzzleGeneration &&
                        it.sequence > afterSequence &&
                        SystemClock.elapsedRealtime() - it.receivedElapsedMillis in 0..8_999
                } == true
            }

        suspend fun awaitFeature(control: CarControl): SdkFeatureState {
            awaitMain("terminal SDK capability for $control") {
                runtime.puzzleStatus.value[control]?.availability in setOf(SdkAvailability.READY,
                    SdkAvailability.UNSUPPORTED, SdkAvailability.PERMISSION_DENIED, SdkAvailability.ERROR)
            }
            return withContext(Dispatchers.Main.immediate) { checkNotNull(runtime.puzzleStatus.value[control]) }
        }

        suspend fun events(): List<CarControl> = withContext(Dispatchers.Main.immediate) { accepted.toList() }

        suspend fun awaitEvents(expected: List<CarControl>) {
            awaitMain("mapped sequence $expected") { accepted.size >= expected.size }
            assertEquals(expected, events())
        }

        suspend fun assertEventsRemain(expected: List<CarControl>) {
            // Real-time observation window, not a UI frame/timing assertion.
            delay(500)
            assertEquals("Repeated level and inactive return must not add inputs", expected, events())
        }

        private suspend fun awaitMain(label: String, condition: () -> Boolean) =
            withContext(Dispatchers.Main.immediate) {
                withTimeout(20_000) {
                    while (!condition()) delay(40)
                }
                Log.i(TAG, "OBSERVED:$label")
            }
    }

    private data class ConsoleSnapshot(
        val frontWiper: String, val windowFl: Int, val hazard: Boolean, val turnSignal: String,
        val gear: String, val speed: Double,
    ) {
        fun requireParked() {
            assertEquals("Console must already be in Park; test never changes gear", "Park", gear)
            assertEquals("Console must already be stationary", 0.0, speed, 0.001)
        }
    }

    private class ConsoleClient {
        private val client = "carescape-instrumentation-${UUID.randomUUID()}"
        private val base = "http://127.0.0.1:8088"

        suspend fun heartbeat(closing: Boolean) = request(
            "/browser/session?client=$client${if (closing) "&closing=1" else ""}", "POST")

        suspend fun snapshot(): ConsoleSnapshot {
            val state = JSONObject(request("/vehicle/state", "GET"))
            val body = state.getJSONObject("body")
            return ConsoleSnapshot(body.getJSONObject("wipers").getJSONObject("front").getString("mode"),
                body.getJSONObject("windows").getInt("front_left"),
                state.getJSONObject("lights").getBoolean("hazard_on"),
                state.getJSONObject("lights").getString("turn_signal_mode"),
                state.getJSONObject("gear").getJSONObject("actual").getString("value"),
                state.getJSONObject("controls").getDouble("speed_kph"))
        }

        suspend fun request(path: String, method: String): String = withContext(Dispatchers.IO) {
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

    private fun fixture(chapterIndex: Int, focus: String): StoryState {
        val episode = StoryCatalog.episodeFor(StoryCatalog.EXPLORE_FLOW_VERSION)
        val chapter = episode.chapters[chapterIndex]
        val spot = checkNotNull(chapter.scene).hotspots.single { focus in it.locks }
        val prerequisites = listOfNotNull(spot.requires,
            spot.lockStep?.takeIf { focus == spot.thenLock })
        val state = StoryState(episode, chapterIndex, StoryPhase.PUZZLE,
            flowVersion = StoryCatalog.EXPLORE_FLOW_VERSION, exploreFocus = focus,
            puzzleProgress = listOf(StoryPuzzleProgress(TourCatalog.checkpoints[chapterIndex].id,
                solvedSteps = prerequisites, found = prerequisites)))
        assertNotNull("Fixture must open its actual production puzzle", state.currentPuzzle)
        assertEquals(focus, state.currentPuzzle!!.id)
        return state
    }

    companion object { private const val TAG = "CarEscapeVehicleSdkTest" }
}
