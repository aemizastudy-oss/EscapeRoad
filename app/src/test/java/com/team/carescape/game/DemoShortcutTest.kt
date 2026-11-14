package com.team.carescape.game

import com.team.carescape.data.StoredTrip
import com.team.carescape.data.TripStoreState
import com.team.carescape.data.upsertTrip
import com.team.carescape.data.validateStoredTrip
import com.team.carescape.model.TourClock
import com.team.carescape.model.TourMode
import com.team.carescape.model.TourStartRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoShortcutTest {
    private val flow = StoryCatalog.CURRENT_FLOW_VERSION
    private val episode = StoryCatalog.episodeFor(flow)
    private val clock = TourClock(1_800_000_000_000L, 50_000_000L, "boot-demo")

    /** 뷰모델의 demoJump와 같은 방식으로 저장 기록을 만든다. */
    private fun stored(state: StoryState): StoredTrip {
        val visited = state.chapterIndex + if (state.phase.ordinal >= StoryPhase.EXPLORING.ordinal) 1 else 0
        val records = TourCatalog.checkpoints.take(visited).mapIndexed { i, c ->
            val back = (visited - 1 - i) * 75L * 60_000
            TourStartRecord("demo", c.id, TourMode.MOCK, clock.copy(wallTimeMillis = clock.wallTimeMillis - back,
                elapsedRealtimeMillis = (clock.elapsedRealtimeMillis - back).coerceAtLeast(0)), debugTimeBypass = true)
        }
        return StoredTrip(TourMode.MOCK, "demo", chapterIndex = state.chapterIndex, phase = state.phase, items = state.items,
            clues = state.clues, startedAtEpochMillis = clock.wallTimeMillis - 10 * 3_600_000L, tourRecords = records,
            flowVersion = flow, puzzleProgress = state.puzzleProgress,
            endedAtEpochMillis = if (state.phase == StoryPhase.ENDED) clock.wallTimeMillis else null)
    }

    @Test fun everyShortcutLandsWhereItSaysAndSavesValidly() {
        DemoShortcut.targets(episode.chapters.size).forEach { t ->
            val s = DemoShortcut.play(episode, flow, t)
            when (t) {
                is DemoTarget.Arrive -> { assertEquals(t.label, t.chapter, s.chapterIndex); assertEquals(t.label, StoryPhase.ARRIVED, s.phase) }
                is DemoTarget.Scene -> { assertEquals(t.label, t.chapter, s.chapterIndex); assertEquals(t.label, StoryPhase.PUZZLE, s.phase)
                    assertTrue(t.label, s.puzzleProgressForCurrent!!.let { it.found.isEmpty() && it.solvedSteps.isEmpty() }) }
                DemoTarget.FinalReveal -> assertEquals(StoryPhase.FINAL_REVEAL, s.phase)
                is DemoTarget.Ending -> { assertEquals(t.label, StoryPhase.ENDED, s.phase); assertEquals(t.label, t.kind, s.endingKind) }
            }
            val trip = stored(s)
            validateStoredTrip(trip)
            upsertTrip(TripStoreState(), trip)
        }
    }

    @Test fun normalShortcutCanStillRetryTheTruth() {
        val normal = DemoShortcut.play(episode, flow, DemoTarget.Ending(EndingKind.NORMAL))
        assertTrue(normal.canRetryTruth)
        assertTrue(DemoShortcut.play(episode, flow, DemoTarget.Ending(EndingKind.BAD)).awakening >= SiyeokCase.BAD_THRESHOLD)
    }
}
