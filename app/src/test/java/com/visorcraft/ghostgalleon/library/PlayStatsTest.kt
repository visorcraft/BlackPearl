package com.visorcraft.ghostgalleon.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test
    fun `cardMetaLine joins last played and playtime`() {
        val now = 10_000L + 2 * 60_000L
        assertEquals(
            "2m ago · 12m",
            SessionMath.cardMetaLine(10_000L, 12 * 60_000L, now),
        )
        assertEquals(
            "2m ago · Played 12m",
            SessionMath.cardMetaLine(10_000L, 12 * 60_000L, now, playtimePrefix = "Played "),
        )
        assertEquals("Never played", SessionMath.cardMetaLine(null, 0L, now))
        assertEquals("5m", SessionMath.cardMetaLine(null, 5 * 60_000L, now))
    }

    @Test
    fun `cardMetaLine appends favorite and dock tags`() {
        val now = 10_000L + 2 * 60_000L
        assertEquals(
            "2m ago · ★ · Dock",
            SessionMath.cardMetaLine(
                10_000L, 0L, now, favorite = true, inDock = true,
            ),
        )
        assertEquals(
            "Never played · ★",
            SessionMath.cardMetaLine(null, 0L, now, favorite = true),
        )
        assertEquals(
            "Never played · Dock",
            SessionMath.cardMetaLine(null, 0L, now, inDock = true),
        )
    }

    @Test
    fun `stampLastPlayed records last launch without playtime`() {
        val stamped = SessionMath.stampLastPlayed(PlayStats.EMPTY, "rom:x", 1_000L)
        assertEquals(1_000L, stamped.lastLaunchedMs["rom:x"])
        assertTrue(stamped.totalPlaytimeMs.isEmpty())
        assertEquals(PlayStats.EMPTY, SessionMath.stampLastPlayed(PlayStats.EMPTY, "  ", 1L))
        assertEquals(PlayStats.EMPTY, SessionMath.stampLastPlayed(PlayStats.EMPTY, "k", 0L))
    }

    @Test
    fun `hasStats and clearStats drop launch and playtime only`() {
        val stats = PlayStats(
            lastLaunchedMs = mapOf("a" to 10L, "b" to 20L),
            totalPlaytimeMs = mapOf("a" to 5_000L),
        )
        assertTrue(SessionMath.hasStats(stats, "a"))
        assertTrue(SessionMath.hasStats(stats, "b"))
        assertFalse(SessionMath.hasStats(stats, "missing"))
        assertFalse(SessionMath.hasStats(stats, "  "))
        val cleared = SessionMath.clearStats(stats, "a")
        assertFalse(SessionMath.hasStats(cleared, "a"))
        assertTrue(SessionMath.hasStats(cleared, "b"))
        assertEquals(mapOf("b" to 20L), cleared.lastLaunchedMs)
        assertTrue(cleared.totalPlaytimeMs.isEmpty())
        // No-op when already empty
        assertEquals(cleared, SessionMath.clearStats(cleared, "a"))
    }

    @Test
    fun `bulkClearStats clears multiple keys`() {
        val stats = PlayStats(
            lastLaunchedMs = mapOf("a" to 10L, "b" to 20L, "c" to 30L),
            totalPlaytimeMs = mapOf("a" to 100L, "c" to 200L),
        )
        assertEquals(2, SessionMath.statsCountInSelection(stats, listOf("a", "x", "c")))
        val (next, n) = SessionMath.bulkClearStats(stats, listOf("a", "c", "missing"))
        assertEquals(2, n)
        assertFalse(SessionMath.hasStats(next, "a"))
        assertFalse(SessionMath.hasStats(next, "c"))
        assertTrue(SessionMath.hasStats(next, "b"))
        assertEquals(mapOf("b" to 20L), next.lastLaunchedMs)
    }
}
