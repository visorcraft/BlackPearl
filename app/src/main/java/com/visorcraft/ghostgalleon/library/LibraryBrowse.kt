package com.visorcraft.ghostgalleon.library

import com.visorcraft.ghostgalleon.rom.RomEntry
import com.visorcraft.ghostgalleon.settings.SlotKey

/**
 * Pure library browse ops for Game Mode / pickers: platform filter, text
 * search, and recents ordering. Host-tested; no Android types.
 */
object LibraryBrowse {

    enum class Mode { ALL, RECENT, FAVORITES, COLLECTION }

    data class BrowseQuery(
        val mode: Mode = Mode.ALL,
        val platformId: String? = null,
        val text: String = "",
        /** When [mode] is COLLECTION, members of this named list. */
        val collectionName: String? = null,
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
        collections: Map<String, List<String>> = emptyMap(),
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
            Mode.COLLECTION -> {
                val name = query.collectionName?.trim().orEmpty()
                val ids = collections[name].orEmpty().mapNotNull { key ->
                    if (SlotKey.isRom(key)) SlotKey.romId(key) else null
                }.toSet()
                roms.filter { it.visibleInUi && it.id in ids }
            }
        }
        val platformed = filterByPlatform(base, query.platformId)
        return searchRoms(platformed, query.text)
    }

    /** Distinct platform ids present in the visible library, sorted. */
    fun presentPlatforms(roms: List<RomEntry>): List<String> =
        roms.filter { it.visibleInUi }.map { it.platformId }.distinct().sorted()

    /** Named collection titles suitable for Game Mode rails (stable sort). */
    fun presentCollectionRails(collections: Map<String, List<String>>): List<String> =
        collections.keys.filter { it.isNotBlank() }.sortedBy { it.lowercase() }

    /**
     * Pick one item at random from [items]. [nextIndex] is called with the
     * list size and must return an index in `0 until size` (injectable RNG
     * for host tests). Empty list → null.
     */
    fun <T> pickRandom(items: List<T>, nextIndex: (size: Int) -> Int): T? {
        if (items.isEmpty()) return null
        val i = nextIndex(items.size)
        if (i !in items.indices) return null
        return items[i]
    }

    /**
     * Continue target: the most recently launched key that still exists in
     * [availableKeys], or null when none.
     */
    fun continueKey(
        availableKeys: List<String>,
        lastLaunchedMs: Map<String, Long>,
    ): String? {
        if (availableKeys.isEmpty() || lastLaunchedMs.isEmpty()) return null
        val present = availableKeys.toSet()
        return orderByRecent(
            lastLaunchedMs.keys.filter { it in present },
            lastLaunchedMs,
        ).firstOrNull()
    }
}
