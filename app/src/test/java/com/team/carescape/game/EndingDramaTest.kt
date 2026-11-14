package com.team.carescape.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EndingDramaTest {
    private val episode = StoryCatalog.episodeFor(StoryCatalog.CURRENT_FLOW_VERSION)

    @Test fun everyEndingHasADramaThatStartsAtTheLakeAndEndsWithItsTitle() {
        EndingKind.entries.forEach { kind ->
            val lines = SiyeokScenes.endingDrama.getValue(kind)
            assertTrue(kind.name, lines.size >= 10)
            assertEquals(kind.name, SceneBg.LAKE_NIGHT, lines.first().bg)
            assertNull(kind.name, lines.last().speaker)
            assertTrue(kind.name, lines.last().text.contains(kind.label))
            assertTrue(kind.name, lines.last().text.contains(episode.endings.getValue(kind).title))
            // 장소가 여러 번 바뀌고, 마지막은 조사국으로 돌아온다.
            assertTrue(kind.name, lines.mapNotNull { it.bg }.distinct().size >= 4)
            assertEquals(kind.name, SceneBg.AGENCY, lines.mapNotNull { it.bg }.last())
        }
    }

    @Test fun everySpeakerHasAPersonAndPortrait() {
        val speakers = SiyeokScenes.endingDrama.values.flatten().mapNotNull { it.speaker }.toSet()
        speakers.forEach { assertNotNull(it, episode.speakerFor(it)) }
        // 진 엔딩에서만 연이와 서도윤이 다시 만난다.
        assertTrue(SiyeokScenes.endingDrama.getValue(EndingKind.TRUE).any { it.speaker == "yeoni" && it.text.contains("오라버니") })
    }
}
