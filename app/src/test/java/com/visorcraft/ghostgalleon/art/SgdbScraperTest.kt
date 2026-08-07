package com.visorcraft.ghostgalleon.art

import com.visorcraft.ghostgalleon.rom.RomEntry
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SgdbScraperTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun rom(id: String, name: String, artUri: String? = null) = RomEntry(
        id = id,
        name = name,
        platformId = "snes",
        uri = "content://$id",
        path = null,
        artUri = artUri,
    )

    /** Canned-response transport; records every request URL in order. */
    private class FakeTransport(
        val gets: Map<String, String> = emptyMap(),
        val downloads: Map<String, ByteArray> = emptyMap(),
        val onGet: (String) -> Unit = {},
    ) : SgdbTransport {
        val requested = mutableListOf<String>()
        var lastApiKey: String? = null

        override fun get(url: String, apiKey: String): String? {
            requested += url
            lastApiKey = apiKey
            onGet(url)
            return gets[url]
        }

        override fun download(url: String): ByteArray? {
            requested += url
            return downloads[url]
        }
    }

    private fun searchJson(id: Long, name: String = "Game") =
        """{"success":true,"data":[{"id":$id,"name":"$name"}]}"""

    private fun imagesJson(url: String) =
        """{"success":true,"data":[{"id":1,"url":"$url","thumb":"$url"}]}"""

    /** Scraper with an identity shrink seam (BitmapFactory is Android-only,
     *  so the real downscale cannot run on the host). */
    private fun scraper(
        cache: ArtCache,
        transport: FakeTransport,
        delayMs: Long = 0,
        shrink: (ByteArray, ArtCache.ArtKind) -> ByteArray? = { bytes, _ -> bytes },
    ) = SgdbScraper(cache, transport, delayMs, shrink)

    @Test
    fun `normalizeName strips region and tag noise`() {
        assertEquals("Super Mario World", Sgdb.normalizeName("Super Mario World (USA) [!]"))
        assertEquals(
            "The Legend of Zelda",
            Sgdb.normalizeName("The Legend of Zelda (Europe) (En,Fr,De) [v1.1]"),
        )
        assertEquals("Hades II", Sgdb.normalizeName("Hades II [0100A00019DE0000][v0]"))
        assertEquals("Chrono Trigger", Sgdb.normalizeName("  Chrono   Trigger "))
    }

    @Test
    fun `urls are built and encoded correctly`() {
        assertEquals(
            "https://www.steamgriddb.com/api/v2/search/autocomplete/Chrono%20Trigger",
            Sgdb.searchUrl("Chrono Trigger"),
        )
        assertEquals(
            "https://www.steamgriddb.com/api/v2/grids/game/42",
            Sgdb.gridsUrl(42),
        )
        assertEquals(
            "https://www.steamgriddb.com/api/v2/heroes/game/42",
            Sgdb.heroesUrl(42),
        )
    }

    @Test
    fun `search response parsing takes the first result`() {
        assertEquals(5261L, Sgdb.parseSearchFirstId(searchJson(5261)))
        assertNull(Sgdb.parseSearchFirstId("""{"success":true,"data":[]}"""))
        assertNull(Sgdb.parseSearchFirstId("not json"))
    }

    @Test
    fun `image response parsing takes the first url`() {
        assertEquals(
            "https://cdn2.steamgriddb.com/grid/x.png",
            Sgdb.parseFirstImageUrl(imagesJson("https://cdn2.steamgriddb.com/grid/x.png")),
        )
        assertNull(Sgdb.parseFirstImageUrl("""{"success":true,"data":[]}"""))
        assertNull(Sgdb.parseFirstImageUrl("{broken"))
    }

    @Test
    fun `job downloads grid and hero for missing entries only`() {
        val cache = ArtCache(tmp.newFolder("art"))
        val cached = rom("snes:a.smc", "Cached Game")
        // Both slots filled → fully skipped (grid-only caching is no longer
        // enough; see the hero-backfill test below).
        cache.writeDiskBytes(cached.id, byteArrayOf(1, 2, 3))
        cache.writeDiskBytes(cached.id, byteArrayOf(4, 5), ArtCache.ArtKind.HERO)
        val local = rom("snes:b.smc", "Local Art Game", artUri = "content://art")
        val target = rom("snes:c.smc", "Chrono Trigger (USA) [!]")
        val transport = FakeTransport(
            gets = mapOf(
                Sgdb.searchUrl("Chrono Trigger") to searchJson(42),
                Sgdb.gridsUrl(42) to imagesJson("https://cdn/grid.png"),
                Sgdb.heroesUrl(42) to imagesJson("https://cdn/hero.png"),
            ),
            downloads = mapOf(
                "https://cdn/grid.png" to byteArrayOf(9, 9),
                "https://cdn/hero.png" to byteArrayOf(8, 8),
            ),
        )
        val scraper = scraper(cache, transport)
        val summary = scraper.runBlocking("KEY", listOf(cached, local, target), {}, { _, _ -> })
        assertEquals(1, summary.downloaded)
        assertEquals(2, summary.skipped)
        assertEquals(0, summary.failed)
        assertFalse(summary.cancelled)
        // Grid lands in the tile slot, hero in the separate hero slot.
        assertTrue(cache.diskHas(target.id))
        assertTrue(cache.diskHas(target.id, ArtCache.ArtKind.HERO))
        // The skipped entries produced no traffic; the bearer key went out.
        assertFalse(transport.requested.any { it.contains("Cached") })
        assertEquals("KEY", transport.lastApiKey)
    }

    @Test
    fun `grid-cached rom with no hero gets a hero-only backfill`() {
        val cache = ArtCache(tmp.newFolder("art"))
        val partial = rom("snes:p.smc", "Partial Game")
        cache.writeDiskBytes(partial.id, byteArrayOf(1, 1)) // grid only
        val transport = FakeTransport(
            gets = mapOf(
                Sgdb.searchUrl("Partial Game") to searchJson(9),
                Sgdb.heroesUrl(9) to imagesJson("https://cdn/hero9.png"),
            ),
            downloads = mapOf("https://cdn/hero9.png" to byteArrayOf(7)),
        )
        val scraper = scraper(cache, transport)
        val summary = scraper.runBlocking("KEY", listOf(partial), {}, { _, _ -> })
        assertEquals(1, summary.downloaded)
        assertEquals(0, summary.skipped)
        assertEquals(0, summary.failed)
        assertTrue(cache.diskHas(partial.id, ArtCache.ArtKind.HERO))
        // The existing grid art is untouched and the grid was never
        // re-requested (no grids call, no grid download).
        assertArrayEquals(byteArrayOf(1, 1), cache.readDiskBytes(partial.id))
        assertFalse(transport.requested.any { it.contains("grids") })
    }

    @Test
    fun `undecodable image bytes count as failure and are never cached`() {
        val cache = ArtCache(tmp.newFolder("art"))
        val target = rom("snes:c.smc", "Chrono Trigger")
        val transport = FakeTransport(
            gets = mapOf(
                Sgdb.searchUrl("Chrono Trigger") to searchJson(42),
                Sgdb.gridsUrl(42) to imagesJson("https://cdn/grid.png"),
            ),
            downloads = mapOf("https://cdn/grid.png" to byteArrayOf(9)),
        )
        // The real shrink returns null for non-image bytes; simulate that.
        val scraper = scraper(cache, transport, shrink = { _, _ -> null })
        val summary = scraper.runBlocking("KEY", listOf(target), {}, { _, _ -> })
        assertEquals(0, summary.downloaded)
        assertEquals(1, summary.failed)
        assertFalse(cache.diskHas(target.id))
    }

    @Test
    fun `no search result and dead transport count as failures`() {
        val cache = ArtCache(tmp.newFolder("art"))
        val noMatch = rom("snes:x.smc", "Obscure Homebrew")
        val dead = rom("snes:y.smc", "Dead Game")
        val transport = FakeTransport(
            gets = mapOf(Sgdb.searchUrl("Obscure Homebrew") to """{"success":true,"data":[]}"""),
        )
        val scraper = scraper(cache, transport)
        val summary = scraper.runBlocking("KEY", listOf(noMatch, dead), {}, { _, _ -> })
        assertEquals(0, summary.downloaded)
        assertEquals(0, summary.skipped)
        assertEquals(2, summary.failed)
    }

    @Test
    fun `cancel stops the job between roms`() {
        val cache = ArtCache(tmp.newFolder("art"))
        val entries = (1..5).map { rom("snes:$it.smc", "Game $it") }
        lateinit var scraper: SgdbScraper
        val transport = FakeTransport(
            gets = mapOf(
                Sgdb.searchUrl("Game 1") to searchJson(7),
                Sgdb.gridsUrl(7) to imagesJson("https://cdn/g.png"),
            ),
            downloads = mapOf("https://cdn/g.png" to byteArrayOf(1)),
            onGet = { if (it.contains("heroes")) scraper.cancel() },
        )
        scraper = SgdbScraper(cache, transport, delayMs = 0, shrink = { bytes, _ -> bytes })
        val summary = scraper.runBlocking("KEY", entries, {}, { _, _ -> })
        assertTrue(summary.cancelled)
        // The cancel lands mid-ROM (after the heroes request): game 1 is
        // counted failed rather than downloaded, and games 2-5 never run.
        assertEquals(0, summary.downloaded)
        assertFalse(transport.requested.any { it.contains("Game+2") || it.contains("Game%202") })
    }

    @Test
    fun `every request is followed by the polite delay`() {
        val cache = ArtCache(tmp.newFolder("art"))
        val target = rom("snes:c.smc", "Chrono Trigger")
        val transport = FakeTransport(
            gets = mapOf(
                Sgdb.searchUrl("Chrono Trigger") to searchJson(42),
                Sgdb.gridsUrl(42) to imagesJson("https://cdn/grid.png"),
                Sgdb.heroesUrl(42) to """{"success":true,"data":[]}""",
            ),
            downloads = mapOf("https://cdn/grid.png" to byteArrayOf(9)),
        )
        val delays = mutableListOf<Long>()
        val scraper = scraper(cache, transport, delayMs = 200)
        scraper.runBlocking("KEY", listOf(target), { delays += it }, { _, _ -> })
        // search + grids + grid download + heroes = 4 requests, 4 delays.
        assertEquals(4, transport.requested.size)
        assertEquals(List(4) { 200L }, delays)
    }
}
