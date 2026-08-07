package com.visorcraft.ghostgalleon.ui.deck

/**
 * Minimal ensure-visible scroll arithmetic for the grid. Pure so the
 * decision (scroll up just enough / down just enough / not at all) stays
 * host-testable; GridDeck feeds it live measurements taken from the
 * GridView itself, never cached offsets.
 */
object EnsureVisibleScroll {

    /**
     * Signed pixel delta for AbsListView.scrollListBy that brings
     * [itemTop, itemBottom] (content coordinates) fully inside the viewport
     * [viewportTop, viewportBottom] (content coordinates): negative scrolls
     * up just enough, positive scrolls down just enough, 0 means the item
     * is already fully visible and the grid must not move.
     */
    fun delta(itemTop: Int, itemBottom: Int, viewportTop: Int, viewportBottom: Int): Int =
        when {
            itemTop < viewportTop -> itemTop - viewportTop
            itemBottom > viewportBottom -> itemBottom - viewportBottom
            else -> 0
        }
}
