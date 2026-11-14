package com.team.carescape.drive

import com.team.carescape.game.TourPolicy

/** App-owned gear value; Pleos SDK types stay in [DriveSignals]. */
enum class DriveGear { PARK, REVERSE, NEUTRAL, DRIVE, UNKNOWN }

/** What the drive chapter needs from the saved trip (TripProgressStore). */
data class TripSnapshot(val sessionId: String, val chapterIndex: Int, val enRoute: Boolean, val ended: Boolean)

/**
 * One spoken line. It is due when the live remaining distance drops to [atRemainingM], or to [atFraction]
 * of the distance at departure (1.0 = at departure). Without either, lines are spread evenly over the leg.
 */
data class LegLine(val id: String, val text: String, val atFraction: Double? = null, val atRemainingM: Double? = null)

/** One leg = driving to the chapter's place. Lines are the chapter's travel scene (voice ids ch<n>_travel_<nn>). */
data class LegPlan(
    val chapterIndex: Int,
    val place: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Double,
    val lines: List<LegLine>,
)

data class ActiveLeg(
    val sessionId: String,
    val chapterIndex: Int,
    val startRemainingM: Double? = null,
    val bgmPaused: Boolean = false,
    val insideFixes: Int = 0,
)

data class DriveState(
    val trip: TripSnapshot? = null,
    val gear: DriveGear = DriveGear.UNKNOWN,
    val leg: ActiveLeg? = null,
    /** Persisted: "<session>/<lineId>" already spoken, "<session>/<chapter>" legs already arrived. */
    val firedLines: Set<String> = emptySet(),
    val arrivedLegs: Set<String> = emptySet(),
    val latitude: Double? = null,
    val longitude: Double? = null,
    val straightM: Double? = null,
    val navRemainingM: Int? = null,
    val navEtaSec: Int? = null,
    val navAtMillis: Long = 0L,
    val remainingM: Double? = null,
    val fromNavigation: Boolean = false,
    /** The player pressed 출발하기 for the current chapter (destination set, waiting for D). */
    val armed: Boolean = false,
    /** Arrived at the place; the app is brought back once the car is in P. */
    val awaitingStop: Boolean = false,
)

sealed interface DriveInput {
    data class Trip(val trip: TripSnapshot?) : DriveInput
    data class Gear(val gear: DriveGear) : DriveInput
    data class Location(val latitude: Double, val longitude: Double) : DriveInput
    data class Navigation(val remainingM: Int, val etaSec: Int, val atMillis: Long) : DriveInput
    /** 출발하기 in the app: set the destination now and (re)arm this chapter's leg. */
    data object Depart : DriveInput
}

sealed interface DriveAction {
    data class Speak(val id: String, val text: String) : DriveAction
    data object StartBgm : DriveAction
    data object PauseBgm : DriveAction
    data object StopBgm : DriveAction
    data class SetNavigation(val chapterIndex: Int) : DriveAction
    /** Bring the navigation screen to the front (leg started in D). */
    data object ShowNavigation : DriveAction
    /** Stop the current line and drop queued lines. */
    data object StopVoice : DriveAction
    data class LegArrived(val chapterIndex: Int) : DriveAction
    /** Arrived and stopped (P): bring the app back to the front. */
    data object NotifyArrival : DriveAction
}

data class DriveStep(val state: DriveState, val actions: List<DriveAction>)

/**
 * Drive chapter rules. Nothing is pre-scripted along a route: every decision comes from live inputs.
 * - A leg starts when the game's trip is EN_ROUTE (the previous place is finished) and the gear is D.
 *   The game only moves to the next EN_ROUTE after the place's puzzles are done, so that is the departure gate.
 * - Each driving line has its own trigger: at departure, or when the live remaining distance drops to a set
 *   distance (e.g. "곧 도착합니다" at 900 m). Lines without a trigger are spread evenly over the leg.
 *   Remaining distance is the navigation's road distance when fresh and plausible, else straight-line.
 * - 출발하기 in the app sets the destination and (re)arms the leg; D then brings the navigation screen up.
 * - Gear P on the way pauses the BGM, D resumes it. Two consecutive fixes inside the place's radius end the leg:
 *   BGM and voice stop, and once the car is in P the app is brought back. The game confirms arrival in the app.
 */
