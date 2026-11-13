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
    /** 관광 조건 확인 후 native 자물쇠를 푸는 단계(flow2 이상). */
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
    val puzzleProgressForCurrent: StoryPuzzleProgress? get() =
        puzzleProgress.firstOrNull { it.checkpointId == TourCatalog.checkpoints.getOrNull(chapterIndex)?.id }
    val steps: List<StoryPuzzleDefinition> get() =
        if (StoryCatalog.usesPuzzles(flowVersion)) chapter.puzzleSteps else emptyList()
    /** 지금 풀 단계. 이 장소를 다 풀었으면 마지막 단계(재제출은 LOCKED). */
    val currentPuzzle: StoryPuzzleDefinition? get() {
        val all = steps
        if (all.isEmpty()) return null
        val index = puzzleProgressForCurrent?.takeUnless { it.solved }?.stepIndex ?: 0
        return all.getOrNull(index) ?: all.last()
    }
    val currentStepIndex: Int get() = puzzleProgressForCurrent?.let { if (it.solved) steps.size else it.stepIndex } ?: 0

    /** 시귀 각성도(0 이상, 화면에는 10까지). */
    val awakening: Int get() = puzzleProgress.sumOf { it.awakening }
    val hintsUsed: Int get() = puzzleProgress.sumOf { maxOf(it.hintsUsed, it.hintsShown) }

    /** 마지막 장소는 FINAL_REVEAL을 지나야 이름이 드러난다. 그 전에는 코스 카드에서도 '???'로 둔다. */
    fun isRevealed(index: Int): Boolean = index < episode.finalChapterIndex || chapterIndex == episode.finalChapterIndex

    /** 단계를 이미 풀었는지(진상 추리는 답을 내고 넘어갔으면 푼 것으로 본다). */
    fun isStepDone(stepId: String): Boolean {
        val chapterIdx = episode.chapters.indexOfFirst { c -> c.puzzleSteps.any { it.id == stepId } }
        if (chapterIdx < 0) return false
        val index = episode.chapters[chapterIdx].puzzleSteps.indexOfFirst { it.id == stepId }
        val id = TourCatalog.checkpoints.getOrNull(chapterIdx)?.id ?: return false
        val progress = puzzleProgress.firstOrNull { it.checkpointId == id } ?: return false
        return progress.solved || progress.stepIndex > index
    }

    /** 사건 기록부에 보이는 문서. 그 장소에서 차로 돌아와 퍼즐을 시작한 뒤부터 열린다. */
    val visibleDocuments: List<CaseDocument> get() = episode.documents.filter { doc ->
        val reached = doc.chapterIndex < chapterIndex ||
            (doc.chapterIndex == chapterIndex && phase.ordinal >= StoryPhase.PUZZLE.ordinal)
        reached && (doc.afterStep == null || isStepDone(doc.afterStep))
    }
    val visiblePeople: List<CasePerson> get() = episode.people.filter { p ->
        p.listed && (p.chapterIndex < chapterIndex || (p.chapterIndex == chapterIndex && phase.ordinal >= StoryPhase.ARRIVED.ordinal))
    }

    /** 지금 가진 소지품(얻은 순서). */
    val inventory: List<CaseItem> get() = episode.caseItems.filter { isStepDone(it.fromStep) }
    fun isItemUsed(item: CaseItem): Boolean = item.usedBy != null && isStepDone(item.usedBy)

    /** 인물 칸에 지금 보이는 기록(기본 설명 + 풀어서 얻은 기록 + 진상). */
    fun personNotes(p: CasePerson): List<String> =
        listOf(p.note) + p.notes.filter { isStepDone(it.afterStep) }.map { it.text } +
            listOfNotNull(p.truthNote?.takeIf { truthSolved })
    fun personRelation(p: CasePerson): String? = if (truthSolved && p.truthRelation != null) p.truthRelation else p.relation

    val truthSteps: List<StoryPuzzleDefinition> get() = episode.chapters.flatMap { it.puzzleSteps }.filter { it.verdict }
    /** 진상 추리를 모두 답했고 하나도 틀리지 않았는지. */
    val truthSolved: Boolean get() = truthSteps.isNotEmpty() && truthSteps.all { isStepDone(it.id) } &&
        puzzleProgress.none { it.verdictMisses.isNotEmpty() }

    val endingKind: EndingKind? get() = when {
        episode.endings.isEmpty() || phase != StoryPhase.ENDED -> null
        awakening >= SiyeokCase.BAD_THRESHOLD -> EndingKind.BAD
        truthSolved -> EndingKind.TRUE
        else -> EndingKind.NORMAL
    }
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
        if (StoryCatalog.usesPuzzles(state.flowVersion)) {
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
        if (!StoryCatalog.usesPuzzles(state.flowVersion) || state.phase != StoryPhase.PUZZLE) return state
        val puzzle = state.currentPuzzle ?: return state
        val progress = state.puzzleProgressForCurrent ?: return state
        if (progress.solved || progress.hintsShown >= puzzle.hints.size) return state
        val cost = if (StoryCatalog.usesAwakening(state.flowVersion)) StoryPuzzles.hintCost(progress.hintsShown) else 0
        return state.withPuzzleProgress(progress.copy(
            hintsShown = progress.hintsShown + 1,
            hintsUsed = if (StoryCatalog.usesAwakening(state.flowVersion)) progress.hintsUsed + 1 else progress.hintsUsed,
            awakening = progress.awakening + cost,
        ))
    }

    fun submitPuzzle(state: StoryState, input: StoryPuzzleInput): StorySubmission {
        if (!StoryCatalog.usesPuzzles(state.flowVersion) || state.phase != StoryPhase.PUZZLE) {
            return StorySubmission(state, StoryPuzzleResult.LOCKED)
        }
        val puzzle = state.currentPuzzle ?: return StorySubmission(state, StoryPuzzleResult.LOCKED)
        val progress = state.puzzleProgressForCurrent ?: return StorySubmission(state, StoryPuzzleResult.LOCKED)
        if (progress.solved) return StorySubmission(state, StoryPuzzleResult.LOCKED)
        val result = StoryPuzzles.evaluate(puzzle, input)
        return when {
            result == StoryPuzzleResult.CORRECT || (result == StoryPuzzleResult.WRONG && puzzle.verdict) -> {
                val missed = if (result == StoryPuzzleResult.WRONG) progress.verdictMisses + puzzle.id else progress.verdictMisses
                val nextIndex = progress.stepIndex + 1
                val done = nextIndex >= state.steps.size
                // flow2 영수증은 힌트 수를 그대로 남긴다(저장 기록의 단조 증가 검증).
                val hints = if (StoryCatalog.usesAwakening(state.flowVersion) && !done) 0 else progress.hintsShown
                val advanced = progress.copy(stepIndex = nextIndex, hintsShown = hints, solved = done, verdictMisses = missed)
                val next = state.withPuzzleProgress(advanced).let {
                    if (!done) it else it.copy(
                        phase = if (state.isFinalChapter) StoryPhase.ENDED else StoryPhase.REVEALED,
                        items = state.items + listOfNotNull(state.chapter.item),
                        clues = state.clues + listOfNotNull(state.chapter.clue),
                    )
                }
                StorySubmission(next, StoryPuzzleResult.CORRECT, if (result == StoryPuzzleResult.WRONG) null else puzzle.success)
            }
            result == StoryPuzzleResult.WRONG -> {
                val note = if (StoryCatalog.usesAwakening(state.flowVersion))
                    puzzle.feedback(StoryPuzzles.answerKey(input)) ?: puzzle.wrongDefault?.let { WrongNote(it) } else null
                StorySubmission(state.withPuzzleProgress(progress.copy(
                    wrongAttempts = if (progress.wrongAttempts < Int.MAX_VALUE) progress.wrongAttempts + 1 else Int.MAX_VALUE,
                    awakening = progress.awakening + (note?.penalty ?: 0),
                )), StoryPuzzleResult.WRONG, note?.message)
            }
            else -> StorySubmission(state, result)
        }
    }

    private fun StoryState.withPuzzleProgress(value: StoryPuzzleProgress): StoryState = copy(
        puzzleProgress = puzzleProgress.map { if (it.checkpointId == value.checkpointId) value else it },
    )

    /** 단서 화면의 다음 버튼. 다음 행선지가 숨겨져 있으면 마지막 장소 공개 화면으로 간다. */
    fun next(state: StoryState): StoryState {
        if (StoryCatalog.usesPuzzles(state.flowVersion) &&
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
