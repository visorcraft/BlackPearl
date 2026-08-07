package com.visorcraft.blackpearl.input

import android.os.Handler
import com.visorcraft.blackpearl.settings.Action

// Hold-to-repeat engine for NAV actions. onPress fires one move immediately
// and schedules repeats (first after HoldRepeat.INITIAL_DELAY_MS, then one
// per HoldRepeat.REPEAT_INTERVAL_MS); onRelease cancels them. Directions
// track independently — holding LEFT+UP repeats both.
//
// The Scheduler seam keeps this host-testable without Robolectric: Android
// code plugs in a Handler-backed scheduler, tests use a fake clock.
class NavRepeater(
    private val scheduler: Scheduler,
    private val move: (Action) -> Unit,
) {
    interface Scheduler {
        fun postDelayed(task: Runnable, delayMs: Long)
        fun cancel(task: Runnable)
    }

    class HandlerScheduler(private val handler: Handler) : Scheduler {
        override fun postDelayed(task: Runnable, delayMs: Long) {
            handler.postDelayed(task, delayMs)
        }

        override fun cancel(task: Runnable) {
            handler.removeCallbacks(task)
        }
    }

    private val repeatTasks = mutableMapOf<Action, Runnable>()

    fun onPress(action: Action) {
        move(action)
        cancel(action)
        scheduleRepeat(action, HoldRepeat.INITIAL_DELAY_MS)
    }

    fun onRelease(action: Action) {
        cancel(action)
    }

    fun cancelAll() {
        repeatTasks.keys.toList().forEach(::cancel)
    }

    private fun scheduleRepeat(action: Action, delayMs: Long) {
        val task = Runnable {
            repeatTasks.remove(action)
            move(action)
            scheduleRepeat(action, HoldRepeat.REPEAT_INTERVAL_MS)
        }
        repeatTasks[action] = task
        scheduler.postDelayed(task, delayMs)
    }

    private fun cancel(action: Action) {
        repeatTasks.remove(action)?.let { scheduler.cancel(it) }
    }
}
