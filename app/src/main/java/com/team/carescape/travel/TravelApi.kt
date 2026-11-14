package com.team.carescape.travel

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

/** 추천 카드 한 장. 별점·리뷰는 다루지 않는다. */
data class Place(
    val name: String,
    val category: String,
    val address: String,
    val lat: Double?,
    val lng: Double?,
    val phone: String? = null,
    val link: String? = null,
    /** 경유지에서의 거리(m). API가 주지 않으면 좌표로 계산한다. */
    val distanceM: Int? = null,
    /** 화면에 밝힐 출처. */
    val source: String,
)

sealed interface TravelResult {
    data class Ok(val places: List<Place>, val sources: Set<String>, val missing: List<String> = emptyList()) : TravelResult
    /** API 키가 아직 없다. [missing]은 local.properties에 넣을 이름. */
    data class NotConfigured(val missing: List<String>) : TravelResult
    data class Failed(val message: String) : TravelResult
}

/** 키 이름은 local.properties와 BuildConfig에 같은 이름으로 쓴다(저장소에는 올리지 않는다). */
data class TravelKeys(val naverClientId: String = "", val naverClientSecret: String = "", val tourApiKey: String = "") {
    val naverReady get() = naverClientId.isNotBlank() && naverClientSecret.isNotBlank()
    val tourReady get() = tourApiKey.isNotBlank()
}

interface HttpGet { suspend fun get(url: String, headers: Map<String, String> = emptyMap()): String }

/** 앱 기본 HTTP. 응답 8초 제한, 위치나 개인정보는 보내지 않는다(경유지 이름·좌표만). */
object UrlHttpGet : HttpGet {
    override suspend fun get(url: String, headers: Map<String, String>): String = withContext(Dispatchers.IO) {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 8_000; c.readTimeout = 8_000
            headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
            val code = c.responseCode
            val body = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw IOException("HTTP $code")
            body
        } finally { c.disconnect() }
    }
}

private val json = Json { ignoreUnknownKeys = true }
private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
private fun JsonElement?.str(): String? = (this as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

/** 경위도 두 점 사이 거리(m). */
fun distanceMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Int {
    val r = 6_371_000.0
    val dLat = Math.toRadians(lat2 - lat1); val dLng = Math.toRadians(lng2 - lng1)
    val a = Math.sin(dLat / 2).let { it * it } +
        Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLng / 2).let { it * it }
    return (2 * r * Math.asin(Math.sqrt(a))).toInt()
}

/**
 * 네이버 검색 API · 지역 검색(openapi.naver.com/v1/search/local.json). 검색어 기반, 한 번에 최대 5개.
 * 상업적 이용·출처 표시 조건은 네이버 개발자센터 약관으로 확인해야 한다.
 */
class NaverLocalClient(private val keys: TravelKeys, private val http: HttpGet = UrlHttpGet) {
    suspend fun search(query: String): List<Place> {
        val url = "https://openapi.naver.com/v1/search/local.json?query=${enc(query)}&display=5&start=1&sort=random"
        return parse(http.get(url, mapOf("X-Naver-Client-Id" to keys.naverClientId, "X-Naver-Client-Secret" to keys.naverClientSecret)))
    }

    companion object {
        const val SOURCE = "네이버 지역 검색"
        private val tags = Regex("<[^>]+>")

        /** mapx·mapy는 경위도에 10^7을 곱한 정수 문자열이다. */
        fun parse(body: String): List<Place> {
            val items = json.parseToJsonElement(body).jsonObject["items"] as? JsonArray ?: return emptyList()
            return items.mapNotNull { e ->
                val o = e as? JsonObject ?: return@mapNotNull null
                val name = o["title"].str()?.replace(tags, "")?.let(::unescape) ?: return@mapNotNull null
                fun coord(k: String): Double? = o[k].str()?.toDoubleOrNull()?.let { if (it > 1_000) it / 1e7 else it }
                Place(
                    name = name,
                    category = o["category"].str().orEmpty(),
                    address = o["roadAddress"].str() ?: o["address"].str().orEmpty(),
                    lat = coord("mapy"), lng = coord("mapx"),
                    phone = o["telephone"].str(), link = o["link"].str(), source = SOURCE,
                )
            }
        }

        private fun unescape(s: String) = s.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'")
    }
}

