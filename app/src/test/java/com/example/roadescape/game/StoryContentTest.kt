package com.example.roadescape.game

import com.example.roadescape.model.GamePackage
import java.io.File
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 시역(屍疫) 이야기 층: 콘텐츠가 유효하고, 이야기가 없는 기존 패키지는 그대로 동작한다. */
class StoryContentTest {
    private val json = Json { classDiscriminator = "type" }
    private fun load(name: String): GamePackage = json.decodeFromString(File("src/main/assets/$name").readText())

    @Test fun suwonPackageCarriesSiyeokStoryForEveryChapter() {
        val suwon = load("suwon_tour_package.json")
        assertTrue(ContentValidator.validate(suwon).toString(), ContentValidator.validate(suwon).isEmpty())
        val story = assertNotNull(suwon.story).let { suwon.story!! }
        assertEquals("시역(屍疫)", story.title)
        assertEquals(3, story.rules.size)
        assertEquals(5, suwon.checkpoints.size)
        suwon.checkpoints.forEach { assertNotNull(it.id, it.story) }
        // 재료는 화홍문(물)·만석공원(곡식)·박물관(완성한 백신) 세 곳에서만 얻는다.
        assertEquals(
            listOf(null, "멈추지 않고 흐르는 물", "만 석의 들판에서 거둔 곡식", "완성한 백신", null),
            suwon.checkpoints.map { it.story?.item },
        )
    }

    @Test fun storyDoesNotChangeTourStructureOrPuzzles() {
        val suwon = load("suwon_tour_package.json")
        assertEquals(TourCatalog.PACKAGE_ID, suwon.id)
        assertEquals(TourCatalog.checkpoints.map { it.id }, suwon.checkpoints.map { it.id })
        assertEquals(listOf("suwon_p_01", "suwon_p_02", "suwon_p_03", "suwon_p_04", "suwon_p_05"), suwon.checkpoints.map { it.puzzleId })
        assertTrue(suwon.requiresTour)
    }

    @Test fun packagesWithoutStoryStayValid() {
        val demo = load("demo_package.json")
        assertNull(demo.story)
        assertTrue(demo.checkpoints.all { it.story == null })
        assertTrue(ContentValidator.validate(demo).isEmpty())
    }

    @Test fun storyRequiresEveryChapterToHaveText() {
        val suwon = load("suwon_tour_package.json")
        val broken = suwon.copy(checkpoints = suwon.checkpoints.mapIndexed { i, c -> if (i == 2) c.copy(story = null) else c })
        assertTrue(ContentValidator.validate(broken).any { "suwon_03" in it })
        val blank = suwon.copy(story = suwon.story!!.copy(ending = " "))
        assertTrue(ContentValidator.validate(blank).isNotEmpty())
    }
}
