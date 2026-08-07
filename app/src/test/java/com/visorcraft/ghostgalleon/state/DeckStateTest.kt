package com.visorcraft.ghostgalleon.state

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeckStateTest {

    @Test
    fun `defaults are grid mode, display 0 primary, no selection`() {
        val s = DeckState()
        assertEquals(UIMode.GRID, s.mode)
        assertEquals(0, s.primaryDisplayId)
        assertNull(s.selectedKey)
    }

    @Test
    fun `toggleMode flips between GRID and GAME`() {
        val s = DeckState()
        s.toggleMode()
        assertEquals(UIMode.GAME, s.mode)
        s.toggleMode()
        assertEquals(UIMode.GRID, s.mode)
    }

    @Test
    fun `swapDisplays flips primary between 0 and 1`() {
        val s = DeckState()
        s.swapDisplays()
        assertEquals(1, s.primaryDisplayId)
        s.swapDisplays()
        assertEquals(0, s.primaryDisplayId)
    }

    @Test
    fun `setPrimaryDisplayId rejects invalid ids`() {
        val s = DeckState()
        try {
            s.setPrimaryDisplayId(7)
            org.junit.Assert.fail("expected IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
        }
        assertEquals(0, s.primaryDisplayId)
    }

    @Test
    fun `select tags lastChange as SELECTION`() {
        val s = DeckState()
        s.setMode(UIMode.GAME) // muddy the tag first
        s.select("com.example.app")
        assertEquals(DeckState.Change.SELECTION, s.lastChange)
    }

    @Test
    fun `toggleMode tags lastChange as MODE`() {
        val s = DeckState()
        s.select("com.example.app")
        s.toggleMode()
        assertEquals(DeckState.Change.MODE, s.lastChange)
    }

    @Test
    fun `swapDisplays tags lastChange as DISPLAY`() {
        val s = DeckState()
        s.select("com.example.app")
        s.swapDisplays()
        assertEquals(DeckState.Change.DISPLAY, s.lastChange)
    }

    @Test
    fun `listeners fire on every mutation and can be removed`() {
        val s = DeckState()
        var calls = 0
        val listener = DeckState.DeckStateListener { calls++ }
        s.addListener(listener)
        s.setMode(UIMode.GAME)
        s.swapDisplays()
        s.select("com.example.app")
        assertEquals(3, calls)
        assertEquals("com.example.app", s.selectedKey)
        s.removeListener(listener)
        s.select(null)
        assertEquals(3, calls)
    }

    @Test
    fun `selectSlot updates slot and key and tags SELECTION`() {
        val s = DeckState()
        s.setMode(UIMode.GAME) // muddy the tag first
        s.selectSlot(4, "com.example.app")
        assertEquals(4, s.selectedSlot)
        assertEquals("com.example.app", s.selectedKey)
        assertEquals(DeckState.Change.SELECTION, s.lastChange)
    }

    @Test
    fun `selectSlot accepts a blank slot key and notifies only on change`() {
        val s = DeckState()
        var calls = 0
        s.addListener(DeckState.DeckStateListener { calls++ })
        s.selectSlot(2, null)
        assertEquals(2, s.selectedSlot)
        assertNull(s.selectedKey)
        assertEquals(1, calls)
        // Same slot and key: no re-notification.
        s.selectSlot(2, null)
        assertEquals(1, calls)
        // Moving between two blank slots still notifies (slot changed).
        s.selectSlot(3, null)
        assertEquals(2, calls)
    }

    @Test
    fun `select does not move the slot selection`() {
        val s = DeckState()
        s.selectSlot(5, "com.example.app")
        s.select("com.other.app")
        assertEquals(5, s.selectedSlot)
        assertEquals("com.other.app", s.selectedKey)
    }

    @Test
    fun `focusDock sets the dock slot and tags SELECTION`() {
        val s = DeckState()
        s.setMode(UIMode.GAME) // muddy the tag first
        s.focusDock(2)
        assertEquals(2, s.dockSlot)
        assertEquals(DeckState.Change.SELECTION, s.lastChange)
    }

    @Test
    fun `focusDock leaves the grid selection and key untouched`() {
        val s = DeckState()
        s.selectSlot(7, "com.example.app")
        s.focusDock(1)
        assertEquals(7, s.selectedSlot)
        assertEquals("com.example.app", s.selectedKey)
        assertEquals(1, s.dockSlot)
    }

    @Test
    fun `focusDock notifies only on change`() {
        val s = DeckState()
        var calls = 0
        s.addListener(DeckState.DeckStateListener { calls++ })
        s.focusDock(3)
        assertEquals(1, calls)
        s.focusDock(3)
        assertEquals(1, calls)
        s.focusDock(4)
        assertEquals(2, calls)
    }

    @Test
    fun `selectSlot clears the dock focus and notifies`() {
        val s = DeckState()
        s.selectSlot(6, "com.example.app")
        s.focusDock(0)
        var calls = 0
        s.addListener(DeckState.DeckStateListener { calls++ })
        // NAV UP from the dock re-selects the SAME grid slot: it must
        // still notify, because the dock focus changed.
        s.selectSlot(6, "com.example.app")
        assertEquals(1, calls)
        assertNull(s.dockSlot)
    }

    @Test
    fun `select clears the dock focus and notifies`() {
        val s = DeckState()
        s.select("com.example.app")
        s.focusDock(2)
        var calls = 0
        s.addListener(DeckState.DeckStateListener { calls++ })
        s.select("com.example.app")
        assertEquals(1, calls)
        assertNull(s.dockSlot)
    }
}