/**
 * 한국관광공사 TourAPI 4.0 국문(apis.data.go.kr/B551011/KorService2) · 위치기반 목록.
 * 출처 ⓒ한국관광공사 표시. 사진은 공공누리 유형이 섞여 있어 쓰지 않는다.
 */
class TourApiClient(private val keys: TravelKeys, private val http: HttpGet = UrlHttpGet) {
    suspend fun nearby(lat: Double, lng: Double, contentTypeId: Int, radius: Int = 3_000, rows: Int = 10): List<Place> {
        val url = "https://apis.data.go.kr/B551011/KorService2/locationBasedList2" +
            "?serviceKey=${enc(keys.tourApiKey)}&MobileOS=AND&MobileApp=EscapeRoad&_type=json" +
            "&mapX=$lng&mapY=$lat&radius=$radius&contentTypeId=$contentTypeId&arrange=E&numOfRows=$rows&pageNo=1"
        return parse(http.get(url))
    }

    companion object {
        const val SOURCE = "ⓒ한국관광공사"
        const val SIGHT = 12; const val CULTURE = 14; const val FESTIVAL = 15; const val LEISURE = 28; const val STAY = 32; const val FOOD = 39

        private val typeLabel = mapOf(SIGHT to "관광지", CULTURE to "문화시설", FESTIVAL to "행사", LEISURE to "레포츠·체험", STAY to "숙박", FOOD to "음식점")

        /** item은 결과가 하나면 객체, 여럿이면 배열, 없으면 빈 문자열로 올 수 있다. */
        fun parse(body: String): List<Place> {
            val root = json.parseToJsonElement(body).jsonObject
            val response = root["response"] as? JsonObject ?: return emptyList()
            val header = response["header"] as? JsonObject
            val code = header?.get("resultCode").str()
            if (code != null && code != "0000") throw IOException("TourAPI ${header?.get("resultMsg").str() ?: code}")
            val items = (response["body"] as? JsonObject)?.get("items") as? JsonObject ?: return emptyList()
            val list = when (val item = items["item"]) {
                is JsonArray -> item.toList()
                is JsonObject -> listOf(item)
                else -> emptyList()
            }
            return list.mapNotNull { e ->
                val o = e as? JsonObject ?: return@mapNotNull null
                val name = o["title"].str() ?: return@mapNotNull null
                Place(
                    name = name,
                    category = o["contenttypeid"].str()?.toIntOrNull()?.let { typeLabel[it] }.orEmpty(),
                    address = listOfNotNull(o["addr1"].str(), o["addr2"].str()).joinToString(" "),
                    lat = o["mapy"].str()?.toDoubleOrNull(), lng = o["mapx"].str()?.toDoubleOrNull(),
                    phone = o["tel"].str(),
                    distanceM = o["dist"].str()?.toDoubleOrNull()?.toInt(),
                    source = SOURCE,
                )
            }
        }
    }
}

/**
 * 탭마다 어떤 출처로 무엇을 찾을지 정하고, 결과를 합쳐 거리순으로 돌려준다.
 * 결과는 이 화면을 보는 동안만 메모리에 둔다(파일로 저장하지 않는다).
 */
