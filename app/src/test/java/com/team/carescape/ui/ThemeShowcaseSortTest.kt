package com.team.carescape.ui

import com.team.carescape.game.ThemeCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeShowcaseSortTest {
    private val cards = ThemeCatalog.cards

    @Test
    fun recommendedPutsPlayableThemeInTheMiddle() {
        val order = sortShowcase(cards, ShowcaseSort.RECOMMENDED, true)
        assertEquals(cards.size, order.size)
        assertTrue(order[order.size / 2].playable)
    }

    @Test
    fun difficultyAndFearSortBothWaysAndKeepSealedLast() {
        for (sort in listOf(ShowcaseSort.DIFFICULTY, ShowcaseSort.FEAR)) {
            val key = { c: com.team.carescape.game.ThemeCard -> if (sort == ShowcaseSort.DIFFICULTY) c.difficulty else c.fear }
            val down = sortShowcase(cards, sort, true)
            val up = sortShowcase(cards, sort, false)
            assertEquals("SEALED", down.last().mode)
            assertEquals("SEALED", up.last().mode)
            assertEquals(down.dropLast(1).map(key), down.dropLast(1).map(key).sortedDescending())
            assertEquals(up.dropLast(1).map(key), up.dropLast(1).map(key).sorted())
            assertEquals(cards.toSet(), down.toSet())
        }
    }
}
