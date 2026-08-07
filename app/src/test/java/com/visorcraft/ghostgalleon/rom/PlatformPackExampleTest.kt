package com.visorcraft.ghostgalleon.rom

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Loads the in-repo example pack via the real [PlatformPack.parse] +
 * [PlatformPack.merge] path (same as Settings "Load example pack").
 */
class PlatformPackExampleTest {

    @Test
    fun `bundled pcengine example pack parses and merges`() {
        val candidates = listOf(
            File("app/src/main/assets/platform_packs/pcengine.json"),
            File("src/main/assets/platform_packs/pcengine.json"),
            File("../src/main/assets/platform_packs/pcengine.json"),
        )
        val file = candidates.firstOrNull { it.isFile }
            ?: error("example pack not found; cwd=" + File(".").absolutePath)
        val text = file.readText()
        val parsed = PlatformPack.parse(text)
        assertNotNull(parsed)
        val merged = PlatformPack.merge(Platforms.BUILTIN, parsed!!.platforms)
        assertTrue(merged.any { it.id == "pcengine" })
        val snes = merged.first { it.id == "snes" }
        assertTrue(snes.players.any { it.id == "ra-bsnes" })
        // Builtins preserved.
        assertTrue(merged.any { it.id == "gb" })
    }
}