object DriveLegEngine {
    const val ARRIVAL_FIXES = 2
    const val NAV_FRESH_MS = 15_000L

    fun reduce(state: DriveState, input: DriveInput, plans: (Int) -> LegPlan?, now: Long): DriveStep = when (input) {
        is DriveInput.Trip -> onTrip(state.copy(trip = input.trip), plans, now)
        is DriveInput.Gear -> onGear(state.copy(gear = input.gear), plans, now)
        is DriveInput.Location -> onLocation(state, input, plans, now)
        DriveInput.Depart -> onDepart(state, plans, now)
        is DriveInput.Navigation -> {
            val next = state.copy(navRemainingM = input.remainingM, navEtaSec = input.etaSec, navAtMillis = input.atMillis)
            if (next.leg != null) evaluate(next, plans, now) else DriveStep(next, emptyList())
        }
    }

    private fun onTrip(state: DriveState, plans: (Int) -> LegPlan?, now: Long): DriveStep {
        val leg = state.leg
        val trip = state.trip
        if (leg != null && (trip == null || !trip.enRoute || trip.sessionId != leg.sessionId || trip.chapterIndex != leg.chapterIndex)) {
            // The game moved on (e.g. confirmed arrival in the app): end this leg quietly.
            return DriveStep(state.copy(leg = null), listOf(DriveAction.StopBgm))
        }
        return maybeStart(state, plans, now)
    }

    private fun onDepart(state: DriveState, plans: (Int) -> LegPlan?, now: Long): DriveStep {
        val trip = state.trip ?: return DriveStep(state, emptyList())
        if (!trip.enRoute || trip.ended) return DriveStep(state, emptyList())
        val plan = plans(trip.chapterIndex) ?: return DriveStep(state, emptyList())
        // Pressing 출발하기 again replays this leg from the start (e.g. a re-run of the demo).
        val lineKeys = plan.lines.map { lineKey(trip.sessionId, it.id) }.toSet()
        val reset = state.copy(
            armed = true, awaitingStop = false,
            // Measure the leg from the next fix, not from where an earlier drive left the car.
            latitude = null, longitude = null, straightM = null, remainingM = null,
            firedLines = state.firedLines - lineKeys,
            arrivedLegs = state.arrivedLegs - legKey(trip.sessionId, trip.chapterIndex),
            leg = state.leg?.takeIf { it.chapterIndex == trip.chapterIndex && it.sessionId == trip.sessionId },
        )
        val start = maybeStart(reset, plans, now)
        return if (start.state.leg != null && state.leg == null) start
        else DriveStep(start.state, listOf(DriveAction.SetNavigation(trip.chapterIndex)) + start.actions)
    }

    private fun onGear(state: DriveState, plans: (Int) -> LegPlan?, now: Long): DriveStep {
        if (state.awaitingStop && state.gear == DriveGear.PARK) {
            return DriveStep(state.copy(awaitingStop = false), listOf(DriveAction.NotifyArrival))
        }
        val leg = state.leg ?: return maybeStart(state, plans, now)
        return when {
            state.gear == DriveGear.PARK && !leg.bgmPaused ->
                DriveStep(state.copy(leg = leg.copy(bgmPaused = true)), listOf(DriveAction.PauseBgm))
            state.gear == DriveGear.DRIVE && leg.bgmPaused ->
                DriveStep(state.copy(leg = leg.copy(bgmPaused = false)), listOf(DriveAction.StartBgm))
            else -> DriveStep(state, emptyList())
        }
    }

