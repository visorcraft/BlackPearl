package com.visorcraft.ghostgalleon.library

/**
 * Honest play sessions: wall-clock between launch and return is NOT all
 * playtime. Time while the launcher is focused or the device is asleep is
 * paused and not accrued. Pure; host-tested.
 *
 * Lifecycle for the app owner:
 * - [onLaunch] when a game/app starts
 * - [onLauncherFocused] when a deck activity resumes (pause accrual)
 * - [onLauncherUnfocused] when leaving for the game again (optional)
 * - [onDeviceSleep] / [onDeviceWake] around screen-off
 * - [onReturn] when the session ends and playtime should be committed
 */
data class OpenSession(
    val key: String,
    val launchAtMs: Long,
    /** Sum of completed active segments so far (ms). */
    val accruedActiveMs: Long = 0L,
    /** Wall time when the current active segment started, or null if paused. */
    val activeSegmentStartMs: Long? = null,
    val pausedForLauncher: Boolean = false,
    val pausedForSleep: Boolean = false,
) {
    val isActive: Boolean get() = activeSegmentStartMs != null
    val isOpen: Boolean get() = true
}

object SessionTracker {

    fun onLaunch(key: String, nowMs: Long): OpenSession =
        OpenSession(
            key = key,
            launchAtMs = nowMs,
            accruedActiveMs = 0L,
            // Start paused for launcher: launch is initiated from the
            // launcher; active time begins when focus leaves (or immediately
            // if the host prefers onLauncherUnfocused). Default: active
            // immediately so a simple launch→return without focus events
            // still accrues (honesty events only subtract when used).
            activeSegmentStartMs = nowMs,
            pausedForLauncher = false,
            pausedForSleep = false,
        )

    fun onLauncherFocused(session: OpenSession, nowMs: Long): OpenSession {
        if (session.pausedForLauncher) return session
        return pause(session, nowMs).copy(pausedForLauncher = true)
    }

    fun onLauncherUnfocused(session: OpenSession, nowMs: Long): OpenSession {
        if (!session.pausedForLauncher) return session
        val cleared = session.copy(pausedForLauncher = false)
        return if (cleared.pausedForSleep) cleared else resume(cleared, nowMs)
    }

    fun onDeviceSleep(session: OpenSession, nowMs: Long): OpenSession {
        if (session.pausedForSleep) return session
        return pause(session, nowMs).copy(pausedForSleep = true)
    }

    fun onDeviceWake(session: OpenSession, nowMs: Long): OpenSession {
        if (!session.pausedForSleep) return session
        val cleared = session.copy(pausedForSleep = false)
        return if (cleared.pausedForLauncher) cleared else resume(cleared, nowMs)
    }

    /**
     * End the session and return total active playtime ms (segments + open
     * active segment if any). Does not count paused time.
     */
    fun onReturn(session: OpenSession, nowMs: Long): Long {
        val closed = pause(session, nowMs)
        return closed.accruedActiveMs.coerceAtLeast(0L)
    }

    /** Elapsed active ms as of [nowMs] without mutating (for Now Playing UI). */
    fun activeElapsedMs(session: OpenSession, nowMs: Long): Long {
        val open = session.activeSegmentStartMs
            ?.let { (nowMs - it).coerceAtLeast(0L) }
            ?: 0L
        return session.accruedActiveMs + open
    }

    private fun pause(session: OpenSession, nowMs: Long): OpenSession {
        val start = session.activeSegmentStartMs ?: return session
        val delta = (nowMs - start).coerceAtLeast(0L)
        return session.copy(
            accruedActiveMs = session.accruedActiveMs + delta,
            activeSegmentStartMs = null,
        )
    }

    private fun resume(session: OpenSession, nowMs: Long): OpenSession {
        if (session.activeSegmentStartMs != null) return session
        if (session.pausedForLauncher || session.pausedForSleep) return session
        return session.copy(activeSegmentStartMs = nowMs)
    }

    /** Commit [activeMs] into [PlayStats.totalPlaytimeMs] for the session key. */
    fun commitPlaytime(stats: PlayStats, key: String, activeMs: Long): PlayStats {
        if (activeMs <= 0L) return stats
        val prev = stats.totalPlaytimeMs[key] ?: 0L
        return stats.copy(
            lastLaunchedMs = stats.lastLaunchedMs + (key to (stats.lastLaunchedMs[key] ?: 0L)),
            totalPlaytimeMs = stats.totalPlaytimeMs + (key to (prev + activeMs)),
        )
    }
}

/**
 * Multi-select bulk transforms for Game Mode. Pure; host-tested.
 */
object MultiSelectOps {

    fun toggleSelection(selected: Set<String>, key: String): Set<String> =
        if (key in selected) selected - key else selected + key

    fun selectAll(keys: List<String>): Set<String> = keys.toSet()

    fun clearSelection(): Set<String> = emptySet()

    /**
     * How many of [selected] are currently in [favorites] (for bulk
     * Unfavorite affordance labels).
     */
    fun favoriteCountInSelection(
        favorites: Set<String>,
        selected: Set<String>,
    ): Int = selected.count { it in favorites }

    fun bulkFavorite(
        favorites: Set<String>,
        selected: Set<String>,
        add: Boolean,
    ): Set<String> =
        if (add) CollectionsOps.bulkAddFavorites(favorites, selected.toList())
        else CollectionsOps.bulkRemoveFavorites(favorites, selected.toList())

    fun bulkPinToGrid(
        gridSlots: List<String?>,
        selected: Set<String>,
    ): List<String?> =
        CollectionsOps.bulkFillSlots(gridSlots, selected.toList())

    /**
     * Pin selected keys into the dock (first blanks). Returns updated dock
     * slots and how many keys were newly added.
     */
    fun bulkPinToDock(
        dockSlots: List<String?>,
        selected: Set<String>,
    ): Pair<List<String?>, Int> =
        com.visorcraft.ghostgalleon.settings.DockSlots.pinKeys(
            dockSlots,
            selected.toList(),
        )

    /**
     * Hide ROM entry ids from selected slot keys (package keys ignored).
     * Returns the updated [hiddenRomIds] set and how many ROMs were newly hidden.
     */
    fun bulkHideRoms(
        hiddenRomIds: Set<String>,
        selected: Set<String>,
    ): Pair<Set<String>, Int> {
        var next = hiddenRomIds
        var added = 0
        selected.forEach { key ->
            val romId = com.visorcraft.ghostgalleon.settings.SlotKey.romId(key) ?: return@forEach
            if (romId !in next) {
                next = HiddenRoms.hide(next, romId)
                added++
            }
        }
        return next to added
    }
}
