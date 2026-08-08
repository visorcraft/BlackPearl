package com.visorcraft.ghostgalleon.library

import com.visorcraft.ghostgalleon.rom.RomEntry
import com.visorcraft.ghostgalleon.settings.SlotKey

/**
 * Pure library browse ops for Game Mode / pickers: platform filter, text
 * search, recents / today / week / month / most-played / recently-installed /
 * games / A–Z / unplayed ordering, letter jump index. Host-tested; no Android types.
 */
object LibraryBrowse {

    /** Default rolling window for [Mode.PLAYED_TODAY] (24 hours). */
    const val DAY_WINDOW_MS: Long = 24L * 60L * 60L * 1000L

    /** Default rolling window for [Mode.PLAYED_THIS_WEEK] (7 days). */
    const val WEEK_WINDOW_MS: Long = 7L * 24L * 60L * 60L * 1000L

    /** Default rolling window for [Mode.PLAYED_THIS_MONTH] (30 days). */
    const val MONTH_WINDOW_MS: Long = 30L * 24L * 60L * 60L * 1000L

    enum class Mode {
        ALL,
        RECENT,
        /**
         * Titles launched within the last 24 hours (rolling), newest first.
         * Empty when nothing has been played in the window.
         */
        PLAYED_TODAY,
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
        /**
         * Optional developer / publisher filter (gamelist meta). ROM-only;
         * case-insensitive exact match on trimmed [RomEntry.developer].
         */
        val developer: String? = null,
        /**
         * Optional release-year decade filter (e.g. `"1990s"`). ROM-only;
         * derived from [RomEntry.year] via [yearDecadeOf].
         */
        val yearDecade: String? = null,
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

    /** True when [rom] matches optional [developer] (case-insensitive exact). */
    fun matchesDeveloper(rom: RomEntry, developer: String?): Boolean {
        val needle = developer?.trim().orEmpty()
        if (needle.isEmpty()) return true
        val raw = rom.developer?.trim().orEmpty()
        return raw.isNotEmpty() && raw.equals(needle, ignoreCase = true)
    }

    fun filterByDeveloper(roms: List<RomEntry>, developer: String?): List<RomEntry> {
        if (developer.isNullOrBlank()) return roms
        return roms.filter { matchesDeveloper(it, developer) }
    }

    /**
     * Parse a 4-digit release year from gamelist-style strings
     * (`"1991"`, `"1991-03"`, `"© 2017"`). Out-of-range → null.
     */
    fun parseYear(raw: String?): Int? {
        if (raw.isNullOrBlank()) return null
        val m = Regex("""(\d{4})""").find(raw.trim()) ?: return null
        val y = m.groupValues[1].toIntOrNull() ?: return null
        return if (y in 1970..2099) y else null
    }

    /** Decade label for a calendar year: `1991` → `"1990s"`. */
    fun yearDecadeLabel(year: Int): String {
        val start = (year / 10) * 10
        return "${start}s"
    }

    /** Decade label for a raw year string, or null when unparseable. */
    fun yearDecadeOf(raw: String?): String? =
        parseYear(raw)?.let { yearDecadeLabel(it) }

    /** True when [rom] falls in optional [decade] (e.g. `"1990s"`). */
    fun matchesYearDecade(rom: RomEntry, decade: String?): Boolean {
        val d = decade?.trim().orEmpty()
        if (d.isEmpty()) return true
        return yearDecadeOf(rom.year)?.equals(d, ignoreCase = true) == true
    }

    fun filterByYearDecade(roms: List<RomEntry>, decade: String?): List<RomEntry> {
        if (decade.isNullOrBlank()) return roms
        return roms.filter { matchesYearDecade(it, decade) }
    }

    /**
     * Distinct release decades present in the listed library with ROM counts,
     * sorted by decade ascending (oldest first). [limit] caps chip bar length
     * (default 8). Used for labeled chips like `"1990s · 12"`.
     */
    fun presentYearDecadeCounts(
        roms: List<RomEntry>,
        hiddenRomIds: Set<String> = emptySet(),
        limit: Int = 8,
    ): List<Pair<String, Int>> {
        val counts = linkedMapOf<String, Int>()
        HiddenRoms.listed(roms, hiddenRomIds).forEach { rom ->
            val d = yearDecadeOf(rom.year) ?: return@forEach
            counts[d] = (counts[d] ?: 0) + 1
        }
        return counts.entries
            .sortedBy { it.key }
            .take(limit.coerceAtLeast(0))
            .map { it.key to it.value }
    }

