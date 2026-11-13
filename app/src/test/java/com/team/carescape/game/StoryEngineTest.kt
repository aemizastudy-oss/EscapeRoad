package com.team.carescape.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StoryEngineTest {
    private val ep = Siyeok.episode

    /** 실제 주행처럼: 움직였다가 정차. */
    private fun drive(s: StoryState) = StoryEngine.onParked(StoryEngine.onParked(s, false), true)

    private fun visit(s: StoryState): StoryState =
        StoryEngine.returnToCar(StoryEngine.leaveCar(drive(s)))

    @Test fun `시역은 장소 5곳이고 마지막 장소도 관광 단계가 있다`() {
        assertEquals(5, ep.chapters.size)
        assertEquals(null, ep.chapters.last().explore)
        ep.chapters.dropLast(1).forEach { assertTrue(it.place, it.explore != null) }
        val final = StoryEngine.start(ep).copy(chapterIndex = ep.finalChapterIndex)
        assertEquals(StoryPhase.EXPLORING, StoryEngine.leaveCar(StoryEngine.verifiedArrival(final)).phase)
    }

    @Test fun `출발지에 그대로 서 있으면 도착으로 보지 않는다`() {
        val s = StoryEngine.onParked(StoryEngine.start(ep), true)
        assertEquals(StoryPhase.EN_ROUTE, s.phase)
    }

    @Test fun `정차 여부를 모르면 도착으로 보지 않는다`() {
        val s = StoryEngine.onParked(StoryEngine.onParked(StoryEngine.start(ep), false), null)
        assertEquals(StoryPhase.EN_ROUTE, s.phase)
        val unknownThenParked = StoryEngine.onParked(StoryEngine.onParked(StoryEngine.start(ep), null), true)
        assertEquals(StoryPhase.EN_ROUTE, unknownThenParked.phase)
        assertFalse(unknownThenParked.movedSinceGuidance)
    }

    @Test fun `움직였다가 정차하면 도착한다`() {
        assertEquals(StoryPhase.ARRIVED, drive(StoryEngine.start(ep)).phase)
    }

    @Test fun `시연용 도착은 정차 중에만 된다`() {
        val start = StoryEngine.start(ep)
        assertEquals(StoryPhase.EN_ROUTE, StoryEngine.demoArrive(start, false).phase)
        assertEquals(StoryPhase.ARRIVED, StoryEngine.demoArrive(start, true).phase)
    }

    @Test fun `처음부터 끝까지 진행하면 재료 3개와 단서 4개를 모으고 엔딩에 닿는다`() {
        var s = StoryEngine.start(ep)
        repeat(4) { i ->
            s = visit(s)
            assertEquals(StoryPhase.REVEALED, s.phase)
            s = StoryEngine.next(s)
            if (i < 3) assertEquals(i + 1, s.chapterIndex)
        }
        assertEquals(StoryPhase.FINAL_REVEAL, s.phase)
        assertFalse(s.isRevealed(ep.finalChapterIndex))
        s = StoryEngine.next(s)
        assertTrue(s.isFinalChapter)
        assertTrue(s.isRevealed(ep.finalChapterIndex))
        s = StoryEngine.leaveCar(drive(s))
        assertEquals(StoryPhase.EXPLORING, s.phase)
        s = StoryEngine.returnToCar(s)
        assertEquals(StoryPhase.ENDED, s.phase)
        assertEquals(listOf("멈추지 않고 흐르는 물", "만 석의 들판에서 거둔 곡식", "완성한 백신"), s.items)
        assertEquals(4, s.clues.size)
    }

    @Test fun `순서를 건너뛰는 입력은 무시한다`() {
        val s = StoryEngine.start(ep)
        assertEquals(s, StoryEngine.returnToCar(s))
        assertEquals(s, StoryEngine.next(s))
        assertEquals(s, StoryEngine.leaveCar(s))
    }

    @Test fun `검증된 도착은 이동 여부를 추정하지 않고 한 번만 반영한다`() {
        val arrived = StoryEngine.verifiedArrival(StoryEngine.start(ep))
        assertEquals(StoryPhase.ARRIVED, arrived.phase)
        assertEquals(arrived, StoryEngine.verifiedArrival(arrived))
        val exploring = StoryEngine.leaveCar(arrived)
        assertEquals(exploring, StoryEngine.verifiedArrival(exploring))
    }

    @Test fun `이전 완료 면제는 완료된 챕터 다음 이동만 허용하고 새 퍼즐은 생략하지 않는다`() {
        val episode = StoryCatalog.episodeFor(2)
        val retained = StoryState(episode, phase = StoryPhase.REVEALED,
            clues = listOfNotNull(episode.chapters.first().clue), flowVersion = 2, legacyCompletedCount = 1)
        val next = StoryEngine.next(retained)
        assertEquals(1, next.chapterIndex)
        assertEquals(1, next.legacyCompletedCount)
        assertEquals(StoryPhase.EN_ROUTE, next.phase)
        val puzzle = StoryEngine.returnToCar(StoryEngine.leaveCar(StoryEngine.verifiedArrival(next)))
        assertEquals(StoryPhase.PUZZLE, puzzle.phase)
        assertEquals(retained.clues, puzzle.clues)
        assertEquals(StoryPuzzleProgress("suwon_02"), puzzle.puzzleProgressForCurrent)
        assertEquals(puzzle, StoryEngine.next(puzzle))
    }

    @Test fun `퍼즐 오답 누적이 정수 범위를 넘지 않고 이미 해결한 기록은 다시 지급하지 않는다`() {
        val puzzle = StoryEngine.returnToCar(StoryEngine.leaveCar(StoryEngine.verifiedArrival(
            StoryEngine.start(StoryCatalog.episodeFor(2), 2),
        ))).copy(puzzleProgress = listOf(StoryPuzzleProgress("suwon_01", wrongAttempts = Int.MAX_VALUE)))
        val wrong = StoryEngine.submitPuzzle(puzzle, StoryPuzzleInput.Numeric("9999"))
        assertEquals(Int.MAX_VALUE, wrong.state.puzzleProgressForCurrent!!.wrongAttempts)
        val solved = StoryEngine.submitPuzzle(puzzle, StoryPuzzleInput.Numeric("0427"))
        val forgedReplay = solved.state.copy(phase = StoryPhase.PUZZLE)
        assertEquals(StoryPuzzleResult.LOCKED, StoryEngine.submitPuzzle(forgedReplay, StoryPuzzleInput.Numeric("0427")).result)
        assertEquals(solved.state.items, forgedReplay.items)
        assertEquals(solved.state.clues, forgedReplay.clues)
    }
}
