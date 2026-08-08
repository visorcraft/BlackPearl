package com.visorcraft.ghostgalleon.rom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PathGateTest {

    @Test
    fun `URI style always Ok regardless of path`() {
        val d = PathGate.decide(
            UriStyle.URI,
            path = null,
            pathExists = { false },
            pathReadable = { false },
        )
        assertEquals(PathGate.Decision.Ok, d)
        assertTrue(PathGate.isReady(UriStyle.URI, null, { false }, { false }))
    }

    @Test
    fun `PATH with blank path is blocked`() {
        val d = PathGate.decide(UriStyle.PATH, "  ", { true }, { true })
        assertTrue(d is PathGate.Decision.Blocked)
        assertEquals("ROM path unavailable", (d as PathGate.Decision.Blocked).reason)
    }

    @Test
    fun `PATH with missing file is blocked`() {
        val d = PathGate.decide(
            UriStyle.PATH,
            "/storage/sdcard1/roms/snes/game.sfc",
            pathExists = { false },
            pathReadable = { true },
        )
        assertTrue(d is PathGate.Decision.Blocked)
        assertEquals(
            "Storage unmounted or file missing",
            (d as PathGate.Decision.Blocked).reason,
        )
    }

    @Test
    fun `PATH with unreadable file is blocked`() {
        val d = PathGate.decide(
            UriStyle.PATH,
            "/storage/emulated/0/roms/snes/game.sfc",
            pathExists = { true },
            pathReadable = { false },
        )
        assertTrue(d is PathGate.Decision.Blocked)
        assertEquals("ROM file not readable", (d as PathGate.Decision.Blocked).reason)
    }

    @Test
    fun `PATH with existing readable file is Ok`() {
        val path = "/storage/emulated/0/roms/snes/game.sfc"
        val d = PathGate.decide(
            UriStyle.PATH,
            path,
            pathExists = { it == path },
            pathReadable = { it == path },
        )
        assertEquals(PathGate.Decision.Ok, d)
        assertTrue(PathGate.isReady(UriStyle.PATH, path, { true }, { true }))
        assertFalse(PathGate.isReady(UriStyle.PATH, null, { true }, { true }))
    }
}
