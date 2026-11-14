package com.team.carescape.travel

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.team.carescape.game.TourCatalog
import java.time.DayOfWeek
import kotlinx.serialization.json.Json

/** 여행 도우미의 경유지. 방탈출 코스와 같은 다섯 곳(좌표는 관광지 대표점). */
object GuideStops {
    private val search = mapOf(
        "suwon_01" to "수원 화성행궁", "suwon_02" to "수원 화홍문", "suwon_03" to "수원 만석공원",
        "suwon_04" to "수원광교박물관", "suwon_05" to "광교호수공원",
    )
    // 박물관 월요일 휴관은 방문 전 공식 안내로 다시 확인한다.
    private val closed = mapOf("suwon_04" to DayOfWeek.MONDAY)

    val all: List<GuideStop> = TourCatalog.checkpoints.mapIndexed { i, w ->
        GuideStop(i, w.id, w.name, search[w.id] ?: w.name, w.latitude ?: 0.0, w.longitude ?: 0.0, closed[w.id])
    }
}

/** 여행 정보는 유지하되, 준비한 장소 목록은 curated_plan_v1에 저장한다. 이전 API 검색 저장값(plan)은 읽거나 재사용하지 않는다. */
class TripProfileStore(context: Context) {
    private val prefs = context.getSharedPreferences("travel_guide", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    fun load(): TripProfile? = prefs.getString("profile", null)?.let { runCatching { json.decodeFromString<TripProfile>(it) }.getOrNull() }
    fun save(p: TripProfile) { prefs.edit().putString("profile", json.encodeToString(TripProfile.serializer(), p)).apply() }
    fun loadPlan(): TripPlan = prefs.getString("curated_plan_v1", null)?.let { runCatching { json.decodeFromString<TripPlan>(it) }.getOrNull() } ?: TripPlan()
    fun savePlan(p: TripPlan) { prefs.edit().putString("curated_plan_v1", json.encodeToString(TripPlan.serializer(), p)).apply() }
}

enum class GuideMode(val label: String) { SCHEDULE("일정"), EXPLORE("둘러보기"), SAVED("저장") }

class TravelViewModel(application: Application) : AndroidViewModel(application) {
    private val store = TripProfileStore(application)
    private val repo = TravelRepository()

    var profile by mutableStateOf(store.load())
        private set
    var plan by mutableStateOf(store.loadPlan())
        private set
    var editing by mutableStateOf(profile == null)
        private set
    var mode by mutableStateOf(GuideMode.SCHEDULE)
        private set
    var day by mutableIntStateOf(1)
        private set
    var selectedStop by mutableIntStateOf(0)
        private set
    var tab by mutableStateOf(TravelTab.FOOD)
        private set
    /** 지금 채우려는 빈 칸(일정에서 칸을 누르면 정해진다). null이면 「담기」는 저장에만 넣는다. */
    var activeSlot by mutableStateOf<String?>(null)
        private set
    var result by mutableStateOf<TravelResult?>(null)
        private set
    var selectedPlace by mutableStateOf<Place?>(null)
    /** 방탈출을 하지 않는 이용자가 마지막 장소를 열어 보기로 한 경우(이 화면을 보는 동안만). */
    var spoilerOptIn by mutableStateOf(false)
        private set
    /** 방탈출에서 마지막 장소가 밝혀졌는지. 화면이 게임 상태를 읽어 넘겨 준다(게임 상태는 바꾸지 않는다). */
    var finalRevealed by mutableStateOf(false)
        private set
    val catalogStatus = "직접 준비한 장소 · 실시간 검색 아님"

    fun updateFinalRevealed(value: Boolean) { finalRevealed = value }
    /** 마지막 장소가 아직 비밀인지. */
    fun isLocked(stop: Int): Boolean = stop == GuideStops.all.lastIndex && !finalRevealed && !spoilerOptIn
    val visiblePlan: TripPlan get() = plan.visibleAtStops { !isLocked(it) }
    fun days(): List<DayPlan> = profile?.let { Itinerary.build(it, GuideStops.all, finalRevealed || spoilerOptIn) }.orEmpty()

    fun startEdit() { editing = true }
    fun cancelEdit() { if (profile != null) editing = false }
    fun saveProfile(p: TripProfile) {
        store.save(p); profile = p; editing = false
        day = day.coerceIn(1, p.length.days)
        if (tab !in Itinerary.tabs(p)) tab = Itinerary.tabs(p).first()
        refresh()
    }

    fun switchMode(m: GuideMode) { mode = m; if (m != GuideMode.SCHEDULE) activeSlot = null }
    fun selectDay(d: Int) {
        day = d; activeSlot = null
        days().getOrNull(d - 1)?.items?.filterIsInstance<PlanItem.Stop>()?.firstOrNull()?.let { select(it.stop.index) }
    }
    fun select(stop: Int) { selectedStop = stop; selectedPlace = null; refresh() }
    fun selectTab(t: TravelTab) { tab = t; selectedPlace = null; activeSlot = null; refresh() }
    fun optInSpoiler() { spoilerOptIn = true; refresh() }

    /** 일정의 빈 칸을 누르면 그 근처의 맛집·숙소를 연다. 다시 누르면 칸 선택을 푼다. */
    fun pickSlot(key: String, item: PlanItem) {
        if (activeSlot == key) { activeSlot = null; return }
        val (near, t) = when (item) {
            is PlanItem.Eat -> item.nearIndex to TravelTab.FOOD
            is PlanItem.Sleep -> item.nearIndex to TravelTab.STAY
            is PlanItem.Stop -> return
        }
        val p = profile ?: return
        tab = if (t in Itinerary.tabs(p)) t else TravelTab.FOOD
        selectedStop = near; selectedPlace = null; activeSlot = key
        refresh()
    }

    /** 「담기」: 채우려는 칸이 있으면 그 칸에, 없으면 저장에 넣는다. */
    fun put(place: Place) {
        if (isLocked(selectedStop)) return
        val saved = place.toSaved(selectedStop, tab)
        plan = activeSlot?.let { plan.assign(it, saved) } ?: plan.save(saved)
        activeSlot = null
        store.savePlan(plan)
    }
    fun unsave(id: String) { plan = plan.remove(id); store.savePlan(plan) }
    fun clearSlot(key: String) { plan = plan.clearSlot(key); store.savePlan(plan) }

    fun refresh() {
        if (profile == null) return
        val stop = GuideStops.all.getOrNull(selectedStop) ?: return
        result = if (isLocked(selectedStop)) null else repo.load(stop, tab)
    }
}
