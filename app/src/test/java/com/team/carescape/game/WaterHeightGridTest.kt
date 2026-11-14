package com.team.carescape.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WaterHeightGridTest {
    @Test fun choosesTheSmallestLowerNeighbourRatherThanTheFirstLowerOne() {
        val grid = WaterHeightGrid(
            listOf(listOf(8, 4, 9), listOf(3, 7, 6), listOf(2, 5, 1)), start = 7,
        )
        assertEquals(listOf(7, 3, 2), grid.path)
        assertEquals(listOf(LockDirection.LEFT, LockDirection.DOWN), grid.directions)
        // The global minimum 1 is neither adjacent to 7 nor reachable by another descent from 2.
        assertEquals(2, grid.path.last())
    }

    @Test fun aLowerDiagonalCellCannotBeChosenAsOneMove() {
        val grid = WaterHeightGrid(listOf(listOf(9, 7), listOf(8, 1)), start = 9)
        assertEquals(listOf(9, 7, 1), grid.path)
        assertEquals(listOf(LockDirection.RIGHT, LockDirection.DOWN), grid.directions)
    }

    @Test fun stopsWithoutClimbingWhenAllCardinalNeighboursAreHigher() {
        val grid = WaterHeightGrid(listOf(listOf(9, 8), listOf(7, 3)), start = 3)
        assertEquals(listOf(3), grid.path)
        assertTrue(grid.directions.isEmpty())
    }

    @Test fun snapshotsCallerOwnedRowsBeforeCalculatingAndDisplayingTheRoute() {
        val rows = mutableListOf(mutableListOf(9, 5), mutableListOf(7, 1))
        val grid = WaterHeightGrid(rows, start = 9)
        rows[0][1] = 100
        rows.add(mutableListOf(8, 6))
        assertEquals(listOf(listOf(9, 5), listOf(7, 1)), grid.rows)
        assertEquals(listOf(9, 5, 1), grid.path)
        assertEquals(listOf(LockDirection.RIGHT, LockDirection.DOWN), grid.directions)
    }

    @Test fun rejectsBoardsWithAmbiguousOrMissingHeightsAndNonRectangularRows() {
        val invalid = listOf(
            emptyList(), listOf(emptyList()), listOf(listOf(1, 2), listOf(3)),
            listOf(listOf(1, 1)), listOf(listOf(1, 0)), listOf(listOf(1, -2)),
        )
        invalid.forEach { rows -> assertTrue(rows.toString(), runCatching { WaterHeightGrid(rows, start = 1) }.isFailure) }
        assertTrue(runCatching { WaterHeightGrid(listOf(listOf(1, 2)), start = 3) }.isFailure)
    }

    @Test fun directionLockCannotDeclareAnAnswerThatDisagreesWithItsDisplayedGrid() {
        val grid = WaterHeightGrid(listOf(listOf(2, 1)), start = 2)
        assertEquals(grid, StoryLockDefinition.Directions(listOf(LockDirection.RIGHT), grid).heightGrid)
        assertTrue(runCatching { StoryLockDefinition.Directions(listOf(LockDirection.LEFT), grid) }.isFailure)
    }
}
