package com.team.carescape.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 테마 콘텐츠를 실제로 끝까지 풀 수 있는지, 기획 규칙을 지키는지 확인한다. */
class ContentTest {
    private val parked = VehicleState(parked = true, batteryPercent = 80)

    private fun blink(s: GameState, side: TurnSide): GameState {
        val on = PuzzleEngine.onVehicle(s, s.lastVehicle.copy(turnSignal = side))
        return PuzzleEngine.onVehicle(on, on.lastVehicle.copy(turnSignal = null))
    }

    @Test fun `모든 퍼즐은 힌트가 3단계다`() {
        val all = ThemeCatalog.cards.mapNotNull { it.episode }.flatMap { e -> e.puzzles + e.puzzles.mapNotNull { it.fallback } }
        all.forEach { assertEquals(it.id, 3, it.hints.size) }
    }

    @Test fun `플레이 가능한 테마마다 차량 조작 퍼즐이 하나 이상 있다`() {
        ThemeCatalog.cards.mapNotNull { it.episode }.forEach { e ->
            val usesCar = e.puzzles.any { p ->
                when (val a = p.answer) {
                    is Answer.TurnSequence, Answer.LowBeamOn -> true
                    is Answer.Code -> a.revealWhen != null
                    is Answer.Choice -> a.revealWhen != null
                }
            }
            assertTrue(e.id, usesCar)
        }
    }

    @Test fun `CAR DETECTIVE는 미션 5개를 끝까지 풀 수 있다`() {
        var s = PuzzleEngine.onVehicle(PuzzleEngine.start(CarDetective.episode), parked)
        assertEquals(5, s.episode.puzzles.size)
        s = blink(s, TurnSide.LEFT); s = blink(s, TurnSide.RIGHT)
        s = PuzzleEngine.submitChoice(s, 0) // 배터리 80% → 반보다 많아요
        s = PuzzleEngine.submitCode(s, "3")
        s = PuzzleEngine.submitChoice(s, 0)
        s = PuzzleEngine.onVehicle(s, s.lastVehicle.copy(lowBeamOn = true))
        s = PuzzleEngine.submitChoice(s, 0)
        assertTrue(s.finished)
        assertEquals(3, s.stars)
    }

    @Test fun `CASE 001 시연판 두 퍼즐을 풀 수 있다`() {
        var s = PuzzleEngine.onVehicle(PuzzleEngine.start(Case001.episode), parked)
        assertEquals(Feedback.LOCKED, PuzzleEngine.submitCode(s, "2341").feedback) // 헤드라이트 전에는 잠김
        s = PuzzleEngine.onVehicle(s, s.lastVehicle.copy(lowBeamOn = true))
        s = PuzzleEngine.submitCode(s, "2341")
        s = PuzzleEngine.submitChoice(s, 1)
        assertTrue(s.finished)
    }

    @Test fun `배터리 값을 못 읽으면 대체 퀴즈를 낸다`() {
        val s = PuzzleEngine.onVehicle(PuzzleEngine.start(CarDetective.episode), VehicleState(parked = true))
            .copy(puzzleIndex = 1)
        assertEquals("energy-quiz", s.currentPuzzle?.id)
        assertEquals(2, PuzzleEngine.submitChoice(s, 1).puzzleIndex)
    }

    @Test fun `MODE 3은 아직 플레이할 수 없다`() {
        assertEquals(null, ThemeCatalog.cards.single { it.mode == "MODE 3" }.episode)
    }
}
