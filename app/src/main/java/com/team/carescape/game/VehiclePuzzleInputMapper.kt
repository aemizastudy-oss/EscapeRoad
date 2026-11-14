package com.team.carescape.game

/**
 * One actual SDK callback, normalized by the reader rather than guessed by the puzzle.
 *
 * WIPER means OFF=false / ON=true (not the number of physical blade sweeps).
 * WINDOW means completely closed=false / open or partially open=true.
 * TURN_LEFT/TURN_RIGHT normalize one SDK state: LEFT=true/false,
 * RIGHT=false/true, OFF=false/false. Unknown is null for both directions.
 * Unknown, unsupported, service and failed readings use null. In particular, a turn
 * signal is never a substitute for an unavailable HAZARD reading.
 *
 * [sequence] increases across all controls for the lifetime of the runtime. Every
 * reconnect also changes [generation]. The timestamp belongs to the original
 * reader callback and must not be refreshed when delivering or seeding a sample.
 */
data class VehiclePuzzleSample(
    val control: CarControl,
    val active: Boolean?,
    val generation: Long,
    val sequence: Long,
    val receivedElapsedMillis: Long,
)

/** Capture a new context on screen, puzzle, reset, safety, foreground or mode changes. */
data class VehiclePuzzleInputContext(
    val scopeKey: String?,
    val eligible: Boolean,
    val activatedAtElapsedMillis: Long,
    val generation: Long,
    val sequenceFloor: Long,
) {
    init {
        require(activatedAtElapsedMillis >= 0L)
        require(generation >= 0L)
        require(sequenceFloor >= 0L)
    }
}

enum class VehiclePuzzleInputStatus {
    READY,
    WAITING_FOR_BASELINE,
    UNKNOWN,
    STALE,
    INVALID_TIMESTAMP,
    BEFORE_CONTEXT,
    INELIGIBLE,
    CONNECTION_CHANGED,
    UNSUPPORTED_CONTROL,
}

data class VehiclePuzzleInputSnapshot(
    val control: CarControl,
    val status: VehiclePuzzleInputStatus,
    val sample: VehiclePuzzleSample? = null,
) {
    val isReady: Boolean get() = status == VehiclePuzzleInputStatus.READY
}

/**
 * A pure state machine owned by one sequential consumer (normally the ViewModel).
 * It contains no SDK, clock, coroutine or Android dependency.
 *
 * reset -> seedBaseline(latest callbacks) -> accept(ordered new callbacks).
 * A baseline never emits an answer. Only a fresh, continuous false -> true edge
 * inside the same eligible context emits one [CarControl]. Repeated ON/open
 * values, mode changes such as wiper Low -> High, and moving window samples do
 * not emit additional answers. A turn signal's direct LEFT -> RIGHT or RIGHT -> LEFT
 * switch activates only the new direction. OFF rearms either direction. After an
 * unknown value or a sampling gap the first valid sample is a new baseline.
 */
