package com.team.carescape.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 엔진 규칙 테스트. 콘텐츠 문구가 바뀌어도 깨지지 않게 테스트 전용 에피소드를 쓴다. */
class PuzzleEngineTest {
    private val hints = listOf("h1", "h2", "h3")
    private fun p(id: String, answer: Answer, needs: VehicleData? = null, fallback: Puzzle? = null) =
        Puzzle(id, id, "screen", "audio", hints, answer, "ok", needs, fallback)

    private val episode = Episode(
        id = "test", title = "T", subtitle = "", tone = ThemeTone.KIDS, intro = "", ending = "",
        puzzles = listOf(
            p("turn", Answer.TurnSequence(listOf(TurnSide.LEFT, TurnSide.RIGHT, TurnSide.LEFT))),
            p("beam", Answer.LowBeamOn),
            p("rain", Answer.Code("372", revealWhen = RevealCondition.WIPER_ON)),
            p(
                "battery",
                Answer.Choice(listOf("많아요", "적어요"), rule = ChoiceRule.BATTERY_HALF_OR_MORE),
                needs = VehicleData.BATTERY_PERCENT,
                fallback = p("battery-quiz", Answer.Choice(listOf("a", "b", "c"), correctIndex = 2)),
            ),
            p("final", Answer.Choice(listOf("x", "y"), correctIndex = 1, revealWhen = RevealCondition.LOW_BEAM_ON)),
        ),
    )
    private val parked = VehicleState(parked = true)

    private fun startParked(vehicle: VehicleState = parked) = PuzzleEngine.onVehicle(PuzzleEngine.start(episode), vehicle)
    private fun at(index: Int, vehicle: VehicleState = parked) = startParked(vehicle).copy(puzzleIndex = index)

    /** 방향지시등을 켰다가 끄는 동작 한 번. */
    private fun blink(state: GameState, side: TurnSide): GameState {
        val on = PuzzleEngine.onVehicle(state, state.lastVehicle.copy(turnSignal = side))
        return PuzzleEngine.onVehicle(on, on.lastVehicle.copy(turnSignal = null))
    }

    @Test fun `정차가 확인되지 않으면 일시정지하고 입력을 무시한다`() {
        val unknown = PuzzleEngine.start(episode)
        assertTrue(unknown.paused)
        val driving = PuzzleEngine.onVehicle(unknown, VehicleState(parked = false, turnSignal = TurnSide.LEFT))
        assertTrue(driving.paused)
        assertEquals(emptyList<TurnSide>(), driving.turnProgress)
        assertEquals(Feedback.LOCKED, PuzzleEngine.submitChoice(driving.copy(puzzleIndex = 4), 1).feedback)
    }

    @Test fun `방향지시등을 순서대로 켜면 풀린다`() {
        var s = startParked()
        s = blink(s, TurnSide.LEFT)
        s = blink(s, TurnSide.RIGHT)
        assertEquals(listOf(TurnSide.LEFT, TurnSide.RIGHT), s.turnProgress)
        s = PuzzleEngine.onVehicle(s, s.lastVehicle.copy(turnSignal = TurnSide.LEFT))
        assertEquals(Feedback.CORRECT, s.feedback)
        assertEquals(1, s.puzzleIndex)
    }

    @Test fun `켜진 채로 있는 방향지시등은 한 번만 센다`() {
        var s = startParked()
        s = PuzzleEngine.onVehicle(s, s.lastVehicle.copy(turnSignal = TurnSide.LEFT))
        s = PuzzleEngine.onVehicle(s, s.lastVehicle.copy(turnSignal = TurnSide.LEFT))
        assertEquals(listOf(TurnSide.LEFT), s.turnProgress)
    }

