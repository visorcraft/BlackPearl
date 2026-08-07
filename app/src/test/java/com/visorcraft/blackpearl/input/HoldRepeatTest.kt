package com.visorcraft.blackpearl.input

import com.visorcraft.blackpearl.settings.Action
import org.junit.Assert.assertEquals
import org.junit.Test

class HoldRepeatTest {

    // Timing the user explicitly specified; guard against regressions.
    @Test
    fun `initial delay and repeat interval constants`() {
        assertEquals(1000L, HoldRepeat.INITIAL_DELAY_MS)
        assertEquals(350L, HoldRepeat.REPEAT_INTERVAL_MS)
    }

    /** Recording scheduler: timestamps tasks, runs them on advanceBy. */
    private class FakeScheduler : NavRepeater.Scheduler {
        private data class Entry(val task: Runnable, val runAt: Long)

        private val entries = mutableListOf<Entry>()
        var now = 0L

        override fun postDelayed(task: Runnable, delayMs: Long) {
            entries += Entry(task, now + delayMs)
        }

        override fun cancel(task: Runnable) {
            entries.removeAll { it.task === task }
        }

        fun pendingCount(): Int = entries.size

        fun advanceBy(ms: Long) {
            val target = now + ms
            while (true) {
                val next = entries.filter { it.runAt <= target }.minByOrNull { it.runAt }
                    ?: break
                entries.remove(next)
                now = next.runAt
                next.task.run()
            }
            now = target
        }
    }

    private class Fixture {
        val scheduler = FakeScheduler()
        val moves = mutableListOf<Action>()
        val repeater = NavRepeater(scheduler) { moves += it }
    }

    @Test
    fun `onPress fires one move immediately`() {
        val f = Fixture()
        f.repeater.onPress(Action.NAV_RIGHT)
        assertEquals(listOf(Action.NAV_RIGHT), f.moves)
    }

    @Test
    fun `no repeat fires before the initial delay`() {
        val f = Fixture()
        f.repeater.onPress(Action.NAV_RIGHT)
        f.scheduler.advanceBy(HoldRepeat.INITIAL_DELAY_MS - 1)
        assertEquals(1, f.moves.size)
    }

    @Test
    fun `first repeat fires at the initial delay`() {
        val f = Fixture()
        f.repeater.onPress(Action.NAV_RIGHT)
        f.scheduler.advanceBy(HoldRepeat.INITIAL_DELAY_MS)
        assertEquals(2, f.moves.size)
    }

    @Test
    fun `repeats continue at the repeat interval while held`() {
        val f = Fixture()
        f.repeater.onPress(Action.NAV_DOWN)
        f.scheduler.advanceBy(HoldRepeat.INITIAL_DELAY_MS)
        assertEquals(2, f.moves.size)
        f.scheduler.advanceBy(HoldRepeat.REPEAT_INTERVAL_MS)
        assertEquals(3, f.moves.size)
        f.scheduler.advanceBy(HoldRepeat.REPEAT_INTERVAL_MS)
        assertEquals(4, f.moves.size)
        assertEquals(listOf(Action.NAV_DOWN, Action.NAV_DOWN, Action.NAV_DOWN, Action.NAV_DOWN),
            f.moves)
    }

    @Test
    fun `onRelease cancels pending repeats`() {
        val f = Fixture()
        f.repeater.onPress(Action.NAV_LEFT)
        f.scheduler.advanceBy(HoldRepeat.INITIAL_DELAY_MS)
        assertEquals(2, f.moves.size)
        f.repeater.onRelease(Action.NAV_LEFT)
        f.scheduler.advanceBy(10_000)
        assertEquals(2, f.moves.size)
    }

    @Test
    fun `release before the initial delay cancels the first repeat`() {
        val f = Fixture()
        f.repeater.onPress(Action.NAV_UP)
        f.scheduler.advanceBy(500)
        f.repeater.onRelease(Action.NAV_UP)
        f.scheduler.advanceBy(10_000)
        assertEquals(listOf(Action.NAV_UP), f.moves)
    }

    @Test
    fun `re-press replaces the old schedule instead of doubling it`() {
        val f = Fixture()
        f.repeater.onPress(Action.NAV_RIGHT)
        f.scheduler.advanceBy(500)
        f.repeater.onPress(Action.NAV_RIGHT)
        assertEquals(1, f.scheduler.pendingCount())
        f.scheduler.advanceBy(HoldRepeat.INITIAL_DELAY_MS)
        assertEquals(3, f.moves.size) // 2 immediate presses + 1 repeat, not 2 repeats
    }

    @Test
    fun `directions hold and release independently`() {
        val f = Fixture()
        f.repeater.onPress(Action.NAV_LEFT)
        f.repeater.onPress(Action.NAV_UP)
        f.scheduler.advanceBy(HoldRepeat.INITIAL_DELAY_MS)
        assertEquals(4, f.moves.size) // 2 immediate + 2 first repeats
        f.repeater.onRelease(Action.NAV_LEFT)
        f.scheduler.advanceBy(HoldRepeat.REPEAT_INTERVAL_MS)
        // Only NAV_UP keeps repeating.
        assertEquals(Action.NAV_UP, f.moves.last())
        val upCount = f.moves.count { it == Action.NAV_UP }
        val leftCount = f.moves.count { it == Action.NAV_LEFT }
        assertEquals(3, upCount)
        assertEquals(2, leftCount)
    }

    @Test
    fun `cancelAll stops every held direction`() {
        val f = Fixture()
        f.repeater.onPress(Action.NAV_LEFT)
        f.repeater.onPress(Action.NAV_UP)
        f.repeater.cancelAll()
        assertEquals(0, f.scheduler.pendingCount())
        f.scheduler.advanceBy(10_000)
        assertEquals(2, f.moves.size)
    }
}
