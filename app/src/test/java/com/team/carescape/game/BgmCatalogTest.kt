package com.team.carescape.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class BgmCatalogTest {
    private val ep = StoryCatalog.episodeFor(StoryCatalog.EXPLORE_FLOW_VERSION)
    private fun state(chapter: Int, phase: StoryPhase, focus: String? = null) =
        StoryState(ep, chapter, phase, flowVersion = StoryCatalog.EXPLORE_FLOW_VERSION, exploreFocus = focus)

    @Test fun drivingPlaysTheRouteTrackForTheNextPlace() {
        assertEquals("bgm_palace_drive", BgmCatalog.story(state(0, StoryPhase.EN_ROUTE))?.name)
        assertEquals("bgm_lake_drive", BgmCatalog.story(state(4, StoryPhase.EN_ROUTE))?.name)
    }

    @Test fun eachPlaceKeepsOneTrackFromArrivalToReveal() {
        val names = listOf("bgm_palace_explore", "bgm_sluice_explore", "bgm_reservoir_explore", "bgm_museum_explore")
        names.forEachIndexed { i, name ->
            for (phase in listOf(StoryPhase.ARRIVED, StoryPhase.EXPLORING, StoryPhase.PUZZLE, StoryPhase.REVEALED)) {
                assertEquals("$i $phase", name, BgmCatalog.story(state(i, phase))?.name)
            }
        }
    }

    @Test fun theLakeArrivalCrossesBackInTimeThenLoopsTheLakeTrack() {
        val cue = BgmCatalog.story(state(4, StoryPhase.ARRIVED))!!
        assertEquals("bgm_lake_arrival", cue.name)
        assertFalse(cue.loop)
        assertEquals("bgm_lake_explore", cue.then)
        assertEquals("bgm_lake_explore", BgmCatalog.story(state(4, StoryPhase.PUZZLE))?.name)
    }

    @Test fun theVerdictLocksPlayTheTruthTrack() {
        assertEquals("bgm_truth", BgmCatalog.story(state(4, StoryPhase.PUZZLE, "c5_truth_who"))?.name)
        assertEquals("bgm_truth", BgmCatalog.story(state(4, StoryPhase.PUZZLE, "c5_truth_why"))?.name)
    }

    @Test fun endingsPlayOnceThenSettleOnYeonisTheme() {
        for ((kind, name) in listOf(EndingKind.TRUE to "bgm_ending_true", EndingKind.NORMAL to "bgm_ending_normal", EndingKind.BAD to "bgm_ending_bad")) {
            val cue = BgmCatalog.ending(kind)
            assertEquals(name, cue.name)
            assertFalse(cue.loop)
            assertEquals("bgm_yeoni_theme", cue.then)
        }
        // 진상을 풀지 않고 끝나면 일반 엔딩 곡.
        assertEquals("bgm_ending_normal", BgmCatalog.story(state(4, StoryPhase.ENDED))?.name)
    }

    @Test fun prologueFollowsTheSceneBackground() {
        assertEquals("bgm_agency", BgmCatalog.prologue(SceneBg.AGENCY).name)
        assertEquals("bgm_title", BgmCatalog.prologue(null).alt)
        assertEquals("bgm_time_rift", BgmCatalog.prologue(SceneBg.TIME_RIFT).name)
        assertEquals("bgm_palace_wall", BgmCatalog.prologue(SceneBg.PALACE_NIGHT).name)
    }
}
