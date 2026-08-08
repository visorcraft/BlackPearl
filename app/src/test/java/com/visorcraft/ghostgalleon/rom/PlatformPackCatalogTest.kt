package com.visorcraft.ghostgalleon.rom

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Every bundled platform pack under assets must parse and merge through the
 * real [PlatformPack] path (same as Settings catalog load).
 */
class PlatformPackCatalogTest {

    private fun packDir(): File {
        val candidates = listOf(
            File("app/src/main/assets/platform_packs"),
            File("src/main/assets/platform_packs"),
            File("../src/main/assets/platform_packs"),
        )
        return candidates.firstOrNull { it.isDirectory }
            ?: error("platform_packs dir not found; cwd=" + File(".").absolutePath)
    }

    @Test
    fun `all bundled packs parse and merge without dropping builtins`() {
        val dir = packDir()
        val expected = listOf(
            "arcade-fbneo.json",
            "dreamcast-flycast-ra.json",
            "n64-extra.json",
            "pcengine.json",
            "psvita.json",
        )
        val files = expected.map { File(dir, it) }
        for (file in files) {
            assertTrue("missing bundled pack ${file.name}", file.isFile)
        }
        var merged = Platforms.BUILTIN
        for (file in files) {
            val parsed = PlatformPack.parse(file.readText())
            assertNotNull("${file.name} failed to parse", parsed)
            merged = PlatformPack.merge(merged, parsed!!.platforms)
        }
        // Builtins preserved.
        assertTrue(merged.any { it.id == "gb" })
        assertTrue(merged.any { it.id == "snes" })
        val ids = merged.map { it.id }.toSet()
        assertTrue("pcengine missing after catalog merge", "pcengine" in ids)
        assertTrue("psvita missing after catalog merge", "psvita" in ids)
        assertTrue(merged.size >= Platforms.BUILTIN.size)
    }

    @Test
    fun `psvita pack adds psvita platform`() {
        val file = File(packDir(), "psvita.json")
        assertTrue(file.isFile)
        val parsed = PlatformPack.parse(file.readText())
        assertNotNull(parsed)
        val merged = PlatformPack.merge(Platforms.BUILTIN, parsed!!.platforms)
        assertTrue(merged.any { it.id == "psvita" })
    }

    @Test
    fun `n64-extra prepends alternate player`() {
        val file = File(packDir(), "n64-extra.json")
        assertTrue(file.isFile)
        val parsed = PlatformPack.parse(file.readText())
        assertNotNull(parsed)
        val merged = PlatformPack.merge(Platforms.BUILTIN, parsed!!.platforms)
        val n64 = merged.first { it.id == "n64" }
        assertTrue(n64.players.any { it.id == "ra-mupen-extra" })
    }

    @Test
    fun `malformed pack rejects without merge`() {
        assertTrue(PlatformPack.parse("{not json") == null)
        assertTrue(PlatformPack.parse("""{"platforms":[]}""") == null)
        val before = Platforms.BUILTIN
        val after = PlatformPack.merge(before, emptyList())
        assertTrue(after === before || after == before)
    }
}
