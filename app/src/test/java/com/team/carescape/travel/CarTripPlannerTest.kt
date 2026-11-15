package com.team.carescape.travel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CarTripPlannerTest {
    private val stops = GuideStops.all

    @Test
    fun legsCoverTheRestOfTheCourseFromTheCurrentStop() {
        assertEquals(4, CarTripPlanner.legs(stops, 0).size)
        assertEquals(2, CarTripPlanner.legs(stops, 2).size)
        assertTrue(CarTripPlanner.legs(stops, 4).isEmpty())
        CarTripPlanner.legs(stops, 0).forEach {
            assertTrue(it.estimateKm >= it.straightKm)
            assertTrue(it.estimateMinutes >= 3)
        }
    }

    @Test
    fun verdictComparesRangeWithRemainingCourseAndReserve() {
        val remaining = CarTripPlanner.plan(stops, 0, CarEnergy()).remainingKm
        assertEquals(EnergyVerdict.ENOUGH, CarTripPlanner.plan(stops, 0, CarEnergy(rangeKm = remaining + CarTripPlanner.RESERVE_KM)).verdict)
        assertEquals(EnergyVerdict.TIGHT, CarTripPlanner.plan(stops, 0, CarEnergy(rangeKm = remaining)).verdict)
        assertEquals(EnergyVerdict.CHARGE, CarTripPlanner.plan(stops, 0, CarEnergy(rangeKm = remaining - 1)).verdict)
    }

    @Test
    fun missingRangeNeverClaimsTheCourseIsFine() {
        assertEquals(EnergyVerdict.NO_DATA, CarTripPlanner.plan(stops, 0, CarEnergy()).verdict)
        assertEquals(EnergyVerdict.RANGE_UNKNOWN, CarTripPlanner.plan(stops, 0, CarEnergy(batteryPercent = 80)).verdict)
        assertEquals(EnergyVerdict.LOW, CarTripPlanner.plan(stops, 0, CarEnergy(batteryPercent = 10)).verdict)
        assertEquals(EnergyVerdict.LOW, CarTripPlanner.plan(stops, 0, CarEnergy(fuelPercent = 5)).verdict)
    }

    @Test
    fun tourGuideCoversEveryStopWithThreeLooks() {
        stops.forEach { s ->
            val spot = TourGuide.spot(s.checkpointId)
            assertTrue(s.checkpointId, spot != null && spot.looks.size == 3)
        }
        assertNull(TourGuide.spot("unknown"))
    }
}
