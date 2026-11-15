package com.team.carescape.travel

import java.time.DayOfWeek
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TravelGuideTest {
    private val stops = (0 until 5).map { GuideStop(it, "suwon_0${it + 1}", "장소${it + 1}", "수원 장소${it + 1}", 37.28 + it * 0.005, 127.01 + it * 0.01, if (it == 3) DayOfWeek.MONDAY else null) }

    @Test fun daysSplitTheFiveStopsAndOnlyNonFinalDaysHaveLodging() {
        assertEquals(listOf(0 until 5), Itinerary.split(TripLength.DAY, 5))
        assertEquals(listOf(0 until 3, 3 until 5), Itinerary.split(TripLength.ONE_NIGHT, 5))
        assertEquals(listOf(0 until 2, 2 until 4, 4 until 5), Itinerary.split(TripLength.TWO_NIGHTS, 5))
        val plan = Itinerary.build(TripProfile(TripLength.ONE_NIGHT), stops, finalRevealed = true)
        assertEquals(2, plan.size)
        assertEquals(listOf(PlanItem.Sleep(2)), plan[0].items.filterIsInstance<PlanItem.Sleep>())
        assertTrue(plan[1].items.none { it is PlanItem.Sleep })
        assertTrue(Itinerary.build(TripProfile(TripLength.DAY), stops, true).single().items.none { it is PlanItem.Sleep })
    }

    @Test fun finalStopStaysLockedAndNothingIsRecommendedNearItUntilRevealed() {
        val plan = Itinerary.build(TripProfile(TripLength.DAY, meals = setOf(Meal.DINNER)), stops, finalRevealed = false)
        val final = plan.flatMap { it.items }.filterIsInstance<PlanItem.Stop>().single { it.stop.index == 4 }
        assertTrue(final.locked)
        // 마지막 장소 근처 저녁 대신 바로 앞 장소 근처를 추천한다.
        assertEquals(3, plan.single().items.filterIsInstance<PlanItem.Eat>().single().nearIndex)
        val open = Itinerary.build(TripProfile(TripLength.DAY, meals = setOf(Meal.DINNER)), stops, finalRevealed = true)
        assertFalse(open.flatMap { it.items }.filterIsInstance<PlanItem.Stop>().any { it.locked })
        assertEquals(4, open.single().items.filterIsInstance<PlanItem.Eat>().single().nearIndex)
    }

    @Test fun datesAndClosedDayWarnings() {
        // 2026-11-16은 월요일. 1박 2일 둘째 날(17일, 화)에는 경고가 없고, 당일치기 16일에는 박물관 경고.
        val day = Itinerary.build(TripProfile(TripLength.DAY, startDate = "2026-11-16"), stops, true).single()
        assertEquals(listOf(3), day.closedStops().map { it.index })
        val two = Itinerary.build(TripProfile(TripLength.ONE_NIGHT, startDate = "2026-11-15"), stops, true)
        assertEquals(listOf("2026-11-15", "2026-11-16"), two.map { it.date.toString() })
        assertEquals(listOf(3), two[1].closedStops().map { it.index })
        assertTrue(Itinerary.build(TripProfile(TripLength.DAY), stops, true).single().closedStops().isEmpty())
    }

    @Test fun tabsFollowTripLengthAndInterests() {
        assertEquals(TravelTab.NIGHT, Itinerary.tabs(TripProfile(TripLength.DAY)).last())
        assertTrue(TravelTab.STAY in Itinerary.tabs(TripProfile(TripLength.ONE_NIGHT)))
        assertFalse(TravelTab.CAFE in Itinerary.tabs(TripProfile(interests = setOf(Interest.FOOD))))
        assertTrue(TravelTab.CAFE in Itinerary.tabs(TripProfile(interests = setOf(Interest.CAFE))))
        val activityFirst = Itinerary.tabs(TripProfile(interests = setOf(Interest.ACTIVITY)))
        assertEquals(listOf(TravelTab.PARKING, TravelTab.ACTIVITY), activityFirst.take(2))
    }

    @Test fun everyCheckpointHasPreparedParkingWithProvenanceAndHonestNavigation() {
        val repo = TravelRepository()
        stops.forEach { stop ->
            val places = repo.load(stop, TravelTab.PARKING).places
            assertTrue(places.isNotEmpty())
            places.forEach { p ->
                assertTrue(p.catalogId!!.startsWith("curated:"))
                assertTrue(p.address.isNotBlank() && p.source.isNotBlank() && p.link!!.startsWith("https://"))
                // 입구를 확인하지 않은 좌표를 실제 장소 좌표로 표시하지 않는다.
                assertEquals(null, p.lat)
                assertEquals(null, p.lng)
                assertEquals(stop.lat, p.navigationLat)
                assertEquals(stop.lng, p.navigationLng)
                assertEquals(stop.name, p.navigationName)
                assertEquals("주변 관광지로 안내", p.navigationLabel)
                assertTrue(p.note.contains("입구 좌표"))
            }
        }
    }

    @Test fun everyStopHasCardsAcrossAllSupportedCategoriesWithSafeNavigation() {
        val repo = TravelRepository()
        val categories = listOf(TravelTab.PARKING, TravelTab.FOOD, TravelTab.CAFE,
            TravelTab.ACTIVITY, TravelTab.STAY, TravelTab.NIGHT)
        stops.forEach { stop -> categories.forEach { tab ->
            val places = repo.load(stop, tab).places
            assertTrue("${stop.checkpointId}/${tab.name} needs prepared cards", places.isNotEmpty())
            assertEquals("A result must not repeat a card", places.size, places.map { it.catalogId }.toSet().size)
            places.forEach { place ->
                assertTrue(place.catalogId!!.startsWith("curated:"))
                assertTrue(place.address.isNotBlank() && place.source.isNotBlank() && place.link!!.startsWith("https://"))
                assertEquals(null, place.lat)
                assertEquals(null, place.lng)
                assertEquals(null, place.distanceM)
                assertEquals(stop.lat, place.navigationLat)
                assertEquals(stop.lng, place.navigationLng)
                assertEquals(stop.name, place.navigationName)
                assertEquals("주변 관광지로 안내", place.navigationLabel)
                assertTrue(place.note.contains("입구 좌표"))
            }
        } }
    }

    @Test fun unsupportedCategoryAndUnknownStopsStayEmptyWithoutFictionalResults() {
        val repo = TravelRepository()
        TravelTab.entries.forEach { tab ->
            assertTrue(repo.load(stops.first().copy(checkpointId = "unknown"), tab).places.isEmpty())
        }
        assertTrue(repo.load(stops.first(), TravelTab.SIGHT).places.isEmpty())
    }

    @Test fun originalCardIdsAndSharedCandidatesRemainStableWhenSaved() {
        val repo = TravelRepository()
        val originals = listOf("haenggung-parking", "hwahong-parking", "manseok-parking-5",
            "gwanggyo-museum-parking", "woncheon-parking", "jinmi-chicken", "yeonpo-galbi", "siru-hyanggi")
        val all = stops.flatMap { stop -> TravelTab.entries.flatMap { repo.load(stop, it).places } }
        originals.forEach { id -> assertTrue("Keep existing saved ID $id", all.any { it.catalogId == "curated:$id" }) }
        val first = repo.load(stops[0], TravelTab.STAY).places.first { it.catalogId == "curated:dono-hotel" }.toSaved(0, TravelTab.STAY)
        val nearby = repo.load(stops[2], TravelTab.STAY).places.first { it.catalogId == first.id }.toSaved(2, TravelTab.STAY)
        assertTrue(nearby.note.contains("차량 이동"))
        val saved = TripPlan().save(first).save(nearby)
        assertEquals(listOf(first), saved.saved)
    }

    @Test fun savedCardsPreserveAttributionAndTheDifferentNavigationTarget() {
        val p = TravelRepository().load(stops.first(), TravelTab.FOOD).places.first()
        val saved = p.toSaved(0, TravelTab.FOOD)
        assertEquals(p.catalogId, saved.id)
        assertEquals(p.link, saved.sourceUrl)
        assertEquals(p.note, saved.note)
        assertEquals(p.navigationName, saved.navigationName)
        assertEquals(p.navigationLat, saved.navigationLat)
        assertEquals(p.navigationLabel, saved.navigationLabel)
        assertEquals(null, saved.lat)
    }
}
