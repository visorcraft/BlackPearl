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
}
