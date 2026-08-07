package com.visorcraft.ghostgalleon.rom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlatformPackTest {

    private val validPack = """
        {
          "schemaVersion": 1,
          "platforms": [
            {
              "id": "snes",
              "displayName": "SNES Pack",
              "shortName": "SNES",
              "folderNames": ["snes", "snes-extra"],
              "extensions": ["smc", "sfc"],
              "players": [
                {
                  "id": "custom-snes",
                  "displayName": "Custom SNES",
                  "component": "com.example.snes/.Play",
                  "uriStyle": "URI",
                  "extras": { "ROM": "{file.uri}" }
                }
              ]
            },
            {
              "id": "pcengine",
              "displayName": "PC Engine",
              "shortName": "PCE",
              "folderNames": ["pce", "pcengine"],
              "extensions": ["pce", "sgx"],
              "players": [
                {
                  "id": "ra-pce",
                  "displayName": "RetroArch PCE",
                  "component": "com.retroarch.aarch64/com.retroarch.browser.retroactivity.RetroActivityFuture",
                  "uriStyle": "PATH",
                  "extras": { "ROM": "{file.path}", "LIBRETRO": "/data/x.so" }
                }
              ]
            }
          ]
        }
    """.trimIndent()

    @Test
    fun `parse valid pack yields platforms and players`() {
        val r = PlatformPack.parse(validPack)
        assertNotNull(r)
        assertEquals(2, r!!.platforms.size)
        assertEquals("snes", r.platforms[0].id)
        assertEquals("custom-snes", r.platforms[0].players[0].id)
        assertEquals("pcengine", r.platforms[1].id)
    }

    @Test
    fun `parse rejects malformed json`() {
        assertNull(PlatformPack.parse("{ not json"))
        assertNull(PlatformPack.parse("""{"platforms":[]}"""))
        assertNull(PlatformPack.parse("""{"no":"platforms"}"""))
    }

    @Test
    fun `merge adds new platform and overlays player on builtin`() {
        Platforms.clearPackOverlay()
        val parsed = PlatformPack.parse(validPack)!!
        val merged = PlatformPack.merge(Platforms.BUILTIN, parsed.platforms)
        val snes = merged.first { it.id == "snes" }
        assertEquals("custom-snes", snes.players.first().id)
        // Builtin snes players still present under other ids.
        assertTrue(snes.players.any { it.id != "custom-snes" })
        assertTrue(merged.any { it.id == "pcengine" })
        // Builtins not deleted.
        assertTrue(merged.any { it.id == "gb" })
    }

    @Test
    fun `merge replaces builtin player with same id`() {
        val builtinId = Platforms.SNES.player.id
        val pack = """
            {
              "platforms": [{
                "id": "snes",
                "displayName": "Super Nintendo",
                "shortName": "SNES",
                "folderNames": ["snes"],
                "extensions": ["smc"],
                "players": [{
                  "id": "$builtinId",
                  "displayName": "Override Core",
                  "component": "com.override/.Play",
                  "uriStyle": "PATH",
                  "extras": { "ROM": "{file.path}" }
                }]
              }]
            }
        """.trimIndent()
        val parsed = PlatformPack.parse(pack)!!
        val snes = PlatformPack.merge(Platforms.BUILTIN, parsed.platforms)
            .first { it.id == "snes" }
        val player = snes.players.first { it.id == builtinId }
        assertEquals("Override Core", player.displayName)
        assertEquals("com.override/.Play", player.component)
    }

    @Test
    fun `merge with empty import returns builtins`() {
        assertEquals(Platforms.BUILTIN, PlatformPack.merge(Platforms.BUILTIN, emptyList()))
    }

    @Test
    fun `invalid pack leaves registry equivalent to builtins when overlay cleared`() {
        Platforms.clearPackOverlay()
        assertEquals(Platforms.BUILTIN.map { it.id }, Platforms.ALL.map { it.id })
        assertNull(PlatformPack.parse("{bad"))
        // Reject path must not install anything.
        assertTrue(Platforms.packOverlay().isEmpty())
    }
}