    @Test fun `순서가 틀리면 처음부터 다시 세고 반응은 다음 신호에도 남는다`() {
        var s = startParked()
        s = blink(s, TurnSide.LEFT)
        s = blink(s, TurnSide.LEFT)
        assertEquals(Feedback.WRONG, s.feedback)
        assertEquals(listOf(TurnSide.LEFT), s.turnProgress)
        s = blink(s, TurnSide.RIGHT)
        s = blink(s, TurnSide.RIGHT)
        assertEquals(Feedback.WRONG, s.feedback)
        assertEquals(emptyList<TurnSide>(), s.turnProgress)
    }

    @Test fun `전조등은 꺼진 상태에서 켜야 인정한다`() {
        val alreadyOn = at(1, parked.copy(lowBeamOn = true))
        val stillOn = PuzzleEngine.onVehicle(alreadyOn, parked.copy(lowBeamOn = true))
        assertEquals(1, stillOn.puzzleIndex)
        var s = PuzzleEngine.onVehicle(stillOn, parked.copy(lowBeamOn = false))
        s = PuzzleEngine.onVehicle(s, parked.copy(lowBeamOn = true))
        assertEquals(2, s.puzzleIndex)
    }

    @Test fun `와이퍼를 켜기 전에는 암호를 입력할 수 없다`() {
        var s = at(2)
        assertEquals(Feedback.LOCKED, PuzzleEngine.submitCode(s, "372").feedback)
        s = PuzzleEngine.onVehicle(s, parked.copy(wiperOn = true))
        assertTrue(s.clueRevealed)
        assertEquals(Feedback.WRONG, PuzzleEngine.submitCode(s, "123").feedback)
        assertEquals(3, PuzzleEngine.submitCode(s, " 3 7 2 ").puzzleIndex)
    }

    @Test fun `배터리 50% 이상이면 첫 보기, 미만이면 두 번째 보기가 정답이다`() {
        val full = at(3, parked.copy(batteryPercent = 50))
        assertEquals(Feedback.WRONG, PuzzleEngine.submitChoice(full, 1).feedback)
        assertEquals(4, PuzzleEngine.submitChoice(full, 0).puzzleIndex)
        val low = at(3, parked.copy(batteryPercent = 49))
        assertEquals(4, PuzzleEngine.submitChoice(low, 1).puzzleIndex)
    }

    @Test fun `배터리 값을 모르면 가짜 값 대신 대체 퍼즐을 낸다`() {
        val s = at(3)
        assertEquals("battery-quiz", s.currentPuzzle?.id)
        assertEquals(Feedback.WRONG, PuzzleEngine.submitChoice(s, 0).feedback)
        assertEquals(4, PuzzleEngine.submitChoice(s, 2).puzzleIndex)
    }

    @Test fun `전조등으로 드러나는 객관식은 켜기 전에 잠겨 있다`() {
        var s = at(4)
        assertEquals(Feedback.LOCKED, PuzzleEngine.submitChoice(s, 1).feedback)
        s = PuzzleEngine.onVehicle(s, parked.copy(lowBeamOn = true))
        s = PuzzleEngine.submitChoice(s, 1)
        assertTrue(s.finished)
    }

    @Test fun `힌트는 세 단계까지만 펼치고 별점에 반영한다`() {
        var s = startParked()
        repeat(5) { s = PuzzleEngine.showHint(s) }
        assertEquals(3, s.hintsShown)
        assertEquals(3, s.hintsUsedTotal)
        assertEquals(1, s.stars)
    }

    @Test fun `기어를 P에서 빼면 진행은 그대로 두고 멈춘다`() {
        var s = startParked()
        s = blink(s, TurnSide.LEFT)
        s = PuzzleEngine.onVehicle(s, s.lastVehicle.copy(parked = false))
        assertTrue(s.paused)
        assertEquals(listOf(TurnSide.LEFT), s.turnProgress)
        s = PuzzleEngine.onVehicle(s, s.lastVehicle.copy(parked = true))
        s = blink(s, TurnSide.RIGHT)
        assertEquals(listOf(TurnSide.LEFT, TurnSide.RIGHT), s.turnProgress)
    }
}
