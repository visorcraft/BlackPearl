package com.visorcraft.ghostgalleon.library

/**
 * Recent library search queries for Game Mode Search. Newest-first,
 * case-insensitive dedupe, blank rejected. Pure; host-tested. No Android types.
 *
 * Stored as optional [com.visorcraft.ghostgalleon.settings.Settings.searchHistory]
 * (schema v8, no bump). Long-press Search opens the history picker; applying
 * a non-blank search pushes onto the list.
 */
object SearchHistory {

    /** Default cap for persisted recent queries. */
    const val DEFAULT_LIMIT: Int = 12

    /**
     * Normalize a raw query for storage/compare: trim; empty → null.
     */
    fun normalize(query: String): String? {
        val q = query.trim()
        return q.ifEmpty { null }
    }

    /**
     * Push [query] to the front of [history]. Blank → unchanged.
     * Matching prior entries (case-insensitive) are dropped so the newest
     * casing wins. Result is capped at [limit] (default [DEFAULT_LIMIT]).
     */
    fun push(
        history: List<String>,
        query: String,
        limit: Int = DEFAULT_LIMIT,
    ): List<String> {
        val q = normalize(query) ?: return history
        val cap = limit.coerceAtLeast(0)
        if (cap == 0) return emptyList()
        val rest = history.filterNot { it.equals(q, ignoreCase = true) }
        return (listOf(q) + rest).take(cap)
    }

    /**
     * Drop the first case-insensitive match of [query]. Missing → unchanged.
     */
    fun remove(history: List<String>, query: String): List<String> {
        val q = normalize(query) ?: return history
        var dropped = false
        return history.filter { entry ->
            if (!dropped && entry.equals(q, ignoreCase = true)) {
                dropped = true
                false
            } else {
                true
            }
        }
    }

    fun clear(): List<String> = emptyList()
}
