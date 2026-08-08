package com.visorcraft.ghostgalleon.library

import com.visorcraft.ghostgalleon.rom.RomEntry
import com.visorcraft.ghostgalleon.settings.SlotKey

/**
 * Pure library browse ops for Game Mode / pickers: platform filter, text
 * search, recents / week / month / most-played / recently-installed / games /
 * A–Z / unplayed ordering, letter jump index. Host-tested; no Android types.
 */
object LibraryBrowse {

    /** Default rolling window for [Mode.PLAYED_THIS_WEEK] (7 days). */
    const val WEEK_WINDOW_MS: Long = 7L * 24L * 60L * 60L * 1000L

    /** Default rolling window for [Mode.PLAYED_THIS_MONTH] (30 days). */
    const val MONTH_WINDOW_MS: Long = 30L * 24L * 60L * 60L * 1000L

    enum class Mode {
        ALL,
        RECENT,
        /**
         * Titles launched within the last week (rolling), newest first.
         * Empty when nothing has been played in the window.
         */
        PLAYED_THIS_WEEK,
        /**
         * Titles launched within the last 30 days (rolling), newest first.
         * Empty when nothing has been played in the window.
         */
        PLAYED_THIS_MONTH,
        FAVORITES,
        COLLECTION,
        MOST_PLAYED,
        /** Apps ordered by package install time (newest first). ROMs omitted. */
        RECENTLY_INSTALLED,
        /**
         * Games catalog: Android CATEGORY_GAME apps + all listed ROMs.
         * ROM side uses the same listing as [ALL] (platform/search still apply).
         */
        GAMES,
        ALPHA,
        UNPLAYED,
    }

    data class BrowseQuery(
        val mode: Mode = Mode.ALL,
        val platformId: String? = null,
        val text: String = "",
        /** When [mode] is COLLECTION, members of this named list. */
        val collectionName: String? = null,
        /**
         * Optional genre token (from gamelist meta). ROM-only filter; matches
         * any slash/comma segment of [RomEntry.genre] case-insensitively.
         */
        val genre: String? = null,
    )

    /** Listed ROMs only (scanner-visible, not user-hidden), optional platform. */
    fun filterByPlatform(
        roms: List<RomEntry>,
        platformId: String?,
        hiddenRomIds: Set<String> = emptySet(),
    ): List<RomEntry> {
        val visible = HiddenRoms.listed(roms, hiddenRomIds)
        if (platformId == null) return visible
        return visible.filter { it.platformId == platformId }
    }

