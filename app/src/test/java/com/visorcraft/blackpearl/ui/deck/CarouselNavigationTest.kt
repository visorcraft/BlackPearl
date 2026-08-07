package com.visorcraft.blackpearl.ui.deck

import com.visorcraft.blackpearl.settings.Action
import org.junit.Assert.assertEquals
import org.junit.Test

class CarouselNavigationTest {

    private val nav = CarouselNavigation(itemCount = 8)

    @Test
    fun `left and right clamp at both ends`() {
        assertEquals(0, nav.move(0, Action.NAV_LEFT))
        assertEquals(1, nav.move(0, Action.NAV_RIGHT))
        assertEquals(7, nav.move(7, Action.NAV_RIGHT))
    }

    @Test
    fun `page actions mirror left and right`() {
        assertEquals(2, nav.move(3, Action.PAGE_PREV))
        assertEquals(4, nav.move(3, Action.PAGE_NEXT))
    }

    @Test
    fun `vertical and unrelated actions do not move`() {
        assertEquals(3, nav.move(3, Action.NAV_UP))
        assertEquals(3, nav.move(3, Action.NAV_DOWN))
        assertEquals(3, nav.move(3, Action.CONFIRM))
    }
}
