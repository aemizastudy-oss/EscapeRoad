package com.team.carescape.travel

import java.time.DayOfWeek
import java.time.LocalDate
import kotlinx.serialization.Serializable

/**
 * 여행 도우미(부가 서비스). 방탈출 스토리·진행 기록과 완전히 분리되어 있다.
 * 이 패키지는 [com.team.carescape.game]의 진행 상태를 바꾸지 않고, 마지막 장소 공개 여부만 읽는다.
 */
@Serializable
enum class TripLength(val label: String, val nights: Int) {
    DAY("당일치기", 0), ONE_NIGHT("1박 2일", 1), TWO_NIGHTS("2박 3일", 2);
    val days: Int get() = nights + 1
}

@Serializable
enum class TravelParty(val label: String) { SOLO("혼자"), FRIENDS("친구"), COUPLE("연인"), FAMILY("가족(아이 동반)"), PARENTS("부모님과") }

@Serializable
enum class Interest(val label: String) { FOOD("맛집"), CAFE("카페"), HISTORY("역사"), ACTIVITY("체험"), NIGHT("야경"), SHOPPING("쇼핑") }

@Serializable
enum class Meal(val label: String) { BREAKFAST("아침"), LUNCH("점심"), DINNER("저녁") }

/** 이용자가 입력한 여행 정보. 기기 안 별도 파일에만 저장한다. */
@Serializable
data class TripProfile(
    val length: TripLength = TripLength.DAY,
    /** ISO 날짜(yyyy-MM-dd). 정하지 않았으면 null. */
    val startDate: String? = null,
    val companion: TravelParty = TravelParty.FRIENDS,
    val interests: Set<Interest> = setOf(Interest.FOOD, Interest.HISTORY),
    val meals: Set<Meal> = setOf(Meal.LUNCH, Meal.DINNER),
) {
    val start: LocalDate? get() = startDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
}

/** 경유지 카드의 탭. */
enum class TravelTab(val label: String) {
    PARKING("주차"), FOOD("맛집"), CAFE("카페"), SIGHT("함께 보기"), ACTIVITY("체험"), STAY("숙소"), NIGHT("야경·저녁");
}

/** 여행 도우미가 보여 주는 경유지. 방탈출 코스의 장소와 같지만 독립된 사본이다. */
data class GuideStop(
    val index: Int,
    val checkpointId: String,
    val name: String,
    /** 검색어로 쓸 이름(지역을 붙여 같은 이름의 다른 곳을 피한다). */
    val searchName: String,
    val lat: Double,
    val lng: Double,
    /** 휴무 안내(방문 전 확인 필요). 예: 월요일. */
    val closedOn: DayOfWeek? = null,
)

sealed interface PlanItem {
    data class Stop(val stop: GuideStop, val locked: Boolean) : PlanItem
    data class Eat(val meal: Meal, val nearIndex: Int) : PlanItem
    data class Sleep(val nearIndex: Int) : PlanItem
}

data class DayPlan(val day: Int, val date: LocalDate?, val items: List<PlanItem>) {
    /** 이날 들르는 곳 가운데 휴무일에 걸리는 곳. */
    fun closedStops(): List<GuideStop> = if (date == null) emptyList() else
        items.filterIsInstance<PlanItem.Stop>().map { it.stop }.filter { it.closedOn == date.dayOfWeek }
}

object Itinerary {
    /** 경유지를 날짜별로 나눈다. 숙소는 그날 마지막 장소 근처, 마지막 날에는 숙소가 없다. */
    fun split(length: TripLength, count: Int): List<IntRange> = when (length) {
        TripLength.DAY -> listOf(0 until count)
        TripLength.ONE_NIGHT -> listOf(0 until minOf(3, count), minOf(3, count) until count)
        TripLength.TWO_NIGHTS -> listOf(0 until minOf(2, count), minOf(2, count) until minOf(4, count), minOf(4, count) until count)
    }.filter { !it.isEmpty() }

    fun build(profile: TripProfile, stops: List<GuideStop>, finalRevealed: Boolean): List<DayPlan> {
        val ranges = split(profile.length, stops.size)
        return ranges.mapIndexed { d, range ->
            val dayStops = range.map { stops[it] }
            val items = mutableListOf<PlanItem>()
            if (Meal.BREAKFAST in profile.meals && d > 0) items += PlanItem.Eat(Meal.BREAKFAST, dayStops.first().index)
            dayStops.forEachIndexed { k, s ->
                items += PlanItem.Stop(s, locked = s.index == stops.lastIndex && !finalRevealed)
                // 점심은 그날 첫 장소 뒤(장소가 하나면 그 뒤)
                if (k == 0 && Meal.LUNCH in profile.meals) items += PlanItem.Eat(Meal.LUNCH, s.index)
            }
            val last = dayStops.last()
            val lastDay = d == ranges.lastIndex
            if (Meal.DINNER in profile.meals) items += PlanItem.Eat(Meal.DINNER, nearForSpoiler(last, stops, finalRevealed))
            if (!lastDay) items += PlanItem.Sleep(last.index)
            DayPlan(d + 1, profile.start?.plusDays(d.toLong()), items)
        }
    }

