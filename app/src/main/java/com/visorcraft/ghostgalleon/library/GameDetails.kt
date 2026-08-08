package com.visorcraft.ghostgalleon.library

/**
 * Pure detail-sheet body for long-press "Details…" on apps/ROMs, plus
 * "Browse related" filter options from ROM meta (platform / genre / dev /
 * decade). Host-tested; no Android types.
 */
object GameDetails {

    data class Input(
        val title: String,
        val key: String,
        val kind: String, // "App" | "ROM" | custom
        val platformId: String? = null,
        val genre: String? = null,
        val developer: String? = null,
        val year: String? = null,
        val rating: String? = null,
        val lastLaunchedMs: Long? = null,
        val playtimeMs: Long = 0L,
        val favorite: Boolean = false,
        val collections: List<String> = emptyList(),
        val nowMs: Long = 0L,
    )

    /**
     * One "Browse related" jump: label for the picker and the meta fields to
     * apply on [LibraryBrowse.Mode.ALL] (other filters cleared).
     */
    data class RelatedOption(
        val label: String,
        val platformId: String? = null,
        val genre: String? = null,
        val developer: String? = null,
        val yearDecade: String? = null,
    )

    /**
     * Collections (sorted) that list [key] as a member. "Favorites" is not
     * auto-injected — callers pass the named map only.
     */
    fun collectionsContaining(
        collections: Map<String, List<String>>,
        key: String,
    ): List<String> {
        val k = key.trim()
        if (k.isEmpty()) return emptyList()
        return collections.entries
            .filter { (_, members) -> k in members }
            .map { it.key }
            .sortedBy { it.lowercase() }
    }

    /** Multi-line body for an AlertDialog-style details sheet. */
    fun body(input: Input): String {
        val lines = mutableListOf<String>()
        lines.add(input.title.trim().ifEmpty { input.key })
        lines.add("")
        lines.add("Type: ${input.kind}")
        input.platformId?.trim()?.takeIf { it.isNotEmpty() }?.let {
            lines.add("Platform: $it")
        }
        input.year?.trim()?.takeIf { it.isNotEmpty() }?.let {
            lines.add("Year: $it")
        }
        input.genre?.trim()?.takeIf { it.isNotEmpty() }?.let {
            lines.add("Genre: $it")
        }
        input.developer?.trim()?.takeIf { it.isNotEmpty() }?.let {
            lines.add("Developer: $it")
        }
        input.rating?.trim()?.takeIf { it.isNotEmpty() }?.let {
            lines.add("Rating: $it")
        }
        lines.add("Key: ${input.key}")
        lines.add("")
        val last = SessionMath.formatLastPlayed(input.lastLaunchedMs, input.nowMs)
            ?: "Never"
        lines.add("Last played: $last")
        lines.add("Playtime: ${SessionMath.formatPlaytime(input.playtimeMs)}")
        lines.add("Favorite: ${if (input.favorite) "Yes" else "No"}")
        if (input.collections.isNotEmpty()) {
            lines.add("Collections: ${input.collections.joinToString(", ")}")
        } else {
            lines.add("Collections: —")
        }
        return lines.joinToString("\n")
    }

    /**
     * Related-filter picker rows from ROM (or app) meta. Only includes
     * dimensions allowed by the corresponding browse-chrome flags so
     * [BrowseChrome.sanitize] will not immediately strip them. Genre strings
     * may yield up to [maxGenreTokens] token rows. Empty when nothing usable.
     * Pure; host-tested.
     */
    fun relatedOptions(
        platformId: String? = null,
        genre: String? = null,
        developer: String? = null,
        year: String? = null,
        allowPlatform: Boolean = true,
        allowGenre: Boolean = false,
        allowDeveloper: Boolean = false,
        allowYear: Boolean = false,
        maxGenreTokens: Int = 3,
    ): List<RelatedOption> {
        val out = mutableListOf<RelatedOption>()
        if (allowPlatform) {
            platformId?.trim()?.takeIf { it.isNotEmpty() }?.let { pid ->
                out += RelatedOption(label = "Platform · $pid", platformId = pid)
            }
        }
        if (allowGenre) {
            val limit = maxGenreTokens.coerceAtLeast(0)
            val seen = linkedSetOf<String>()
            LibraryBrowse.genreTokens(genre).forEach { token ->
                if (out.size >= 20) return@forEach
                val key = token.lowercase()
                if (key in seen) return@forEach
                if (seen.size >= limit) return@forEach
                seen += key
                out += RelatedOption(label = "Genre · $token", genre = token)
            }
        }
        if (allowDeveloper) {
            developer?.trim()?.takeIf { it.isNotEmpty() }?.let { dev ->
                out += RelatedOption(label = "Developer · $dev", developer = dev)
            }
        }
        if (allowYear) {
            LibraryBrowse.yearDecadeOf(year)?.let { decade ->
                out += RelatedOption(label = "Decade · $decade", yearDecade = decade)
            }
        }
        return out
    }

    /** Convenience: related options from a details [Input] + chrome flags. */
    fun relatedOptions(
        input: Input,
        allowPlatform: Boolean = true,
        allowGenre: Boolean = false,
        allowDeveloper: Boolean = false,
        allowYear: Boolean = false,
        maxGenreTokens: Int = 3,
    ): List<RelatedOption> = relatedOptions(
        platformId = input.platformId,
        genre = input.genre,
        developer = input.developer,
        year = input.year,
        allowPlatform = allowPlatform,
        allowGenre = allowGenre,
        allowDeveloper = allowDeveloper,
        allowYear = allowYear,
        maxGenreTokens = maxGenreTokens,
    )

    /**
     * Browse query for a related jump: All rail, only the option's meta set,
     * sort preserved when provided. Pure; host-tested.
     */
    fun toBrowseQuery(
        option: RelatedOption,
        sort: LibraryBrowse.Sort = LibraryBrowse.Sort.DEFAULT,
    ): LibraryBrowse.BrowseQuery =
        LibraryBrowse.BrowseQuery(
            mode = LibraryBrowse.Mode.ALL,
            platformId = option.platformId?.trim()?.takeIf { it.isNotEmpty() },
            genre = option.genre?.trim()?.takeIf { it.isNotEmpty() },
            developer = option.developer?.trim()?.takeIf { it.isNotEmpty() },
            yearDecade = option.yearDecade?.trim()?.takeIf { it.isNotEmpty() },
            text = "",
            collectionName = null,
            sort = sort,
        )
}
