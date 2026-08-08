package com.visorcraft.ghostgalleon.library

import com.visorcraft.ghostgalleon.rom.RomEntry
import com.visorcraft.ghostgalleon.settings.SlotKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryBrowseTest {

    private fun rom(
        platform: String,
        name: String,
        visible: Boolean = true,
    ) = RomEntry(
        id = "$platform:$name.rom",
        name = name,
        platformId = platform,
        uri = "content://x/$name",
        path = "/storage/x/$name.rom",
        visibleInUi = visible,
    )

    private val library = listOf(
        rom("snes", "Zelda"),
        rom("snes", "Mario"),
        rom("3ds", "Pokemon"),
        rom("nds", "Hidden", visible = false),
        rom("switch", "BotW"),
    )

    @Test
    fun `filterByPlatform keeps only matching visible roms`() {
        val snes = LibraryBrowse.filterByPlatform(library, "snes")
        assertEquals(listOf("Zelda", "Mario"), snes.map { it.name })
        assertTrue(LibraryBrowse.filterByPlatform(library, null).none { !it.visibleInUi })
    }

    @Test
    fun `searchRoms matches name case-insensitively`() {
        val hits = LibraryBrowse.searchRoms(library, "zel")
        assertEquals(listOf("Zelda"), hits.map { it.name })
    }

    @Test
    fun `orderByRecent sorts by last launch descending`() {
        val keys = listOf("a", "b", "c")
        val last = mapOf("b" to 300L, "a" to 100L)
        assertEquals(listOf("b", "a", "c"), LibraryBrowse.orderByRecent(keys, last))
    }

    @Test
    fun `browseRoms RECENT returns launched roms newest first`() {
        val last = mapOf(
            SlotKey.rom("switch:BotW.rom") to 200L,
            SlotKey.rom("snes:Zelda.rom") to 100L,
        )
        val recent = LibraryBrowse.browseRoms(
            library,
            LibraryBrowse.BrowseQuery(mode = LibraryBrowse.Mode.RECENT),
            lastLaunchedMs = last,
        )
        assertEquals(listOf("BotW", "Zelda"), recent.map { it.name })
    }

    @Test
    fun `browseRoms FAVORITES intersects with favorites set`() {
        val favs = setOf(SlotKey.rom("3ds:Pokemon.rom"), SlotKey.rom("missing:x"))
        val out = LibraryBrowse.browseRoms(
            library,
            LibraryBrowse.BrowseQuery(mode = LibraryBrowse.Mode.FAVORITES),
            favorites = favs,
        )
        assertEquals(listOf("Pokemon"), out.map { it.name })
    }

    @Test
    fun `browseRoms combines platform filter and search`() {
        val out = LibraryBrowse.browseRoms(
            library,
            LibraryBrowse.BrowseQuery(platformId = "snes", text = "mar"),
        )
        assertEquals(listOf("Mario"), out.map { it.name })
    }

    @Test
    fun `presentPlatforms lists distinct sorted ids`() {
        assertEquals(listOf("3ds", "snes", "switch"), LibraryBrowse.presentPlatforms(library))
    }

    @Test
    fun `browseRoms COLLECTION filters to named membership`() {
        val cols = mapOf(
            "RPGs" to listOf(SlotKey.rom("snes:Zelda.rom"), SlotKey.rom("3ds:Pokemon.rom")),
            "Empty" to emptyList(),
        )
        val out = LibraryBrowse.browseRoms(
            library,
            LibraryBrowse.BrowseQuery(
                mode = LibraryBrowse.Mode.COLLECTION,
                collectionName = "RPGs",
            ),
            collections = cols,
        )
        assertEquals(setOf("Zelda", "Pokemon"), out.map { it.name }.toSet())
    }

    @Test
    fun `presentCollectionRails sorts names`() {
        val rails = LibraryBrowse.presentCollectionRails(
            mapOf("Zebra" to listOf("a"), "alpha" to listOf("b")),
        )
        assertEquals(listOf("alpha", "Zebra"), rails)
    }

    @Test
    fun `orderByPlaytime ranks positive times descending`() {
        val keys = listOf("a", "b", "c", "d")
        // Missing keys sort after explicit zeros (MIN_VALUE vs 0).
        val play = mapOf("c" to 500L, "a" to 100L, "d" to 0L)
        assertEquals(listOf("c", "a", "d", "b"), LibraryBrowse.orderByPlaytime(keys, play))
    }

    @Test
    fun `browseRoms MOST_PLAYED orders by playtime and drops zero`() {
        val play = mapOf(
            SlotKey.rom("snes:Mario.rom") to 900L,
            SlotKey.rom("snes:Zelda.rom") to 100L,
            SlotKey.rom("switch:BotW.rom") to 0L,
            SlotKey.rom("3ds:Pokemon.rom") to 400L,
        )
        val out = LibraryBrowse.browseRoms(
            library,
            LibraryBrowse.BrowseQuery(mode = LibraryBrowse.Mode.MOST_PLAYED),
            playtimeMs = play,
        )
        assertEquals(listOf("Mario", "Pokemon", "Zelda"), out.map { it.name })
    }

    @Test
    fun `browseRoms MOST_PLAYED respects platform filter`() {
        val play = mapOf(
            SlotKey.rom("snes:Mario.rom") to 50L,
            SlotKey.rom("3ds:Pokemon.rom") to 999L,
        )
        val out = LibraryBrowse.browseRoms(
            library,
            LibraryBrowse.BrowseQuery(
                mode = LibraryBrowse.Mode.MOST_PLAYED,
                platformId = "snes",
            ),
            playtimeMs = play,
        )
        assertEquals(listOf("Mario"), out.map { it.name })
    }

    @Test
    fun `orderByName is case-insensitive A-Z and stable`() {
        val names = listOf("zeta", "Alpha", "alpha2", "Mario")
        assertEquals(
            listOf("Alpha", "alpha2", "Mario", "zeta"),
            LibraryBrowse.orderByName(names) { it },
        )
    }

    @Test
    fun `isUnplayed treats missing and zero as unplayed`() {
        val last = mapOf("a" to 1L, "b" to 0L)
        assertTrue(LibraryBrowse.isUnplayed("b", last))
        assertTrue(LibraryBrowse.isUnplayed("missing", last))
        assertTrue(!LibraryBrowse.isUnplayed("a", last))
    }

    @Test
    fun `browseRoms ALPHA sorts by name`() {
        val out = LibraryBrowse.browseRoms(
            library,
            LibraryBrowse.BrowseQuery(mode = LibraryBrowse.Mode.ALPHA),
        )
        assertEquals(listOf("BotW", "Mario", "Pokemon", "Zelda"), out.map { it.name })
    }

    @Test
    fun `browseRoms UNPLAYED drops launched and sorts A-Z`() {
        val last = mapOf(
            SlotKey.rom("snes:Zelda.rom") to 100L,
            SlotKey.rom("switch:BotW.rom") to 50L,
        )
        val out = LibraryBrowse.browseRoms(
            library,
            LibraryBrowse.BrowseQuery(mode = LibraryBrowse.Mode.UNPLAYED),
            lastLaunchedMs = last,
        )
        assertEquals(listOf("Mario", "Pokemon"), out.map { it.name })
    }

    @Test
    fun `browseRoms UNPLAYED respects platform filter`() {
        val last = mapOf(SlotKey.rom("snes:Mario.rom") to 10L)
        val out = LibraryBrowse.browseRoms(
            library,
            LibraryBrowse.BrowseQuery(
                mode = LibraryBrowse.Mode.UNPLAYED,
                platformId = "snes",
            ),
            lastLaunchedMs = last,
        )
        assertEquals(listOf("Zelda"), out.map { it.name })
    }

    @Test
    fun `letterBucket maps first letter and non-letters to hash`() {
        assertEquals('Z', LibraryBrowse.letterBucket("zelda"))
        assertEquals('A', LibraryBrowse.letterBucket("  Alpha"))
        assertEquals('#', LibraryBrowse.letterBucket("007 Bond"))
        assertEquals('#', LibraryBrowse.letterBucket(""))
        assertEquals('#', LibraryBrowse.letterBucket("  "))
    }

    @Test
    fun `presentLetterIndex lists A-Z then hash only when present`() {
        val labels = listOf("Zelda", "Mario", "007", "alpha", "Pokemon")
        assertEquals(
            listOf('A', 'M', 'P', 'Z', '#'),
            LibraryBrowse.presentLetterIndex(labels),
        )
        assertEquals(emptyList<Char>(), LibraryBrowse.presentLetterIndex(emptyList()))
        assertEquals(listOf('B'), LibraryBrowse.presentLetterIndex(listOf("BotW")))
    }

    @Test
    fun `firstIndexForLetter finds first matching bucket`() {
        val labels = listOf("Alpha", "BotW", "Mario", "Zelda", "007")
        assertEquals(0, LibraryBrowse.firstIndexForLetter(labels, 'A'))
        assertEquals(0, LibraryBrowse.firstIndexForLetter(labels, 'a'))
        assertEquals(2, LibraryBrowse.firstIndexForLetter(labels, 'M'))
        assertEquals(4, LibraryBrowse.firstIndexForLetter(labels, '#'))
        assertEquals(-1, LibraryBrowse.firstIndexForLetter(labels, 'Q'))
    }
}