class TravelRepository(
    private val keys: TravelKeys,
    private val naver: NaverLocalClient = NaverLocalClient(keys),
    private val tour: TourApiClient = TourApiClient(keys),
) {
    private val cache = mutableMapOf<String, TravelResult.Ok>()

    /** 탭별 출처: 네이버 검색어 또는 TourAPI 분류. */
    data class Plan(val naverQueries: List<String>, val tourTypes: List<Int>)

    fun planFor(stop: GuideStop, tab: TravelTab, profile: TripProfile): Plan {
        val n = stop.searchName
        val family = profile.companion == TravelParty.FAMILY
        return when (tab) {
            TravelTab.PARKING -> Plan(listOf("$n 주차장"), emptyList())
            TravelTab.FOOD -> Plan(listOf(if (family) "$n 가족 맛집" else "$n 맛집"), emptyList())
            TravelTab.CAFE -> Plan(listOf("$n 카페"), emptyList())
            TravelTab.SIGHT -> Plan(emptyList(), listOf(TourApiClient.SIGHT, TourApiClient.CULTURE))
            TravelTab.ACTIVITY -> Plan(listOf(if (family) "$n 아이와 체험" else "$n 체험"), listOf(TourApiClient.LEISURE))
            TravelTab.STAY -> Plan(listOf("$n 숙소"), listOf(TourApiClient.STAY))
            TravelTab.NIGHT -> Plan(listOf("$n 야경", "$n 저녁"), emptyList())
        }
    }

    suspend fun load(stop: GuideStop, tab: TravelTab, profile: TripProfile, refresh: Boolean = false): TravelResult {
        val plan = planFor(stop, tab, profile)
        val missing = buildList {
            if (plan.naverQueries.isNotEmpty() && !keys.naverReady) addAll(listOf("NAVER_CLIENT_ID", "NAVER_CLIENT_SECRET"))
            if (plan.tourTypes.isNotEmpty() && !keys.tourReady) add("TOUR_API_KEY")
        }
        val usable = Plan(if (keys.naverReady) plan.naverQueries else emptyList(), if (keys.tourReady) plan.tourTypes else emptyList())
        if (usable.naverQueries.isEmpty() && usable.tourTypes.isEmpty()) return TravelResult.NotConfigured(missing)
        val key = "${stop.checkpointId}:$tab:${plan.hashCode()}"
        if (!refresh) cache[key]?.let { return it }
        return try {
            val places = usable.naverQueries.flatMap { naver.search(it) } +
                usable.tourTypes.flatMap { tour.nearby(stop.lat, stop.lng, it) }
            val withDistance = places
                .map { p -> if (p.distanceM == null && p.lat != null && p.lng != null) p.copy(distanceM = distanceMeters(stop.lat, stop.lng, p.lat, p.lng)) else p }
                .distinctBy { it.name.replace(" ", "") }
                .sortedBy { it.distanceM ?: Int.MAX_VALUE }
            TravelResult.Ok(withDistance, withDistance.map { it.source }.toSet(), missing).also { cache[key] = it }
        } catch (e: Exception) {
            TravelResult.Failed(e.message ?: "정보를 불러오지 못했어요.")
        }
    }
}

/**
 * 화면 배치 확인용 예시 장소(debug 전용). 실제 장소가 아니며 출처에 그렇게 밝힌다.
 * 경유지 주변에 고정된 위치로 흩어 놓는다.
 */
object SamplePlaces {
    const val SOURCE = "시연용 예시 · 실제 장소 아님"
    fun around(stop: GuideStop, tab: TravelTab): TravelResult.Ok {
        val offsets = listOf(0.0016 to 0.0011, -0.0012 to 0.0019, 0.0024 to -0.0015, -0.0021 to -0.0009, 0.0006 to 0.0031)
        val places = offsets.mapIndexed { i, (dLat, dLng) ->
            val lat = stop.lat + dLat; val lng = stop.lng + dLng
            Place("예시 ${tab.label} ${i + 1}", tab.label, "${stop.name} 근처(예시 주소)", lat, lng,
                distanceM = distanceMeters(stop.lat, stop.lng, lat, lng), source = SOURCE)
        }.sortedBy { it.distanceM }
        return TravelResult.Ok(places, setOf(SOURCE))
    }
}
