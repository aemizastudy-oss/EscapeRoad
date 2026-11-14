package com.team.carescape.travel

/** 앱에 준비한 장소 카드. 네트워크 검색이나 영업 여부 조회 결과가 아니다. */
data class Place(
    val name: String,
    val category: String,
    val address: String,
    val lat: Double?,
    val lng: Double?,
    val phone: String? = null,
    val link: String? = null,
    val distanceM: Int? = null,
    val source: String,
    val catalogId: String? = null,
    val note: String = "",
    val navigationName: String = name,
    val navigationLat: Double? = lat,
    val navigationLng: Double? = lng,
    val navigationLabel: String = "길 안내",
)

sealed interface TravelResult {
    data class Ok(val places: List<Place>) : TravelResult
}

/** 직선거리이며 도로 거리·도보 경로를 뜻하지 않는다. */
fun distanceMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Int {
    val r = 6_371_000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLng = Math.toRadians(lng2 - lng1)
    val a = Math.sin(dLat / 2).let { it * it } +
        Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLng / 2).let { it * it }
    return (2 * r * Math.asin(Math.sqrt(a.coerceIn(0.0, 1.0)))).toInt()
}

/** 고정 목록만 읽는다. 인증값·HTTP 클라이언트·원격 응답 캐시는 사용하지 않는다. */
class TravelRepository {
    fun load(stop: GuideStop, tab: TravelTab): TravelResult.Ok = TravelResult.Ok(CuratedPlaces.around(stop, tab))
}
