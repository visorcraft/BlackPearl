package com.visorcraft.ghostgalleon.library

/**
 * Pure ranking helpers for light stats (Most played / Recently played).
 * Keys are slot keys (package or `rom:<id>`). Host-tested.
 */
object LibraryStats {

    data class RankedKey(
        val key: String,
        val score: Long,
    )

    /**
     * Keys ordered by accumulated playtime descending. Zero/missing playtime
     * omitted. Ties keep relative input order of [keys] when provided, else
     * order of map iteration after sort is stable by score only.
     */
    fun mostPlayed(
        playtimeMs: Map<String, Long>,
        limit: Int = 20,
        knownKeys: Collection<String>? = null,
    ): List<RankedKey> {
        val pool = knownKeys?.toSet()
        return playtimeMs
            .filter { (k, v) -> v > 0L && (pool == null || k in pool) }
            .entries
            .sortedWith(
                compareByDescending<Map.Entry<String, Long>> { it.value }
                    .thenBy { it.key },
            )
            .take(limit.coerceAtLeast(0))
            .map { RankedKey(it.key, it.value) }
    }

    /**
     * Keys ordered by last-launch time descending. Missing timestamps omitted.
     */
    fun recentlyPlayed(
        lastLaunchedMs: Map<String, Long>,
        limit: Int = 20,
        knownKeys: Collection<String>? = null,
    ): List<RankedKey> {
        val pool = knownKeys?.toSet()
        return lastLaunchedMs
            .filter { (k, v) -> v > 0L && (pool == null || k in pool) }
            .entries
            .sortedWith(
                compareByDescending<Map.Entry<String, Long>> { it.value }
                    .thenBy { it.key },
            )
            .take(limit.coerceAtLeast(0))
            .map { RankedKey(it.key, it.value) }
    }

    fun hasAnySessions(
        playtimeMs: Map<String, Long>,
        lastLaunchedMs: Map<String, Long>,
    ): Boolean =
        playtimeMs.any { it.value > 0L } || lastLaunchedMs.any { it.value > 0L }
}