    private fun maybeStart(state: DriveState, plans: (Int) -> LegPlan?, now: Long): DriveStep {
        val trip = state.trip ?: return DriveStep(state, emptyList())
        if (state.leg != null || !trip.enRoute || trip.ended || state.gear != DriveGear.DRIVE) return DriveStep(state, emptyList())
        if (legKey(trip.sessionId, trip.chapterIndex) in state.arrivedLegs) return DriveStep(state, emptyList())
        val plan = plans(trip.chapterIndex) ?: return DriveStep(state, emptyList())
        val started = state.copy(leg = ActiveLeg(trip.sessionId, trip.chapterIndex), armed = false, awaitingStop = false)
        val actions = mutableListOf<DriveAction>(
            DriveAction.StartBgm, DriveAction.SetNavigation(trip.chapterIndex), DriveAction.ShowNavigation,
        )
        var fired = started.firedLines
        // Lines meant for the departure (first evenly-spread line, or atFraction >= 1) are spoken right away.
        plan.lines.forEachIndexed { index, line ->
            val atStart = (line.atFraction ?: if (line.atRemainingM == null && index == 0) 1.0 else null)?.let { it >= 1.0 } == true
            val key = lineKey(trip.sessionId, line.id)
            if (atStart && key !in fired) { actions += DriveAction.Speak(line.id, line.text); fired = fired + key }
        }
        val next = started.copy(firedLines = fired)
        if (next.latitude == null) return DriveStep(next, actions)
        val follow = evaluate(next, plans, now)
        return DriveStep(follow.state, actions + follow.actions)
    }

    private fun onLocation(state: DriveState, input: DriveInput.Location, plans: (Int) -> LegPlan?, now: Long): DriveStep {
        val withFix = state.copy(latitude = input.latitude, longitude = input.longitude)
        val leg = withFix.leg ?: return DriveStep(withFix, emptyList())
        val plan = plans(leg.chapterIndex) ?: return DriveStep(withFix, emptyList())
        val straight = TourPolicy.distanceMeters(input.latitude, input.longitude, plan.latitude, plan.longitude)
        val inside = if (straight <= plan.radiusMeters) leg.insideFixes + 1 else 0
        val next = withFix.copy(straightM = straight, leg = leg.copy(insideFixes = inside))
        if (inside >= ARRIVAL_FIXES) return arrive(next, plan)
        return evaluate(next, plans, now)
    }

    private fun evaluate(state: DriveState, plans: (Int) -> LegPlan?, now: Long): DriveStep {
        val leg = state.leg ?: return DriveStep(state, emptyList())
        val plan = plans(leg.chapterIndex) ?: return DriveStep(state, emptyList())
        val nav = state.navRemainingM
        val navUsable = nav != null && now - state.navAtMillis <= NAV_FRESH_MS && plausible(nav.toDouble(), state.straightM)
        val remaining = if (navUsable) nav!!.toDouble() else state.straightM
        var next = state.copy(remainingM = remaining, fromNavigation = navUsable && remaining != null)
        if (remaining == null) return DriveStep(next, emptyList())
        val start = leg.startRemainingM ?: remaining.also { next = next.copy(leg = leg.copy(startRemainingM = it)) }
        val k = plan.lines.size
        val actions = mutableListOf<DriveAction>()
        var fired = next.firedLines
        plan.lines.forEachIndexed { index, line ->
            val threshold = line.atRemainingM ?: (start * (line.atFraction ?: ((k - index).toDouble() / k)))
            val key = lineKey(leg.sessionId, line.id)
            if (key !in fired && remaining <= threshold) {
                actions += DriveAction.Speak(line.id, line.text)
                fired = fired + key
            }
        }
        return DriveStep(next.copy(firedLines = fired), actions)
    }

    private fun arrive(state: DriveState, plan: LegPlan): DriveStep {
        val leg = state.leg!!
        // At the place BGM and voice stop; the arrival scene belongs to the app.
        val actions = listOf(DriveAction.StopBgm, DriveAction.StopVoice, DriveAction.LegArrived(leg.chapterIndex))
        val stopped = state.gear == DriveGear.PARK
        return DriveStep(
            state.copy(
                leg = null, awaitingStop = !stopped,
                arrivedLegs = state.arrivedLegs + legKey(leg.sessionId, leg.chapterIndex),
            ),
            if (stopped) actions + DriveAction.NotifyArrival else actions,
        )
    }

    /** A road distance can't be shorter than the straight line and is rarely over ~3x it; otherwise navigation is routing from elsewhere. */
    fun plausible(navM: Double, straightM: Double?): Boolean =
        // Before the first position fix there is nothing to compare with; 0 m is navigation before routing.
        straightM != null && navM > 0 && navM in (straightM * 0.9 - 150.0)..(straightM * 3.0 + 3_000.0)

    fun legKey(sessionId: String, chapterIndex: Int) = "$sessionId/$chapterIndex"
    fun lineKey(sessionId: String, lineId: String) = "$sessionId/$lineId"
}
