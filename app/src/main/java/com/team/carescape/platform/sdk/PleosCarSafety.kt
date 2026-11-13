package com.team.carescape.platform.sdk

import android.car.Car
import android.car.VehicleGear
import android.car.VehiclePropertyIds
import android.car.drivingstate.CarUxRestrictions
import android.car.drivingstate.CarUxRestrictionsManager
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs

/** AAOS P, actual speed, and official CarUx are independent requirements. No DO declaration. */
internal class PleosCarSafety(context: Context, private val emit: (CarSafetyReading) -> Unit) : CarSafetyMonitor {
    private val context = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val polling = AtomicBoolean(false)
    private var car: Car? = null
    private var properties: CarPropertyManager? = null
    private var restrictions: CarUxRestrictionsManager? = null
    private var propertyListener: CarPropertyManager.CarPropertyEventCallback? = null
    private var uxListener: CarUxRestrictionsManager.OnUxRestrictionsChangedListener? = null
    private var closed = false
    private var generation = 0L
    private var parked: Boolean? = null
    private var speed: Float? = null
    private var uxAllowed: Boolean? = null
    private var gearAt = 0L
    private var speedAt = 0L
    private var uxAt = 0L
    private var lastSpeedSourceNanos = 0L
    private var initializedAt = 0L
    private var failure = SdkFeatureState(SdkAvailability.CHECKING)