    /** 공개 전 마지막 장소 근처의 식당은 추천하지 않고 바로 앞 장소 근처로 돌린다. */
    private fun nearForSpoiler(stop: GuideStop, stops: List<GuideStop>, finalRevealed: Boolean): Int =
        if (stop.index == stops.lastIndex && !finalRevealed && stop.index > 0) stop.index - 1 else stop.index

    /** 경유지 카드에 보일 탭. 당일치기는 숙소 대신 야경·저녁, 카페에 관심 있으면 카페 탭을 더한다. */
    fun tabs(profile: TripProfile): List<TravelTab> = buildList {
        add(TravelTab.PARKING)
        add(TravelTab.FOOD)
        if (Interest.CAFE in profile.interests) add(TravelTab.CAFE)
        add(TravelTab.SIGHT)
        add(TravelTab.ACTIVITY)
        add(if (profile.length == TripLength.DAY) TravelTab.NIGHT else TravelTab.STAY)
    }.let { base ->
        // 관심사에 맞는 탭을 주차 바로 뒤로 당긴다.
        val front = base.filter { t ->
            (t == TravelTab.ACTIVITY && Interest.ACTIVITY in profile.interests) ||
                (t == TravelTab.SIGHT && Interest.HISTORY in profile.interests) ||
                (t == TravelTab.NIGHT && Interest.NIGHT in profile.interests)
        }
        listOf(TravelTab.PARKING) + (base - TravelTab.PARKING).sortedBy { if (it in front) 0 else 1 }
    }
}

/** 일정의 빈 칸(식사·숙소) 이름. 예: "1:LUNCH", "1:STAY". */
fun slotKey(day: Int, item: PlanItem): String? = when (item) {
    is PlanItem.Eat -> "$day:${item.meal.name}"
    is PlanItem.Sleep -> "$day:STAY"
    is PlanItem.Stop -> null
}

/** 시간이 붙은 일정 한 줄. [travelBefore]는 앞 장소에서 차로 오는 어림 시간(분). */
data class TimedItem(val item: PlanItem, val start: java.time.LocalTime, val end: java.time.LocalTime, val travelBefore: Int?)

object Timeline {
    val dayStart: java.time.LocalTime = java.time.LocalTime.of(10, 0)
    const val STOP_MINUTES = 60
    const val MEAL_MINUTES = 60

    /** 직선거리 × 1.4를 시속 30km로 달린다고 보고 5분 단위로 올린다(최소 5분). 실제 시간은 내비게이션에서 확인한다. */
    fun driveMinutes(a: GuideStop, b: GuideStop): Int {
        val km = distanceMeters(a.lat, a.lng, b.lat, b.lng) / 1000.0 * 1.4
        val min = Math.ceil(km / 30.0 * 60.0 / 5.0).toInt() * 5
        return maxOf(5, min)
    }

    fun of(day: DayPlan): List<TimedItem> {
        var t = if (day.items.firstOrNull() is PlanItem.Eat) dayStart.minusMinutes(MEAL_MINUTES.toLong() + 30) else dayStart
        var prev: GuideStop? = null
        return day.items.map { item ->
            val travel = (item as? PlanItem.Stop)?.let { s -> prev?.let { driveMinutes(it, s.stop) } }
            if (travel != null) t = t.plusMinutes(travel.toLong())
            val length = when (item) { is PlanItem.Stop -> STOP_MINUTES; is PlanItem.Eat -> MEAL_MINUTES; is PlanItem.Sleep -> 0 }
            val start = t
            t = t.plusMinutes(length.toLong())
            if (item is PlanItem.Stop) prev = item.stop
            TimedItem(item, start, t, travel)
        }
    }
}

/** 담은 장소. 기기 안에만 저장한다. */
@Serializable
data class SavedPlace(
    val id: String,
    val name: String,
    val category: String,
    val address: String,
    val lat: Double? = null,
    val lng: Double? = null,
    val phone: String? = null,
    val source: String,
    val stopIndex: Int,
    val tab: String,
)

@Serializable
data class TripPlan(val saved: List<SavedPlace> = emptyList(), val slots: Map<String, String> = emptyMap()) {
    fun place(slot: String): SavedPlace? = slots[slot]?.let { id -> saved.firstOrNull { it.id == id } }
    fun isSaved(id: String) = saved.any { it.id == id }

    fun save(p: SavedPlace): TripPlan = if (isSaved(p.id)) this else copy(saved = saved + p)
    /** 빈 칸에 담는다. 같은 장소가 다른 칸에 있으면 그대로 두고 이 칸만 바꾼다. */
    fun assign(slot: String, p: SavedPlace): TripPlan = save(p).let { it.copy(slots = it.slots + (slot to p.id)) }
    fun clearSlot(slot: String): TripPlan = copy(slots = slots - slot)
    /** 저장에서 빼면 그 장소를 담은 칸도 비운다. */
    fun remove(id: String): TripPlan = copy(saved = saved.filterNot { it.id == id }, slots = slots.filterValues { it != id })
}

fun Place.toSaved(stopIndex: Int, tab: TravelTab): SavedPlace = SavedPlace(
    id = "${name.replace(" ", "")}@${lat?.let { "%.4f".format(java.util.Locale.ROOT, it) }}:${lng?.let { "%.4f".format(java.util.Locale.ROOT, it) }}",
    name = name, category = category, address = address, lat = lat, lng = lng, phone = phone, source = source,
    stopIndex = stopIndex, tab = tab.name,
)
