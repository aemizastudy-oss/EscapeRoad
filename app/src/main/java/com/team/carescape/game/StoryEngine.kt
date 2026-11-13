package com.team.carescape.game

/** 한 장소 안에서의 진행 단계. */
enum class StoryPhase {
    /** 다음 장소로 이동 중. 정차가 확인되면 도착으로 본다. */
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
     * 이동 중 단계에서 움직였다가(정차 아님) 다시 정차가 확인되면 도착으로 본다. 모르는 상태(null)는 정차로 보지 않는다.
     */
    fun onParked(state: StoryState, parked: Boolean?): StoryState {
        if (state.phase != StoryPhase.EN_ROUTE) return state
        return when {
            parked != true -> state.copy(movedSinceGuidance = true)
            state.movedSinceGuidance -> state.copy(phase = StoryPhase.ARRIVED, movedSinceGuidance = false)
            else -> state
        }
    }

    /** 시연용: 실제로 달리지 않고 도착한 것으로 한다. 정차 중일 때만. */
    fun demoArrive(state: StoryState, parked: Boolean?): StoryState =
        if (state.phase == StoryPhase.EN_ROUTE && parked == true) state.copy(phase = StoryPhase.ARRIVED, movedSinceGuidance = false) else state

    /** 도착 화면의 버튼. 마지막 장소는 둘러보기 없이 엔딩으로 간다. */
    fun leaveCar(state: StoryState): StoryState {
        if (state.phase != StoryPhase.ARRIVED) return state
        return if (state.chapter.explore == null) state.copy(phase = StoryPhase.ENDED)
        else state.copy(phase = StoryPhase.EXPLORING)
    }

    /** 둘러보고 차로 돌아왔을 때: 이 장소의 재료와 단서를 얻는다. */
    fun returnToCar(state: StoryState): StoryState {
        if (state.phase != StoryPhase.EXPLORING) return state
        val c = state.chapter
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
