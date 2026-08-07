package com.visorcraft.blackpearl.art

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ArtCacheTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `key is stable, id-dependent, and filesystem-safe`() {
        val id = "snes:snes/Super Mario Kart (USA).smc"
        assertEquals(ArtCache.keyFor(id), ArtCache.keyFor(id))
        assertNotEquals(ArtCache.keyFor(id), ArtCache.keyFor("snes:snes/other.smc"))
        // SHA-256 hex: safe as a filename for any id (ids contain ':'/'/').
        assertTrue(ArtCache.keyFor(id).matches(Regex("[0-9a-f]{64}")))
    }

    @Test
    fun `disk round-trip in a temp dir`() {
        val cache = ArtCache(tmp.root.resolve("art"))
        val bytes = byteArrayOf(1, 2, 3, 4, 5)
        cache.writeDiskBytes("snes:snes/smw.smc", bytes)
        assertTrue(cache.diskHas("snes:snes/smw.smc"))
        assertArrayEquals(bytes, cache.readDiskBytes("snes:snes/smw.smc"))
        // No tmp file left behind.
        assertTrue(tmp.root.resolve("art").listFiles()!!.none { it.name.endsWith(".tmp") })
    }

    @Test
    fun `miss returns null`() {
        val cache = ArtCache(tmp.root.resolve("art"))
        assertFalse(cache.diskHas("snes:snes/nope.smc"))
        assertNull(cache.readDiskBytes("snes:snes/nope.smc"))
    }

    @Test
    fun `invalidate removes disk bytes and source stamp`() {
        val artDir = tmp.root.resolve("art-inv")
        val cache = ArtCache(artDir)
        val id = "snes:snes/smw.smc"
        cache.writeDiskBytes(id, byteArrayOf(9, 9, 9))
        // Simulate a URI-stamped entry left by a prior load.
        File(artDir, ArtCache.keyFor(id) + ".src").writeText("content://old-art")
        assertTrue(cache.diskHas(id))
        assertTrue(File(artDir, ArtCache.keyFor(id) + ".src").isFile)
        cache.invalidate(id)
        assertFalse(cache.diskHas(id))
        assertNull(cache.readDiskBytes(id))
        assertFalse(File(artDir, ArtCache.keyFor(id) + ".src").exists())
        assertFalse(File(artDir, ArtCache.keyFor(id) + ".hero.png").exists())
    }

    @Test
    fun `sourceStampMatches rejects mismatched override sources`() {
        // No expected URI (scrape path): any stamp is acceptable.
        assertTrue(ArtCache.sourceStampMatches(null, null))
        assertTrue(ArtCache.sourceStampMatches("content://x", null))
        // Expected URI (override / local art): stamp must match exactly.
        assertFalse(ArtCache.sourceStampMatches(null, "content://override"))
        assertFalse(ArtCache.sourceStampMatches("content://old", "content://override"))
        assertTrue(ArtCache.sourceStampMatches("content://override", "content://override"))
    }

    @Test
    fun `memKeyFor changes when source URI changes so override cannot hit old mem`() {
        val id = "snes:snes/smw.smc"
        val plain = ArtCache.memKeyFor(id, ArtCache.ArtKind.GRID, null)
        val a = ArtCache.memKeyFor(id, ArtCache.ArtKind.GRID, "content://a")
        val b = ArtCache.memKeyFor(id, ArtCache.ArtKind.GRID, "content://b")
        assertEquals(id, plain)
        assertNotEquals(plain, a)
        assertNotEquals(a, b)
        assertTrue(a.startsWith("$id|src:"))
    }

    @Test
    fun `effectiveArtUri plus memKeyFor models SET_ART cache isolation`() {
        // Shipped load path: effectiveArtUri(override) → memKeyFor(source).
        // After SET_ART the source URI changes, so the mem key must change
        // and cannot hit the prior scanner-art entry.
        val entry = com.visorcraft.blackpearl.rom.RomEntry(
            id = "snes:game.smc", name = "game", platformId = "snes",
            uri = "content://rom", path = "/r", artUri = "content://scanner",
        )
        val before = ArtOverride.effectiveArtUri(entry, emptyMap())
        val after = ArtOverride.effectiveArtUri(
            entry, mapOf(entry.id to "content://picked"),
        )
        assertEquals("content://scanner", before)
        assertEquals("content://picked", after)
        assertNotEquals(
            ArtCache.memKeyFor(entry.id, ArtCache.ArtKind.GRID, before),
            ArtCache.memKeyFor(entry.id, ArtCache.ArtKind.GRID, after),
        )
    }

    @Test
    fun `sample size keeps both dimensions at or above target`() {
        assertEquals(1, ArtCache.sampleSizeFor(1000, 750, 512))
        assertEquals(2, ArtCache.sampleSizeFor(2048, 1536, 512))
        assertEquals(4, ArtCache.sampleSizeFor(4000, 3000, 512))
        // One dimension below target blocks further sampling (no upscale,
        // no drop below target); already-small images stay at 1.
        assertEquals(1, ArtCache.sampleSizeFor(4000, 600, 512))
        assertEquals(1, ArtCache.sampleSizeFor(512, 512, 512))
        assertEquals(1, ArtCache.sampleSizeFor(300, 200, 512))
    }

    @Test
    fun `hero sample size tracks width alone`() {
        // Heroes are wide and short; the scrape path passes the width as
        // both dimensions so height never blocks sampling.
        assertEquals(1, ArtCache.sampleSizeFor(1920, 1920, 1600))
        assertEquals(2, ArtCache.sampleSizeFor(3840, 3840, 1600))
        // A 3840x620 hero samples to 1920x310 under the width-only policy,
        // where the both-dims policy would have left it full-size.
        assertEquals(1, ArtCache.sampleSizeFor(3840, 620, 1600))
    }

    // Eviction: oldest-lastModified files go first, deleting only as many
    // as needed to get back under the cap.

    private fun sizedFile(name: String, bytes: Int, lastModified: Long): File =
        tmp.root.resolve(name).apply {
            writeBytes(ByteArray(bytes))
            setLastModified(lastModified)
        }

    @Test
    fun `eviction deletes oldest first and stops at the cap`() {
        val old = sizedFile("old.png", 60, 1_000)
        val mid = sizedFile("mid.png", 60, 2_000)
        val new = sizedFile("new.png", 60, 3_000)
        // 180 total, cap 120: only the oldest goes.
        assertEquals(
            listOf(old),
            ArtCache.evictionCandidates(listOf(new, mid, old), 120))
        // Cap 60: oldest two go, the newest alone fills the cap.
        assertEquals(
            listOf(old, mid),
            ArtCache.evictionCandidates(listOf(new, mid, old), 60))
    }

    @Test
    fun `eviction is a no-op at or under the cap`() {
        val a = sizedFile("a.png", 40, 1_000)
        val b = sizedFile("b.png", 40, 2_000)
        assertTrue(ArtCache.evictionCandidates(listOf(a, b), 80).isEmpty())
        assertTrue(ArtCache.evictionCandidates(listOf(a, b), 1_000).isEmpty())
        assertTrue(ArtCache.evictionCandidates(emptyList(), 0).isEmpty())
    }

    @Test
    fun `eviction selection on a real cache dir picks the stale file`() {
        val cache = ArtCache(tmp.root.resolve("art"))
        cache.writeDiskBytes("snes:snes/a.smc", ByteArray(10))
        cache.writeDiskBytes("snes:snes/b.smc", ByteArray(10))
        val artDir = tmp.root.resolve("art")
        // Pin timestamps explicitly (write-time lastModified has only ~1 s
        // resolution, too coarse to order two writes in one test).
        File(artDir, ArtCache.keyFor("snes:snes/a.smc") + ".png").setLastModified(2_000)
        File(artDir, ArtCache.keyFor("snes:snes/b.smc") + ".png").setLastModified(1_000)
        val evict = ArtCache.evictionCandidates(artDir.listFiles()!!.toList(), 15)
        assertEquals(
            listOf(ArtCache.keyFor("snes:snes/b.smc") + ".png"),
            evict.map { it.name })
    }

    @Test
    fun `disk cache cap is 256 MiB`() {
        assertEquals(256L * 1024 * 1024, ArtCache.DISK_CACHE_CAP_BYTES)
    }
}
