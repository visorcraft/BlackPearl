package com.visorcraft.ghostgalleon.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowseFeedbackTest {

    @Test
    fun `emptyHint prioritizes search over mode`() {
        val q = LibraryBrowse.BrowseQuery(
            mode = LibraryBrowse.Mode.PLAYED_THIS_WEEK,
            text = "zelda",
        )
        assertEquals("No matches for \"zelda\"", BrowseFeedback.emptyHint(q))
    }

    @Test
    fun `emptyHint genre and platform`() {
        assertEquals(
            "No titles in RPG",
            BrowseFeedback.emptyHint(LibraryBrowse.BrowseQuery(genre = "RPG")),
        )
        assertEquals(
            "No titles on snes",
            BrowseFeedback.emptyHint(LibraryBrowse.BrowseQuery(platformId = "snes")),
        )
    }

    @Test
    fun `emptyHint mode messages`() {
        assertEquals(
            "Nothing played this week",
            BrowseFeedback.emptyHint(
                LibraryBrowse.BrowseQuery(mode = LibraryBrowse.Mode.PLAYED_THIS_WEEK),
            ),
        )
        assertEquals(
            "Nothing played this month",
            BrowseFeedback.emptyHint(
                LibraryBrowse.BrowseQuery(mode = LibraryBrowse.Mode.PLAYED_THIS_MONTH),
            ),
        )
        assertEquals(
            "\"Speedrun\" is empty",
            BrowseFeedback.emptyHint(
                LibraryBrowse.BrowseQuery(
                    mode = LibraryBrowse.Mode.COLLECTION,
                    collectionName = "Speedrun",
                ),
            ),
        )
        assertNull(BrowseFeedback.emptyHint(LibraryBrowse.BrowseQuery()))
    }

    @Test
    fun `searchApplied formats count and clear`() {
        assertEquals("Search cleared", BrowseFeedback.searchApplied(0, "  "))
        assertEquals("No matches for \"xyz\"", BrowseFeedback.searchApplied(0, "xyz"))
        assertEquals("1 match for \"zel\"", BrowseFeedback.searchApplied(1, "zel"))
        assertEquals("12 matches for \"a\"", BrowseFeedback.searchApplied(12, "a"))
    }

    @Test
    fun `preferFullCount marks interleaved modes`() {
        assertTrue(BrowseFeedback.preferFullCount(LibraryBrowse.Mode.RECENT))
        assertTrue(BrowseFeedback.preferFullCount(LibraryBrowse.Mode.FAVORITES))
        assertTrue(!BrowseFeedback.preferFullCount(LibraryBrowse.Mode.ALL))
    }
}
