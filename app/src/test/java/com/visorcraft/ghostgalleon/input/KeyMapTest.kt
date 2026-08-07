package com.visorcraft.ghostgalleon.input

import com.visorcraft.ghostgalleon.settings.Action
import com.visorcraft.ghostgalleon.settings.Settings
import org.junit.Assert.assertEquals
import org.junit.Test

class KeyMapTest {

    @Test
    fun `default map resolves gamepad and dpad keys`() {
        val s = Settings.DEFAULT
        assertEquals(Action.CONFIRM, KeyMap.resolve(96, s))   // BUTTON_A
        assertEquals(Action.BACK, KeyMap.resolve(97, s))      // BUTTON_B
        assertEquals(Action.SWAP_SCREENS, KeyMap.resolve(99, s))
        assertEquals(Action.TOGGLE_MODE, KeyMap.resolve(100, s))
        assertEquals(Action.NAV_UP, KeyMap.resolve(19, s))
        assertEquals(Action.PAGE_NEXT, KeyMap.resolve(103, s))
    }

    @Test
    fun `unmapped key resolves to NONE`() {
        assertEquals(Action.NONE, KeyMap.resolve(999, Settings.DEFAULT))
    }

    @Test
    fun `custom remap overrides defaults`() {
        val s = Settings.DEFAULT.copy(
            keyMap = Settings.DEFAULT_KEY_MAP + (99 to Action.OPEN_SETTINGS)
        )
        assertEquals(Action.OPEN_SETTINGS, KeyMap.resolve(99, s))
    }
}