    override fun start() {
        if (closed || car != null) return
        initializedAt = SystemClock.elapsedRealtime()
        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) {
            failure = SdkFeatureState(SdkAvailability.UNSUPPORTED, "NOT_AUTOMOTIVE")
            publish()
            return
        }
        failure = SdkFeatureState(SdkAvailability.CHECKING)
        publish()
        try {
            car = Car.createCar(context, main, Car.CAR_WAIT_TIMEOUT_DO_NOT_WAIT) { connected, ready ->
                onMain {
                    if (!closed) {
                        generation++
                        detach()
                        parked = null; speed = null; uxAllowed = null
                        gearAt = 0; speedAt = 0; uxAt = 0; lastSpeedSourceNanos = 0
                        failure = SdkFeatureState(if (ready) SdkAvailability.CHECKING else SdkAvailability.NO_DATA, "CAR_SERVICE")
                        publish()
                        if (ready) connect(connected, generation)
                    }
                }
            }
        } catch (error: Exception) {
            failure = failureState(error); publish()
        } catch (error: LinkageError) {
            failure = SdkFeatureState(SdkAvailability.UNSUPPORTED, error.javaClass.simpleName); publish()
        }
        main.post(tick)
    }

    private fun connect(connected: Car, token: Long) {
        try {
            properties = connected.getCarManager(Car.PROPERTY_SERVICE) as? CarPropertyManager
            restrictions = connected.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as? CarUxRestrictionsManager
            val listener = object : CarPropertyManager.CarPropertyEventCallback {
                override fun onChangeEvent(value: CarPropertyValue<*>) = inConnection(token) { accept(value) }
                override fun onErrorEvent(propertyId: Int, areaId: Int) = inConnection(token) {
                    invalidate(propertyId, SdkFeatureState(SdkAvailability.ERROR, "CAR_PROPERTY_CALLBACK"))
                }
            }
            propertyListener = listener
            for (id in intArrayOf(VehiclePropertyIds.PERF_VEHICLE_SPEED, VehiclePropertyIds.GEAR_SELECTION)) {
                try {
                    if (properties?.registerCallback(listener, id, 5f) != true) {
                        invalidate(id, SdkFeatureState(SdkAvailability.UNSUPPORTED, "CAR_PROPERTY_UNSUPPORTED"))
                    }
                } catch (error: Exception) { invalidate(id, failureState(error)) }
            }
            uxListener = CarUxRestrictionsManager.OnUxRestrictionsChangedListener { value ->
                inConnection(token) { acceptUx(value) }
            }.also { restrictions?.registerListener(it) }
        } catch (error: Exception) {
            failure = failureState(error); publish()
        }
    }

    private val tick = object : Runnable {
        override fun run() {
            if (closed) return
            val manager = properties
            val token = generation
            if (manager != null && polling.compareAndSet(false, true)) {
                worker.execute {
                    try {
                        for (id in intArrayOf(VehiclePropertyIds.PERF_VEHICLE_SPEED, VehiclePropertyIds.GEAR_SELECTION)) {
                            try {
                                val value = manager.getProperty<Any>(id, 0)
                                inConnection(token) {
                                    if (value == null) invalidate(id, SdkFeatureState(SdkAvailability.NO_DATA, "CAR_PROPERTY_NULL"))
                                    else accept(value)
                                }
                            } catch (error: Exception) { inConnection(token) { invalidate(id, failureState(error)) } }
                        }
                    } finally { polling.set(false) }
                }
            }
            try {
                val value = restrictions?.currentCarUxRestrictions
                if (value != null) acceptUx(value)
            } catch (error: Exception) {
                uxAllowed = null; uxAt = 0; failure = failureState(error)
            }
            publish()
            main.postDelayed(this, 1_000)
        }
    }

    private fun accept(value: CarPropertyValue<*>) {
        if (value.status != CarPropertyValue.STATUS_AVAILABLE) {
            invalidate(value.propertyId, SdkFeatureState(SdkAvailability.NO_DATA, "CAR_PROPERTY_UNAVAILABLE")); return
        }
        val now = SystemClock.elapsedRealtime()
        when (value.propertyId) {
            VehiclePropertyIds.PERF_VEHICLE_SPEED -> {
                if (value.timestamp < lastSpeedSourceNanos) return
                val age = SystemClock.elapsedRealtimeNanos() - value.timestamp
                val sample = (value.value as? Number)?.toFloat()
                if (value.timestamp <= 0 || age !in -250_000_000L..6_000_000_000L || sample == null || !sample.isFinite()) {
                    invalidate(value.propertyId, SdkFeatureState(SdkAvailability.STALE, "SPEED_SOURCE_TIME")); return
                }
                lastSpeedSourceNanos = value.timestamp
                speed = sample
                // Re-reading a cached speed must not renew the original sample's lifetime.
                speedAt = value.timestamp / 1_000_000L
            }
            VehiclePropertyIds.GEAR_SELECTION -> {
                val sample = (value.value as? Number)?.toInt()
                if (sample == null || sample == VehicleGear.GEAR_UNKNOWN) {
                    invalidate(value.propertyId, SdkFeatureState(SdkAvailability.NO_DATA, "GEAR_UNKNOWN")); return
                }
                parked = sample == VehicleGear.GEAR_PARK
                // GEAR_SELECTION is on-change; a successful current-value read verifies unchanged gear.
                gearAt = now
            }
        }
        publish()
    }

    private fun acceptUx(value: CarUxRestrictions) {
        uxAllowed = !value.isRequiresDistractionOptimization && value.activeRestrictions == 0
        uxAt = SystemClock.elapsedRealtime()
        publish()
    }

    private fun invalidate(id: Int, state: SdkFeatureState) {
        if (id == VehiclePropertyIds.PERF_VEHICLE_SPEED) { speed = null; speedAt = 0 }
        if (id == VehiclePropertyIds.GEAR_SELECTION) { parked = null; gearAt = 0 }
        failure = state
        publish()
    }

    private fun publish() {
        if (closed) return
        val now = SystemClock.elapsedRealtime()
        fun fresh(at: Long) = at > 0 && now - at in -250L..6_000L
        val gearFresh = parked != null && fresh(gearAt)
        val speedFresh = speed != null && fresh(speedAt)
        val uxFresh = uxAllowed != null && fresh(uxAt)
        val allFresh = gearFresh && speedFresh && uxFresh
        val status = if (allFresh) SdkFeatureState(SdkAvailability.READY, "P_SPEED_CAR_UX_OBSERVED")
        else if ((gearAt > 0 && !gearFresh) || (speedAt > 0 && !speedFresh) || (uxAt > 0 && !uxFresh)) SdkFeatureState(SdkAvailability.STALE, "CAR_SAFETY_STALE")
        else if (failure.availability == SdkAvailability.CHECKING && now - initializedAt >= 12_000) SdkFeatureState(SdkAvailability.NO_DATA, "CAR_SAFETY_INITIALIZE_TIMEOUT")
        else failure
        emit(CarSafetyReading(gearFresh && parked == true, speedFresh && abs(speed!!) < .1f, uxFresh && uxAllowed == true, status))
    }

    private fun inConnection(token: Long, action: () -> Unit) = onMain { if (!closed && token == generation) action() }
    private fun onMain(action: () -> Unit) { if (Looper.myLooper() == Looper.getMainLooper()) action() else main.post(action) }
    private fun detach() {
        runCatching { propertyListener?.let { properties?.unregisterCallback(it) } }
        runCatching { restrictions?.unregisterListener() }
        propertyListener = null; uxListener = null; properties = null; restrictions = null
    }
    override fun close() {
        if (closed) return
        closed = true; generation++
        main.removeCallbacks(tick)
        detach()
        runCatching { car?.disconnect() }; car = null
        worker.shutdownNow()
    }
}
