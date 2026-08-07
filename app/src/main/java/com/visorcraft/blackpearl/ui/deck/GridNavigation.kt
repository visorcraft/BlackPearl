package com.visorcraft.blackpearl.ui.deck

import com.visorcraft.blackpearl.settings.Action

// Pure grid index math, shared by both scroll directions: slots are linear
// indices row-major; NAV moves by one slot/row; PAGE (L1/R1) flips a whole
// page (columns x visible rows) in both "vertical" and "horizontal" modes.
class GridNavigation(
    private val itemCount: Int,
    private val columns: Int,
    private val visibleRows: Int,
) {
    private val pageSize = columns * visibleRows

    fun move(index: Int, action: Action): Int {
        val target = when (action) {
            Action.NAV_LEFT -> index - 1
            Action.NAV_RIGHT -> index + 1
            Action.NAV_UP -> index - columns
            Action.NAV_DOWN -> index + columns
            Action.PAGE_PREV -> index - pageSize
            Action.PAGE_NEXT -> index + pageSize
            else -> index
        }
        return target.coerceIn(0, (itemCount - 1).coerceAtLeast(0))
    }
}
