package com.visorcraft.ghostgalleon.library

/**
 * Short user-facing hints for empty browse rails and search results.
 * Pure; host-tested. No Android types.
 */
object BrowseFeedback {

    /**
     * Message when a rail / filter yields zero items. Null when silence is
     * better (e.g. unrestricted All — apps may still fill the carousel).
     */
    fun emptyHint(query: LibraryBrowse.BrowseQuery): String? {
        val text = query.text.trim()
        if (text.isNotEmpty()) {
            return "No matches for \"$text\""
        }
        if (!query.genre.isNullOrBlank()) {
            return "No titles in ${query.genre.trim()}"
        }
        if (!query.developer.isNullOrBlank()) {
            return "No titles by ${query.developer.trim()}"
        }
        if (!query.yearDecade.isNullOrBlank()) {
            return "No titles from ${query.yearDecade.trim()}"
        }
        if (query.platformId != null) {
            return "No titles on ${query.platformId}"
        }
        return when (query.mode) {
            LibraryBrowse.Mode.PLAYED_TODAY -> "Nothing played today"
            LibraryBrowse.Mode.PLAYED_THIS_WEEK -> "Nothing played this week"
            LibraryBrowse.Mode.PLAYED_THIS_MONTH -> "Nothing played this month"
            LibraryBrowse.Mode.MOST_PLAYED -> "No playtime recorded yet"
            LibraryBrowse.Mode.UNPLAYED -> "No unplayed titles"
            LibraryBrowse.Mode.RECENTLY_INSTALLED -> "No installs found"
            LibraryBrowse.Mode.COLLECTION -> {
                val name = query.collectionName?.trim().orEmpty()
                if (name.isEmpty()) "Collection is empty" else "\"$name\" is empty"
            }
            LibraryBrowse.Mode.FAVORITES -> "No favorites yet"
            LibraryBrowse.Mode.RECENT -> "Nothing recent yet"
            LibraryBrowse.Mode.GAMES -> "No games found"
            LibraryBrowse.Mode.ALPHA -> "Library empty"
            LibraryBrowse.Mode.ALL -> null
        }
    }

    /**
     * Toast after applying or clearing a text search.
     * [count] is the number of matching carousel entries (apps + ROMs).
     */
    fun searchApplied(count: Int, query: String): String {
        val q = query.trim()
        if (q.isEmpty()) return "Search cleared"
        return if (count <= 0) {
            "No matches for \"$q\""
        } else {
            val unit = if (count == 1) "match" else "matches"
            "$count $unit for \"$q\""
        }
    }

    /**
     * True when [emptyHint] should fire after switching rails even when only
     * the ROM half of the pipeline is empty (apps may still interleave for
     * some modes — callers decide with a full entry count).
     */
    fun preferFullCount(mode: LibraryBrowse.Mode): Boolean = when (mode) {
        LibraryBrowse.Mode.RECENT,
        LibraryBrowse.Mode.PLAYED_TODAY,
        LibraryBrowse.Mode.PLAYED_THIS_WEEK,
        LibraryBrowse.Mode.PLAYED_THIS_MONTH,
        LibraryBrowse.Mode.MOST_PLAYED,
        LibraryBrowse.Mode.FAVORITES,
        LibraryBrowse.Mode.GAMES,
        LibraryBrowse.Mode.RECENTLY_INSTALLED,
        LibraryBrowse.Mode.COLLECTION,
        -> true
        else -> false
    }
}
