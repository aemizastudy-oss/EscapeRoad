package com.example.roadescape.game

import com.example.roadescape.model.WaypointConfig

/** Public attraction representative points, not parking locations. All require field verification. */
object TourCatalog {
    const val PACKAGE_ID = "suwon_tour_preview"
    const val CITY_GUIDE_SOURCE = "https://www.suwon.go.kr/webcontent/ckeditor/2019/1/28/22c61a9a-2220-4abc-a683-ea4bd025f901.pdf"
    const val MUSEUM_SOURCE = "https://www.data.go.kr/data/15017323/standard.do"
    val checkpoints = listOf(
        WaypointConfig("suwon_01", "화성행궁", 37.281791, 127.013251),
        WaypointConfig("suwon_02", "화홍문", 37.287456, 127.017955),
        WaypointConfig("suwon_03", "만석공원·만석거", 37.301607, 127.001058),
        WaypointConfig("suwon_04", "수원광교박물관", 37.296351, 127.051421),
        WaypointConfig("suwon_05", "광교호수공원", 37.281651, 127.070650),
    )
    // The two park visit areas / final certification centres are not confirmed by field work.
    val provisionalCenterIds = setOf("suwon_03", "suwon_05")
    fun find(checkpointId: String) = checkpoints.firstOrNull { it.id == checkpointId }
}
