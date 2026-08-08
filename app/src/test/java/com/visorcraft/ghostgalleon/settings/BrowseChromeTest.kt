package com.visorcraft.ghostgalleon.settings

import com.visorcraft.ghostgalleon.library.LibraryBrowse
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowseChromeTest {

    @Test
    fun `minimal defaults disallow power rails`() {
        val c = BrowseChrome.MINIMAL
        assertFalse(c.installedRail)
        assertFalse(c.gamesRail)
        assertFalse(c.topRail)
        assertFalse(c.weekRail)
        assertFalse(c.monthRail)
        assertFalse(c.alphaRail)
        assertFalse(c.unplayedRail)
        assertFalse(c.randomChip)
        assertFalse(c.genreChips)
        assertFalse(c.deckStatusPill)
        assertFalse(c.quickPanelBrowse)
        assertTrue(c.platformChips)
        assertTrue(c.collectionRails)
        assertTrue(c.isMinimal())
    }

    @Test
    fun `full enables all chrome`() {
        val c = BrowseChrome.FULL
        assertTrue(c.installedRail && c.gamesRail && c.topRail && c.alphaRail)
        assertTrue(c.weekRail && c.monthRail)
        assertTrue(c.unplayedRail && c.randomChip && c.genreChips)
        assertTrue(c.deckStatusPill && c.quickPanelBrowse)
        assertTrue(c.isFull())
    }

    @Test
    fun `sanitize drops disallowed modes to ALL`() {
        val c = BrowseChrome.MINIMAL
        val q = LibraryBrowse.BrowseQuery(mode = LibraryBrowse.Mode.GAMES)
        assertEquals(LibraryBrowse.Mode.ALL, c.sanitize(q).mode)
    }

    @Test
    fun `sanitize keeps core modes`() {
        val c = BrowseChrome.MINIMAL
        assertEquals(
            LibraryBrowse.Mode.RECENT,
            c.sanitize(LibraryBrowse.BrowseQuery(mode = LibraryBrowse.Mode.RECENT)).mode,
        )
        assertEquals(
            LibraryBrowse.Mode.FAVORITES,
            c.sanitize(LibraryBrowse.BrowseQuery(mode = LibraryBrowse.Mode.FAVORITES)).mode,
        )
    }

    @Test
    fun `sanitize clears genre when genre chips off`() {
        val c = BrowseChrome.MINIMAL
        val q = LibraryBrowse.BrowseQuery(genre = "RPG")
        assertEquals(null, c.sanitize(q).genre)
    }

    @Test
    fun `json round trip`() {
        val original = BrowseChrome.FULL.copy(platformChips = false)
        val back = BrowseChrome.fromJson(original.toJson())
        assertEquals(original, back)
    }

    @Test
    fun `null json yields minimal`() {
        assertEquals(BrowseChrome.MINIMAL, BrowseChrome.fromJson(null))
        assertEquals(BrowseChrome.MINIMAL, BrowseChrome.fromJson(JSONObject()))
    }

    @Test
    fun `allowsMode matches flags`() {
        val c = BrowseChrome.MINIMAL.copy(topRail = true)
        assertTrue(c.allowsMode(LibraryBrowse.Mode.MOST_PLAYED))
        assertFalse(c.allowsMode(LibraryBrowse.Mode.GAMES))
        assertFalse(c.allowsMode(LibraryBrowse.Mode.PLAYED_THIS_MONTH))
        assertTrue(
            BrowseChrome.MINIMAL.copy(monthRail = true)
                .allowsMode(LibraryBrowse.Mode.PLAYED_THIS_MONTH),
        )
    }
}