    /**
     * Split a raw genre string into tokens (comma / slash / pipe / semicolon).
     * Empty or blank → empty list.
     */
    fun genreTokens(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.split(',', '/', '|', ';')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    /** True when [rom] matches optional [genre] token (any segment). */
    fun matchesGenre(rom: RomEntry, genre: String?): Boolean {
        val needle = genre?.trim().orEmpty()
        if (needle.isEmpty()) return true
        return genreTokens(rom.genre).any { it.equals(needle, ignoreCase = true) }
    }

    fun filterByGenre(roms: List<RomEntry>, genre: String?): List<RomEntry> {
        if (genre.isNullOrBlank()) return roms
        return roms.filter { matchesGenre(it, genre) }
    }

    /**
     * Distinct genre tokens present in the listed library, sorted by count
     * descending then name. [limit] caps chip bar length (default 12).
     */
    fun presentGenres(
        roms: List<RomEntry>,
        hiddenRomIds: Set<String> = emptySet(),
        limit: Int = 12,
    ): List<String> {
        val counts = linkedMapOf<String, Int>()
        // canonical display form: first-seen casing per lowercase key
        val display = linkedMapOf<String, String>()
        HiddenRoms.listed(roms, hiddenRomIds).forEach { rom ->
            genreTokens(rom.genre).forEach { token ->
                val key = token.lowercase()
                if (key !in display) display[key] = token
                counts[key] = (counts[key] ?: 0) + 1
            }
        }
        return counts.entries
            .sortedWith(
                compareByDescending<Map.Entry<String, Int>> { it.value }
                    .thenBy { it.key },
            )
            .take(limit.coerceAtLeast(0))
            .map { display[it.key] ?: it.key }
    }

    /** Case-insensitive substring match on ROM name (and id as fallback). */
    fun searchRoms(
        roms: List<RomEntry>,
        query: String,
        hiddenRomIds: Set<String> = emptySet(),
    ): List<RomEntry> {
        val listed = HiddenRoms.listed(roms, hiddenRomIds)
        val q = query.trim()
        if (q.isEmpty()) return listed
        val needle = q.lowercase()
        return listed.filter {
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
     * Order keys by accumulated playtime descending. Keys with zero/missing
     * playtime fall to the end, preserving relative input order among ties.
     */
    fun orderByPlaytime(keys: List<String>, playtimeMs: Map<String, Long>): List<String> {
        return keys.withIndex().sortedWith(
            compareByDescending<IndexedValue<String>> { playtimeMs[it.value] ?: Long.MIN_VALUE }
                .thenBy { it.index },
        ).map { it.value }
    }

    /**
     * Order keys by install time descending (newest installs first). Missing
     * or non-positive stamps fall to the end, stable on input order.
     */
    fun orderByInstallTime(keys: List<String>, firstInstallMs: Map<String, Long>): List<String> {
        return keys.withIndex().sortedWith(
            compareByDescending<IndexedValue<String>> {
                firstInstallMs[it.value] ?: Long.MIN_VALUE
            }.thenBy { it.index },
        ).map { it.value }
    }

    /**
     * Case-insensitive A–Z by [labelOf], stable on input order for ties.
     */
    fun <T> orderByName(items: List<T>, labelOf: (T) -> String): List<T> {
        return items.withIndex().sortedWith(
            compareBy<IndexedValue<T>> { labelOf(it.value).lowercase() }
                .thenBy { it.index },
        ).map { it.value }
    }

    /** True when [key] has never been launched (missing or non-positive stamp). */
    fun isUnplayed(key: String, lastLaunchedMs: Map<String, Long>): Boolean =
        (lastLaunchedMs[key] ?: 0L) <= 0L

    /**
     * True when [key] was last launched at or after [sinceMs] (inclusive).
     * Missing / non-positive stamps → false.
     */
    fun isPlayedSince(
        key: String,
        lastLaunchedMs: Map<String, Long>,
        sinceMs: Long,
    ): Boolean {
        val t = lastLaunchedMs[key] ?: return false
        return t > 0L && t >= sinceMs
    }

    /** Start of a rolling window of [windowMs] ending at [nowMs]. */
    fun windowStartMs(nowMs: Long, windowMs: Long = WEEK_WINDOW_MS): Long =
        nowMs - windowMs.coerceAtLeast(0L)

    /**
     * Keys launched within [nowMs - windowMs, nowMs], newest first.
     * Keys outside the window or with missing stamps are dropped.
     * Non-positive [nowMs] → empty (caller must supply a real clock).
     */
    fun filterPlayedInWindow(
        keys: List<String>,
        lastLaunchedMs: Map<String, Long>,
        nowMs: Long,
        windowMs: Long = WEEK_WINDOW_MS,
    ): List<String> {
        if (nowMs <= 0L) return emptyList()
        val since = windowStartMs(nowMs, windowMs)
        val inWindow = keys.filter { isPlayedSince(it, lastLaunchedMs, since) }
        return orderByRecent(inWindow, lastLaunchedMs)
    }

    /**
     * Filter Android apps marked as games ([AppEntry.isGame]). Pure helper for
     * the Games rail (host can pass any list of packages with a flag).
     */
    fun <T> filterGameApps(items: List<T>, isGame: (T) -> Boolean): List<T> =
        items.filter(isGame)

    /**
     * Full browse pipeline: mode → platform → genre → text. Recents /
     * week / month / most-played / favorites / A–Z / unplayed are applied by
     * restricting first, then filter/search.
     *
     * [nowMs] is used by [Mode.PLAYED_THIS_WEEK] and [Mode.PLAYED_THIS_MONTH]
     * (default 0 → empty window).
     */
    fun browseRoms(
        roms: List<RomEntry>,
        query: BrowseQuery,
        lastLaunchedMs: Map<String, Long> = emptyMap(),
        favorites: Set<String> = emptySet(),
        collections: Map<String, List<String>> = emptyMap(),
        playtimeMs: Map<String, Long> = emptyMap(),
        hiddenRomIds: Set<String> = emptySet(),
        nowMs: Long = 0L,
    ): List<RomEntry> {
        val listed = HiddenRoms.listed(roms, hiddenRomIds)
        val base = when (query.mode) {
            Mode.ALL -> listed
            Mode.ALPHA -> orderByName(listed) { it.name }
            Mode.UNPLAYED -> {
                val unplayed = listed.filter {
                    isUnplayed(SlotKey.rom(it.id), lastLaunchedMs)
                }
                orderByName(unplayed) { it.name }
            }
            Mode.RECENT -> {
                val byKey = listed.associateBy { SlotKey.rom(it.id) }
                val recentKeys = lastLaunchedMs.keys.filter { it in byKey }
                orderByRecent(recentKeys, lastLaunchedMs).mapNotNull { byKey[it] }
            }
            Mode.PLAYED_THIS_WEEK -> {
                val byKey = listed.associateBy { SlotKey.rom(it.id) }
                val weekKeys = filterPlayedInWindow(
                    byKey.keys.toList(),
                    lastLaunchedMs,
                    nowMs = nowMs,
                    windowMs = WEEK_WINDOW_MS,
                )
                weekKeys.mapNotNull { byKey[it] }
            }
            Mode.PLAYED_THIS_MONTH -> {
                val byKey = listed.associateBy { SlotKey.rom(it.id) }
                val monthKeys = filterPlayedInWindow(
                    byKey.keys.toList(),
                    lastLaunchedMs,
                    nowMs = nowMs,
                    windowMs = MONTH_WINDOW_MS,
                )
                monthKeys.mapNotNull { byKey[it] }
            }
            Mode.MOST_PLAYED -> {
                val byKey = listed.associateBy { SlotKey.rom(it.id) }
                // Only keys with positive playtime — empty Top rail when none.
                val topKeys = playtimeMs
                    .filter { (k, v) -> v > 0L && k in byKey }
                    .keys
                    .toList()
                orderByPlaytime(topKeys, playtimeMs).mapNotNull { byKey[it] }
            }
            // Install-time rail is app-only (PackageManager firstInstallTime).
            Mode.RECENTLY_INSTALLED -> emptyList()
            // Games rail includes every listed ROM (emulated titles are games).
            Mode.GAMES -> listed
            Mode.FAVORITES -> {
                val favIds = favorites.mapNotNull { key ->
                    if (SlotKey.isRom(key)) SlotKey.romId(key) else null
                }.toSet()
                listed.filter { it.id in favIds }
            }
            Mode.COLLECTION -> {
                // Preserve user member order (apps skipped here; GameDeck merges).
                val name = query.collectionName?.trim().orEmpty()
                val byId = listed.associateBy { it.id }
                collections[name].orEmpty().mapNotNull { key ->
                    SlotKey.romId(key)?.let { byId[it] }
                }
            }
        }
        // base already listed; pass empty hidden set so platform/search do not
        // re-filter away members that were intentionally kept (none).
        val platformed = filterByPlatform(base, query.platformId, emptySet())
        val genred = filterByGenre(platformed, query.genre)
        return searchRoms(genred, query.text, emptySet())
    }

    /** Distinct platform ids present in the listed library, sorted. */
    fun presentPlatforms(
        roms: List<RomEntry>,
        hiddenRomIds: Set<String> = emptySet(),
    ): List<String> =
        HiddenRoms.listed(roms, hiddenRomIds).map { it.platformId }.distinct().sorted()

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

    /**
     * Highest playtime key (positive only), or null when none. Used by Quick
     * Panel → Top to jump selection after switching to the Most Played rail.
     */
    fun topPlayedKey(playtimeMs: Map<String, Long>): String? {
        val keys = playtimeMs.filter { it.value > 0L }.keys.toList()
        if (keys.isEmpty()) return null
        return orderByPlaytime(keys, playtimeMs).firstOrNull()
    }

    /** Browse query for a Game Mode rail (Quick Panel shortcuts). */
    fun railQuery(mode: Mode): BrowseQuery =
        BrowseQuery(mode = mode)

    /**
     * A–Z letter bucket for a display label: first significant character
     * uppercased into A–Z, or '#' for empty / non-letter (digits, symbols).
     * Used by the Game Mode letter jump strip under A–Z / New rails.
     */
    fun letterBucket(label: String): Char {
        val c = label.trim().firstOrNull()?.uppercaseChar() ?: return '#'
        return if (c in 'A'..'Z') c else '#'
    }

    /**
     * Distinct letter buckets present in [labels], A–Z then optional '#'.
     * Empty input → empty list (no strip).
     */
    fun presentLetterIndex(labels: List<String>): List<Char> {
        if (labels.isEmpty()) return emptyList()
        val present = labels.map { letterBucket(it) }.toSet()
        val letters = ('A'..'Z').filter { it in present }
        return if ('#' in present) letters + '#' else letters
    }

    /**
     * First index in [labels] whose [letterBucket] equals [letter]
     * (case-insensitive A–Z; anything else maps to '#'). Missing → -1.
     */
    fun firstIndexForLetter(labels: List<String>, letter: Char): Int {
        val bucket = letter.uppercaseChar().let { c ->
            if (c in 'A'..'Z') c else '#'
        }
        return labels.indexOfFirst { letterBucket(it) == bucket }
    }
}