    /**
     * Distinct developers present in the listed library with ROM counts,
     * sorted by count descending then name. [limit] caps chip bar length
     * (default 10). Used for labeled chips like "Nintendo · 42".
     */
    fun presentDeveloperCounts(
        roms: List<RomEntry>,
        hiddenRomIds: Set<String> = emptySet(),
        limit: Int = 10,
    ): List<Pair<String, Int>> {
        val counts = linkedMapOf<String, Int>()
        val display = linkedMapOf<String, String>()
        HiddenRoms.listed(roms, hiddenRomIds).forEach { rom ->
            val raw = rom.developer?.trim().orEmpty()
            if (raw.isEmpty()) return@forEach
            val key = raw.lowercase()
            if (key !in display) display[key] = raw
            counts[key] = (counts[key] ?: 0) + 1
        }
        return counts.entries
            .sortedWith(
                compareByDescending<Map.Entry<String, Int>> { it.value }
                    .thenBy { it.key },
            )
            .take(limit.coerceAtLeast(0))
            .map { (key, n) -> (display[key] ?: key) to n }
    }

    /**
     * Distinct genre tokens present in the listed library with per-token
     * ROM counts, sorted by count descending then name. [limit] caps chip
     * bar length (default 12). Used for labeled chips like "Action · 4".
     */
    fun presentGenreCounts(
        roms: List<RomEntry>,
        hiddenRomIds: Set<String> = emptySet(),
        limit: Int = 12,
    ): List<Pair<String, Int>> {
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
            .map { (key, n) -> (display[key] ?: key) to n }
    }

    /**
     * Distinct genre tokens present in the listed library, sorted by count
     * descending then name. [limit] caps chip bar length (default 12).
     */
    fun presentGenres(
        roms: List<RomEntry>,
        hiddenRomIds: Set<String> = emptySet(),
        limit: Int = 12,
    ): List<String> = presentGenreCounts(roms, hiddenRomIds, limit).map { it.first }

    /**
     * True when [rom] matches a search [needle] (already lowercased, non-empty).
     * Hits name, id, platform, genre, developer, year, and description.
     */
    fun romMatchesSearch(rom: RomEntry, needle: String): Boolean {
        if (needle.isEmpty()) return true
        fun hit(raw: String?): Boolean =
            !raw.isNullOrBlank() && raw.lowercase().contains(needle)
        return hit(rom.name) ||
            hit(rom.id) ||
            hit(rom.platformId) ||
            hit(rom.genre) ||
            hit(rom.developer) ||
            hit(rom.year) ||
            hit(rom.description)
    }

