package com.visorcraft.blackpearl.ui

import com.visorcraft.blackpearl.state.DeckState
import org.junit.Assert.assertEquals
import org.junit.Test

class DisplayRoleTest {

    @Test
    fun `display 0 is primary by default`() {
        val s = DeckState()
        assertEquals(DisplayRole.PRIMARY, DisplayRole.roleFor(0, s))
        assertEquals(DisplayRole.COMPANION, DisplayRole.roleFor(1, s))
    }

    @Test
    fun `roles invert after swap`() {
        val s = DeckState()
        s.swapDisplays()
        assertEquals(DisplayRole.COMPANION, DisplayRole.roleFor(0, s))
        assertEquals(DisplayRole.PRIMARY, DisplayRole.roleFor(1, s))
    }
}
