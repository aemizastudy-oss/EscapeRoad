package com.team.carescape.drive

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Log
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Debug builds only, demo (MOCK) trips only: after 출발하기 the emulator car drives itself.
 * Gear and speed go through the Pleos Virtual Console (127.0.0.1:8088 inside the emulator), so the
 * vehicle SDK, driving view and navigation react as in a real car. The position is fed to the drive
 * chapter along a bundled road route (assets/demo_routes.json), because an app cannot move the
 * emulator's GPS itself.
 *
 * Timing: about 60 seconds from D to parking at the place, regardless of the leg's length (time-compressed):
 * enough for the BGM, the departure line, the "곧 도착합니다" line and a navigation prompt pausing them.
 */
internal object DemoDrive {
    /** Whole route length in seconds. The 500 m arrival radius is reached at about 3/4 of it, so the car parks about 60 s after D. */
    const val LEG_SECONDS = 80
    private const val TAG = "DemoDrive"
    private const val CONSOLE = "http://127.0.0.1:8088"

    @Serializable private data class Leg(val chapterIndex: Int, val distanceM: Int, val points: List<List<Double>>)
    @Serializable private data class Routes(val start: List<Double>, val legs: List<Leg>)

    fun available(context: Context): Boolean = routes(context) != null

    private var cached: Routes? = null
    private fun routes(context: Context): Routes? = cached ?: runCatching {
        val text = context.assets.open("demo_routes.json").bufferedReader().use { it.readText() }
        Json { ignoreUnknownKeys = true }.decodeFromString<Routes>(text)
    }.getOrNull().also { cached = it }

    /** Where the demo trip starts (before the first leg). */
    fun start(context: Context): Pair<Double, Double>? = routes(context)?.start?.let { it[0] to it[1] }

    /**
     * Drives leg [chapterIndex]: D, speed and positions every second, then speed 0 and P at the place.
     * [onFix] and [onDone] are called on the main thread. Close the result to abort (the car is put in P).
     */
    fun drive(context: Context, chapterIndex: Int, onFix: (Double, Double) -> Unit, onDone: () -> Unit): AutoCloseable? {
        val leg = routes(context)?.legs?.firstOrNull { it.chapterIndex == chapterIndex } ?: return null
        val points = leg.points.map { it[0] to it[1] }
        if (points.size < 2) return null
        return Run(points, onFix, onDone).also { it.begin() }
    }

    private class Run(
        private val points: List<Pair<Double, Double>>,
        private val onFix: (Double, Double) -> Unit,
        private val onDone: () -> Unit,
    ) : AutoCloseable {
        private val main = Handler(Looper.getMainLooper())
        private val thread = HandlerThread("demo-drive").apply { start() }
        private val io = Handler(thread.looper)
        private val cumulative = DoubleArray(points.size).also { c ->
            for (i in 1 until points.size) c[i] = c[i - 1] + meters(points[i - 1], points[i])
        }
        @Volatile private var closed = false
        private var second = 0
        private var speed = 0.0

        fun begin() {
            io.post(heartbeat)
            io.postDelayed({ put("/vehicle/gear?gear=Drive"); io.post(tick) }, 1_500)
        }

        // The Virtual Console accepts writes only while a client keeps a session open.
        private val heartbeat = object : Runnable {
            override fun run() {
                if (closed) return
                http("POST", "/browser/session?client=escape-road-demo")
                io.postDelayed(this, 1_000)
            }
        }

        private val tick = object : Runnable {
            override fun run() {
                if (closed) return
                second++
                val t = (second.toDouble() / LEG_SECONDS).coerceAtMost(1.0)
                // Ease in/out so the car pulls away and slows down at the place.
                val f = t * t * (3 - 2 * t)
                val (lat, lon) = at(f * cumulative.last())
                val target = if (t < 1.0) 32.0 + 22.0 * (0.5 + 0.5 * kotlin.math.sin(second / 6.0)) else 0.0
                speed = if (t > 0.9) max(0.0, speed - 8) else min(target, speed + 9)
                put("/vehicle/controls?speed=${speed.toInt()}&accelerator=${if (speed > 0) 10 else 0}&brake=${if (speed > 0) 0 else 30}&steering=0")
                main.post { if (!closed) onFix(lat, lon) }
                if (t < 1.0) { io.postDelayed(this, 1_000); return }
                // At the place: a second fix confirms arrival, then park.
                io.postDelayed({
                    main.post { if (!closed) onFix(lat, lon) }
                    put("/vehicle/controls?speed=0&accelerator=0&brake=30&steering=0")
                    io.postDelayed({ put("/vehicle/gear?gear=Park"); finish() }, 2_500)
                }, 1_000)
            }
        }

        private fun at(distance: Double): Pair<Double, Double> {
            val k = cumulative.indexOfFirst { it >= distance }.let { if (it <= 0) 1 else it }
            val seg = cumulative[k] - cumulative[k - 1]
            val f = if (seg <= 0) 1.0 else ((distance - cumulative[k - 1]) / seg).coerceIn(0.0, 1.0)
            val a = points[k - 1]; val b = points[k]
            return (a.first + (b.first - a.first) * f) to (a.second + (b.second - a.second) * f)
        }

        private fun finish() {
            closed = true
            io.removeCallbacksAndMessages(null)
            thread.quitSafely()
            main.post(onDone)
        }

        /** Stops where the car is (arrived within the place's radius, or aborted): brake, then P. */
        override fun close() {
            if (closed) return
            closed = true
            io.removeCallbacksAndMessages(null)
            io.post {
                http("POST", "/browser/session?client=escape-road-demo")
                put("/vehicle/controls?speed=0&accelerator=0&brake=30&steering=0")
            }
            io.postDelayed({
                http("POST", "/browser/session?client=escape-road-demo")
                put("/vehicle/gear?gear=Park")
                thread.quitSafely()
            }, 2_000)
        }

        private fun put(path: String) = http("PUT", path)

        private fun http(method: String, path: String) {
            runCatching {
                (URL(CONSOLE + path).openConnection() as HttpURLConnection).run {
                    requestMethod = method; connectTimeout = 1_500; readTimeout = 1_500
                    responseCode.also { disconnect() }
                }
            }.onFailure { Log.w(TAG, "$method $path: ${it.message}") }
        }

        private fun meters(a: Pair<Double, Double>, b: Pair<Double, Double>): Double {
            val dy = (b.first - a.first) * 111_195.0
            val dx = (b.second - a.second) * 111_195.0 * cos(Math.toRadians(a.first))
            return sqrt(dx * dx + dy * dy)
        }
    }
}
