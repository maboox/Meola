package ir.nama.core

/** A rectangle on the home grid, in cells. */
data class Cell(val id: String, val x: Int, val y: Int, val w: Int = 1, val h: Int = 1) {
    fun overlaps(o: Cell): Boolean = x < o.x + o.w && o.x < x + w && y < o.y + o.h && o.y < y + h
    fun contains(cx: Int, cy: Int): Boolean = cx >= x && cx < x + w && cy >= y && cy < y + h
}

/** Placement rules for the home screen grid (like Android's workspace). */
object GridMath {
    fun fits(c: Cell, cols: Int, rows: Int): Boolean = c.x >= 0 && c.y >= 0 && c.x + c.w <= cols && c.y + c.h <= rows

    /** True if [c] is inside the grid and does not overlap any other cell (ignoring the one with [ignoreId]). */
    fun canPlace(cells: List<Cell>, c: Cell, cols: Int, rows: Int, ignoreId: String? = c.id): Boolean =
        fits(c, cols, rows) && cells.none { it.id != ignoreId && it.overlaps(c) }

    /** First free spot for a w×h item, scanning rows top to bottom, or null if the page is full. */
    fun findFree(cells: List<Cell>, w: Int, h: Int, cols: Int, rows: Int, fromBottom: Boolean = false): Pair<Int, Int>? {
        if (w > cols || h > rows) return null
        val ys = if (fromBottom) (rows - h downTo 0) else (0..rows - h)
        for (y in ys) for (x in 0..cols - w) {
            if (canPlace(cells, Cell("?", x, y, w, h), cols, rows, ignoreId = null)) return x to y
        }
        return null
    }

    /** The item covering a cell, if any. */
    fun at(cells: List<Cell>, x: Int, y: Int): Cell? = cells.firstOrNull { it.contains(x, y) }

    /**
     * Makes a page valid for a (possibly smaller) grid: items that no longer fit or overlap are
     * moved to the first free spot; items that cannot be placed at all are returned separately.
     */
    fun normalize(cells: List<Cell>, cols: Int, rows: Int): Pair<List<Cell>, List<Cell>> {
        val placed = mutableListOf<Cell>()
        val homeless = mutableListOf<Cell>()
        // Big items first so widgets keep their place before single icons.
        val order = cells.sortedWith(compareByDescending<Cell> { it.w * it.h }.thenBy { it.y }.thenBy { it.x })
        for (c in order) {
            val w = minOf(c.w, cols)
            val h = minOf(c.h, rows)
            val sized = c.copy(w = w, h = h)
            if (canPlace(placed, sized, cols, rows, ignoreId = null)) {
                placed += sized
            } else {
                val spot = findFree(placed, w, h, cols, rows)
                if (spot != null) placed += sized.copy(x = spot.first, y = spot.second) else homeless += c
            }
        }
        return placed to homeless
    }
}
