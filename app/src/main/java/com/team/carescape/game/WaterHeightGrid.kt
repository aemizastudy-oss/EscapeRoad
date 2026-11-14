package com.team.carescape.game

/** A snapshot of a height board. Water chooses the lowest strictly lower cardinal neighbour. */
class WaterHeightGrid(rows: List<List<Int>>, val start: Int) {
    val rows: List<List<Int>> = rows.map { it.toList() }
    val path: List<Int>
    val directions: List<LockDirection>

    private data class Cell(val row: Int, val column: Int)

    init {
        require(this.rows.isNotEmpty() && this.rows.first().isNotEmpty()) { "The height board must not be empty." }
        require(this.rows.all { it.size == this.rows.first().size }) { "The height board must be rectangular." }
        val heights = this.rows.flatten()
        require(heights.all { it > 0 } && heights.distinct().size == heights.size) { "Heights must be positive and unique." }
        require(start in heights) { "The starting height must exist on the board." }

        val startRow = this.rows.indexOfFirst { start in it }
        var current = Cell(startRow, this.rows[startRow].indexOf(start))
        val visited = mutableListOf(current)
        val moves = mutableListOf<LockDirection>()
        while (true) {
            val next = listOf(
                Cell(current.row - 1, current.column), Cell(current.row + 1, current.column),
                Cell(current.row, current.column - 1), Cell(current.row, current.column + 1),
            ).filter { it.row in this.rows.indices && it.column in this.rows[it.row].indices }
                .filter { this.rows[it.row][it.column] < this.rows[current.row][current.column] }
                .minByOrNull { this.rows[it.row][it.column] }
                ?: break
            moves += when {
                next.row < current.row -> LockDirection.UP
                next.row > current.row -> LockDirection.DOWN
                next.column < current.column -> LockDirection.LEFT
                else -> LockDirection.RIGHT
            }
            visited += next
            current = next
        }
        path = visited.map { this.rows[it.row][it.column] }
        directions = moves.toList()
    }
}
