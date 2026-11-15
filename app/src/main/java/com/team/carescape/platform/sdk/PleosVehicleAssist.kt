package com.team.carescape.platform.sdk

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.Brake
import ai.pleos.playground.vehicle.api.EvBattery
import ai.pleos.playground.vehicle.api.Fuel
import ai.pleos.playground.vehicle.api.Odometer
import ai.pleos.playground.vehicle.api.model.capability.CapabilityConfig
import ai.pleos.playground.vehicle.api.model.capability.AccessType

internal data class VehicleAssistReading(
    val status: SdkFeatureState = SdkFeatureState(),
    val brakePedal: Boolean? = null,
    val batteryPercent: Int? = null,
    val receivedElapsedMillis: Long? = null,
    /** 주행 가능 거리(km). Odometer.getRangeRemainingDistance, 여행 도우미 표시 전용. */
    val rangeKm: Int? = null,
    /** 연료 잔량(%). 내연기관 차량용. */
    val fuelPercent: Int? = null,
)

/** 여행 도우미용 선택 신호 하나. 읽기 지원을 먼저 확인하고 3초마다 다시 읽으며, 9초 넘은 값은 버린다. */
private class OptionalSignal(val tag: String) {
    var value: Int? = null
    var at = 0L
    var pending = false
    var requestedAt = 0L
    var request = 0L
    var readable = false
    var unsupported = false
    fun fresh(now: Long): Int? = value.takeIf { at > 0 && now - at in 0..9_000 }
}

/** Optional, read-only puzzle assistance. These signals never substitute for P/speed/CarUx. */
internal class PleosVehicleAssist(context: Context, private val emit: (VehicleAssistReading) -> Unit) : AutoCloseable {
    private val context = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private var sdk: Vehicle? = null
    private var brake: Brake? = null
    private var battery: EvBattery? = null
    private var odometer: Odometer? = null
    private var fuel: Fuel? = null
    private val range = OptionalSignal("RANGE")
    private val fuelLevel = OptionalSignal("FUEL")
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
            odometer = runCatching { sdk?.getOdometer() }.getOrNull()
            fuel = runCatching { sdk?.getFuel() }.getOrNull()
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
            listOf(range, fuelLevel).forEach { if (it.pending && now - it.requestedAt >= 8_000) { it.pending = false; it.request++; it.value = null } }
            readBrake(); readBattery(); readRange(); readFuel(); publish()
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

    private fun readRange() {
        val domain = odometer ?: return
        // 공개 API에 주행 가능 거리 capability 확인이 없어 바로 읽고, 실패하면 값 없음으로 둔다.
        readOptional(range, null) { done, fail ->
            domain.getRangeRemainingDistance(onComplete = { v -> done(v?.takeIf { it.isFinite() && it >= 0f }?.let { Math.round(it * RANGE_TO_KM) }) }, onFailed = fail)
        }
    }

    private fun readFuel() {
        val domain = fuel ?: return
        readOptional(fuelLevel, { ok, fail -> domain.checkFuelPercentCapabilities(onComplete = ok, onFailed = fail) }) { done, fail ->
            domain.getFuelPercent(onComplete = { v -> done(v?.takeIf { it in 0..100 }) }, onFailed = fail)
        }
    }

    /** 실패·미지원은 값 없음으로 남긴다(0이나 만충으로 추정하지 않는다). 퍼즐·안전 판정에는 쓰지 않는다. */
    private fun readOptional(
        sig: OptionalSignal,
        check: (((CapabilityConfig) -> Unit, (Exception) -> Unit) -> Unit)?,
        get: ((Int?) -> Unit, (Exception) -> Unit) -> Unit,
    ) {
        if (sig.pending || sig.unsupported) return
        sig.pending = true; sig.requestedAt = SystemClock.elapsedRealtime()
        val request = ++sig.request
        fun active(action: () -> Unit) = onMain { if (!closed && request == sig.request) action() }
        val failed: (Exception) -> Unit = { active { sig.pending = false; sig.value = null; publish() } }
        val read = {
            try {
                get({ v -> active {
                    sig.pending = false; sig.value = v
                    sig.at = SystemClock.elapsedRealtime().takeIf { v != null } ?: 0
                    publish()
                } }, failed)
            } catch (error: Exception) { failed(error) }
        }
        try {
            if (check == null) sig.readable = true
            if (sig.readable) read() else check!!({ config -> active {
                if (config.accessType == AccessType.READ || config.accessType == AccessType.READ_WRITE) { sig.readable = true; read() }
                else { sig.pending = false; sig.unsupported = true; sig.value = null; publish() }
            } }, failed)
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
            rangeKm = range.fresh(now), fuelPercent = fuelLevel.fresh(now),
        ))
    }
    private fun onMain(action: () -> Unit) { if (Looper.myLooper() == Looper.getMainLooper()) action() else main.post(action) }
    override fun close() {
        if (closed) return
        closed = true; brakeRequest++; batteryRequest++
        main.removeCallbacks(tick)
        runCatching { sdk?.release() }
        range.request++; fuelLevel.request++
        sdk = null; brake = null; battery = null; odometer = null; fuel = null
        brakeValue = null; batteryValue = null
    }
}

/** getRangeRemainingDistance는 미터 단위다(2026-11-15 Pleos Connect v2.1.0 에뮬레이터: 500000.0 = 500km). km로 바꾼다. */
private const val RANGE_TO_KM = 0.001f
