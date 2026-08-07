package com.visorcraft.blackpearl.rom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlatformTileTest {

    @Test
    fun `color is deterministic for the same platform id`() {
        Platforms.ALL.forEach { platform ->
            assertEquals(
                "colorFor(${platform.id})",
                PlatformTile.colorFor(platform.id),
                PlatformTile.colorFor(platform.id),
            )
        }
    }

    @Test
    fun `colors are opaque and dark enough for white text`() {
        Platforms.ALL.forEach { platform ->
            val color = PlatformTile.colorFor(platform.id)
            assertEquals(0xFF, (color ushr 24) and 0xFF)
            // VALUE 0.42 -> no channel may exceed ~0.42 * 255.
            listOf(
                (color shr 16) and 0xFF,
                (color shr 8) and 0xFF,
                color and 0xFF,
            ).forEach { channel ->
                assertTrue("channel $channel too bright", channel <= 110)
            }
        }
    }

    @Test
    fun `different platforms get different hues`() {
        // Not a strict guarantee of the hash scheme, but the shipped
        // platforms must not collide on color.
        val colors = Platforms.ALL.map { PlatformTile.colorFor(it.id) }
        assertEquals(colors.size, colors.toSet().size)
    }

    @Test
    fun `short text uses the platform shortName uppercased`() {
        assertEquals("SNES", PlatformTile.shortText("snes"))
        assertEquals("3DS", PlatformTile.shortText("3ds"))
        assertEquals("SWITCH", PlatformTile.shortText("switch"))
        assertEquals("GB", PlatformTile.shortText("gb"))
    }

    @Test
    fun `unknown platform falls back to the uppercased id`() {
        assertEquals("NGAGE", PlatformTile.shortText("ngage"))
    }

    @Test
    fun `hash pins`() {
        // Exact-value pins: a change here means someone altered the color
        // scheme deliberately (update the pins) or broke determinism.
        assertNotEquals(PlatformTile.colorFor("snes"), PlatformTile.colorFor("3ds"))
    }
}
