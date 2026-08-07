package com.visorcraft.ghostgalleon.ui.deck

import com.visorcraft.ghostgalleon.settings.Action

// Pure dock focus math, shared by the grid and game decks. The dock is a
// fixed row of slots below the deck content: NAV DOWN from the grid's last
// row (or from the carousel) moves focus into the dock, LEFT/RIGHT walk
// the slots, and NAV UP returns to the grid (same column, clamped to the
// last row) or the carousel. Host-tested in DockNavigationTest.
class DockNavigation(
    private val dockCount: Int,
    private val gridCount: Int,
    private val columns: Int,
) {
    // True when [gridIndex] sits on the grid's last row — the only row
    // NAV DOWN leaves the grid from.
    fun isLastRow(gridIndex: Int): Boolean =
        gridIndex >= (gridCount - columns).coerceAtLeast(0)

    // Dock slot to focus when pressing DOWN from [gridIndex]: the same
    // column, clamped into the dock row.
    fun enterFromGrid(gridIndex: Int): Int =
        (gridIndex % columns.coerceAtLeast(1))
            .coerceIn(0, (dockCount - 1).coerceAtLeast(0))

    // Grid slot to focus when pressing UP from [dockIndex]: the same
    // column on the last row, clamped to the grid (the dock can be wider
    // or narrower than the grid's column count).
    fun exitToGrid(dockIndex: Int): Int {
        if (gridCount <= 0) return 0
        val lastRowStart = (gridCount - columns).coerceAtLeast(0)
        return (lastRowStart + dockIndex).coerceIn(0, gridCount - 1)
    }

    // LEFT/RIGHT walk the dock row, clamped at both ends; every other
    // action stays put.
    fun move(dockIndex: Int, action: Action): Int {
        val target = when (action) {
            Action.NAV_LEFT -> dockIndex - 1
            Action.NAV_RIGHT -> dockIndex + 1
            else -> dockIndex
        }
        return target.coerceIn(0, (dockCount - 1).coerceAtLeast(0))
    }
}
