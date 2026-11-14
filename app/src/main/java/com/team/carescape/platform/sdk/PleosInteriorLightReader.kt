package com.team.carescape.platform.sdk

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.Light
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.api.model.capability.AccessType
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.control.LightState
import ai.pleos.playground.vehicle.listener.LightStateListener
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.team.carescape.game.InteriorLightSnapshot

/** Read-only actual reading-light states. The foreground runtime owns start/close. */
internal class PleosInteriorLightReader(
    context: Context,
    private val emit: (InteriorLightSnapshot) -> Unit,
) : AutoCloseable {
    private val context = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val inputs = listOf(
        Input("front_left", SeatValue.ROW_1_LEFT),
        Input("front_right", SeatValue.ROW_1_RIGHT),
        Input("rear_left", SeatValue.ROW_2_LEFT),
        Input("rear_right", SeatValue.ROW_2_RIGHT),
    )
    private var sdk: Vehicle? = null
    private var light: Light? = null
    private var started = false
    private var closed = false
    private var generation = 0L
    private var capabilitiesReady = false
    private var blocked = false
    private var capabilityPending = false
    private var capabilityRequest = 0L
    private var capabilityRequestedAt = 0L
    private var nextCapabilityAt = 0L
    private var nextPollAt = 0L
    private var statusMessage = CHECKING

    private class Input(val key: String, seat: SeatValue) {
        val area = SeatArea(seat)
        var value: Boolean? = null
        var receivedAt = 0L
        var baseline = false
        var pending = false
        var request = 0L
        var requestedAt = 0L
        var listenerEpoch = 0L
        var listenerRevision = 0L
        var listener: LightStateListener? = null
        var message = CHECKING
    }

    fun start() {
        checkMain()
        if (closed || started) return
        started = true
        generation++
        publish()
        try {
            sdk = Vehicle(context).also { it.initialize() }
            light = sdk?.getLight()
            main.post(tick)
        } catch (error: Exception) {
            blocked = true
            statusMessage = failureMessage(error)
            invalidateAll(statusMessage)
            releaseSdk()
        } catch (_: LinkageError) {
            blocked = true
            statusMessage = "이 기기는 실내등 상태 읽기를 지원하지 않습니다."
            invalidateAll(statusMessage)
            releaseSdk()
        }
    }

    private val tick = object : Runnable {
        override fun run() {
            if (closed || !started) return
            val now = SystemClock.elapsedRealtime()
            if (capabilityPending && now - capabilityRequestedAt >= REQUEST_TIMEOUT_MS) {
                capabilityPending = false
                capabilityRequest++
                statusMessage = "실내등 지원 정보를 받지 못했습니다. 다시 확인하고 있습니다."
                nextCapabilityAt = now + POLL_MS
                invalidateAll(statusMessage)
            }
            if (!capabilitiesReady && !blocked && !capabilityPending && now >= nextCapabilityAt) {
                requestCapabilities()
            }
            for (input in inputs) {
                if (input.pending && now - input.requestedAt >= REQUEST_TIMEOUT_MS) {
                    input.pending = false
                    input.request++ // A timed-out get cannot revive the previous sample.
                    invalidate(input, "실내등 상태를 받지 못했습니다. 갱신을 기다리고 있습니다.")
                }
            }
            if (capabilitiesReady && now >= nextPollAt) {
                nextPollAt = now + POLL_MS
                inputs.filterNot { it.pending }.forEach(::requestCurrent)
            }
            publish()
            main.postDelayed(this, TICK_MS)
        }
    }

    private fun requestCapabilities() {
        val domain = light ?: return
        val token = generation
        val request = ++capabilityRequest
        capabilityPending = true
        capabilityRequestedAt = SystemClock.elapsedRealtime()
        nextCapabilityAt = capabilityRequestedAt + POLL_MS
        val failed: (Exception) -> Unit = { error -> inCapability(token, request) {
            capabilityPending = false
            statusMessage = failureMessage(error)
            nextCapabilityAt = SystemClock.elapsedRealtime() + POLL_MS
            invalidateAll(statusMessage)
        } }
        try {
            domain.checkReadingLightStateCapabilities(onComplete = { config, areas -> inCapability(token, request) {
                capabilityPending = false
                // Require four distinct single-seat areas, not one combined/global cabin-light state.
                val allReadable = config.accessType in READABLE && inputs.all { input ->
                    areas[input.area]?.let { LightState.OFF in it && LightState.ON in it } == true
                }
                if (!allReadable) {
                    blocked = true
                    statusMessage = "이 차량은 좌석별 실내등 4개의 켜짐 상태 읽기를 지원하지 않습니다."
                    invalidateAll(statusMessage)
                    return@inCapability
                }
                try {
                    inputs.forEach { register(it, token) }
                } catch (error: Exception) {
                    blocked = true
                    statusMessage = failureMessage(error)
                    invalidateAll(statusMessage)
                    releaseSdk()
                    return@inCapability
                }
                capabilitiesReady = true
                nextPollAt = SystemClock.elapsedRealtime() + POLL_MS
                inputs.forEach(::requestCurrent)
                publish()
            } }, onFailed = failed)
        } catch (error: Exception) { failed(error) }
    }

    private fun register(input: Input, token: Long) {
        val epoch = ++input.listenerEpoch
        val listener = object : LightStateListener {
            override fun onLightStateUpdated(state: LightState?) = inListener(input, token, epoch) {
                input.listenerRevision++
                // First connection/recovery must verify the current value before trusting callbacks.
                if (input.baseline) accept(input, state, currentRead = false)
            }

            override fun onFailed(e: Exception) = inListener(input, token, epoch) {
                input.listenerRevision++
                input.pending = false
                input.request++
                invalidate(input, failureMessage(e))
                publish()
            }
        }
        input.listener = listener
        light?.registerReadingLightState(input.area, listener)
    }

    private fun requestCurrent(input: Input) {
        val domain = light ?: return
        val token = generation
        val request = ++input.request
        val listenerRevision = input.listenerRevision
        input.pending = true
        input.requestedAt = SystemClock.elapsedRealtime()
        val failed: (Exception) -> Unit = { error -> inRequest(input, token, request) {
            input.pending = false
            invalidate(input, failureMessage(error))
            publish()
        } }
        try {
            domain.getCurrentReadingLightState(input.area, onComplete = { state -> inRequest(input, token, request) {
                input.pending = false
                // A get started before a newer listener update must not roll this seat back.
                if (listenerRevision == input.listenerRevision) accept(input, state, currentRead = true)
            } }, onFailed = failed)
        } catch (error: Exception) { failed(error) }
    }

    private fun accept(input: Input, state: LightState?, currentRead: Boolean) {
        val active = when (state) {
            LightState.ON -> true
            LightState.OFF -> false
            else -> null // Switch modes/AUTO and other unknown states are never OFF or an ON count.
        }
        if (active == null) {
            invalidate(input, "실내등의 켜짐 상태가 확인되지 않아 갱신을 기다리고 있습니다.")
        } else {
            if (currentRead) input.baseline = true
            if (!input.baseline) return
            input.value = active
            input.receivedAt = SystemClock.elapsedRealtime()
            input.message = "실내등 상태를 갱신하고 있습니다."
        }
        publish()
    }

    private fun invalidate(input: Input, message: String) {
        input.value = null
        input.receivedAt = 0L
        input.baseline = false
        input.message = message
    }

    private fun invalidateAll(message: String) {
        inputs.forEach { input ->
            input.pending = false
            input.request++
            invalidate(input, message)
        }
        publish()
    }

    private fun publish() {
        if (closed) return
        val now = SystemClock.elapsedRealtime()
        for (input in inputs) {
            if (input.value != null && now - input.receivedAt !in 0..FRESH_MS) {
                invalidate(input, "실내등 상태가 오래되어 갱신을 기다리고 있습니다.")
            }
        }
        val states = inputs.mapNotNull { input -> input.value?.let { input.key to it } }.toMap()
        val ready = capabilitiesReady && states.size == inputs.size
        emit(InteriorLightSnapshot(
            states = states,
            // Use the oldest seat timestamp: all four observations must still be fresh together.
            receivedElapsedMillis = if (ready) inputs.minOf { it.receivedAt } else 0L,
            ready = ready,
            message = when {
                ready -> "실내등 4개의 켜짐 상태를 확인했습니다."
                !capabilitiesReady -> statusMessage
                else -> inputs.firstOrNull { it.value == null }?.message ?: CHECKING
            },
        ))
    }

    private fun inCapability(token: Long, request: Long, action: () -> Unit) = onMain {
        if (!closed && token == generation && capabilityPending && request == capabilityRequest) action()
    }

    private fun inRequest(input: Input, token: Long, request: Long, action: () -> Unit) = onMain {
        if (!closed && token == generation && input.pending && request == input.request) action()
    }

    private fun inListener(input: Input, token: Long, epoch: Long, action: () -> Unit) = onMain {
        if (!closed && token == generation && epoch == input.listenerEpoch) action()
    }

    private fun onMain(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) action() else main.post(action)
    }

    private fun checkMain() = check(Looper.myLooper() == Looper.getMainLooper())

    private fun failureMessage(error: Exception): String = when {
        error is SecurityException || error.javaClass.simpleName.contains("Permission", ignoreCase = true) ->
            "실내등 읽기 권한이 허용되지 않았습니다. 권한을 확인해 주세요."
        error is UnsupportedOperationException -> "이 차량은 실내등 상태 읽기를 지원하지 않습니다."
        else -> "실내등 상태를 받지 못했습니다. 연결과 갱신을 기다리고 있습니다."
    }

    private fun releaseSdk() {
        inputs.forEach { input ->
            input.listenerEpoch++
            runCatching { input.listener?.let { light?.unregisterReadingLightState(input.area, it) } }
            input.listener = null
        }
        // Own the whole Vehicle lifetime as well; unregister alone is not the lifetime boundary.
        runCatching { sdk?.release() }
        light = null
        sdk = null
    }

    override fun close() {
        checkMain()
        if (closed) return
        closed = true
        generation++
        capabilityRequest++
        capabilityPending = false
        capabilitiesReady = false
        main.removeCallbacks(tick)
        inputs.forEach { input ->
            input.pending = false
            input.request++
            invalidate(input, CHECKING)
        }
        releaseSdk()
    }

    private companion object {
        const val CHECKING = "실내등 상태를 확인하고 있습니다."
        const val TICK_MS = 1_000L
        const val POLL_MS = 3_000L
        const val REQUEST_TIMEOUT_MS = 8_000L
        const val FRESH_MS = 9_000L
        val READABLE = setOf(AccessType.READ, AccessType.READ_WRITE)
    }
}
