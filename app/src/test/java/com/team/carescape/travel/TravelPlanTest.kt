package com.team.carescape.travel

import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TravelPlanTest {
    private val stops = (0 until 5).map { GuideStop(it, "suwon_0${it + 1}", "장소${it + 1}", "수원 장소${it + 1}", 37.28 + it * 0.01, 127.01 + it * 0.01) }

    @Test fun timelineStartsAtTenAddsDriveTimeAndMeals() {
        val day = Itinerary.build(TripProfile(TripLength.DAY, meals = setOf(Meal.LUNCH, Meal.DINNER)), stops, true).single()
        val t = Timeline.of(day)
        assertEquals(LocalTime.of(10, 0), t.first().start)
        assertNull(t.first().travelBefore)
        // 점심은 첫 장소 뒤 11:00
        val lunch = t.first { (it.item as? PlanItem.Eat)?.meal == Meal.LUNCH }
        assertEquals(LocalTime.of(11, 0), lunch.start)
        // 두 번째 장소부터 이동 시간이 붙고, 시간은 계속 늘어난다.
        val stopsTimed = t.filter { it.item is PlanItem.Stop }
        assertTrue(stopsTimed.drop(1).all { (it.travelBefore ?: 0) >= 5 && it.travelBefore!! % 5 == 0 })
        assertTrue(t.zipWithNext().all { (a, b) -> !b.start.isBefore(a.start) })
    }

    @Test fun breakfastDayStartsEarlierAndDriveMinutesRoundUp() {
        val days = Itinerary.build(TripProfile(TripLength.ONE_NIGHT, meals = setOf(Meal.BREAKFAST)), stops, true)
        assertEquals(LocalTime.of(8, 30), Timeline.of(days[1]).first().start)
        assertEquals(LocalTime.of(10, 0), Timeline.of(days[0]).first().start)
        assertEquals(5, Timeline.driveMinutes(stops[0], stops[0]))
        assertTrue(Timeline.driveMinutes(stops[0], stops[4]) > Timeline.driveMinutes(stops[0], stops[1]))
    }

    @Test fun slotsAreKeyedByDayAndKind() {
        val days = Itinerary.build(TripProfile(TripLength.ONE_NIGHT), stops, true)
        val keys = days.flatMap { d -> d.items.mapNotNull { slotKey(d.day, it) } }
        assertEquals(listOf("1:LUNCH", "1:DINNER", "1:STAY", "2:LUNCH", "2:DINNER"), keys)
    }

    @Test fun savingAssigningAndRemovingPlaces() {
        val a = Place("국밥집", "한식", "주소", 37.281, 127.013, source = "예시").toSaved(0, TravelTab.FOOD)
        val b = Place("국 밥집", "한식", "주소", 37.281, 127.013, source = "예시").toSaved(0, TravelTab.FOOD)
        assertEquals(a.id, b.id) // 띄어쓰기만 다른 같은 장소는 같은 id
        var plan = TripPlan().save(a).save(b)
        assertEquals(1, plan.saved.size)
        plan = plan.assign("1:LUNCH", a)
        assertEquals("국밥집", plan.place("1:LUNCH")?.name)
        plan = plan.assign("1:DINNER", a)
        assertEquals(1, plan.saved.size)
        plan = plan.clearSlot("1:LUNCH")
        assertNull(plan.place("1:LUNCH"))
        assertEquals("국밥집", plan.place("1:DINNER")?.name)
        plan = plan.remove(a.id)
        assertTrue(plan.saved.isEmpty())
        assertTrue(plan.slots.isEmpty())
    }

    @Test fun samplePlacesAreClearlyLabeledAsNotReal() {
        val r = SamplePlaces.around(stops[0], TravelTab.FOOD)
        assertEquals(5, r.places.size)
        assertTrue(r.places.all { it.source == SamplePlaces.SOURCE && it.name.startsWith("예시") })
        assertTrue(SamplePlaces.SOURCE.contains("실제 장소 아님"))
        assertFalse(r.places.zipWithNext().any { (x, y) -> x.distanceM!! > y.distanceM!! })
    }
}
