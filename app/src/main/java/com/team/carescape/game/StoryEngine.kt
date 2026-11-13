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
    /** 관광 조건 확인 후 native 자물쇠를 푸는 단계(flow2). */
    PUZZLE,
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
    val flowVersion: Int = 1,
    val legacyCompletedCount: Int = 0,
    val puzzleProgress: List<StoryPuzzleProgress> = emptyList(),
) {
    val chapter: StoryChapter get() = episode.chapters[chapterIndex]
    val isFinalChapter: Boolean get() = chapterIndex == episode.finalChapterIndex
    val currentPuzzle: StoryPuzzleDefinition? get() =
        if (flowVersion == StoryCatalog.CURRENT_FLOW_VERSION) chapter.puzzle else null
    val puzzleProgressForCurrent: StoryPuzzleProgress? get() =
        puzzleProgress.firstOrNull { it.checkpointId == TourCatalog.checkpoints.getOrNull(chapterIndex)?.id }

    /** 마지막 장소는 FINAL_REVEAL을 지나야 이름이 드러난다. 그 전에는 코스 카드에서도 '???'로 둔다. */
    fun isRevealed(index: Int): Boolean = index < episode.finalChapterIndex || chapterIndex == episode.finalChapterIndex
}

/** 순수 진행 로직. 호출자는 관광 시간·현재 위치·안전 조건과 저장 성공을 확인한다. */
object StoryEngine {

    fun start(episode: StoryEpisode, flowVersion: Int = 1): StoryState =
        StoryState(episode, flowVersion = flowVersion)

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
        if (state.flowVersion == StoryCatalog.CURRENT_FLOW_VERSION) {
            if (state.currentPuzzle == null) return state
            val id = TourCatalog.checkpoints.getOrNull(state.chapterIndex)?.id ?: return state
            if (state.puzzleProgressForCurrent?.solved == true) return state
            return state.copy(phase = StoryPhase.PUZZLE,
                puzzleProgress = if (state.puzzleProgressForCurrent == null)
                    state.puzzleProgress + StoryPuzzleProgress(id) else state.puzzleProgress)
        }
        if (state.flowVersion != StoryCatalog.LEGACY_FLOW_VERSION) return state
        val c = state.chapter
        if (c.explore == null) return state.copy(phase = StoryPhase.ENDED)
        return state.copy(
            phase = StoryPhase.REVEALED,
            items = state.items + listOfNotNull(c.item),
            clues = state.clues + listOfNotNull(c.clue),
        )
    }

    fun showPuzzleHint(state: StoryState): StoryState {
        if (state.flowVersion != StoryCatalog.CURRENT_FLOW_VERSION || state.phase != StoryPhase.PUZZLE) return state
        val puzzle = state.currentPuzzle ?: return state
        val progress = state.puzzleProgressForCurrent ?: return state
        if (progress.solved || progress.hintsShown >= puzzle.hints.size) return state
        return state.withPuzzleProgress(progress.copy(hintsShown = progress.hintsShown + 1))
    }

    fun submitPuzzle(state: StoryState, input: StoryPuzzleInput): StorySubmission {
        if (state.flowVersion != StoryCatalog.CURRENT_FLOW_VERSION || state.phase != StoryPhase.PUZZLE) {
            return StorySubmission(state, StoryPuzzleResult.LOCKED)
        }
        val puzzle = state.currentPuzzle ?: return StorySubmission(state, StoryPuzzleResult.LOCKED)
        val progress = state.puzzleProgressForCurrent ?: return StorySubmission(state, StoryPuzzleResult.LOCKED)
        if (progress.solved) return StorySubmission(state, StoryPuzzleResult.LOCKED)
        val result = StoryPuzzles.evaluate(puzzle, input)
        val next = when (result) {
            StoryPuzzleResult.CORRECT -> state.withPuzzleProgress(progress.copy(solved = true)).copy(
                phase = if (state.isFinalChapter) StoryPhase.ENDED else StoryPhase.REVEALED,
                items = state.items + listOfNotNull(state.chapter.item),
                clues = state.clues + listOfNotNull(state.chapter.clue),
            )
            StoryPuzzleResult.WRONG -> state.withPuzzleProgress(progress.copy(
                wrongAttempts = if (progress.wrongAttempts < Int.MAX_VALUE) progress.wrongAttempts + 1 else Int.MAX_VALUE,
            ))
            else -> state
        }
        return StorySubmission(next, result)
    }

    private fun StoryState.withPuzzleProgress(value: StoryPuzzleProgress): StoryState = copy(
        puzzleProgress = puzzleProgress.map { if (it.checkpointId == value.checkpointId) value else it },
    )

    /** 단서 화면의 다음 버튼. 다음 행선지가 숨겨져 있으면 마지막 장소 공개 화면으로 간다. */
    fun next(state: StoryState): StoryState {
        if (state.flowVersion == StoryCatalog.CURRENT_FLOW_VERSION &&
            state.phase in listOf(StoryPhase.REVEALED, StoryPhase.FINAL_REVEAL) &&
            state.chapterIndex >= state.legacyCompletedCount && state.puzzleProgressForCurrent?.solved != true
        ) return state
        return when (state.phase) {
        StoryPhase.REVEALED ->
            if (state.chapterIndex + 1 == state.episode.finalChapterIndex) state.copy(phase = StoryPhase.FINAL_REVEAL)
            else state.copy(chapterIndex = state.chapterIndex + 1, phase = StoryPhase.EN_ROUTE, movedSinceGuidance = false)
        StoryPhase.FINAL_REVEAL ->
            state.copy(chapterIndex = state.episode.finalChapterIndex, phase = StoryPhase.EN_ROUTE, movedSinceGuidance = false)
        else -> state
        }
    }
}
