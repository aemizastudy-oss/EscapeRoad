package com.team.carescape.platform.sdk

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import android.os.Looper
import java.lang.reflect.InvocationTargetException
import com.team.carescape.model.TourLocation
import com.team.carescape.model.TourMode
import com.team.carescape.model.TourSafety
import com.team.carescape.model.WaypointConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Application-owned live runtime. Every public operation is made on the main thread. */
class PleosSdkRuntime(context: Context) : AutoCloseable {
    private val context = context.applicationContext
    private val mutableLocations = MutableStateFlow<TourLocation?>(null)
    private val mutableSafety = MutableStateFlow(TourSafety(false, false, false, false))
    private val mutableStatus = MutableStateFlow(PleosSdkStatus())
    val locations = mutableLocations.asStateFlow()
    val safety = mutableSafety.asStateFlow()
    val status = mutableStatus.asStateFlow()
    var revision: Long = 0
        private set
    private var requested = false
    private var foreground = false
    private var active = false
    private var closed = false
    private var generation = 0L
    private var safetyRevision = 0L
    private var car: CarSafetyMonitor? = null
    private var vehicle: PleosVehicleAssist? = null
    private var locationNavigation: PleosLocationNavigation? = null
    private var speech: PleosSpeechAdapter? = null

    fun start() {
        checkMain()
        if (closed) return
        requested = true
        synchronizeLifecycle()
    }
    fun setForeground(value: Boolean) {
        checkMain()
        if (closed) return
        if (foreground != value) revision++
        foreground = value
        synchronizeLifecycle()
    }
    fun stop() {
        checkMain()
        requested = false
        stopServices()
    }
    private fun synchronizeLifecycle() {
        if (requested && foreground && !active) startServices()
        else if ((!requested || !foreground) && active) stopServices()
    }
    private fun startServices() {
        active = true
        val token = ++generation
        revision++; safetyRevision++
        mutableSafety.value = TourSafety(foreground, false, false, false)
        speech = PleosSpeechAdapter(context) { reading ->
            if (active && token == generation) mutableStatus.value = mutableStatus.value.copy(
                tts = reading.tts, stt = reading.stt, speaking = reading.speaking, listening = reading.listening,
            )
        }
        val onCarReading: (CarSafetyReading) -> Unit = { reading ->
            if (active && token == generation) {
                val next = TourSafety(foreground, reading.parked, reading.stationary, reading.uxAllowed)
                if (next != mutableSafety.value || reading.status != mutableStatus.value.carSafety) { revision++; safetyRevision++ }
                mutableSafety.value = next
                mutableStatus.value = mutableStatus.value.copy(carSafety = reading.status)
                speech?.setAllowed(isSafe())
            }
        }
        startOptionalCarSafety(onCarReading)
        vehicle = PleosVehicleAssist(context) { reading ->
            if (active && token == generation) mutableStatus.value = mutableStatus.value.copy(
                vehicle = reading.status, brakePedal = reading.brakePedal, batteryPercent = reading.batteryPercent,
                vehicleReceivedElapsedMillis = reading.receivedElapsedMillis,
            )
        }.also { it.start() }
        locationNavigation = PleosLocationNavigation(context, onLocation = { location ->
            if (active && token == generation) {
                if (location != mutableLocations.value) revision++
                mutableLocations.value = location
            }
        }, onFused = { state, confidence ->
            if (active && token == generation) mutableStatus.value = mutableStatus.value.copy(fused = state, confidenceRaw = confidence)
        }, onNavigation = { state, route ->
            if (active && token == generation) mutableStatus.value = mutableStatus.value.copy(navigation = state, routeState = route)
        }).also { it.start() }
    }
    private fun startOptionalCarSafety(onReading: (CarSafetyReading) -> Unit) {
        try {
            // Some Pleos images omit the public AAOS shared library. A P/speed substitute is not valid.
            val loader = context.classLoader
            listOf("android.car.Car", "android.car.VehicleGear", "android.car.VehiclePropertyIds",
                "android.car.hardware.CarPropertyValue", "android.car.hardware.property.CarPropertyManager",
                "android.car.drivingstate.CarUxRestrictions", "android.car.drivingstate.CarUxRestrictionsManager")
                .forEach { Class.forName(it, false, loader) }
            // Load the implementation only after the optional API check. The runtime's verifier never
            // has to eagerly resolve a concrete class whose methods reference missing android.car types.
            val implementation = Class.forName("com.team.carescape.platform.sdk.PleosCarSafety", true, loader)
            val constructor = implementation.getConstructor(Context::class.java, Function1::class.java)
            val monitor = constructor.newInstance(context, onReading) as CarSafetyMonitor
            car = monitor
            monitor.start()
        } catch (error: ClassNotFoundException) {
            unavailableCar(error)
        } catch (error: LinkageError) {
            unavailableCar(error)
        } catch (error: ReflectiveOperationException) {
            unavailableCar((error as? InvocationTargetException)?.targetException ?: error)
        } catch (error: Exception) {
            unavailableCar(error)
        }
    }
    private fun unavailableCar(error: Throwable) {
        runCatching { car?.close() }; car = null
        revision++; safetyRevision++
        mutableSafety.value = TourSafety(foreground, false, false, false)
        mutableStatus.value = mutableStatus.value.copy(carSafety = SdkFeatureState(
            if (error is ClassNotFoundException || error is LinkageError) SdkAvailability.UNSUPPORTED else SdkAvailability.ERROR,
            if (error is ClassNotFoundException || error is LinkageError) "ANDROID_CAR_CLASSES_MISSING" else error.javaClass.simpleName,
        ))
        speech?.setAllowed(false)
    }
    private fun stopServices() {
        revision++; safetyRevision++; generation++; active = false
        mutableSafety.value = TourSafety(foreground && requested, false, false, false)
        mutableLocations.value = TourLocation(TourMode.LIVE, error = if (foreground) "SDK_STOPPED" else "BACKGROUND")
        speech?.close(); speech = null
        locationNavigation?.close(); locationNavigation = null
        vehicle?.close(); vehicle = null
        car?.close(); car = null
        mutableStatus.value = PleosSdkStatus()
    }
    fun navigate(waypoint: WaypointConfig): Boolean {
        checkMain()
        if (!isSafe()) return false
        return locationNavigation?.navigate(waypoint) == true
    }
    fun speak(text: String): Boolean {
        checkMain()
        return isSafe() && speech?.speak(text) == true
    }
    fun listen(onResult: (String?) -> Unit): Boolean {
        checkMain()
        if (!isSafe()) return false
        val token = safetyRevision
        return speech?.listen { text -> onResult(text.takeIf { isSafe() && token == safetyRevision }) } == true
    }
    fun stopAudio() {
        checkMain()
        speech?.stopAudio()
    }
    private fun isSafe() = active && !closed && mutableSafety.value.let { it.foreground && it.parked && it.stationary && it.carUxAllowed }
    private fun checkMain() = check(Looper.myLooper() == Looper.getMainLooper()) { "PleosSdkRuntime operations require the main thread" }
    override fun close() {
        checkMain()
        if (closed) return
        stop(); closed = true
    }

    companion object {
        val declaredSdkPermissions = listOf(
            "pleos.car.permission.FUSED_LOCATION", "pleos.car.permission.NAVI_ROUTE", "pleos.car.permission.NAVI_ROUTE_SEARCH",
            "pleos.car.permission.CAR_DRIVE", "pleos.car.permission.CAR_ENERGY", "pleos.car.permission.TTS_SERVICE",
            "pleos.car.permission.STT_SERVICE", "android.car.permission.CAR_SPEED", "android.car.permission.CAR_POWERTRAIN",
        )

        /** Permission protection differs by platform. Request only actually declared dangerous permissions. */
        fun missingRuntimePermissions(context: Context): List<String> = declaredSdkPermissions.filter { permission ->
            context.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED && runCatching {
                @Suppress("DEPRECATION")
                val info = context.packageManager.getPermissionInfo(permission, 0)
                info.protectionLevel and PermissionInfo.PROTECTION_MASK_BASE == PermissionInfo.PROTECTION_DANGEROUS
            }.getOrDefault(false)
        }
    }
}
