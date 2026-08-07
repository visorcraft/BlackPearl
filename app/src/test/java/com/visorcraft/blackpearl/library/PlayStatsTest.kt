package com.visorcraft.blackpearl.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlayStatsTest {

    @Test
    fun `sessionDurationMs clamps negative to zero`() {
        assertEquals(0L, SessionMath.sessionDurationMs(100, 50))
        assertEquals(50L, SessionMath.sessionDurationMs(100, 150))
    }

    @Test
    fun `recordLaunch stamps lastLaunchedMs`() {
        val next = SessionMath.recordLaunch(PlayStats.EMPTY, "rom:snes:x", 1_000L)
        assertEquals(1_000L, next.lastLaunchedMs["rom:snes:x"])
    }

    @Test
    fun `recordReturn accrues playtime`() {
        val afterLaunch = SessionMath.recordLaunch(PlayStats.EMPTY, "k", 1_000L)
        val after = SessionMath.recordReturn(afterLaunch, "k", 1_000L, 1_000L + 120_000L)
        assertEquals(120_000L, after.totalPlaytimeMs["k"])
        val twice = SessionMath.recordReturn(after, "k", 2_000L, 2_000L + 60_000L)
        assertEquals(180_000L, twice.totalPlaytimeMs["k"])
    }

    @Test
    fun `formatPlaytime humanizes`() {
        assertEquals("0m", SessionMath.formatPlaytime(0))
        assertEquals("5m", SessionMath.formatPlaytime(5 * 60_000L))
        assertEquals("1h", SessionMath.formatPlaytime(60 * 60_000L))
        assertEquals("1h 5m", SessionMath.formatPlaytime(65 * 60_000L))
    }

    @Test
    fun `formatLastPlayed returns null when unknown`() {
        assertNull(SessionMath.formatLastPlayed(null, 1_000L))
        assertEquals("Just now", SessionMath.formatLastPlayed(1_000L, 1_000L + 10_000L))
        assertEquals("2m ago", SessionMath.formatLastPlayed(1_000L, 1_000L + 2 * 60_000L))
    }
}
