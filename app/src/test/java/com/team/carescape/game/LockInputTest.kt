package com.team.carescape.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LockInputTest {
    @Test
    fun englishAcceptsOnlyAsciiLettersAndNormalizesCase() {
        assertEquals("LIGHT", LockInput.normalizeEnglish(" l1i-가g🙂h t！"))
        assertEquals("I", LockInput.normalizeEnglish("iıİéＡ"))
    }

    @Test
    fun englishLimitsTheFilteredResultAndSupportsDeletion() {
        assertEquals("ABCDE", LockInput.normalizeEnglish("a1b2c3d4e5f"))
        assertEquals("AB", LockInput.normalizeEnglish("ab"))
        assertEquals("", LockInput.normalizeEnglish(""))
        assertEquals("", LockInput.normalizeEnglish("abc", 0))
        assertEquals("ABCDEFG", LockInput.normalizeEnglish("abcdefgh", 7))
    }

    @Test(expected = IllegalArgumentException::class)
    fun negativeEnglishLengthIsRejected() {
        LockInput.normalizeEnglish("abc", -1)
    }

    @Test
    fun directionsUseTheDominantAxisInScreenCoordinates() {
        assertEquals(LockDirection.UP, LockInput.directionOf(5f, -40f, 28f))
        assertEquals(LockDirection.DOWN, LockInput.directionOf(-5f, 40f, 28f))
        assertEquals(LockDirection.LEFT, LockInput.directionOf(-40f, 5f, 28f))
        assertEquals(LockDirection.RIGHT, LockInput.directionOf(40f, -5f, 28f))
    }

    @Test
    fun deadZoneAndItsExactBoundaryAreDifferent() {
        assertNull(LockInput.directionOf(0f, 0f, 0f))
        assertNull(LockInput.directionOf(0f, -27.999f, 28f))
        assertEquals(LockDirection.UP, LockInput.directionOf(0f, -28f, 28f))
        assertEquals(LockDirection.DOWN, LockInput.directionOf(0f, 28.001f, 28f))
        assertEquals(LockDirection.RIGHT, LockInput.directionOf(20f, 20f, 28f))
    }

    @Test
    fun diagonalTiesHaveOneDeterministicDirection() {
        assertEquals(LockDirection.RIGHT, LockInput.directionOf(40f, -40f, 28f))
        assertEquals(LockDirection.LEFT, LockInput.directionOf(-40f, 40f, 28f))
    }

    @Test
    fun invalidCoordinatesOrThresholdNeverBecomeAnAnswer() {
        for (invalid in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            assertNull(LockInput.directionOf(invalid, 30f, 28f))
            assertNull(LockInput.directionOf(30f, invalid, 28f))
            assertNull(LockInput.directionOf(30f, 0f, invalid))
        }
        assertNull(LockInput.directionOf(30f, 0f, -1f))
    }
}
