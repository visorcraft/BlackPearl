package com.visorcraft.ghostgalleon.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionTrackerTest {

    @Test
    fun `launch then return accrues full active span when never paused`() {
        val s = SessionTracker.onLaunch("rom:x", 1_000L)
        assertTrue(s.isActive)
        val played = SessionTracker.onReturn(s, 1_000L + 120_000L)
        assertEquals(120_000L, played)
    }

    @Test
    fun `launcher focus pauses and does not over-count`() {
        var s = SessionTracker.onLaunch("k", 0L)
        // 30s active
        s = SessionTracker.onLauncherFocused(s, 30_000L)
        assertFalse(s.isActive)
        // 5 minutes idle in launcher
        s = SessionTracker.onLauncherUnfocused(s, 30_000L + 300_000L)
        assertTrue(s.isActive)
        // 10s more active then return
        val played = SessionTracker.onReturn(s, 30_000L + 300_000L + 10_000L)
        assertEquals(40_000L, played)
    }

    @Test
    fun `device sleep pauses accrual`() {
        var s = SessionTracker.onLaunch("k", 0L)
        s = SessionTracker.onDeviceSleep(s, 20_000L)
        s = SessionTracker.onDeviceWake(s, 20_000L + 600_000L)
        val played = SessionTracker.onReturn(s, 20_000L + 600_000L + 5_000L)
        assertEquals(25_000L, played)
    }

    @Test
    fun `launcher unfocus while asleep does not resume until wake`() {
        // Production pairs SCREEN_OFF with SCREEN_ON. If wake is missing,
        // pausedForSleep sticks and playtime freezes forever.
        var s = SessionTracker.onLaunch("k", 0L)
        s = SessionTracker.onDeviceSleep(s, 10_000L)
        s = SessionTracker.onLauncherUnfocused(s, 15_000L)
        assertFalse(s.isActive)
        assertTrue(s.pausedForSleep)
        s = SessionTracker.onDeviceWake(s, 20_000L)
        assertTrue(s.isActive)
        assertFalse(s.pausedForSleep)
        val played = SessionTracker.onReturn(s, 30_000L)
        // 10s active before sleep + 10s after wake
        assertEquals(20_000L, played)
    }

    @Test
    fun `activeElapsedMs includes open segment`() {
        val s = SessionTracker.onLaunch("k", 100L)
        assertEquals(50L, SessionTracker.activeElapsedMs(s, 150L))
        val paused = SessionTracker.onLauncherFocused(s, 150L)
        assertEquals(50L, SessionTracker.activeElapsedMs(paused, 999L))
    }

    @Test
    fun `commitPlaytime adds to stats`() {
        val stats = SessionTracker.commitPlaytime(PlayStats.EMPTY, "k", 90_000L)
        assertEquals(90_000L, stats.totalPlaytimeMs["k"])
    }
}

class MultiSelectOpsTest {

    @Test
    fun `toggleSelection adds and removes`() {
        val a = MultiSelectOps.toggleSelection(emptySet(), "a")
        assertEquals(setOf("a"), a)
        assertEquals(emptySet<String>(), MultiSelectOps.toggleSelection(a, "a"))
    }

    @Test
    fun `bulkFavorite add and remove`() {
        val fav = MultiSelectOps.bulkFavorite(emptySet(), setOf("a", "b"), add = true)
        assertEquals(setOf("a", "b"), fav)
        assertEquals(setOf("b"), MultiSelectOps.bulkFavorite(fav, setOf("a"), add = false))
    }

    @Test
    fun `bulkPinToGrid fills empty slots`() {
        val slots = listOf<String?>(null, "keep", null)
        val next = MultiSelectOps.bulkPinToGrid(slots, setOf("x", "y"))
        assertEquals(listOf("x", "keep", "y"), next)
    }

    @Test
    fun `bulkHideRoms hides rom keys and skips packages`() {
        val selected = setOf(
            "rom:snes:a.sfc",
            "com.example.app",
            "rom:nds:b.nds",
            "rom:snes:a.sfc", // dup set
        )
        val (hidden, added) = MultiSelectOps.bulkHideRoms(emptySet(), selected)
        assertEquals(setOf("snes:a.sfc", "nds:b.nds"), hidden)
        assertEquals(2, added)
        val (again, added2) = MultiSelectOps.bulkHideRoms(hidden, setOf("rom:snes:a.sfc"))
        assertEquals(hidden, again)
        assertEquals(0, added2)
    }
}
