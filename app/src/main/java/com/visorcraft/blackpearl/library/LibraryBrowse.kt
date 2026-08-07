package com.visorcraft.blackpearl.library

import com.visorcraft.blackpearl.rom.RomEntry
import com.visorcraft.blackpearl.settings.SlotKey

/**
 * Pure library browse ops for Game Mode / pickers: platform filter, text
 * search, and recents ordering. Host-tested; no Android types.
 */
object LibraryBrowse {

    enum class Mode { ALL, RECENT, FAVORITES }

    data class BrowseQuery(
        val mode: Mode = Mode.ALL,
        val platformId: String? = null,
        val text: String = "",
    )

    /** Visible ROMs only, filtered by optional platform id. */
    fun filterByPlatform(roms: List<RomEntry>, platformId: String?): List<RomEntry> {
        val visible = roms.filter { it.visibleInUi }
        if (platformId == null) return visible
        return visible.filter { it.platformId == platformId }
    }

    /** Case-insensitive substring match on ROM name (and id as fallback). */
    fun searchRoms(roms: List<RomEntry>, query: String): List<RomEntry> {
        val q = query.trim()
        if (q.isEmpty()) return roms.filter { it.visibleInUi }
        val needle = q.lowercase()
        return roms.filter { it.visibleInUi }.filter {
            it.name.lowercase().contains(needle) ||
                it.id.lowercase().contains(needle) ||
                it.platformId.lowercase().contains(needle)
        }
    }

    /**
     * Order keys by last-launch time descending. Keys with no timestamp fall
     * to the end, preserving relative input order among unknowns.
     */
    fun orderByRecent(keys: List<String>, lastLaunchedMs: Map<String, Long>): List<String> {
        return keys.withIndex().sortedWith(
            compareByDescending<IndexedValue<String>> { lastLaunchedMs[it.value] ?: Long.MIN_VALUE }
                .thenBy { it.index },
        ).map { it.value }
    }

    /**
     * Full browse pipeline: mode → platform → text. Recents/favorites are
     * applied by restricting to known keys first, then filter/search.
     */
    fun browseRoms(
        roms: List<RomEntry>,
        query: BrowseQuery,
        lastLaunchedMs: Map<String, Long> = emptyMap(),
        favorites: Set<String> = emptySet(),
    ): List<RomEntry> {
        val base = when (query.mode) {
            Mode.ALL -> roms.filter { it.visibleInUi }
            Mode.RECENT -> {
                val byKey = roms.filter { it.visibleInUi }
                    .associateBy { SlotKey.rom(it.id) }
                val recentKeys = lastLaunchedMs.keys.filter { it in byKey }
                orderByRecent(recentKeys, lastLaunchedMs).mapNotNull { byKey[it] }
            }
            Mode.FAVORITES -> {
                val favIds = favorites.mapNotNull { key ->
                    if (SlotKey.isRom(key)) SlotKey.romId(key) else null
                }.toSet()
                roms.filter { it.visibleInUi && it.id in favIds }
            }
        }
        val platformed = filterByPlatform(base, query.platformId)
        return searchRoms(platformed, query.text)
    }

    /** Distinct platform ids present in the visible library, sorted. */
    fun presentPlatforms(roms: List<RomEntry>): List<String> =
        roms.filter { it.visibleInUi }.map { it.platformId }.distinct().sorted()
}
