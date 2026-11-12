package com.example.roadescape.platform.sdk

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.Brake
import ai.pleos.playground.vehicle.api.EvBattery
import ai.pleos.playground.vehicle.api.model.capability.AccessType

internal data class VehicleAssistReading(
    val status: SdkFeatureState = SdkFeatureState(),
    val brakePedal: Boolean? = null,
    val batteryPercent: Int? = null,
    val receivedElapsedMillis: Long? = null,
)

/** Optional, read-only puzzle assistance. These signals never substitute for P/speed/CarUx. */
internal class PleosVehicleAssist(context: Context, private val emit: (VehicleAssistReading) -> Unit) : AutoCloseable {
    private val context = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private var sdk: Vehicle? = null
    private var brake: Brake? = null
    private var battery: EvBattery? = null
    private var closed = false
    private var brakeValue: Boolean? = null
    private var batteryValue: Int? = null
    private var brakeAt = 0L
    private var batteryAt = 0L
    private var brakePending = false
    private var batteryPending = false
    private var brakeRequestedAt = 0L
    private var batteryRequestedAt = 0L
    private var brakeRequest = 0L
    private var batteryRequest = 0L
    private var brakeReadable = false
    private var batteryReadable = false
    private var brakeUnsupported = false
    private var batteryUnsupported = false
    private var lastFailure = SdkFeatureState(SdkAvailability.CHECKING)

    fun start() {
        if (closed || sdk != null) return
        publish()
        try {
            sdk = Vehicle(context).also { it.initialize() }
            brake = sdk?.getBrake()
            battery = sdk?.getEvBattery()
            main.post(tick)
        } catch (error: Exception) { lastFailure = failureState(error); publish() }
    }

    private val tick = object : Runnable {
        override fun run() {
            if (closed) return
            val now = SystemClock.elapsedRealtime()
            if (brakePending && now - brakeRequestedAt >= 8_000) {
                brakePending = false; brakeRequest++; brakeValue = null
                lastFailure = SdkFeatureState(SdkAvailability.NO_DATA, "BRAKE_READ_TIMEOUT")
            }
            if (batteryPending && now - batteryRequestedAt >= 8_000) {
                batteryPending = false; batteryRequest++; batteryValue = null
                lastFailure = SdkFeatureState(SdkAvailability.NO_DATA, "BATTERY_READ_TIMEOUT")
            }
            readBrake(); readBattery(); publish()
            main.postDelayed(this, 3_000)
        }
    }

    private fun readBrake() {
        val domain = brake ?: return
        if (brakePending || brakeUnsupported) return
        brakePending = true; brakeRequestedAt = SystemClock.elapsedRealtime()
        val request = ++brakeRequest
        fun active(action: () -> Unit) = onMain { if (!closed && request == brakeRequest) action() }
        val failed: (Exception) -> Unit = { error -> active {
            brakePending = false; brakeValue = null; lastFailure = failureState(error); publish()
        } }
        val read = {
            try {
                domain.getCurrentBrakePedalStatus(onComplete = { value -> active {
                    brakePending = false; brakeValue = value
                    brakeAt = SystemClock.elapsedRealtime().takeIf { value != null } ?: 0
                    if (value == null) lastFailure = SdkFeatureState(SdkAvailability.NO_DATA, "BRAKE_NULL")
                    publish()
                } }, onFailed = failed)
            } catch (error: Exception) { failed(error) }
        }
        try {
            if (brakeReadable) read() else domain.checkBrakePedalStatusCapabilities(onComplete = { config -> active {
                if (config.accessType == AccessType.READ || config.accessType == AccessType.READ_WRITE) {
                    brakeReadable = true; read()
                } else {
                    brakePending = false; brakeUnsupported = true; brakeValue = null
                    lastFailure = SdkFeatureState(SdkAvailability.UNSUPPORTED, "BRAKE_NOT_READABLE"); publish()
                }
            } }, onFailed = failed)
        } catch (error: Exception) { failed(error) }
    }

    private fun readBattery() {
        val domain = battery ?: return
        if (batteryPending || batteryUnsupported) return
        batteryPending = true; batteryRequestedAt = SystemClock.elapsedRealtime()
        val request = ++batteryRequest
        fun active(action: () -> Unit) = onMain { if (!closed && request == batteryRequest) action() }
        val failed: (Exception) -> Unit = { error -> active {
            batteryPending = false; batteryValue = null; lastFailure = failureState(error); publish()
        } }
        val read = {
            try {
                domain.getEvBatteryStateOfCharge(onComplete = { value -> active {
                    batteryPending = false; batteryValue = value?.takeIf { it in 0..100 }
                    batteryAt = SystemClock.elapsedRealtime().takeIf { batteryValue != null } ?: 0
                    if (batteryValue == null) lastFailure = SdkFeatureState(SdkAvailability.NO_DATA, "BATTERY_NULL_OR_RANGE")
                    publish()
                } }, onFailed = failed)
            } catch (error: Exception) { failed(error) }
        }
        try {
            if (batteryReadable) read() else domain.checkEvBatteryStateOfChargeCapabilities(onComplete = { config -> active {
                if (config.accessType == AccessType.READ || config.accessType == AccessType.READ_WRITE) {
                    batteryReadable = true; read()
                } else {
                    batteryPending = false; batteryUnsupported = true; batteryValue = null
                    lastFailure = SdkFeatureState(SdkAvailability.UNSUPPORTED, "BATTERY_NOT_READABLE"); publish()
                }
            } }, onFailed = failed)
        } catch (error: Exception) { failed(error) }
    }

    private fun publish() {
        if (closed) return
        val now = SystemClock.elapsedRealtime()
        val freshBrake = brakeValue.takeIf { brakeAt > 0 && now - brakeAt in 0..9_000 }
        val freshBattery = batteryValue.takeIf { batteryAt > 0 && now - batteryAt in 0..9_000 }
        val usable = freshBrake != null || freshBattery != null
        emit(VehicleAssistReading(
            if (usable) SdkFeatureState(SdkAvailability.READY, "OPTIONAL_READ_ONLY_SIGNALS")
            else if ((brakeAt > 0 && now - brakeAt > 9_000) || (batteryAt > 0 && now - batteryAt > 9_000)) SdkFeatureState(SdkAvailability.STALE, "VEHICLE_ASSIST_STALE")
            else lastFailure,
            freshBrake, freshBattery, maxOf(brakeAt, batteryAt).takeIf { usable },
        ))
    }
    private fun onMain(action: () -> Unit) { if (Looper.myLooper() == Looper.getMainLooper()) action() else main.post(action) }
    override fun close() {
        if (closed) return
        closed = true; brakeRequest++; batteryRequest++
        main.removeCallbacks(tick)
        runCatching { sdk?.release() }
        sdk = null; brake = null; battery = null
        brakeValue = null; batteryValue = null
    }
}
