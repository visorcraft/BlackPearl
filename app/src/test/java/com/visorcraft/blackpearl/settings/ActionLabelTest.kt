package com.visorcraft.blackpearl.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ActionLabelTest {

    @Test
    fun `remappable actions have user-friendly labels`() {
        assertEquals("Confirm / Launch", Action.CONFIRM.label())
        assertEquals("Back", Action.BACK.label())
        assertEquals("Swap screens", Action.SWAP_SCREENS.label())
        assertEquals("Toggle mode", Action.TOGGLE_MODE.label())
        assertEquals("Open settings", Action.OPEN_SETTINGS.label())
        assertEquals("Page left (L1)", Action.PAGE_PREV.label())
        assertEquals("Page right (R1)", Action.PAGE_NEXT.label())
    }

    @Test
    fun `no user-visible label is a raw enum name`() {
        Action.entries.forEach { action ->
            assertNotEquals(action.name, action.label())
        }
    }
}
