package com.team.carescape.game

/** 직전 입력에 대한 짧은 반응. 화면·소리 연출이 이 값을 보고 반응한다. */
enum class Feedback { CORRECT, WRONG, PROGRESS, LOCKED }

/**
 * 한 테마의 진행 상태. 불변 객체이며 [PuzzleEngine]의 함수가 새 상태를 돌려준다.
 * 이 형태 그대로 저장하면 다음 정차 때 이어서 할 수 있다.
 */
data class GameState(
    val episode: Episode,
    val puzzleIndex: Int = 0,
    /** 현재 퍼즐에서 펼친 힌트 수 (0~3). */
    val hintsShown: Int = 0,
    /** 테마 전체에서 쓴 힌트 수. 별점에만 쓴다. */
    val hintsUsedTotal: Int = 0,
    /** 방향지시등 순서 퍼즐에서 지금까지 맞게 입력한 방향. */
    val turnProgress: List<TurnSide> = emptyList(),
    /** 가려진 단서가 드러났는지 (예: 와이퍼·전조등을 켜서 단서가 보임). */
    val clueRevealed: Boolean = false,
    val lastVehicle: VehicleState = VehicleState(),
    val feedback: Feedback? = null,
    val finished: Boolean = false,
) {
    /**
     * 지금 풀 퍼즐. 필요한 차량 값을 읽지 못했으면 대체 퍼즐을 낸다.
     * 읽지 못한 값을 가짜 숫자로 채우지 않는다.
     */
    val currentPuzzle: Puzzle?
        get() {
            val base = episode.puzzles.getOrNull(puzzleIndex) ?: return null
            val missing = when (base.needs) {
                VehicleData.BATTERY_PERCENT -> lastVehicle.batteryPercent == null
                null -> false
            }
            return if (missing) base.fallback ?: base else base
        }

    /** 방금 푼 퍼즐 (성공 문구 표시용). */
    val previousPuzzle: Puzzle? get() = episode.puzzles.getOrNull(puzzleIndex - 1)

    /** 정차가 확인되지 않으면 일시정지. 모르는 상태도 정차로 보지 않는다. */
    val paused: Boolean get() = lastVehicle.parked != true

    val visibleHints: List<String> get() = currentPuzzle?.hints?.take(hintsShown).orEmpty()

    /** 힌트 0개 = 별 3, 1~2개 = 별 2, 그 이상 = 별 1. */
    val stars: Int get() = when {
        hintsUsedTotal == 0 -> 3
        hintsUsedTotal <= 2 -> 2
        else -> 1
    }
}

/** 차량 입력·화면 입력·힌트 요청을 받아 다음 상태를 계산하는 순수 로직. */
object PuzzleEngine {

    fun start(episode: Episode): GameState = GameState(episode)

    /** 차량 신호가 바뀔 때마다 부른다. 정차 중이 아니면 퍼즐 입력으로 쓰지 않는다. */
    fun onVehicle(state: GameState, vehicle: VehicleState): GameState {
        val previous = state.lastVehicle
        // 반응(feedback)은 새 입력이 생길 때만 바꾼다. 신호가 주기적으로 들어와도 직전 반응이 지워지지 않게 한다.
        val next = state.copy(lastVehicle = vehicle)
        if (next.paused || next.finished) return next
        val answer = next.currentPuzzle?.answer ?: return next

        return when (answer) {
            is Answer.TurnSequence -> onTurnSignal(next, answer, previous.turnSignal, vehicle.turnSignal)
            Answer.LowBeamOn ->
                if (vehicle.lowBeamOn == true && previous.lowBeamOn != true) solve(next) else next
            is Answer.Code -> reveal(next, answer.revealWhen, vehicle)
            is Answer.Choice -> reveal(next, answer.revealWhen, vehicle)
        }
    }

    /** 화면에서 코드를 입력했을 때. */
    fun submitCode(state: GameState, input: String): GameState {
        if (state.paused || state.finished) return state.copy(feedback = Feedback.LOCKED)
        val answer = state.currentPuzzle?.answer as? Answer.Code ?: return state.copy(feedback = Feedback.LOCKED)
        if (answer.revealWhen != null && !state.clueRevealed) return state.copy(feedback = Feedback.LOCKED)
        return if (input.filter { !it.isWhitespace() } == answer.code) solve(state)
        else state.copy(feedback = Feedback.WRONG)
    }

    /** 객관식 보기를 골랐을 때. */
    fun submitChoice(state: GameState, index: Int): GameState {
        if (state.paused || state.finished) return state.copy(feedback = Feedback.LOCKED)
        val answer = state.currentPuzzle?.answer as? Answer.Choice ?: return state.copy(feedback = Feedback.LOCKED)
        if (answer.revealWhen != null && !state.clueRevealed) return state.copy(feedback = Feedback.LOCKED)
        val correct = correctChoice(answer, state.lastVehicle) ?: return state.copy(feedback = Feedback.LOCKED)
        return if (index == correct) solve(state) else state.copy(feedback = Feedback.WRONG)
    }

    /** 다음 단계 힌트를 펼친다. 이미 모두 펼쳤으면 그대로 둔다. */
    fun showHint(state: GameState): GameState {
        val puzzle = state.currentPuzzle ?: return state
        if (state.paused || state.hintsShown >= puzzle.hints.size) return state
        return state.copy(hintsShown = state.hintsShown + 1, hintsUsedTotal = state.hintsUsedTotal + 1, feedback = null)
    }

    /** 객관식 정답 번호. 차량 값이 필요한데 없으면 null (판정하지 않음). */
    fun correctChoice(answer: Answer.Choice, vehicle: VehicleState): Int? = when (answer.rule) {
        null -> answer.correctIndex
        ChoiceRule.BATTERY_HALF_OR_MORE -> vehicle.batteryPercent?.let { if (it >= 50) 0 else 1 }
    }

    private fun reveal(state: GameState, condition: RevealCondition?, vehicle: VehicleState): GameState {
        if (condition == null || state.clueRevealed) return state
        val met = when (condition) {
            RevealCondition.WIPER_ON -> vehicle.wiperOn == true
            RevealCondition.LOW_BEAM_ON -> vehicle.lowBeamOn == true
        }
        return if (met) state.copy(clueRevealed = true, feedback = Feedback.PROGRESS) else state
    }

    private fun onTurnSignal(state: GameState, answer: Answer.TurnSequence, before: TurnSide?, now: TurnSide?): GameState {
        // 새로 켜진 순간만 한 번의 입력으로 센다. 계속 켜져 있는 동안은 다시 세지 않는다.
        if (now == null || now == before) return state
        val attempt = state.turnProgress + now
        return when {
            attempt == answer.sides -> solve(state)
            answer.sides.take(attempt.size) == attempt -> state.copy(turnProgress = attempt, feedback = Feedback.PROGRESS)
            // 틀리면 처음부터. 다만 지금 입력이 첫 칸과 같으면 그걸 첫 입력으로 인정한다.
            now == answer.sides.first() -> state.copy(turnProgress = listOf(now), feedback = Feedback.WRONG)
            else -> state.copy(turnProgress = emptyList(), feedback = Feedback.WRONG)
        }
    }

    private fun solve(state: GameState): GameState {
        val nextIndex = state.puzzleIndex + 1
        return state.copy(
            puzzleIndex = nextIndex,
            hintsShown = 0,
            turnProgress = emptyList(),
            clueRevealed = false,
            feedback = Feedback.CORRECT,
            finished = nextIndex >= state.episode.puzzles.size,
        )
    }
}
