package com.team.carescape.game

import kotlinx.serialization.Serializable

/** 한 장소 안에서의 진행 단계. */
@Serializable
enum class StoryPhase {
    /** 다음 장소로 이동 중. 실제 도착은 외부 위치·안전 정책을 통과한 뒤 확인한다. */
    EN_ROUTE,
    /** 도착 화면. */
    ARRIVED,
    /** 차에서 내려 둘러보는 중. */
    EXPLORING,
    /** 단서·재료 공개 화면. */
    REVEALED,
    /** 모은 단서로 마지막 장소가 드러나는 화면. */
    FINAL_REVEAL,
    /** 엔딩. */
    ENDED,
}

data class StoryState(
    val episode: StoryEpisode,
    val chapterIndex: Int = 0,
    val phase: StoryPhase = StoryPhase.EN_ROUTE,
    val items: List<String> = emptyList(),
    val clues: List<String> = emptyList(),
    /** 안내를 시작한 뒤 차가 실제로 움직였는지. 출발지에서 그대로 서 있는 것을 도착으로 착각하지 않게 한다. */
    val movedSinceGuidance: Boolean = false,
) {
    val chapter: StoryChapter get() = episode.chapters[chapterIndex]
    val isFinalChapter: Boolean get() = chapterIndex == episode.finalChapterIndex

    /** 마지막 장소는 FINAL_REVEAL을 지나야 이름이 드러난다. 그 전에는 코스 카드에서도 '???'로 둔다. */
    fun isRevealed(index: Int): Boolean = index < episode.finalChapterIndex || chapterIndex == episode.finalChapterIndex
}

/** 스토리형 테마 진행. 퍼즐 없이 도착 → 둘러보기 → 단서 → 다음 장소 순서만 관리하는 순수 로직. */
object StoryEngine {

    fun start(episode: StoryEpisode): StoryState = StoryState(episode)

    /**
     * 차량 정차 상태가 바뀔 때마다 부른다.
     * 기존 시연의 이동/정차 흐름만 처리한다. LIVE 도착 인증에는 쓰지 않는다.
     * 모르는 상태(null)는 이동이나 도착의 증거로 사용하지 않는다.
     */
    fun onParked(state: StoryState, parked: Boolean?): StoryState {
        if (state.phase != StoryPhase.EN_ROUTE) return state
        return when {
            parked == false -> state.copy(movedSinceGuidance = true)
            parked == true && state.movedSinceGuidance -> verifiedArrival(state)
            else -> state
        }
    }

    /** 호출자는 최신 위치·현재 모드·P·정지·CarUx·foreground·선행 챕터를 먼저 검증한다. */
    fun verifiedArrival(state: StoryState): StoryState =
        if (state.phase == StoryPhase.EN_ROUTE) state.copy(phase = StoryPhase.ARRIVED, movedSinceGuidance = false) else state

    /** 시연용: 실제로 달리지 않고 도착한 것으로 한다. 정차 중일 때만. */
    fun demoArrive(state: StoryState, parked: Boolean?): StoryState =
        if (state.phase == StoryPhase.EN_ROUTE && parked == true) state.copy(phase = StoryPhase.ARRIVED, movedSinceGuidance = false) else state

    /** 모든 경유지는 관광 시작 저장 후 EXPLORING으로 간다. 마지막 장소도 15분 정책을 적용한다. */
    fun leaveCar(state: StoryState): StoryState {
        if (state.phase != StoryPhase.ARRIVED) return state
        return state.copy(phase = StoryPhase.EXPLORING)
    }

    /** 호출자가 관광 시간·최신 위치·안전 조건을 검증한 뒤 호출한다. */
    fun returnToCar(state: StoryState): StoryState {
        if (state.phase != StoryPhase.EXPLORING) return state
        val c = state.chapter
        if (c.explore == null) return state.copy(phase = StoryPhase.ENDED)
        return state.copy(
            phase = StoryPhase.REVEALED,
            items = state.items + listOfNotNull(c.item),
            clues = state.clues + listOfNotNull(c.clue),
        )
    }

    /** 단서 화면의 다음 버튼. 다음 행선지가 숨겨져 있으면 마지막 장소 공개 화면으로 간다. */
    fun next(state: StoryState): StoryState = when (state.phase) {
        StoryPhase.REVEALED ->
            if (state.chapterIndex + 1 == state.episode.finalChapterIndex) state.copy(phase = StoryPhase.FINAL_REVEAL)
            else state.copy(chapterIndex = state.chapterIndex + 1, phase = StoryPhase.EN_ROUTE, movedSinceGuidance = false)
        StoryPhase.FINAL_REVEAL ->
            state.copy(chapterIndex = state.episode.finalChapterIndex, phase = StoryPhase.EN_ROUTE, movedSinceGuidance = false)
        else -> state
    }
}
