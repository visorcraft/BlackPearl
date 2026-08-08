package com.visorcraft.ghostgalleon.library

import com.visorcraft.ghostgalleon.rom.RomEntry
import com.visorcraft.ghostgalleon.settings.SlotKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryDiscoveryTest {

    private fun rom(platform: String, name: String) = RomEntry(
        id = "$platform:$name.rom",
        name = name,
        platformId = platform,
        uri = "content://x/$name",
        path = "/storage/x/$name.rom",
    )

    @Test
    fun `pickRandom returns the item at injected index`() {
        val items = listOf("a", "b", "c", "d")
        assertEquals("c", LibraryBrowse.pickRandom(items) { 2 })
        assertEquals("a", LibraryBrowse.pickRandom(items) { 0 })
    }

    @Test
    fun `pickRandom empty list is null`() {
        assertNull(LibraryBrowse.pickRandom(emptyList<String>()) { error("no") })
    }

    @Test
    fun `pickRandom out of range index is null`() {
        assertNull(LibraryBrowse.pickRandom(listOf("x")) { 5 })
    }

    @Test
    fun `continueKey returns most recently launched present key`() {
        val keys = listOf(
            SlotKey.rom("snes:a.rom"),
            SlotKey.rom("snes:b.rom"),
            "com.example.app",
        )
        val last = mapOf(
            SlotKey.rom("snes:a.rom") to 100L,
            SlotKey.rom("snes:b.rom") to 300L,
            "com.example.app" to 200L,
            "gone" to 999L,
        )
        assertEquals(SlotKey.rom("snes:b.rom"), LibraryBrowse.continueKey(keys, last))
    }

    @Test
    fun `continueKey null when no overlap`() {
        assertNull(
            LibraryBrowse.continueKey(
                listOf("a"),
                mapOf("b" to 1L),
            ),
        )
    }

    @Test
    fun `continueCandidates excludes selected and orders newest first`() {
        val keys = listOf("a", "b", "c")
        val last = mapOf("a" to 10L, "b" to 30L, "c" to 20L)
        assertEquals(listOf("b", "c", "a"), LibraryBrowse.continueCandidates(keys, last))
        assertEquals(listOf("b", "c"), LibraryBrowse.continueCandidates(keys, last, excludeKey = "a"))
    }

    @Test
    fun `continueAfterSwipe cycles then clears past end`() {
        val c = listOf("newest", "mid", "oldest")
        assertEquals("mid", LibraryBrowse.continueAfterSwipe(c, "newest", delta = +1))
        assertEquals("oldest", LibraryBrowse.continueAfterSwipe(c, "mid", delta = +1))
        assertNull(LibraryBrowse.continueAfterSwipe(c, "oldest", delta = +1))
        assertEquals("mid", LibraryBrowse.continueAfterSwipe(c, "oldest", delta = -1))
        assertNull(LibraryBrowse.continueAfterSwipe(c, "newest", delta = -1))
        // Solo candidate: any swipe clears.
        assertNull(LibraryBrowse.continueAfterSwipe(listOf("only"), "only", delta = +1))
        assertNull(LibraryBrowse.continueAfterSwipe(listOf("only"), "only", delta = -1))
    }

    @Test
    fun `applyContinueSwipe promote and clear`() {
        val base = mapOf("a" to 10L, "b" to 20L)
        val cleared = LibraryBrowse.applyContinueSwipe(base, current = "b", next = null, nowMs = 99L)
        assertEquals(mapOf("a" to 10L), cleared)
        val promoted = LibraryBrowse.applyContinueSwipe(base, current = "b", next = "a", nowMs = 99L)
        assertTrue(promoted.getValue("a") > promoted.getValue("b"))
        assertEquals("a", LibraryBrowse.continueKey(listOf("a", "b"), promoted))
    }

    @Test
    fun `coldStartKey prefers first grid slot over last launched`() {
        // Regression: AppVerifier BG as last-launched stole the hero from Eden
        // in grid slot 0.
        val grid = listOf("com.eden.emu", "com.other.app", null)
        val last = mapOf(
            "com.appverifier.bg" to 9_999_999L,
            "com.eden.emu" to 1L,
        )
        assertEquals(
            "com.eden.emu",
            LibraryBrowse.coldStartKey(gridSlots = grid, lastLaunchedMs = last),
        )
    }

    @Test
    fun `coldStartKey falls back to continue when grid empty`() {
        val last = mapOf("com.recent.app" to 100L, "com.older.app" to 50L)
        assertEquals(
            "com.recent.app",
            LibraryBrowse.coldStartKey(
                gridSlots = listOf(null, null),
                dockSlots = emptyList(),
                lastLaunchedMs = last,
            ),
        )
    }

    @Test
    fun `coldStartKey uses dock when grid empty`() {
        assertEquals(
            "com.dock.app",
            LibraryBrowse.coldStartKey(
                gridSlots = listOf(null),
                dockSlots = listOf(null, "com.dock.app"),
                lastLaunchedMs = mapOf("com.other" to 99L),
            ),
        )
    }

    @Test
    fun `pickRandom over browsed roms is deterministic with seed`() {
        val library = listOf(rom("snes", "Zelda"), rom("snes", "Mario"), rom("3ds", "Pokemon"))
        val browsed = LibraryBrowse.browseRoms(
            library,
            LibraryBrowse.BrowseQuery(mode = LibraryBrowse.Mode.ALL),
        )
        val keys = browsed.map { SlotKey.rom(it.id) }
        // Fake RNG always picks last index.
        val pick = LibraryBrowse.pickRandom(keys) { size -> size - 1 }
        assertEquals(SlotKey.rom("3ds:Pokemon.rom"), pick)
    }
}
