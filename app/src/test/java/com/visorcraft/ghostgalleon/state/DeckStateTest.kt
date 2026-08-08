package com.visorcraft.ghostgalleon.state

import com.visorcraft.ghostgalleon.library.LibraryBrowse
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
    fun `swapDisplaysWith flips to other topology id`() {
        val s = DeckState()
        s.swapDisplaysWith(1)
        assertEquals(1, s.primaryDisplayId)
        s.swapDisplaysWith(0)
        assertEquals(0, s.primaryDisplayId)
        s.swapDisplaysWith(20)
        assertEquals(20, s.primaryDisplayId)
    }

    @Test
    fun `setPrimaryDisplayId accepts any topology id`() {
        val s = DeckState()
        s.setPrimaryDisplayId(7)
        assertEquals(7, s.primaryDisplayId)
        s.setPrimaryDisplayId(20)
        assertEquals(20, s.primaryDisplayId)
    }

    @Test
    fun `ensurePrimaryIn realigns when id not in topology`() {
        val s = DeckState()
        s.setPrimaryDisplayId(99)
        s.ensurePrimaryIn(listOf(0, 1), preferred = 1)
        assertEquals(1, s.primaryDisplayId)
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
    fun `swapDisplaysWith tags lastChange as DISPLAY`() {
        val s = DeckState()
        s.select("com.example.app")
        s.swapDisplaysWith(1)
        assertEquals(DeckState.Change.DISPLAY, s.lastChange)
    }

    @Test
    fun `notifySelectionRefresh is SELECTION not SETTINGS`() {
        val s = DeckState()
        s.setMode(UIMode.GAME)
        s.notifySelectionRefresh()
        assertEquals(DeckState.Change.SELECTION, s.lastChange)
    }

    @Test
    fun `notifyChanged is SETTINGS full rebuild tag`() {
        val s = DeckState()
        s.select("com.example.app")
        s.notifyChanged()
        assertEquals(DeckState.Change.SETTINGS, s.lastChange)
    }

    @Test
    fun `setLibraryBrowse clears platform when All query applied`() {
        val s = DeckState()
        s.setLibraryBrowse(
            LibraryBrowse.BrowseQuery(
                mode = LibraryBrowse.Mode.ALL,
                platformId = "nds",
            ),
        )
        assertEquals("nds", s.libraryBrowse.platformId)
        s.setLibraryBrowse(LibraryBrowse.BrowseQuery())
        assertEquals(LibraryBrowse.Mode.ALL, s.libraryBrowse.mode)
        assertNull(s.libraryBrowse.platformId)
        assertEquals("", s.libraryBrowse.text)
        assertNull(s.libraryBrowse.collectionName)
        assertEquals(DeckState.Change.SETTINGS, s.lastChange)
    }

    @Test
    fun `setLibraryBrowse force re-notifies when query unchanged`() {
        val s = DeckState()
        var calls = 0
        s.addListener(DeckState.DeckStateListener { calls++ })
        val q = LibraryBrowse.BrowseQuery(platformId = "nds")
        s.setLibraryBrowse(q)
        assertEquals(1, calls)
        s.setLibraryBrowse(q)
        assertEquals(1, calls) // equality early-return
        s.setLibraryBrowse(q, force = true)
        assertEquals(2, calls)
        assertEquals(DeckState.Change.SETTINGS, s.lastChange)
    }

    @Test
    fun `select force re-notifies when key unchanged`() {
        val s = DeckState()
        var calls = 0
        s.addListener(DeckState.DeckStateListener { calls++ })
        s.select("rom:nds:a.nds")
        assertEquals(1, calls)
        s.select("rom:nds:a.nds")
        assertEquals(1, calls)
        s.select("rom:nds:a.nds", force = true)
        assertEquals(2, calls)
        assertEquals(DeckState.Change.SELECTION, s.lastChange)
    }

    @Test
    fun `listeners fire on every mutation and can be removed`() {
        val s = DeckState()
        var calls = 0
        val listener = DeckState.DeckStateListener { calls++ }
        s.addListener(listener)
        s.setMode(UIMode.GAME)
        s.swapDisplaysWith(1)
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
