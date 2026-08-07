package com.visorcraft.ghostgalleon.art

import com.visorcraft.ghostgalleon.rom.RomEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ArtOverrideTest {

    private val entry = RomEntry(
        id = "snes:game.smc",
        name = "game",
        platformId = "snes",
        uri = "content://rom",
        path = "/roms/snes/game.smc",
        artUri = "content://local-art",
    )

    @Test
    fun `override wins over scanner artUri`() {
        val overrides = mapOf("snes:game.smc" to "content://picked")
        assertEquals(
            "content://picked",
            ArtOverride.effectiveArtUri(entry, overrides),
        )
    }

    @Test
    fun `falls back to artUri when no override`() {
        assertEquals("content://local-art", ArtOverride.effectiveArtUri(entry, emptyMap()))
        assertNull(
            ArtOverride.effectiveArtUri(entry.copy(artUri = null), emptyMap()),
        )
    }

    @Test
    fun `set and clear override keys`() {
        val set = ArtOverride.setOverride(emptyMap(), "snes:game.smc", "content://x")
        assertEquals("content://x", set["snes:game.smc"])
        assertNull(ArtOverride.clearOverride(set, "snes:game.smc")["snes:game.smc"])
    }

    @Test
    fun `normalizeStem strips region tags`() {
        assertEquals(
            "zelda",
            ArtOverride.normalizeStem("Zelda (USA) [! ]"),
        )
    }
}
