package com.team.carescape.travel

/**
 * 2026-11-15 공식 안내에서 사실 정보만 확인해 수동으로 준비한 장소.
 * 외부 검색 응답·사진·리뷰를 복제하지 않는다. 개별 입구 좌표는 아직 검증하지 않았다.
 * 출처와 확인 한계: docs/curated-places.md
 */
object CuratedPlaces {
    private data class Entry(
        val id: String, val checkpoint: String, val tab: TravelTab, val name: String,
        val category: String, val address: String, val source: String, val url: String,
    )

    private const val PARKS = "https://www.suwonudc.co.kr/park/PageLink.do?link=forward%3A%2FPageContent.do&menuNo=010000&subMenuNo=010600&tempParam1=&thirdMenuNo=010602"
    private val entries = listOf(
        Entry("haenggung-parking", "suwon_01", TravelTab.PARKING, "화성행궁 주차장", "주차장",
            "경기도 수원시 팔달구 행궁로 18", "수원시 주차 안내",
            "https://www.suwon.go.kr/web/board/BD_board.view.do?bbsCd=1042&seq=20251008222237044"),
        Entry("hwahong-parking", "suwon_02", TravelTab.PARKING, "화홍문 공영주차장", "공영주차장",
            "경기도 수원시 장안구 팔달로 280", "수원도시공사 주차 안내", PARKS),
        Entry("manseok-parking-5", "suwon_03", TravelTab.PARKING, "만석공원 제5주차장", "공영주차장",
            "경기도 수원시 장안구 송정로 9", "수원도시공사 주차 안내", PARKS),
        Entry("gwanggyo-museum-parking", "suwon_04", TravelTab.PARKING, "수원광교박물관 주차장", "박물관 주차장",
            "경기도 수원시 영통구 광교로 182", "수원광교박물관 공식 안내",
            "https://ggmuseum.suwon.go.kr/comm/faq.do"),
        Entry("woncheon-parking", "suwon_05", TravelTab.PARKING, "광교호수공원 원천호수 주차장", "공영주차장",
            "경기도 수원시 영통구 광교호수로 75", "수원도시공사 주차 안내", PARKS),
        Entry("jinmi-chicken", "suwon_01", TravelTab.FOOD, "진미통닭", "통닭",
            "경기도 수원시 팔달구 정조로800번길 21, 2층", "수원시 2026년 가맹 목록",
            "https://www.suwon.go.kr/webcontent/ckeditor/2026/1/20/d6b6f241-0f13-4757-9be2-642fa14dd722.pdf"),
        Entry("yeonpo-galbi", "suwon_02", TravelTab.FOOD, "연포갈비", "갈비",
            "경기도 수원시 팔달구 정조로906번길 56-1", "한국관광공사 공식 관광 안내",
            "https://korean.visitkorea.or.kr/detail/rem_detail.do?cotid=71bb331c-0d15-4601-8ffa-ed4b1fad0df5"),
        Entry("siru-hyanggi", "suwon_03", TravelTab.FOOD, "시루향기", "한식",
            "경기도 수원시 장안구 만석로 198", "수원시 장안구 음식점 안내",
            "https://jangan.suwon.go.kr/sub_view.asp?menu_id=sub04020302"),
    )

    fun around(stop: GuideStop, tab: TravelTab): List<Place> = entries
        .filter { it.checkpoint == stop.checkpointId && it.tab == tab }
        .map { entry ->
            Place(
                name = entry.name, category = entry.category, address = entry.address,
                lat = null, lng = null, source = entry.source, link = entry.url,
                catalogId = "curated:${entry.id}",
                note = "입구 좌표는 미확인입니다. 길 안내는 ${stop.name} 대표 지점까지이며, 장소 입구는 주소로 확인하세요.",
                navigationName = stop.name, navigationLat = stop.lat, navigationLng = stop.lng,
                navigationLabel = "주변 관광지로 안내",
            )
        }
}
