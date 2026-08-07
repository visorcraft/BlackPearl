package com.visorcraft.ghostgalleon.ui.deck

import com.visorcraft.ghostgalleon.settings.Action
import org.junit.Assert.assertEquals
import org.junit.Test

class GridNavigationTest {

    private val nav = GridNavigation(itemCount = 12, columns = 5, visibleRows = 2)

    @Test
    fun `left and right clamp at row edges of the list`() {
        assertEquals(0, nav.move(0, Action.NAV_LEFT))
        assertEquals(1, nav.move(0, Action.NAV_RIGHT))
        assertEquals(11, nav.move(11, Action.NAV_RIGHT))
    }

    @Test
    fun `up and down move by one row and clamp at bounds`() {
        assertEquals(0, nav.move(3, Action.NAV_UP))
        assertEquals(8, nav.move(3, Action.NAV_DOWN))
        assertEquals(11, nav.move(10, Action.NAV_DOWN))
    }

    @Test
    fun `page actions jump by one full page`() {
        // 5 columns x 2 visible rows = 10 slots per page; L1/R1 flip a
        // whole page in both scroll directions' modes.
        val paged = GridNavigation(itemCount = 24, columns = 5, visibleRows = 2)
        assertEquals(13, paged.move(3, Action.PAGE_NEXT))
        assertEquals(3, paged.move(13, Action.PAGE_PREV))
    }

    @Test
    fun `page actions clamp at the grid bounds`() {
        assertEquals(23, GridNavigation(itemCount = 24, columns = 5, visibleRows = 2)
            .move(22, Action.PAGE_NEXT))
        assertEquals(0, GridNavigation(itemCount = 24, columns = 5, visibleRows = 2)
            .move(2, Action.PAGE_PREV))
    }

    @Test
    fun `navigation runs over the full slot count, blanks included`() {
        // The curated grid navigates slots, not entries: even with a single
        // filled slot every position in the fixed slot range is reachable.
        val slotNav = GridNavigation(itemCount = 12, columns = 4, visibleRows = 2)
        assertEquals(11, slotNav.move(10, Action.NAV_RIGHT))
        assertEquals(11, slotNav.move(8, Action.NAV_DOWN))
        assertEquals(0, slotNav.move(2, Action.NAV_UP))
    }
}
