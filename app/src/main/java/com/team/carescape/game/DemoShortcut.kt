package com.team.carescape.game

/** 시연(debug·MOCK) 바로가기 목표. */
sealed interface DemoTarget {
    val label: String
    /** n번째 장소에 막 도착한 상태(도착 장면부터). */
    data class Arrive(val chapter: Int) : DemoTarget { override val label get() = "${chapter + 1} 도착" }
    /** n번째 장소의 탐색 장면(퍼즐)을 아직 손대지 않은 상태. */
    data class Scene(val chapter: Int) : DemoTarget { override val label get() = "${chapter + 1} 장면" }
    /** 모은 단서로 마지막 장소가 드러나는 화면. */
    data object FinalReveal : DemoTarget { override val label get() = "마지막 공개" }
    /** 엔딩. TRUE는 모두 정답, NORMAL은 진상 추리 하나를 틀림, BAD는 주변 살피기를 많이 써서 각성도 8 이상. */
    data class Ending(val kind: EndingKind) : DemoTarget { override val label get() = kind.label }
}

/**
 * 시연용으로 앞부분을 정답으로 자동 진행한 상태를 만든다. 실제 엔진 함수만 써서 진행하므로
 * 결과는 정상 플레이로 닿을 수 있는 상태다(저장 검증도 그대로 통과해야 한다).
 */
object DemoShortcut {
    fun answer(lock: StoryLockDefinition): StoryPuzzleInput = when (lock) {
        is StoryLockDefinition.Numeric -> StoryPuzzleInput.Numeric(lock.answer)
        is StoryLockDefinition.Directions -> StoryPuzzleInput.Directions(lock.answer)
        is StoryLockDefinition.Device -> StoryPuzzleInput.Device(lock.channel, lock.level)
        is StoryLockDefinition.Choice -> StoryPuzzleInput.Choice(lock.answer)
        is StoryLockDefinition.Order -> StoryPuzzleInput.Order(lock.answer)
        is StoryLockDefinition.CarSequence -> StoryPuzzleInput.CarSequence(lock.answer)
        is StoryLockDefinition.SymbolDial -> StoryPuzzleInput.Dial(lock.answer)
        is StoryLockDefinition.UseItem -> StoryPuzzleInput.UseItem(lock.answer)
        is StoryLockDefinition.Sequence -> StoryPuzzleInput.Sequence(lock.answer)
        is StoryLockDefinition.InteriorLightCount -> StoryPuzzleInput.InteriorLightCount(lock.requiredLights)
    }

    private fun input(step: StoryPuzzleDefinition, missVerdict: Boolean): StoryPuzzleInput {
        val lock = step.lock
        if (missVerdict && step.verdict && lock is StoryLockDefinition.Choice) {
            return StoryPuzzleInput.Choice(lock.options.first { it.id != lock.answer }.id)
        }
        return answer(lock)
    }

    /** 장소 하나를 끝까지 푼다. [missVerdict]면 진상 추리 하나를 틀리고, [hints]면 행동마다 주변 살피기를 쓴다. */
    private fun clear(start: StoryState, missVerdict: Boolean, hints: Boolean): StoryState {
        var s = start
        var missed = false
        var guard = 0
        while (s.phase == StoryPhase.PUZZLE && guard++ < 500) {
            if (s.isExplore) {
                if (hints) s = StoryEngine.sceneHint(s)
                val target = s.actionableHotspots.firstOrNull() ?: break
                s = if (target.locks.isEmpty()) StoryEngine.examine(s, target.id) else {
                    val focused = s.copy(exploreFocus = s.activeLock(target))
                    val step = focused.currentPuzzle ?: break
                    val miss = missVerdict && !missed && step.verdict
                    if (miss) missed = true
                    StoryEngine.submitPuzzle(focused, input(step, miss)).state.copy(exploreFocus = null)
                }
            } else {
                val step = s.currentPuzzle ?: break
                if (hints) repeat(step.hints.size) { s = StoryEngine.showPuzzleHint(s) }
                val miss = missVerdict && !missed && step.verdict
                if (miss) missed = true
                s = StoryEngine.submitPuzzle(s, input(step, miss)).state
            }
        }
        return s
    }

    fun play(episode: StoryEpisode, flowVersion: Int, target: DemoTarget): StoryState {
        val ending = (target as? DemoTarget.Ending)?.kind
        var state = StoryEngine.start(episode, flowVersion)
        episode.chapters.indices.forEach { index ->
            state = StoryEngine.verifiedArrival(state)
            if (target == DemoTarget.Arrive(index)) return state
            state = StoryEngine.returnToCar(StoryEngine.leaveCar(state))
            if (target == DemoTarget.Scene(index)) return state
            state = clear(state, missVerdict = ending == EndingKind.NORMAL, hints = ending == EndingKind.BAD)
            if (index < episode.finalChapterIndex) {
                state = StoryEngine.next(state)
                if (state.phase == StoryPhase.FINAL_REVEAL) {
                    if (target == DemoTarget.FinalReveal) return state
                    state = StoryEngine.next(state)
                }
            }
        }
        return state
    }

    fun targets(chapters: Int): List<DemoTarget> =
        (0 until chapters).flatMap { listOf(DemoTarget.Arrive(it), DemoTarget.Scene(it)) } +
            DemoTarget.FinalReveal + EndingKind.entries.map { DemoTarget.Ending(it) }
}
