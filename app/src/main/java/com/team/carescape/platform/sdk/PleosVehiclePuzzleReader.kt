package com.team.carescape.platform.sdk

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.TurnSignal
import ai.pleos.playground.vehicle.api.Window
import ai.pleos.playground.vehicle.api.Wiper
import ai.pleos.playground.vehicle.api.model.area.WindowArea
import ai.pleos.playground.vehicle.api.model.capability.AccessType
import ai.pleos.playground.vehicle.constant.area.TurnSignalValue
import ai.pleos.playground.vehicle.constant.area.WindowValue
import ai.pleos.playground.vehicle.constant.control.WiperState
import ai.pleos.playground.vehicle.listener.TurnSignalStateListener
import ai.pleos.playground.vehicle.listener.WindowPositionListener
import ai.pleos.playground.vehicle.listener.WiperStateListener
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.team.carescape.game.CarControl

/** Read-only input. The foreground runtime owns start/close; the caller owns puzzle submission. */
internal class PleosVehiclePuzzleReader(
    context: Context,
    private val emit: (CarControl, Boolean?, SdkFeatureState, Long) -> Unit,
) : AutoCloseable {
    private val context = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val windshield = WindowArea(WindowValue.FRONT_WINDSHIELD)
    private val frontLeft = WindowArea(WindowValue.ROW_1_LEFT)
    private val wiperInput = Input(CarControl.WIPER)
    private val windowInput = Input(CarControl.WINDOW)
    // One SDK state/request owns both directional samples; these are never separate reads.
    private val turnLeftInput = Input(CarControl.TURN_LEFT)
    private val turnRightInput = Input(CarControl.TURN_RIGHT)
    private val turnInputs = listOf(turnLeftInput, turnRightInput)
    private val inputs = listOf(wiperInput, windowInput) + turnInputs
    private val requestInputs = listOf(wiperInput, windowInput, turnLeftInput)
    private var sdk: Vehicle? = null
    private var wiper: Wiper? = null
    private var window: Window? = null
    private var turnSignal: TurnSignal? = null
    private var wiperListener: WiperStateListener? = null
    private var windowListener: WindowPositionListener? = null
    private var turnSignalListener: TurnSignalStateListener? = null
    private var windowMax = 0
    private var started = false
    private var closed = false
    private var generation = 0L

    private class Input(val control: CarControl) {
        var supported: Boolean? = null
        var registered = false
        var listenerEpoch = 0L
        var listenerRevision = 0L
        var baseline = false
        var value: Boolean? = null
        var receivedAt = 0L
        var pending = false
        var request = 0L
        var requestedAt = 0L
        var nextReadAt = 0L
        var state = SdkFeatureState(SdkAvailability.CHECKING)
    }

    fun start() {
        checkMain()
        if (closed || started) return
        started = true
        generation++
        inputs.forEach(::publish)
        emit(CarControl.HAZARD, null,
            SdkFeatureState(SdkAvailability.UNSUPPORTED, "VEHICLE_2_3_2_NO_PUBLIC_HAZARD_API"), SystemClock.elapsedRealtime())
        try {
            sdk = Vehicle(context)
            sdk?.initialize()
            wiper = sdk?.getWiper()
            window = sdk?.getWindow()
            turnSignal = sdk?.getTurnSignal()
            main.post(tick)
        } catch (error: Exception) {
            requestInputs.forEach { fail(it, failureState(error)) }
        } catch (error: LinkageError) {
            requestInputs.forEach {
                fail(it, SdkFeatureState(SdkAvailability.UNSUPPORTED, error.javaClass.simpleName))
            }
        }
    }

    private val tick = object : Runnable {
        override fun run() {
            if (closed || !started) return
            val now = SystemClock.elapsedRealtime()
            for (input in requestInputs) {
                if (input.pending && now - input.requestedAt >= REQUEST_TIMEOUT_MS) {
                    input.pending = false
                    input.request++ // A late capability/current-value callback cannot revive this request.
                    fail(input, SdkFeatureState(SdkAvailability.NO_DATA, "PUZZLE_READ_TIMEOUT"))
                }
                if (!input.pending && input.supported != false && now >= input.nextReadAt) {
                    input.nextReadAt = now + POLL_MS
                    when (input) {
                        wiperInput -> requestWiper()
                        windowInput -> requestWindow()
                        turnLeftInput -> requestTurnSignal()
                    }
                }
            }
            inputs.forEach(::publish)
            main.postDelayed(this, 1_000)
        }
    }

    private fun requestWiper() {
        val domain = wiper ?: return
        val input = wiperInput
        val token = generation
        val request = begin(input)
        val failed: (Exception) -> Unit = { error -> inRequest(input, token, request) {
            input.pending = false
            fail(input, failureState(error))
        } }
        try {
            if (input.supported == true && input.registered) readWiper(token, request)
            else domain.checkWiperStateCapabilities(onComplete = { config, areas -> inRequest(input, token, request) {
                val states = areas[windshield]
                if (config.accessType !in READABLE || states == null || WiperState.OFF !in states || WiperState.ON !in states) {
                    unsupported(input, "WIPER_OFF_ON_NOT_READABLE")
                } else {
                    input.supported = true
                    registerWiper(token)
                    if (input.registered) readWiper(token, request)
                }
            } }, onFailed = failed)
        } catch (error: Exception) { failed(error) }
    }

    private fun registerWiper(token: Long) {
        val input = wiperInput
        val epoch = ++input.listenerEpoch
        val listener = object : WiperStateListener {
            override fun onWiperStateUpdated(state: WiperState?) = inListener(input, token, epoch) {
                input.listenerRevision++
                if (input.baseline) accept(input, state.asActive())
            }
            override fun onFailed(e: Exception) = inListener(input, token, epoch) {
                detachWiper()
                input.supported = null
                input.pending = false
                input.request++
                fail(input, failureState(e))
            }
        }
        wiperListener = listener
        try {
            wiper?.registerWiperState(windshield, listener)
            input.registered = input.listenerEpoch == epoch
        } catch (error: Exception) {
            detachWiper()
            input.pending = false
            fail(input, failureState(error))
        }
    }

    private fun readWiper(token: Long, request: Long) {
        val listenerRevision = wiperInput.listenerRevision
        try {
            wiper?.getCurrentWiperState(windshield, onComplete = { state -> inRequest(wiperInput, token, request) {
                wiperInput.pending = false
                // A get started before a newer listener update cannot roll the input back.
                if (listenerRevision == wiperInput.listenerRevision) {
                    accept(wiperInput, state.asActive(), currentRead = true)
                }
            } }, onFailed = { error -> inRequest(wiperInput, token, request) {
                wiperInput.pending = false
                fail(wiperInput, failureState(error))
            } })
        } catch (error: Exception) {
            wiperInput.pending = false
            fail(wiperInput, failureState(error))
        }
    }

    private fun requestWindow() {
        val domain = window ?: return
        val input = windowInput
        val token = generation
        val request = begin(input)
        val failed: (Exception) -> Unit = { error -> inRequest(input, token, request) {
            input.pending = false
            fail(input, failureState(error))
        } }
        try {
            if (input.supported == true && input.registered) readWindow(token, request)
            else domain.checkWindowPositionCapabilities(onComplete = { config, areas -> inRequest(input, token, request) {
                val bounds = areas[frontLeft]
                // WINDOW_POS: side window 0=closed, positive=in-plane opening. No percent assumption.
                if (config.accessType !in READABLE || bounds == null || bounds.minValue != 0 || bounds.maxValue <= 0) {
                    unsupported(input, "WINDOW_POSITION_RANGE_NOT_READABLE")
                } else {
                    windowMax = bounds.maxValue
                    input.supported = true
                    registerWindow(token)
                    if (input.registered) readWindow(token, request)
                }
            } }, onFailed = failed)
        } catch (error: Exception) { failed(error) }
    }

    private fun registerWindow(token: Long) {
        val input = windowInput
        val epoch = ++input.listenerEpoch
        val listener = object : WindowPositionListener {
            override fun onWindowPositionUpdated(position: Int?) = inListener(input, token, epoch) {
                input.listenerRevision++
                if (input.baseline) accept(input, position.windowActive())
            }
            override fun onFailed(e: Exception) = inListener(input, token, epoch) {
                detachWindow()
                input.supported = null
                input.pending = false
                input.request++
                fail(input, failureState(e))
            }
        }
        windowListener = listener
        try {
            window?.registerWindowPosition(frontLeft, listener)
            input.registered = input.listenerEpoch == epoch
        } catch (error: Exception) {
            detachWindow()
            input.pending = false
            fail(input, failureState(error))
        }
    }

    private fun readWindow(token: Long, request: Long) {
        val listenerRevision = windowInput.listenerRevision
        try {
            window?.getCurrentWindowPosition(frontLeft, onComplete = { position -> inRequest(windowInput, token, request) {
                windowInput.pending = false
                if (listenerRevision == windowInput.listenerRevision) {
                    accept(windowInput, position.windowActive(), currentRead = true)
                }
            } }, onFailed = { error -> inRequest(windowInput, token, request) {
                windowInput.pending = false
                fail(windowInput, failureState(error))
            } })
        } catch (error: Exception) {
            windowInput.pending = false
            fail(windowInput, failureState(error))
        }
    }

    private fun requestTurnSignal() {
        val domain = turnSignal ?: return
        val input = turnLeftInput
        val token = generation
        val request = begin(input)
        val failed: (Exception) -> Unit = { error -> inRequest(input, token, request) {
            input.pending = false
            fail(input, failureState(error))
        } }
        try {
            if (input.supported == true && input.registered) readTurnSignal(token, request)
            else domain.checkTurnSignalStateCapabilities(onComplete = { config -> inRequest(input, token, request) {
                if (config.accessType !in READABLE) {
                    unsupported(input, "TURN_SIGNAL_STATE_NOT_READABLE")
                } else {
                    turnInputs.forEach { it.supported = true }
                    registerTurnSignal(token)
                    if (input.registered) readTurnSignal(token, request)
                }
            } }, onFailed = failed)
        } catch (error: Exception) { failed(error) }
    }

    private fun registerTurnSignal(token: Long) {
        val input = turnLeftInput
        val epoch = ++input.listenerEpoch
        val listener = object : TurnSignalStateListener {
            override fun onTurnSignalStateUpdated(state: TurnSignalValue?) = inListener(input, token, epoch) {
                input.listenerRevision++
                if (input.baseline) acceptTurnSignal(state)
            }
            override fun onFailed(e: Exception) = inListener(input, token, epoch) {
                detachTurnSignal()
                turnInputs.forEach { it.supported = null }
                input.pending = false
                input.request++
                fail(input, failureState(e))
            }
        }
        turnSignalListener = listener
        try {
            turnSignal?.registerTurnSignalState(listener)
            input.registered = input.listenerEpoch == epoch
        } catch (error: Exception) {
            detachTurnSignal()
            input.pending = false
            fail(input, failureState(error))
        }
    }

    private fun readTurnSignal(token: Long, request: Long) {
        val input = turnLeftInput
        val listenerRevision = input.listenerRevision
        try {
            turnSignal?.getCurrentTurnSignalState(onComplete = { state -> inRequest(input, token, request) {
                input.pending = false
                // A delayed get cannot undo a more recent LEFT/RIGHT/OFF callback.
                if (listenerRevision == input.listenerRevision) acceptTurnSignal(state, currentRead = true)
            } }, onFailed = { error -> inRequest(input, token, request) {
                input.pending = false
                fail(input, failureState(error))
            } })
        } catch (error: Exception) {
            input.pending = false
            fail(input, failureState(error))
        }
    }

    private fun acceptTurnSignal(state: TurnSignalValue?, currentRead: Boolean = false) {
        if (state == null) {
            fail(turnLeftInput, SdkFeatureState(SdkAvailability.NO_DATA, "PUZZLE_VALUE_NULL_OR_UNRECOGNIZED"))
            return
        }
        if (currentRead) turnInputs.forEach { it.baseline = true }
        if (turnInputs.any { !it.baseline }) return
        val receivedAt = SystemClock.elapsedRealtime()
        // Publish the inactive side first, so a direct direction change has no transient both-ON state.
        val ordered = if (state == TurnSignalValue.LEFT) listOf(turnRightInput, turnLeftInput) else turnInputs
        ordered.forEach { input ->
            val active = when (input.control) {
                CarControl.TURN_LEFT -> state == TurnSignalValue.LEFT
                CarControl.TURN_RIGHT -> state == TurnSignalValue.RIGHT
                else -> error("Not a turn signal input")
            }
            accept(input, active, receivedAt = receivedAt)
        }
    }

    private fun begin(input: Input): Long {
        input.pending = true
        input.requestedAt = SystemClock.elapsedRealtime()
        return ++input.request
    }

    private fun accept(
        input: Input,
        value: Boolean?,
        currentRead: Boolean = false,
        receivedAt: Long = SystemClock.elapsedRealtime(),
    ) {
        if (value == null) {
            fail(input, SdkFeatureState(SdkAvailability.NO_DATA, "PUZZLE_VALUE_NULL_OR_UNRECOGNIZED"))
            return
        }
        if (currentRead) input.baseline = true
        if (!input.baseline) return
        input.value = value
        input.receivedAt = receivedAt
        input.state = SdkFeatureState(SdkAvailability.READY, "VEHICLE_READ_ONLY_INPUT")
        publish(input)
    }

    private fun fail(input: Input, state: SdkFeatureState) {
        val affected = if (input in turnInputs) turnInputs else listOf(input)
        affected.forEach {
            it.value = null
            it.receivedAt = 0
            it.baseline = false // Recovery must establish a new current-value baseline before callbacks.
            it.state = state
        }
        affected.forEach(::publish)
    }

    private fun unsupported(input: Input, detail: String) {
        input.pending = false
        (if (input in turnInputs) turnInputs else listOf(input)).forEach { it.supported = false }
        fail(input, SdkFeatureState(SdkAvailability.UNSUPPORTED, detail))
    }

    private fun publish(input: Input) {
        if (closed) return
        val fresh = input.receivedAt > 0 && SystemClock.elapsedRealtime() - input.receivedAt in 0..FRESH_MS
        if (input.value != null && !fresh) {
            fail(input, SdkFeatureState(SdkAvailability.STALE, "PUZZLE_INPUT_STALE"))
            return
        }
        val sample = input.value.takeIf { fresh }
        emit(input.control, sample, input.state, if (sample != null) input.receivedAt else SystemClock.elapsedRealtime())
    }

    private fun WiperState?.asActive(): Boolean? = when (this) {
        WiperState.ON -> true
        WiperState.OFF -> false
        else -> null
    }
    private fun Int?.windowActive(): Boolean? = this?.takeIf { it in 0..windowMax }?.let { it > 0 }
    private fun inRequest(input: Input, token: Long, request: Long, action: () -> Unit) = onMain {
        if (!closed && token == generation && input.pending && input.request == request) action()
    }
    private fun inListener(input: Input, token: Long, epoch: Long, action: () -> Unit) = onMain {
        if (!closed && token == generation && input.listenerEpoch == epoch) action()
    }
    private fun onMain(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) action() else main.post(action)
    }
    private fun checkMain() = check(Looper.myLooper() == Looper.getMainLooper())
    private fun detachWiper() {
        wiperInput.listenerEpoch++
        runCatching { wiperListener?.let { wiper?.unregisterWiperState(windshield, it) } }
        wiperListener = null
        wiperInput.registered = false
    }
    private fun detachWindow() {
        windowInput.listenerEpoch++
        runCatching { windowListener?.let { window?.unregisterWindowPosition(frontLeft, it) } }
        windowListener = null
        windowInput.registered = false
    }

    private fun detachTurnSignal() {
        turnLeftInput.listenerEpoch++
        runCatching { turnSignalListener?.let { turnSignal?.unregisterTurnSignalState(it) } }
        turnSignalListener = null
        turnLeftInput.registered = false
    }

    override fun close() {
        checkMain()
        if (closed) return
        closed = true
        generation++
        main.removeCallbacks(tick)
        detachWiper()
        detachWindow()
        detachTurnSignal()
        runCatching { sdk?.release() }
        sdk = null; wiper = null; window = null; turnSignal = null
        for (input in inputs) {
            input.request++
            input.pending = false
            input.value = null
            input.baseline = false
        }
    }

    private companion object {
        const val POLL_MS = 3_000L
        const val FRESH_MS = 9_000L
        const val REQUEST_TIMEOUT_MS = 8_000L
        val READABLE = setOf(AccessType.READ, AccessType.READ_WRITE)
    }
}
