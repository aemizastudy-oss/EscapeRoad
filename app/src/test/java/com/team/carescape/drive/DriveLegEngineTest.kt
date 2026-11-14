package com.team.carescape.drive

import com.team.carescape.game.TourCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DriveLegEngineTest {
    private val plan = LegPlan(0, "A", 37.0, 127.0, 200.0, listOf(LegLine("l1", "one"), LegLine("l2", "two"), LegLine("l3", "three")))
    private val plans: (Int) -> LegPlan? = { if (it == 0) plan else null }
    private var now = 1_000_000L
    private val enRoute = TripSnapshot("s", 0, enRoute = true, ended = false)

    /** [m] metres south of the place (1° latitude ≈ 111.2 km). */
    private fun south(m: Double) = DriveInput.Location(37.0 - m / 111_195.0, 127.0)
    private fun run(state: DriveState, vararg inputs: DriveInput): DriveStep {
        var s = state
        val all = mutableListOf<DriveAction>()
        inputs.forEach { val step = DriveLegEngine.reduce(s, it, plans, now); s = step.state; all += step.actions }
        return DriveStep(s, all)
    }
    private fun spoken(step: DriveStep) = step.actions.filterIsInstance<DriveAction.Speak>().map { it.text }

    @Test fun legStartsOnlyWhenTripIsEnRouteAndGearIsDrive() {
        val parked = run(DriveState(), DriveInput.Trip(enRoute), DriveInput.Gear(DriveGear.PARK))
        assertTrue(parked.actions.isEmpty())
        val atPlace = run(DriveState(), DriveInput.Trip(enRoute.copy(enRoute = false)), DriveInput.Gear(DriveGear.DRIVE))
        assertTrue("puzzle not finished: the game has not moved to EN_ROUTE", atPlace.actions.isEmpty())
        val go = run(parked.state, DriveInput.Gear(DriveGear.DRIVE))
        assertTrue(DriveAction.StartBgm in go.actions)
        assertTrue(DriveAction.SetNavigation(0) in go.actions)
        assertTrue(DriveAction.ShowNavigation in go.actions)
        assertEquals(listOf("one"), spoken(go))
    }

    @Test fun linesAreSpreadOverLiveRemainingDistance() {
        var s = run(DriveState(), DriveInput.Trip(enRoute), DriveInput.Gear(DriveGear.DRIVE), south(3_000.0)).state
        assertTrue(spoken(run(s, south(2_200.0))).isEmpty())
        // A detour away from the place changes nothing; lines follow remaining distance, not a fixed route.
        s = run(s, south(4_000.0)).state
        val two = run(s, south(1_900.0))
        assertEquals(listOf("two"), spoken(two))
        assertEquals(listOf("three"), spoken(run(two.state, south(900.0))))
    }

    @Test fun parkingOnTheWayPausesAndResumesBgm() {
        val s = run(DriveState(), DriveInput.Trip(enRoute), DriveInput.Gear(DriveGear.DRIVE)).state
        val pause = run(s, DriveInput.Gear(DriveGear.PARK))
        assertEquals(listOf(DriveAction.PauseBgm), pause.actions)
        assertEquals(listOf(DriveAction.StartBgm), run(pause.state, DriveInput.Gear(DriveGear.DRIVE)).actions)
    }

    @Test fun arrivalNeedsTwoFixesStopsAudioAndNotifiesAfterPark() {
        val s = run(DriveState(), DriveInput.Trip(enRoute), DriveInput.Gear(DriveGear.DRIVE), south(800.0)).state
        val jump = run(s, south(50.0), south(900.0), south(40.0))
        assertTrue(jump.state.leg != null)
        val arrived = run(jump.state, south(30.0))
        assertTrue(DriveAction.StopBgm in arrived.actions)
        assertTrue("BGM and voice stop at the place", DriveAction.StopVoice in arrived.actions)
        assertTrue(spoken(arrived).isEmpty())
        assertTrue(arrived.state.leg == null && arrived.state.awaitingStop)
        assertFalse(DriveAction.NotifyArrival in arrived.actions)
        val parked = run(arrived.state, DriveInput.Gear(DriveGear.PARK))
        assertEquals(listOf(DriveAction.NotifyArrival), parked.actions)
        // Driving around the place before the game confirms arrival does not restart the leg.
        val again = run(parked.state, DriveInput.Gear(DriveGear.DRIVE))
        assertTrue(again.actions.isEmpty())
    }

    @Test fun departButtonSetsDestinationAndReplaysAFinishedLeg() {
        val s = run(DriveState(), DriveInput.Trip(enRoute), DriveInput.Gear(DriveGear.DRIVE), south(800.0), south(30.0), south(20.0)).state
        assertTrue(s.leg == null)
        val armed = run(s, DriveInput.Gear(DriveGear.PARK), DriveInput.Depart)
        assertTrue(DriveAction.SetNavigation(0) in armed.actions)
        assertTrue(armed.state.armed)
        val go = run(armed.state, DriveInput.Gear(DriveGear.DRIVE))
        assertTrue(DriveAction.StartBgm in go.actions)
        assertTrue(go.state.leg != null && !go.state.armed)
    }

    @Test fun gameLeavingEnRouteEndsTheLeg() {
        val s = run(DriveState(), DriveInput.Trip(enRoute), DriveInput.Gear(DriveGear.DRIVE)).state
        val confirmed = run(s, DriveInput.Trip(enRoute.copy(enRoute = false)))
        assertEquals(listOf(DriveAction.StopBgm), confirmed.actions)
        assertTrue(confirmed.state.leg == null)
    }

    @Test fun linesAreNeverRepeatedAfterRestart() {
        val first = run(DriveState(), DriveInput.Trip(enRoute), DriveInput.Gear(DriveGear.DRIVE))
        val restarted = DriveState(firedLines = first.state.firedLines)
        assertTrue(spoken(run(restarted, DriveInput.Trip(enRoute), DriveInput.Gear(DriveGear.DRIVE))).isEmpty())
    }

    @Test fun implausibleNavigationDistanceIsIgnored() {
        val s = run(DriveState(), DriveInput.Trip(enRoute), DriveInput.Gear(DriveGear.DRIVE), south(1_600.0)).state
        val nav = run(s, DriveInput.Navigation(21_000, 2_340, now))
        assertFalse(nav.state.fromNavigation)
        assertTrue(DriveLegEngine.plausible(2_400.0, 800.0))
        assertFalse(DriveLegEngine.plausible(500.0, 1_000.0))
        assertFalse("navigation reports 0 m before routing", DriveLegEngine.plausible(0.0, 50.0))
        assertFalse("no position yet", DriveLegEngine.plausible(2_000.0, null))
        // Navigation 0 m right after D must not release every line at once.
        val early = run(DriveState(), DriveInput.Trip(enRoute), DriveInput.Gear(DriveGear.DRIVE), DriveInput.Navigation(0, 0, now))
        assertEquals(listOf("one"), spoken(early))
    }

    @Test fun departureAndNearLinesUseTheirOwnTriggers() {
        val custom = LegPlan(0, "A", 37.0, 127.0, 200.0, listOf(
            LegLine("info", "info", atFraction = 1.0), LegLine("near", "near", atRemainingM = 900.0)))
        val p: (Int) -> LegPlan? = { if (it == 0) custom else null }
        fun go(state: DriveState, vararg inputs: DriveInput): DriveStep {
            var st = state; val all = mutableListOf<DriveAction>()
            inputs.forEach { val step = DriveLegEngine.reduce(st, it, p, now); st = step.state; all += step.actions }
            return DriveStep(st, all)
        }
        val start = go(DriveState(), DriveInput.Trip(enRoute), DriveInput.Gear(DriveGear.DRIVE), south(3_000.0))
        assertEquals(listOf("info"), spoken(start))
        assertTrue(spoken(go(start.state, south(1_200.0))).isEmpty())
        assertEquals(listOf("near"), spoken(go(start.state, south(850.0))))
    }

    @Test fun everyChapterHasShortDrivingLines() {
        val script = DriveScript.parse(java.io.File("src/main/assets/drive_lines.json").readText())
        TourCatalog.checkpoints.indices.forEach { i ->
            val plan = DriveCourse.plan(i, script)!!
            assertEquals(2, plan.lines.size)
            assertTrue(plan.lines.all { it.id.startsWith("drive_ch${i + 1}_") && it.text.length <= 90 })
            assertTrue(plan.lines.first().atFraction == 1.0 && plan.lines.last().atRemainingM != null)
        }
    }
}
