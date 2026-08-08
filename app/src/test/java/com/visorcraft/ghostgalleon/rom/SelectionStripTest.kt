package com.visorcraft.ghostgalleon.rom

import com.visorcraft.ghostgalleon.library.RaProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SelectionStripTest {

    private fun snesRom(name: String = "Chrono Trigger") = RomEntry(
        id = "snes:chrono.sfc",
        name = name,
        platformId = "snes",
        uri = "content://rom",
        path = "/storage/emulated/0/roms/snes/chrono.sfc",
    )

    @Test
    fun `empty brand fallback is selection prompt`() {
        val m = SelectionStrip.empty()
        assertTrue(m.isEmpty)
        assertFalse(m.isRom)
        assertEquals("Ghost Galleon", m.title)
        assertTrue(m.subtitle.contains("Select"))
    }

    @Test
    fun `forApp is selection context not hud`() {
        val m = SelectionStrip.forApp("Firefox")
        assertEquals("Firefox", m.title)
        assertEquals("App", m.subtitle)
        assertNull(m.raLine)
        assertFalse(m.isRom)
        assertFalse(m.isEmpty)
    }

    @Test
    fun `forRom shows platform player play and RA`() {
        val rom = snesRom()
        val m = SelectionStrip.forRom(
            rom = rom,
            preferredPlayerId = Platforms.SNES.players.first().id,
            installed = { true },
            playMeta = "12m played",
            raProgress = RaProgress(
                gameId = 1,
                title = "Chrono Trigger",
                numAwarded = 3,
                numPossible = 10,
            ),
            hasRaCredentials = true,
        )
        assertEquals("Chrono Trigger", m.title)
        assertEquals("Super Nintendo", m.subtitle) // or SNES displayName
        assertTrue(m.detail!!.contains("Player:"))
        assertTrue(m.detail!!.contains("12m"))
        assertTrue(m.raLine!!.startsWith("RA"))
        assertTrue(m.isRom)
        assertEquals("snes", m.platformId)
    }

    @Test
    fun `forRom without RA credentials omits ra line`() {
        val m = SelectionStrip.forRom(
            rom = snesRom(),
            preferredPlayerId = null,
            installed = { true },
            playMeta = null,
            raProgress = RaProgress(numAwarded = 1, numPossible = 5),
            hasRaCredentials = false,
        )
        assertNull(m.raLine)
    }

    @Test
    fun `strip dimensions leave room for text`() {
        assertTrue(SelectionStrip.ART_SIZE_DP < SelectionStrip.STRIP_HEIGHT_DP)
        assertTrue(SelectionStrip.STRIP_HEIGHT_DP <= 160)
        // Art + vertical padding must fit inside strip height.
        assertTrue(SelectionStrip.ART_SIZE_DP + 16 <= SelectionStrip.STRIP_HEIGHT_DP)
    }

    @Test
    fun `forRom player not installed is still shown as play context`() {
        val m = SelectionStrip.forRom(
            rom = snesRom(),
            preferredPlayerId = Platforms.SNES.players.first().id,
            installed = { false },
            playMeta = null,
            raProgress = null,
            hasRaCredentials = false,
        )
        assertTrue(m.detail!!.contains("not installed") || m.detail!!.startsWith("Player:"))
    }
}