    /**
     * Case-insensitive substring search across ROM name, id, platform, genre,
     * developer, year, and description (gamelist meta when present).
     */
    fun searchRoms(
        roms: List<RomEntry>,
        query: String,
        hiddenRomIds: Set<String> = emptySet(),
    ): List<RomEntry> {
        val listed = HiddenRoms.listed(roms, hiddenRomIds)
        val q = query.trim()
        if (q.isEmpty()) return listed
        val needle = q.lowercase()
        return listed.filter { romMatchesSearch(it, needle) }
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
     * Prefer [filteredKeys] when non-empty for Random (stay in current rail /
     * filter); otherwise fall back to [fullKeys]. Pure; host-tested.
     */
    fun randomPool(filteredKeys: List<String>, fullKeys: List<String>): List<String> =
        if (filteredKeys.isNotEmpty()) filteredKeys else fullKeys

    /**
     * Full browse pipeline: mode → platform → genre → developer → year decade
     * → text. Recents / week / month / most-played / favorites / A–Z /
     * unplayed are applied by restricting first, then filter/search.
     *
     * [nowMs] is used by [Mode.PLAYED_TODAY], [Mode.PLAYED_THIS_WEEK], and
     * [Mode.PLAYED_THIS_MONTH] (default 0 → empty window).
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
            Mode.PLAYED_TODAY -> {
                val byKey = listed.associateBy { SlotKey.rom(it.id) }
                val dayKeys = filterPlayedInWindow(
                    byKey.keys.toList(),
                    lastLaunchedMs,
                    nowMs = nowMs,
                    windowMs = DAY_WINDOW_MS,
                )
                dayKeys.mapNotNull { byKey[it] }
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
        val developed = filterByDeveloper(genred, query.developer)
        val yeared = filterByYearDecade(developed, query.yearDecade)
        return searchRoms(yeared, query.text, emptySet())
    }

    /** Distinct platform ids present in the listed library, sorted. */
    fun presentPlatforms(
        roms: List<RomEntry>,
        hiddenRomIds: Set<String> = emptySet(),
    ): List<String> =
        HiddenRoms.listed(roms, hiddenRomIds).map { it.platformId }.distinct().sorted()

    /**
     * Platforms with listed ROM counts (sorted by platform id). Empty library
     * → empty list. Used for chip labels like "SNES · 12".
     */
    fun presentPlatformCounts(
        roms: List<RomEntry>,
        hiddenRomIds: Set<String> = emptySet(),
    ): List<Pair<String, Int>> {
        val counts = linkedMapOf<String, Int>()
        HiddenRoms.listed(roms, hiddenRomIds).forEach { rom ->
            counts[rom.platformId] = (counts[rom.platformId] ?: 0) + 1
        }
        return counts.entries
            .sortedBy { it.key }
            .map { it.key to it.value }
    }

    /**
     * Chip label with optional positive count suffix: "Fav · 3", "SNES · 12".
     * Non-positive [count] → [base] unchanged.
     */
    fun labeledChip(base: String, count: Int): String {
        val label = base.trim()
        if (label.isEmpty()) return if (count > 0) "· $count" else ""
        return if (count > 0) "$label · $count" else label
    }

    /** Keys with a positive last-launch stamp (Recent rail size proxy). */
    fun recentCount(lastLaunchedMs: Map<String, Long>): Int =
        lastLaunchedMs.count { it.value > 0L }

    /**
     * Keys launched inside a rolling window ending at [nowMs]
     * (Week/Month chip size proxy). Non-positive [nowMs] → 0.
     */
    fun playedInWindowCount(
        lastLaunchedMs: Map<String, Long>,
        nowMs: Long,
        windowMs: Long = WEEK_WINDOW_MS,
    ): Int {
        if (nowMs <= 0L || lastLaunchedMs.isEmpty()) return 0
        return filterPlayedInWindow(
            lastLaunchedMs.keys.toList(),
            lastLaunchedMs,
            nowMs = nowMs,
            windowMs = windowMs,
        ).size
    }

    /** Keys with positive playtime (Top rail size proxy). */
    fun topPlayedCount(playtimeMs: Map<String, Long>): Int =
        playtimeMs.count { it.value > 0L }

    /**
     * Listed ROMs with no positive last-launch stamp (New rail size proxy).
     * Hidden ROMs are excluded.
     */
    fun unplayedRomCount(
        roms: List<RomEntry>,
        lastLaunchedMs: Map<String, Long>,
        hiddenRomIds: Set<String> = emptySet(),
    ): Int =
        HiddenRoms.listed(roms, hiddenRomIds).count {
            isUnplayed(SlotKey.rom(it.id), lastLaunchedMs)
        }

    /** Listed (non-hidden) ROM count — A–Z / Games catalog size proxy half. */
    fun listedRomCount(
        roms: List<RomEntry>,
        hiddenRomIds: Set<String> = emptySet(),
    ): Int = HiddenRoms.listed(roms, hiddenRomIds).size

    /**
     * Games rail size proxy: game apps + listed ROMs.
     * Non-positive parts clamp to 0.
     */
    fun gamesCatalogCount(gameAppCount: Int, listedRomCount: Int): Int =
        gameAppCount.coerceAtLeast(0) + listedRomCount.coerceAtLeast(0)

    /**
     * A–Z rail size proxy: curated apps + listed ROMs.
     * Non-positive parts clamp to 0.
     */
    fun alphaCatalogCount(appCount: Int, listedRomCount: Int): Int =
        appCount.coerceAtLeast(0) + listedRomCount.coerceAtLeast(0)

    /**
     * Continue chip label: `"Continue · Eden"` when a target name is known,
     * truncated to [maxNameLen] (default 14). Blank target → plain [base].
     * Pure; host-tested. No new always-on chrome — depth of core Continue.
     */
    fun continueChipLabel(
        targetLabel: String?,
        base: String = "Continue",
        maxNameLen: Int = 14,
    ): String {
        val name = targetLabel?.trim().orEmpty()
        if (name.isEmpty()) return base.trim().ifEmpty { "Continue" }
        val cap = maxNameLen.coerceAtLeast(1)
        val short = if (name.length <= cap) name else name.take(cap - 1) + "…"
        val head = base.trim().ifEmpty { "Continue" }
        return "$head · $short"
    }

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
     *
     * For **cold-start hero seed**, use [coldStartKey] instead — last-launched
     * is an explicit Continue/Recent action, not the home landing selection.
     */
    fun continueKey(
        availableKeys: List<String>,
        lastLaunchedMs: Map<String, Long>,
    ): String? = continueCandidates(
        availableKeys = availableKeys,
        lastLaunchedMs = lastLaunchedMs,
        excludeKey = null,
    ).firstOrNull()

    /**
     * Resume-chip candidates newest-first: keys present in [availableKeys]
     * with a last-launched stamp, excluding [excludeKey] (usually the current
     * hero selection so Resume never points at what's already shown).
     */
    fun continueCandidates(
        availableKeys: List<String>,
        lastLaunchedMs: Map<String, Long>,
        excludeKey: String? = null,
    ): List<String> {
        if (availableKeys.isEmpty() || lastLaunchedMs.isEmpty()) return emptyList()
        val present = availableKeys.toSet()
        return orderByRecent(
            lastLaunchedMs.keys.filter { it in present && it != excludeKey },
            lastLaunchedMs,
        )
    }

    /**
     * Long-press Continue history: newest-first candidates capped at [limit]
     * (default 20). Empty when nothing is continue-able.
     */
    fun continueHistory(
        availableKeys: List<String>,
        lastLaunchedMs: Map<String, Long>,
        limit: Int = 20,
    ): List<String> {
        if (limit <= 0) return emptyList()
        return continueCandidates(
            availableKeys = availableKeys,
            lastLaunchedMs = lastLaunchedMs,
            excludeKey = null,
        ).take(limit)
    }

    /**
     * Dialog/chip line for a history row: `"Zelda · 2h ago"` when last-played
     * is known, otherwise just [label]. Pure; host-tested.
     */
    fun continueHistoryLine(
        label: String,
        lastMs: Long?,
        nowMs: Long,
    ): String {
        val name = label.trim().ifEmpty { "—" }
        val ago = SessionMath.formatLastPlayed(lastMs, nowMs) ?: return name
        return "$name · $ago"
    }

    /**
     * Swipe the Resume chip. [delta] +1 = older (typically fling left),
     * −1 = newer (fling right). Past either end → null (clear that resume).
     */
    fun continueAfterSwipe(
        candidates: List<String>,
        current: String,
        delta: Int,
    ): String? {
        if (candidates.isEmpty() || delta == 0) return current.takeIf { it in candidates }
        val i = candidates.indexOf(current).let { if (it < 0) 0 else it }
        val next = i + delta
        if (next < 0 || next >= candidates.size) return null
        return candidates[next]
    }

    /**
     * Apply a Resume swipe to [lastLaunchedMs].
     * - [next] null → drop [current] (chip may go away or show the next newest)
     * - [next] other → promote that key to newest so [continueKey] returns it
     */
    fun applyContinueSwipe(
        lastLaunchedMs: Map<String, Long>,
        current: String,
        next: String?,
        nowMs: Long,
    ): Map<String, Long> {
        if (next == null) return lastLaunchedMs - current
        if (next == current) return lastLaunchedMs
        val peak = (lastLaunchedMs.values.maxOrNull() ?: 0L) + 1L
        return lastLaunchedMs + (next to maxOf(peak, nowMs))
    }

    /**
     * Cold-start / first-paint selection for the hero panel.
     *
     * Prefer the first filled grid slot (user-curated home order — e.g. Eden
     * in slot 0). Only fall back to [continueKey] when the grid has no filled
     * slots. Never pick a random last-launched utility app over the grid.
     */
    fun coldStartKey(
        gridSlots: List<String?>,
        dockSlots: List<String?> = emptyList(),
        lastLaunchedMs: Map<String, Long> = emptyMap(),
    ): String? {
        gridSlots.firstOrNull { !it.isNullOrBlank() }?.let { return it }
        dockSlots.firstOrNull { !it.isNullOrBlank() }?.let { return it }
        val available = buildList {
            addAll(gridSlots.filterNotNull())
            addAll(dockSlots.filterNotNull())
            addAll(lastLaunchedMs.keys)
        }
        return continueKey(available, lastLaunchedMs)
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
     * Letter buckets with counts for the A–Z jump strip (A–Z then optional
     * '#'). Empty input → empty list. Used for labels like `"M · 12"`.
     */
    fun presentLetterCounts(labels: List<String>): List<Pair<Char, Int>> {
        if (labels.isEmpty()) return emptyList()
        val counts = linkedMapOf<Char, Int>()
        labels.forEach { label ->
            val b = letterBucket(label)
            counts[b] = (counts[b] ?: 0) + 1
        }
        val letters = ('A'..'Z').mapNotNull { c ->
            counts[c]?.let { n -> c to n }
        }
        return if ('#' in counts) letters + ('#' to counts.getValue('#')) else letters
    }

    /**
     * Distinct letter buckets present in [labels], A–Z then optional '#'.
     * Empty input → empty list (no strip).
     */
    fun presentLetterIndex(labels: List<String>): List<Char> =
        presentLetterCounts(labels).map { it.first }

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
