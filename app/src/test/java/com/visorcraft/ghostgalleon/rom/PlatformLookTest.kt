package com.visorcraft.ghostgalleon.rom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlatformLookTest {

    @Test
    fun `accentColor matches PlatformTile`() {
        assertEquals(PlatformTile.colorFor("snes"), PlatformLook.accentColor("snes"))
    }

    @Test
    fun `panelTint has low alpha over accent`() {
        val tint = PlatformLook.panelTint("snes")
        val alpha = (tint ushr 24) and 0xFF
        assertEquals(0x1F, alpha)
        assertEquals(PlatformTile.colorFor("snes") and 0x00FFFFFF, tint and 0x00FFFFFF)
    }

    @Test
    fun `filterBadge uses shortName`() {
        assertEquals("SNES", PlatformLook.filterBadge("snes"))
        assertTrue(PlatformLook.hasFilter("snes"))
        assertFalse(PlatformLook.hasFilter(null))
        assertFalse(PlatformLook.hasFilter(""))
    }
}
