package com.visorcraft.ghostgalleon.library

/**
 * Pure detail-sheet body for long-press "Details…" on apps/ROMs.
 * Host-tested; no Android types.
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
}
