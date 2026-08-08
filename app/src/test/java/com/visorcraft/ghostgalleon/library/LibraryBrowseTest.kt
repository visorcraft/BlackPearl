package com.visorcraft.ghostgalleon.library

import com.visorcraft.ghostgalleon.rom.RomEntry
import com.visorcraft.ghostgalleon.settings.SlotKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryBrowseTest {

    private fun rom(
        platform: String,
        name: String,
        visible: Boolean = true,
        genre: String? = null,
        developer: String? = null,
        year: String? = null,
        description: String? = null,
    ) = RomEntry(
        id = "$platform:$name.rom",
        name = name,
        platformId = platform,
        uri = "content://x/$name",
        path = "/storage/x/$name.rom",
        visibleInUi = visible,
        genre = genre,
        developer = developer,
        year = year,
        description = description,
    )

    private val library = listOf(
        rom("snes", "Zelda", genre = "Action / Adventure", developer = "Nintendo", year = "1991"),
        rom("snes", "Mario", genre = "Platform", developer = "Nintendo", year = "1990"),
        rom("3ds", "Pokemon", genre = "RPG", developer = "Game Freak", year = "2013"),
        rom("nds", "Hidden", visible = false, genre = "RPG"),
        rom(
            "switch", "BotW", genre = "Action, Adventure",
            developer = "Nintendo EPD", year = "2017",
            description = "Open-world exploration on Hyrule",
        ),
    )

    @Test
    fun `filterByPlatform keeps only matching visible roms`() {
        val snes = LibraryBrowse.filterByPlatform(library, "snes")
        assertEquals(listOf("Zelda", "Mario"), snes.map { it.name })
        assertTrue(LibraryBrowse.filterByPlatform(library, null).none { !it.visibleInUi })
    }

    @Test
    fun `labeledChip appends positive counts only`() {
        assertEquals("Fav", LibraryBrowse.labeledChip("Fav", 0))
        assertEquals("Fav · 3", LibraryBrowse.labeledChip("Fav", 3))
        assertEquals("SNES · 12", LibraryBrowse.labeledChip("SNES", 12))
        assertEquals("", LibraryBrowse.labeledChip("  ", 0))
    }

    @Test
    fun `recentCount and window and top counts`() {
        val last = mapOf(
            "a" to 100L,
            "b" to 0L,
            "c" to 50L,
        )
        assertEquals(2, LibraryBrowse.recentCount(last))
        assertEquals(0, LibraryBrowse.recentCount(emptyMap()))
        // now must exceed MONTH_WINDOW so older stamps stay positive.
        val now = 2_000_000_000_000L
        val week = LibraryBrowse.WEEK_WINDOW_MS
        val stamps = mapOf(
            "fresh" to now - 1_000L,
            "old" to now - week - 5_000L,
        )
        assertEquals(
            1,
            LibraryBrowse.playedInWindowCount(stamps, nowMs = now, windowMs = week),
        )
        assertEquals(
            2,
            LibraryBrowse.playedInWindowCount(
                stamps, nowMs = now, windowMs = LibraryBrowse.MONTH_WINDOW_MS,
            ),
        )
        assertEquals(
            2,
            LibraryBrowse.topPlayedCount(mapOf("x" to 10L, "y" to 0L, "z" to 5L)),
        )
    }

    @Test
    fun `presentPlatformCounts ranks listed roms and hides invisible`() {
        val counts = LibraryBrowse.presentPlatformCounts(library).toMap()
        assertEquals(2, counts["snes"])
        assertEquals(1, counts["switch"])
        assertTrue("nds" !in counts) // Hidden not listed
        val withoutHidden = LibraryBrowse.presentPlatformCounts(
            library,
            hiddenRomIds = setOf("3ds:Pokemon.rom"),
        ).toMap()
        assertTrue("3ds" !in withoutHidden)
    }

    @Test
    fun `searchRoms matches name case-insensitively`() {
        val hits = LibraryBrowse.searchRoms(library, "zel")
        assertEquals(listOf("Zelda"), hits.map { it.name })
    }

    @Test
    fun `searchRoms matches genre developer year and description`() {
        assertEquals(
            setOf("Zelda", "BotW"),
            LibraryBrowse.searchRoms(library, "adventure").map { it.name }.toSet(),
        )
        assertEquals(
            listOf("Pokemon"),
            LibraryBrowse.searchRoms(library, "game freak").map { it.name },
        )
        assertEquals(
            listOf("BotW"),
            LibraryBrowse.searchRoms(library, "2017").map { it.name },
        )
        assertEquals(
            listOf("BotW"),
            LibraryBrowse.searchRoms(library, "hyrule").map { it.name },
        )
        // Still matches platform id
        assertEquals(
            setOf("Zelda", "Mario"),
            LibraryBrowse.searchRoms(library, "snes").map { it.name }.toSet(),
        )
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

    @Test
    fun `orderByInstallTime ranks newest installs first`() {
        val keys = listOf("old", "new", "mid", "unknown")
        val install = mapOf("new" to 300L, "mid" to 200L, "old" to 100L)
        assertEquals(
            listOf("new", "mid", "old", "unknown"),
            LibraryBrowse.orderByInstallTime(keys, install),
        )
    }

    @Test
    fun `browseRoms RECENTLY_INSTALLED is app-only empty for ROMs`() {
        val out = LibraryBrowse.browseRoms(
            library,
            LibraryBrowse.BrowseQuery(mode = LibraryBrowse.Mode.RECENTLY_INSTALLED),
        )
        assertEquals(emptyList<String>(), out.map { it.name })
    }

    @Test
    fun `browseRoms excludes user-hidden ROM ids`() {
        val out = LibraryBrowse.browseRoms(
            library,
            LibraryBrowse.BrowseQuery(mode = LibraryBrowse.Mode.ALL),
            hiddenRomIds = setOf("snes:Zelda.rom", "switch:BotW.rom"),
        )
        assertEquals(listOf("Mario", "Pokemon"), out.map { it.name })
    }

    @Test
    fun `presentPlatforms respects hiddenRomIds`() {
        assertEquals(
            listOf("3ds"),
            LibraryBrowse.presentPlatforms(
                library,
                hiddenRomIds = setOf(
                    "snes:Zelda.rom",
                    "snes:Mario.rom",
                    "switch:BotW.rom",
                ),
            ),
        )
    }

    @Test
    fun `browseRoms GAMES lists all listed ROMs like ALL`() {
        val all = LibraryBrowse.browseRoms(
            library,
            LibraryBrowse.BrowseQuery(mode = LibraryBrowse.Mode.ALL),
        )
        val games = LibraryBrowse.browseRoms(
            library,
            LibraryBrowse.BrowseQuery(mode = LibraryBrowse.Mode.GAMES),
        )
        assertEquals(all.map { it.name }.toSet(), games.map { it.name }.toSet())
    }

    @Test
    fun `filterGameApps keeps only game-flagged items`() {
        data class A(val name: String, val game: Boolean)
        val items = listOf(A("a", true), A("b", false), A("c", true))
        assertEquals(
            listOf("a", "c"),
            LibraryBrowse.filterGameApps(items) { it.game }.map { it.name },
        )
    }

    @Test
    fun `topPlayedKey returns highest positive playtime`() {
        val play = mapOf("a" to 10L, "b" to 50L, "c" to 0L)
        assertEquals("b", LibraryBrowse.topPlayedKey(play))
        assertEquals(null, LibraryBrowse.topPlayedKey(emptyMap()))
        assertEquals(null, LibraryBrowse.topPlayedKey(mapOf("x" to 0L)))
    }

    @Test
    fun `railQuery wraps mode for Quick Panel`() {
        assertEquals(
            LibraryBrowse.Mode.GAMES,
            LibraryBrowse.railQuery(LibraryBrowse.Mode.GAMES).mode,
        )
        assertEquals(
            LibraryBrowse.Mode.FAVORITES,
            LibraryBrowse.railQuery(LibraryBrowse.Mode.FAVORITES).mode,
        )
    }

    @Test
    fun `isPlayedSince and filterPlayedInWindow`() {
        // now must exceed WEEK_WINDOW_MS so the window start stays positive.
        val now = 2_000_000_000_000L
        val week = LibraryBrowse.WEEK_WINDOW_MS
        val last = mapOf(
            "fresh" to now - 1_000L,
            "old" to now - week - 1L,
            "edge" to now - week,
            "zero" to 0L,
        )
        assertTrue(LibraryBrowse.isPlayedSince("fresh", last, now - week))
        assertTrue(LibraryBrowse.isPlayedSince("edge", last, now - week))
        assertTrue(!LibraryBrowse.isPlayedSince("old", last, now - week))
        assertTrue(!LibraryBrowse.isPlayedSince("zero", last, now - week))
        assertTrue(!LibraryBrowse.isPlayedSince("missing", last, now - week))
        assertEquals(
            listOf("fresh", "edge"),
            LibraryBrowse.filterPlayedInWindow(
                listOf("old", "fresh", "edge", "zero", "missing"),
                last,
                nowMs = now,
            ),
        )
    }

    @Test
    fun `browseRoms PLAYED_THIS_WEEK keeps only week launches newest first`() {
        val now = 2_000_000_000_000L
        val week = LibraryBrowse.WEEK_WINDOW_MS
        val last = mapOf(
            SlotKey.rom("switch:BotW.rom") to now - 1_000L,
            SlotKey.rom("snes:Mario.rom") to now - week - 5_000L, // outside
            SlotKey.rom("snes:Zelda.rom") to now - 50_000L,
            SlotKey.rom("3ds:Pokemon.rom") to 0L,
        )
        val out = LibraryBrowse.browseRoms(
            library,
            LibraryBrowse.BrowseQuery(mode = LibraryBrowse.Mode.PLAYED_THIS_WEEK),
            lastLaunchedMs = last,
            nowMs = now,
        )
        assertEquals(listOf("BotW", "Zelda"), out.map { it.name })
        // Default nowMs=0 → nothing in window
        val empty = LibraryBrowse.browseRoms(
            library,
            LibraryBrowse.BrowseQuery(mode = LibraryBrowse.Mode.PLAYED_THIS_WEEK),
            lastLaunchedMs = last,
        )
        assertTrue(empty.isEmpty())
    }

    @Test
    fun `browseRoms PLAYED_THIS_MONTH keeps 30-day window newest first`() {
        val now = 2_000_000_000_000L
        val week = LibraryBrowse.WEEK_WINDOW_MS
        val month = LibraryBrowse.MONTH_WINDOW_MS
        val last = mapOf(
            SlotKey.rom("switch:BotW.rom") to now - 1_000L, // this week
            SlotKey.rom("snes:Zelda.rom") to now - week - 5_000L, // older than week, in month
            SlotKey.rom("snes:Mario.rom") to now - month - 5_000L, // outside month
            SlotKey.rom("3ds:Pokemon.rom") to 0L,
        )
        val out = LibraryBrowse.browseRoms(
            library,
            LibraryBrowse.BrowseQuery(mode = LibraryBrowse.Mode.PLAYED_THIS_MONTH),
            lastLaunchedMs = last,
            nowMs = now,
        )
        assertEquals(listOf("BotW", "Zelda"), out.map { it.name })
        val empty = LibraryBrowse.browseRoms(
            library,
            LibraryBrowse.BrowseQuery(mode = LibraryBrowse.Mode.PLAYED_THIS_MONTH),
            lastLaunchedMs = last,
        )
        assertTrue(empty.isEmpty())
    }

    @Test
    fun `genreTokens splits multi-genre strings`() {
        assertEquals(
            listOf("Action", "Adventure"),
            LibraryBrowse.genreTokens("Action / Adventure"),
        )
        assertEquals(listOf("RPG"), LibraryBrowse.genreTokens("RPG"))
        assertEquals(emptyList<String>(), LibraryBrowse.genreTokens(null))
        assertEquals(emptyList<String>(), LibraryBrowse.genreTokens("  "))
    }

    @Test
    fun `browseRoms genre filter matches any segment`() {
        val out = LibraryBrowse.browseRoms(
            library,
            LibraryBrowse.BrowseQuery(genre = "Adventure"),
        )
        assertEquals(setOf("Zelda", "BotW"), out.map { it.name }.toSet())
    }

    @Test
    fun `presentGenres ranks by frequency and respects hide`() {
        val genres = LibraryBrowse.presentGenres(library, limit = 10)
        assertTrue(genres.any { it.equals("Action", true) })
        assertTrue(genres.any { it.equals("Adventure", true) })
        // Action appears twice (Zelda + BotW) so should rank at or near top
        assertEquals("Action", genres.first())
        // Hide Pokemon — only RPG source among listed → RPG drops
        val withoutPokemon = LibraryBrowse.presentGenres(
            library,
            hiddenRomIds = setOf("3ds:Pokemon.rom"),
            limit = 10,
        )
        assertTrue(withoutPokemon.none { it.equals("RPG", true) })
    }

    @Test
    fun `presentGenreCounts pairs labels with frequencies`() {
        val counts = LibraryBrowse.presentGenreCounts(library, limit = 10).toMap()
        assertEquals(2, counts["Action"])
        assertEquals(2, counts["Adventure"])
        assertEquals(1, counts["Platform"])
        assertEquals(1, counts["RPG"])
        // Names still rank by frequency first
        assertEquals("Action", LibraryBrowse.presentGenreCounts(library).first().first)
    }

    @Test
    fun `presentDeveloperCounts and developer filter`() {
        val counts = LibraryBrowse.presentDeveloperCounts(library, limit = 10).toMap()
        assertEquals(2, counts["Nintendo"])
        assertEquals(1, counts["Game Freak"])
        assertEquals(1, counts["Nintendo EPD"])
        assertEquals("Nintendo", LibraryBrowse.presentDeveloperCounts(library).first().first)
        val filtered = LibraryBrowse.browseRoms(
            library,
            LibraryBrowse.BrowseQuery(developer = "Nintendo"),
        )
        assertEquals(listOf("Zelda", "Mario"), filtered.map { it.name })
        assertTrue(LibraryBrowse.matchesDeveloper(library[0], "nintendo"))
        assertFalse(LibraryBrowse.matchesDeveloper(library[0], "Sony"))
    }

    @Test
    fun `randomPool prefers filtered when non empty`() {
        assertEquals(
            listOf("a", "b"),
            LibraryBrowse.randomPool(listOf("a", "b"), listOf("x", "y")),
        )
        assertEquals(
            listOf("x", "y"),
            LibraryBrowse.randomPool(emptyList(), listOf("x", "y")),
        )
    }

    @Test
    fun `continueHistory caps newest first and history line`() {
        val last = mapOf(
            "a" to 10L,
            "b" to 30L,
            "c" to 20L,
            "gone" to 99L,
        )
        val available = listOf("a", "b", "c")
        assertEquals(
            listOf("b", "c", "a"),
            LibraryBrowse.continueHistory(available, last),
        )
        assertEquals(
            listOf("b"),
            LibraryBrowse.continueHistory(available, last, limit = 1),
        )
        assertEquals(
            emptyList<String>(),
            LibraryBrowse.continueHistory(available, last, limit = 0),
        )
        val now = 30L + 2 * 60_000L
        assertEquals(
            "Zelda · 2m ago",
            LibraryBrowse.continueHistoryLine("Zelda", 30L, now),
        )
        assertEquals(
            "Zelda",
            LibraryBrowse.continueHistoryLine("Zelda", null, now),
        )
    }
}
