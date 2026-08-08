package com.visorcraft.ghostgalleon.settings

import com.visorcraft.ghostgalleon.library.LibraryBrowse
import org.json.JSONObject

/**
 * Optional Game Mode / Quick Panel / deck chrome. Defaults are **minimal**:
 * core rails only (All / Recent / Continue / Fav + platforms + search/select).
 * Power-user rails and extras opt in via Settings so the deck stays uncluttered.
 *
 * Pure; host-tested. Stored as optional JSON under settings (schema v8+).
 */
data class BrowseChrome(
    /** Apps by install time. */
    val installedRail: Boolean = false,
    /** CATEGORY_GAME apps + ROMs. */
    val gamesRail: Boolean = false,
    /** Most-played (Top). */
    val topRail: Boolean = false,
    /** Played in the last 7 days (Week). */
    val weekRail: Boolean = false,
    /** A–Z alphabetical rail (includes letter-jump strip). */
    val alphaRail: Boolean = false,
    /** Unplayed / New ROMs. */
    val unplayedRail: Boolean = false,
    /** Random pick chip on the browse bar. */
    val randomChip: Boolean = false,
    /** Genre chips from gamelist meta. */
    val genreChips: Boolean = false,
    /** Platform filter chips (SNES, Switch, …). Default on — core for ROMs. */
    val platformChips: Boolean = true,
    /** Named user collection chips (not Favorites). Default on if user created any. */
    val collectionRails: Boolean = true,
    /** Clock/battery overlay on Grid/Game decks (not companion hero pill). */
    val deckStatusPill: Boolean = false,
    /**
     * Quick Panel browse shortcuts beyond Continue: Random, Top, Fav, Games,
     * Installed. System tiles (Wi‑Fi / Settings / Theme) always stay.
     */
    val quickPanelBrowse: Boolean = false,
) {
    fun allowsMode(mode: LibraryBrowse.Mode): Boolean = when (mode) {
        LibraryBrowse.Mode.ALL,
        LibraryBrowse.Mode.RECENT,
        LibraryBrowse.Mode.FAVORITES,
        LibraryBrowse.Mode.COLLECTION,
        -> true
        LibraryBrowse.Mode.PLAYED_THIS_WEEK -> weekRail
        LibraryBrowse.Mode.MOST_PLAYED -> topRail
        LibraryBrowse.Mode.RECENTLY_INSTALLED -> installedRail
        LibraryBrowse.Mode.GAMES -> gamesRail
        LibraryBrowse.Mode.ALPHA -> alphaRail
        LibraryBrowse.Mode.UNPLAYED -> unplayedRail
    }

    /** Drop disallowed mode/genre into a safe query for the current chrome. */
    fun sanitize(q: LibraryBrowse.BrowseQuery): LibraryBrowse.BrowseQuery {
        var next = q
        if (!allowsMode(next.mode)) {
            next = LibraryBrowse.BrowseQuery()
        }
        if (!genreChips && !next.genre.isNullOrBlank()) {
            next = next.copy(genre = null)
        }
        if (!platformChips && next.platformId != null) {
            next = next.copy(platformId = null)
        }
        if (!collectionRails && next.mode == LibraryBrowse.Mode.COLLECTION) {
            next = LibraryBrowse.BrowseQuery()
        }
        return next
    }

    fun isMinimal(): Boolean = this == MINIMAL
    fun isFull(): Boolean = this == FULL

    fun toJson(): JSONObject = JSONObject()
        .put("installedRail", installedRail)
        .put("gamesRail", gamesRail)
        .put("topRail", topRail)
        .put("weekRail", weekRail)
        .put("alphaRail", alphaRail)
        .put("unplayedRail", unplayedRail)
        .put("randomChip", randomChip)
        .put("genreChips", genreChips)
        .put("platformChips", platformChips)
        .put("collectionRails", collectionRails)
        .put("deckStatusPill", deckStatusPill)
        .put("quickPanelBrowse", quickPanelBrowse)

    companion object {
        /** Default: uncluttered deck for most users. */
        val MINIMAL = BrowseChrome()

        /** Everything on for power users. */
        val FULL = BrowseChrome(
            installedRail = true,
            gamesRail = true,
            topRail = true,
            weekRail = true,
            alphaRail = true,
            unplayedRail = true,
            randomChip = true,
            genreChips = true,
            platformChips = true,
            collectionRails = true,
            deckStatusPill = true,
            quickPanelBrowse = true,
        )

        fun fromJson(o: JSONObject?): BrowseChrome {
            if (o == null) return MINIMAL
            return BrowseChrome(
                installedRail = o.optBoolean("installedRail", false),
                gamesRail = o.optBoolean("gamesRail", false),
                topRail = o.optBoolean("topRail", false),
                weekRail = o.optBoolean("weekRail", false),
                alphaRail = o.optBoolean("alphaRail", false),
                unplayedRail = o.optBoolean("unplayedRail", false),
                randomChip = o.optBoolean("randomChip", false),
                genreChips = o.optBoolean("genreChips", false),
                platformChips = o.optBoolean("platformChips", true),
                collectionRails = o.optBoolean("collectionRails", true),
                deckStatusPill = o.optBoolean("deckStatusPill", false),
                quickPanelBrowse = o.optBoolean("quickPanelBrowse", false),
            )
        }
    }
}
