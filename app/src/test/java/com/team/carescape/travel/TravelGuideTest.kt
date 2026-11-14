package com.team.carescape.travel

import java.time.DayOfWeek
import kotlinx.coroutines.test.runTest
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

    @Test fun naverLocalParsingStripsTagsAndScalesCoordinates() {
        val body = """{"items":[{"title":"<b>행궁</b> 국밥 &amp; 찜","link":"","category":"한식>국밥","telephone":"","address":"경기 수원시","roadAddress":"경기 수원시 팔달구 정조로 1","mapx":"1270132510","mapy":"372817910"}]}"""
        val p = NaverLocalClient.parse(body).single()
        assertEquals("행궁 국밥 & 찜", p.name)
        assertEquals("경기 수원시 팔달구 정조로 1", p.address)
        assertEquals(127.013251, p.lng!!, 1e-6)
        assertEquals(37.281791, p.lat!!, 1e-6)
        assertEquals(null, p.phone)
        assertTrue(NaverLocalClient.parse("""{"items":[]}""").isEmpty())
    }

    @Test fun tourApiParsingHandlesArrayObjectEmptyAndErrors() {
        fun wrap(items: String) = """{"response":{"header":{"resultCode":"0000","resultMsg":"OK"},"body":{"items":$items,"numOfRows":10,"pageNo":1,"totalCount":1}}}"""
        val one = """{"title":"수원화성박물관","addr1":"경기도 수원시","addr2":"","mapx":"127.0200","mapy":"37.2800","dist":"412.5","tel":"","contenttypeid":"14"}"""
        assertEquals(1, TourApiClient.parse(wrap("""{"item":$one}""")).size)
        val two = TourApiClient.parse(wrap("""{"item":[$one,$one]}"""))
        assertEquals(2, two.size)
        assertEquals(412, two.first().distanceM)
        assertEquals("문화시설", two.first().category)
        assertTrue(TourApiClient.parse(wrap("\"\"")).isEmpty())
        val error = """{"response":{"header":{"resultCode":"30","resultMsg":"SERVICE_KEY_IS_NOT_REGISTERED_ERROR"}}}"""
        assertTrue(runCatching { TourApiClient.parse(error) }.isFailure)
    }

    @Test fun missingKeysReportNotConfiguredAndNeverCallTheNetwork() = runTest {
        var calls = 0
        val http = object : HttpGet { override suspend fun get(url: String, headers: Map<String, String>): String { calls++; return "{}" } }
        val keys = TravelKeys()
        val repo = TravelRepository(keys, NaverLocalClient(keys, http), TourApiClient(keys, http))
        val r = repo.load(stops[0], TravelTab.STAY, TripProfile(TripLength.ONE_NIGHT))
        assertEquals(TravelResult.NotConfigured(listOf("NAVER_CLIENT_ID", "NAVER_CLIENT_SECRET", "TOUR_API_KEY")), r)
        assertEquals(0, calls)
    }

    @Test fun resultsMergeSourcesSortByDistanceAndReportPartialKeys() = runTest {
        val keys = TravelKeys(tourApiKey = "k")
        val near = """{"title":"가까운 곳","addr1":"a","mapx":"127.011","mapy":"37.281","dist":"100","contenttypeid":"12"}"""
        val far = """{"title":"먼 곳","addr1":"b","mapx":"127.03","mapy":"37.29","dist":"2500","contenttypeid":"12"}"""
        val http = object : HttpGet {
            override suspend fun get(url: String, headers: Map<String, String>): String =
                """{"response":{"header":{"resultCode":"0000"},"body":{"items":{"item":[$far,$near]}}}}"""
        }
        val repo = TravelRepository(keys, NaverLocalClient(keys, http), TourApiClient(keys, http))
        val r = repo.load(stops[0], TravelTab.ACTIVITY, TripProfile()) as TravelResult.Ok
        assertEquals(listOf("가까운 곳", "먼 곳"), r.places.map { it.name })
        assertEquals(listOf("NAVER_CLIENT_ID", "NAVER_CLIENT_SECRET"), r.missing)
        assertTrue(r.places.all { it.source == TourApiClient.SOURCE })
    }

    @Test fun familyTripsUseFamilyQueries() {
        val repo = TravelRepository(TravelKeys())
        assertEquals(listOf("수원 장소1 가족 맛집"), repo.planFor(stops[0], TravelTab.FOOD, TripProfile(companion = TravelParty.FAMILY)).naverQueries)
        assertEquals(listOf("수원 장소1 맛집"), repo.planFor(stops[0], TravelTab.FOOD, TripProfile()).naverQueries)
    }
}
