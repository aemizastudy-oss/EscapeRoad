package com.team.carescape.game

/**
 * 탐색 장면의 조사할 곳. 좌표는 장면 배경의 1280×720 설계 좌표다.
 *
 * - [requires]: 이 id(퍼즐 단계 또는 다른 조사할 곳)가 끝나야 장면에 나타난다.
 * - [lockStep]: 누르면 이 퍼즐 단계(자물쇠)를 연다.
 * - [record]: 살펴보면 '찾음'으로 기록한다. 소지품·문서·다른 조사할 곳이 이 id로 열린다.
 * - [prop]: 아직 찾지 않았을 때 그 자리에 그려 둘 물건 그림(ItemArt id).
 */
data class Hotspot(
    val id: String,
    val name: String,
    val x: Float,
    val y: Float,
    val w: Float,
    val h: Float,
    val look: String,
    val requires: String? = null,
    val lockStep: String? = null,
    val record: Boolean = false,
    val doneLook: String? = null,
    val prop: String? = null,
    val speaker: String? = null,
    /** 찾은 뒤에도 소품 그림을 남긴다(주워 가는 물건이 아닌 풍경). */
    val keepProp: Boolean = false,
    /** [lockStep]을 푼 다음 같은 자리에서 이어서 여는 두 번째 자물쇠. */
    val thenLock: String? = null,
    /** 첫 자물쇠를 풀고 두 번째가 남았을 때의 살펴보기 글. */
    val midLook: String? = null,
) {
    val locks: List<String> get() = listOfNotNull(lockStep, thenLock)
}

/** 장소 하나를 직접 뒤지는 장면. 퍼즐 단계는 [StoryChapter.steps]에 있고, 모두 풀면 장소가 끝난다. */
data class ExploreScene(val bg: SceneBg, val intro: String, val hotspots: List<Hotspot>)