class VehiclePuzzleInputMapper(
    val maxSampleAgeMillis: Long = DEFAULT_MAX_SAMPLE_AGE_MILLIS,
) {
    init { require(maxSampleAgeMillis > 0L) }

    private var context: VehiclePuzzleInputContext? = null
    private var highestGenerationSeen: Long = -1L
    private var connectionChanged = false
    private var lastAcceptedSequence = -1L
    private val observations = mutableMapOf<CarControl, Observation>()

    private data class Observation(
        val sample: VehiclePuzzleSample,
        val baselineKnown: Boolean,
        val invalidStatus: VehiclePuzzleInputStatus? = null,
    )

    /** Always call explicitly at an input boundary, including the reset button. */
    fun reset(context: VehiclePuzzleInputContext) {
        this.context = context
        highestGenerationSeen = maxOf(highestGenerationSeen, context.generation)
        connectionChanged = context.generation < highestGenerationSeen
        lastAcceptedSequence = context.sequenceFloor
        observations.clear()
    }

    /**
     * Explicitly seed values already known when the context began. Unlike accept,
     * this permits a sequence at/below the floor, but never emits a puzzle input.
     * Freshness is measured at the captured activation time, not a made-up time.
     */
    fun seedBaseline(sample: VehiclePuzzleSample): Boolean {
        val current = context ?: return false
        if (!isSupported(sample.control) || !matchesGeneration(sample, current)) return false
        if (connectionChanged || !current.eligible || current.scopeKey == null) return false
        if (sample.sequence < 0L) return false
        val previous = observations[sample.control]
        if (previous != null && sample.sequence <= previous.sample.sequence) return false
        val invalid = timestampStatus(sample, current.activatedAtElapsedMillis)
        observations[sample.control] = Observation(sample, sample.active != null && invalid == null, invalid)
        lastAcceptedSequence = maxOf(lastAcceptedSequence, sample.sequence)
        return sample.active != null && invalid == null
    }

    /** A callback is consumed once, even when it is unknown, stale or ineligible. */
    fun accept(sample: VehiclePuzzleSample, nowElapsedMillis: Long): CarControl? {
        val current = context ?: return null
        if (!isSupported(sample.control) || !matchesGeneration(sample, current)) return null
        if (connectionChanged || sample.sequence <= current.sequenceFloor || sample.sequence <= lastAcceptedSequence) return null
        lastAcceptedSequence = sample.sequence

        val previous = observations[sample.control]
        val invalid = timestampStatus(sample, nowElapsedMillis)
            ?: when {
                sample.receivedElapsedMillis < current.activatedAtElapsedMillis -> VehiclePuzzleInputStatus.BEFORE_CONTEXT
                previous != null && previous.baselineKnown && sample.receivedElapsedMillis < previous.sample.receivedElapsedMillis ->
                    VehiclePuzzleInputStatus.INVALID_TIMESTAMP
                else -> null
            }
        val valid = current.eligible && current.scopeKey != null && invalid == null && sample.active != null
        observations[sample.control] = Observation(sample, valid, invalid)
        if (!valid || previous == null || !previous.baselineKnown) return null

        // Both sides of the edge must be continuously observed. A new value after
        // a long gap establishes a baseline rather than inventing a missed action.
        val elapsedSincePrevious = sample.receivedElapsedMillis - previous.sample.receivedElapsedMillis
        if (elapsedSincePrevious < 0L || elapsedSincePrevious > maxSampleAgeMillis) return null
        if (previous.sample.active == false && sample.active == true) return sample.control
        return null
    }

    fun snapshot(control: CarControl, nowElapsedMillis: Long): VehiclePuzzleInputSnapshot {
        fun result(status: VehiclePuzzleInputStatus, sample: VehiclePuzzleSample? = null) =
            VehiclePuzzleInputSnapshot(control, status, sample)
        if (!isSupported(control)) return result(VehiclePuzzleInputStatus.UNSUPPORTED_CONTROL)
        val current = context ?: return result(VehiclePuzzleInputStatus.WAITING_FOR_BASELINE)
        if (connectionChanged) return result(VehiclePuzzleInputStatus.CONNECTION_CHANGED)
        val observation = observations[control]
        if (!current.eligible || current.scopeKey == null) return result(VehiclePuzzleInputStatus.INELIGIBLE, observation?.sample)
        if (observation == null) return result(VehiclePuzzleInputStatus.WAITING_FOR_BASELINE)
        val sample = observation.sample
        val invalid = observation.invalidStatus ?: timestampStatus(sample, nowElapsedMillis)
        if (invalid != null) return result(invalid, sample)
        if (sample.active == null) return result(VehiclePuzzleInputStatus.UNKNOWN, sample)
        if (!observation.baselineKnown) return result(VehiclePuzzleInputStatus.WAITING_FOR_BASELINE, sample)
        return result(VehiclePuzzleInputStatus.READY, sample)
    }

    fun snapshots(nowElapsedMillis: Long): Map<CarControl, VehiclePuzzleInputSnapshot> =
        SUPPORTED_CONTROLS.associateWith { snapshot(it, nowElapsedMillis) }

    fun isReady(controls: Collection<CarControl>, nowElapsedMillis: Long): Boolean =
        controls.isNotEmpty() && controls.all { snapshot(it, nowElapsedMillis).isReady }

    private fun matchesGeneration(sample: VehiclePuzzleSample, current: VehiclePuzzleInputContext): Boolean {
        if (sample.generation > highestGenerationSeen) {
            highestGenerationSeen = sample.generation
            connectionChanged = true
            observations.clear()
        }
        return sample.generation == current.generation && !connectionChanged
    }

    private fun timestampStatus(sample: VehiclePuzzleSample, nowElapsedMillis: Long): VehiclePuzzleInputStatus? = when {
        nowElapsedMillis < 0L || sample.receivedElapsedMillis < 0L || sample.receivedElapsedMillis > nowElapsedMillis ->
            VehiclePuzzleInputStatus.INVALID_TIMESTAMP
        nowElapsedMillis - sample.receivedElapsedMillis > maxSampleAgeMillis -> VehiclePuzzleInputStatus.STALE
        else -> null
    }

    private fun isSupported(control: CarControl): Boolean = control in SUPPORTED_CONTROLS

    companion object {
        const val DEFAULT_MAX_SAMPLE_AGE_MILLIS = 9_000L
        val SUPPORTED_CONTROLS: Set<CarControl> = setOf(
            CarControl.WIPER, CarControl.HAZARD, CarControl.WINDOW,
            CarControl.TURN_LEFT, CarControl.TURN_RIGHT,
        )
    }
}
