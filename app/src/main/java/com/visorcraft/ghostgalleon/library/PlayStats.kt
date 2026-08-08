package com.visorcraft.ghostgalleon.library

/**
 * Per-slot-key launch / playtime stats. Keys are the same as grid/dock
 * slot keys (package name or "rom:<id>"). Pure; host-tested.
 */
data class PlayStats(
    val lastLaunchedMs: Map<String, Long> = emptyMap(),
    val totalPlaytimeMs: Map<String, Long> = emptyMap(),
) {
    companion object {
        val EMPTY = PlayStats()
    }
}

/** Session accrual: launch stamps last-played; return accrues duration. */
object SessionMath {

    /** Duration of a session, clamped to non-negative. */
    fun sessionDurationMs(launchAtMs: Long, returnAtMs: Long): Long =
        (returnAtMs - launchAtMs).coerceAtLeast(0L)

    fun recordLaunch(stats: PlayStats, key: String, nowMs: Long): PlayStats =
        stats.copy(lastLaunchedMs = stats.lastLaunchedMs + (key to nowMs))

    fun recordReturn(
        stats: PlayStats,
        key: String,
        launchAtMs: Long,
        returnAtMs: Long,
    ): PlayStats {
        val delta = sessionDurationMs(launchAtMs, returnAtMs)
        if (delta == 0L) return stats
        val prev = stats.totalPlaytimeMs[key] ?: 0L
        return stats.copy(
            totalPlaytimeMs = stats.totalPlaytimeMs + (key to (prev + delta)),
        )
    }

    /**
     * True when [key] has a positive last-launch stamp and/or positive
     * accumulated playtime (menu can offer "Clear play stats").
     */
    fun hasStats(stats: PlayStats, key: String): Boolean {
        val k = key.trim()
        if (k.isEmpty()) return false
        return (stats.lastLaunchedMs[k] ?: 0L) > 0L ||
            (stats.totalPlaytimeMs[k] ?: 0L) > 0L
    }

    /**
     * Drop last-launch + playtime for [key]. Missing key is a no-op.
     * Does not touch favorites, collections, or dock/grid pins.
     */
    fun clearStats(stats: PlayStats, key: String): PlayStats {
        val k = key.trim()
        if (k.isEmpty()) return stats
        if (!hasStats(stats, k)) return stats
        return stats.copy(
            lastLaunchedMs = stats.lastLaunchedMs - k,
            totalPlaytimeMs = stats.totalPlaytimeMs - k,
        )
    }

    /** Human-readable playtime for hero labels (e.g. "12m", "1h 5m"). */
    fun formatPlaytime(ms: Long): String {
        if (ms <= 0L) return "0m"
        val totalMin = ms / 60_000L
        if (totalMin < 60L) return "${totalMin}m"
        val h = totalMin / 60L
        val m = totalMin % 60L
        return if (m == 0L) "${h}h" else "${h}h ${m}m"
    }

    /** Relative last-played label from [nowMs] and [lastMs], or null. */
    fun formatLastPlayed(lastMs: Long?, nowMs: Long): String? {
        if (lastMs == null || lastMs <= 0L) return null
        val ago = (nowMs - lastMs).coerceAtLeast(0L)
        val min = ago / 60_000L
        return when {
            min < 1L -> "Just now"
            min < 60L -> "${min}m ago"
            min < 60L * 24L -> "${min / 60L}h ago"
            else -> "${min / (60L * 24L)}d ago"
        }
    }

    /**
     * Compact card/hero subtitle: "2h ago · 1h 5m" or "Never played".
     * [playtimeMs] is total accrued; omitted from the line when ≤ 0.
     * [playtimePrefix] is prepended to the duration (e.g. `"Played "` on hero).
     * [favorite] / [inDock] append compact status tags (★ / Dock) without
     * adding always-on chrome elsewhere.
     */
    fun cardMetaLine(
        lastMs: Long?,
        playtimeMs: Long,
        nowMs: Long,
        playtimePrefix: String = "",
        favorite: Boolean = false,
        inDock: Boolean = false,
    ): String {
        val parts = mutableListOf<String>()
        formatLastPlayed(lastMs, nowMs)?.let { parts.add(it) }
        if (playtimeMs > 0L) {
            parts.add(playtimePrefix + formatPlaytime(playtimeMs))
        }
        if (parts.isEmpty()) {
            // Keep "Never played" when we only have status tags, or alone.
            if (!favorite && !inDock) return "Never played"
            parts.add("Never played")
        }
        if (favorite) parts.add("★")
        if (inDock) parts.add("Dock")
        return parts.joinToString(" · ")
    }
}
