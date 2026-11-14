package com.team.carescape.drive

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.team.carescape.MainActivity
import com.team.carescape.R
import com.team.carescape.data.StoredTrip
import com.team.carescape.data.TripProgressStore
import com.team.carescape.game.StoryPhase
import com.team.carescape.model.TourMode
import java.io.FileDescriptor
import java.io.PrintWriter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Drive chapter: plays the BGM and the chapter's travel lines while driving, with the app off screen
 * (Pleos shows its driving view and blocks app screens in D). It only reads the game's saved trip and
 * never writes it; arrival and puzzles stay with the game in the app.
 */
class DriveService : Service() {
    private val main = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var store: TripProgressStore
    private var signals: DriveSignals? = null
    private var audio: DriveAudio? = null
    private var state = DriveState()
    private var flowVersion = 0
    private var script: DriveScript? = null
    private var lastLine: String? = null
    private var status: String? = null
    private var started = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        goForeground()
        if (intent?.action == ACTION_STOP) { stopSelf(); return START_NOT_STICKY }
        if (intent?.action == ACTION_DEPART) {
            departRequested = true
            demoDriveRequested = intent.getBooleanExtra(EXTRA_ALLOW_DEMO_DRIVE, false)
        }
        if (started) { if (departRequested) scope.launch { refreshTrip(); depart() }; return START_STICKY }
        started = true
        store = TripProgressStore(this)
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        state = DriveState(
            firedLines = prefs.getStringSet(KEY_FIRED, emptySet()).orEmpty(),
            arrivedLegs = prefs.getStringSet(KEY_ARRIVED, emptySet()).orEmpty(),
        )
        script = DriveScript.load(this)
        audio = DriveAudio(this) { text -> lastLine = text; notifyStatus() }
        scope.launch { NarrationSwitch.state(this@DriveService).collect { on -> audio?.voiceEnabled = on } }
        signals = DriveSignals(
            this,
            onGear = { feed(DriveInput.Gear(it)); if (it == DriveGear.DRIVE) refreshTripSoon() },
            // While the debug demo drive runs, its route positions replace the emulator's stationary GPS.
            onLocation = { lat, lon -> if (demo == null) feed(DriveInput.Location(lat, lon)) },
            onNavigation = { m, s -> feed(DriveInput.Navigation(m, s, System.currentTimeMillis())) },
            onStatus = { status = it; notifyStatus() },
        ).also { it.start() }
        scope.launch {
            refreshTrip(); depart()
            while (true) { refreshTrip(); delay(TRIP_POLL_MS) }
        }
        return START_STICKY
    }

    private var departRequested = false
    private var demoDriveRequested = false
    private var tripMode: TourMode? = null
    private var demo: AutoCloseable? = null

    /** 출발하기: destination now, leg armed for the next D. In a demo trip (debug builds) the car then drives itself. */
    private fun depart() {
        if (!departRequested) return
        departRequested = false
        val allowDemoDrive = demoDriveRequested
        demoDriveRequested = false
        feed(DriveInput.Depart)
        val trip = state.trip ?: return
        if (!allowDemoDrive || tripMode != TourMode.MOCK || !trip.enRoute || demo != null || !DemoDrive.available(this)) return
        Log.i(TAG, "demo drive to chapter ${trip.chapterIndex}")
        status = "시연 주행 중"; notifyStatus()
        demo = DemoDrive.drive(this, trip.chapterIndex,
            onFix = { lat, lon -> feed(DriveInput.Location(lat, lon)) },
            onDone = { demo = null; status = null; notifyStatus() },
        )
    }

    private fun refreshTripSoon() { scope.launch { refreshTrip() } }

    /** The saved trip decides whether a leg may start (EN_ROUTE = previous place finished). */
    private suspend fun refreshTrip() {
        val trip = runCatching { activeTrip(store.load().trips) }.onFailure { Log.w(TAG, "trip: ${it.message}") }.getOrNull()
        if (trip == null) {
            if (state.trip != null) feed(DriveInput.Trip(null))
            if (state.trip == null && state.leg == null) { Log.i(TAG, "no active trip, stopping"); stopSelf() }
            return
        }
        flowVersion = trip.flowVersion
        tripMode = trip.mode
        val snapshot = TripSnapshot(trip.sessionId, trip.chapterIndex, trip.phase == StoryPhase.EN_ROUTE, trip.phase == StoryPhase.ENDED)
        if (snapshot != state.trip) feed(DriveInput.Trip(snapshot))
    }

    private fun feed(input: DriveInput) {
        val before = state
        val step = DriveLegEngine.reduce(before, input, { DriveCourse.plan(it, script) }, System.currentTimeMillis())
        state = step.state
        if (state.firedLines != before.firedLines || state.arrivedLegs != before.arrivedLegs) {
            getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putStringSet(KEY_FIRED, state.firedLines).putStringSet(KEY_ARRIVED, state.arrivedLegs).apply()
        }
        if (step.actions.isNotEmpty()) Log.i(TAG, "$input -> ${step.actions.joinToString { it.javaClass.simpleName }}")
        step.actions.forEach { action ->
            when (action) {
                DriveAction.StartBgm -> audio?.startBgm()
                DriveAction.PauseBgm -> audio?.pauseBgm()
                DriveAction.StopBgm -> audio?.stopBgm()
                is DriveAction.Speak -> audio?.speak(action.id, action.text)
                is DriveAction.SetNavigation -> DriveCourse.plan(action.chapterIndex, script)?.let { signals?.route(it) }
                DriveAction.ShowNavigation -> signals?.showNavigation()
                DriveAction.StopVoice -> audio?.stopVoice()
                // Demo drive: park as soon as the place is reached, like a driver pulling over near it.
                is DriveAction.LegArrived -> { demo?.close(); demo = null; if (status == "시연 주행 중") status = null }
                DriveAction.OpenApp -> openApp()
            }
        }
        if (step.actions.isNotEmpty() || state.leg?.chapterIndex != before.leg?.chapterIndex) notifyStatus()
    }

    /**
     * Arrived and stopped: bring the app back. A service can't always start an activity from the background
     * (Android limits this), so a high-priority notification that opens the app is posted as well.
     */
    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        // Demo trip: the place was reached by the demo drive, so the app confirms the demo arrival itself.
        state.trip?.takeIf { tripMode == TourMode.MOCK }?.let { DemoArrival.pending.value = it.chapterIndex }
        runCatching { startActivity(intent) }.onFailure { Log.w(TAG, "open app: ${it.message}") }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(ARRIVAL_CHANNEL, "도착 알림", NotificationManager.IMPORTANCE_HIGH))
        val open = PendingIntent.getActivity(this, 1, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val place = state.trip?.let { DriveCourse.plan(it.chapterIndex, script)?.place } ?: "목적지"
        runCatching {
            manager.notify(ARRIVAL_ID, Notification.Builder(this, ARRIVAL_CHANNEL)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("$place 도착")
                .setContentText("눌러서 이스케이프 로드를 엽니다")
                .setContentIntent(open)
                .setFullScreenIntent(open, true)
                .setAutoCancel(true)
                .build())
        }
    }

    private fun statusText(): String {
        val leg = state.leg
        val place = leg?.let { DriveCourse.plan(it.chapterIndex, script)?.place }
        return when {
            state.awaitingStop -> "도착 · 정차하면 앱을 엽니다"
            leg != null -> "${place ?: "다음 장소"}(으)로 이동 중" + (state.remainingM?.let { " · ${"%.1f".format(it / 1000)}km" } ?: "")
            state.trip?.enRoute == true && state.armed -> "출발 준비 완료 · 기어 D로 출발하세요"
            state.trip?.enRoute == true -> "출발 대기 · 앱에서 출발하기를 누르거나 기어 D로 출발하세요"
            state.trip != null -> "장소 조사 중 · 다음 장소가 열리면 출발 시 안내합니다"
            else -> "진행 중인 여행 없음"
        }
    }

    private fun goForeground() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "주행 중 안내", NotificationManager.IMPORTANCE_LOW))
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            } else {
                startForeground(NOTIFICATION_ID, notification())
            }
        }.onFailure { Log.w(TAG, "foreground: ${it.message}") }
    }

    private fun notification(): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("ESCAPE ROAD · ${if (started) statusText() else "주행 중 안내"}")
            .setContentText(lastLine ?: status ?: "주행 중 BGM과 이동 장면 음성을 재생합니다.")
            .setOngoing(true)
            .setContentIntent(open)
            .build()
    }

    private fun notifyStatus() = runCatching { getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification()) }

    /** `adb shell dumpsys activity service com.team.carescape/.drive.DriveService` (used by tools/drive_sim.py). */
    override fun dump(fd: FileDescriptor?, writer: PrintWriter, args: Array<out String>?) {
        val t = state.trip
        writer.println("trip=${if (t == null) "NONE" else if (t.enRoute) "EN_ROUTE" else "AT_PLACE"} chapter=${t?.chapterIndex} leg=${state.leg?.chapterIndex} armed=${state.armed} awaitingStop=${state.awaitingStop}")
        writer.println("gear=${state.gear} remaining=${state.remainingM?.toInt()} nav=${state.fromNavigation} straight=${state.straightM?.toInt()}")
        writer.println("status=${status} lastLine=${lastLine}")
    }

    override fun onDestroy() {
        demo?.close(); demo = null
        scope.cancel()
        main.removeCallbacksAndMessages(null)
        signals?.close(); signals = null
        audio?.close(); audio = null
        super.onDestroy()
    }

    companion object {
        private const val TAG = "DriveService"
        private const val CHANNEL = "drive_chapter"
        private const val NOTIFICATION_ID = 1795
        private const val TRIP_POLL_MS = 4_000L
        private const val PREFS = "drive_chapter"
        private const val KEY_FIRED = "fired_lines"
        private const val KEY_ARRIVED = "arrived_legs"
        private const val ACTION_STOP = "com.team.carescape.drive.STOP"
        private const val ACTION_DEPART = "com.team.carescape.drive.DEPART"
        private const val EXTRA_ALLOW_DEMO_DRIVE = "allow_demo_drive"
        private const val ARRIVAL_CHANNEL = "drive_arrival"
        private const val ARRIVAL_ID = 1796

        /** 출발하기 button: sets the navigation destination and arms the leg (starts the service if needed). */
        fun depart(context: Context, allowDemoDrive: Boolean = false) {
            val app = context.applicationContext
            runCatching {
                app.startForegroundService(Intent(app, DriveService::class.java).setAction(ACTION_DEPART)
                    .putExtra(EXTRA_ALLOW_DEMO_DRIVE, allowDemoDrive))
            }
                .onFailure { Log.w(TAG, "depart: ${it.message}") }
        }

        /** The trip the player is on: LIVE first, the demo (MOCK) trip otherwise. */
        internal fun activeTrip(trips: List<StoredTrip>): StoredTrip? =
            trips.filter { it.phase != StoryPhase.ENDED }.let { open -> open.firstOrNull { it.mode == TourMode.LIVE } ?: open.firstOrNull() }

        /** Starts the service when a trip is in progress. Safe to call from Activity.onStart. */
        fun ensureRunning(context: Context) {
            val app = context.applicationContext
            CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).launch {
                val active = runCatching { activeTrip(TripProgressStore(app).load().trips) }.getOrNull()
                if (active != null) runCatching { app.startForegroundService(Intent(app, DriveService::class.java)) }
                    .onFailure { Log.w(TAG, "start: ${it.message}") }
            }
        }

        /**
         * While the activity is on screen, checks the saved trip every few seconds and starts the service
         * once a trip exists (a trip is usually created after onStart, when the player takes the case).
         */
        fun watch(activity: ComponentActivity) {
            activity.lifecycleScope.launch {
                activity.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    while (true) { ensureRunning(activity); delay(WATCH_MS) }
                }
            }
        }

        private const val WATCH_MS = 5_000L

        fun stop(context: Context) = context.startService(Intent(context, DriveService::class.java).setAction(ACTION_STOP))
    }
}
