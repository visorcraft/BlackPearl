package com.visorcraft.ghostgalleon.library

/**
 * Favorites and named collections of slot keys, plus bulk fill of empty
 * grid slots. Pure; host-tested.
 */
object CollectionsOps {

    fun addFavorite(favorites: Set<String>, key: String): Set<String> =
        favorites + key

    fun removeFavorite(favorites: Set<String>, key: String): Set<String> =
        favorites - key

    fun toggleFavorite(favorites: Set<String>, key: String): Set<String> =
        if (key in favorites) favorites - key else favorites + key

    fun isFavorite(favorites: Set<String>, key: String): Boolean = key in favorites

    fun addToCollection(
        collections: Map<String, List<String>>,
        name: String,
        key: String,
    ): Map<String, List<String>> {
        val n = name.trim()
        if (n.isEmpty()) return collections
        val existing = collections[n].orEmpty()
        if (key in existing) return collections
        return collections + (n to (existing + key))
    }

    fun removeFromCollection(
        collections: Map<String, List<String>>,
        name: String,
        key: String,
    ): Map<String, List<String>> {
        val existing = collections[name] ?: return collections
        val next = existing.filterNot { it == key }
        return if (next.isEmpty()) collections - name else collections + (name to next)
    }

    fun members(collections: Map<String, List<String>>, name: String): List<String> =
        collections[name].orEmpty()

    /**
     * Fill successive null slots left-to-right with [keys], preserving
     * already-filled slots. Extra keys beyond empty capacity are dropped.
     * Returns a new list (never shorter than [slots]).
     */
    fun bulkFillSlots(slots: List<String?>, keys: List<String>): List<String?> {
        if (keys.isEmpty()) return slots
        val out = slots.toMutableList()
        var ki = 0
        for (i in out.indices) {
            if (ki >= keys.size) break
            if (out[i] == null) {
                out[i] = keys[ki]
                ki++
            }
        }
        // Overflow: append new slots for remaining keys.
        while (ki < keys.size) {
            out.add(keys[ki])
            ki++
        }
        return out
    }

    /** Count of null slots that bulkFill could fill without appending. */
    fun emptySlotCount(slots: List<String?>): Int = slots.count { it == null }

    fun renameCollection(
        collections: Map<String, List<String>>,
        from: String,
        to: String,
    ): Map<String, List<String>> {
        val src = from.trim()
        val dest = to.trim()
        if (src.isEmpty() || dest.isEmpty() || src == dest) return collections
        val members = collections[src] ?: return collections
        val merged = (collections[dest].orEmpty() + members).distinct()
        return collections - src + (dest to merged)
    }

    fun deleteCollection(
        collections: Map<String, List<String>>,
        name: String,
    ): Map<String, List<String>> = collections - name.trim()

    /** True when a rail name is user-manageable (not the Favorites mirror). */
    fun isUserCollection(name: String): Boolean =
        name.trim().isNotEmpty() && !name.equals("Favorites", ignoreCase = true)

    fun createCollection(
        collections: Map<String, List<String>>,
        name: String,
    ): Map<String, List<String>> {
        val n = name.trim()
        if (n.isEmpty() || n in collections) return collections
        return collections + (n to emptyList())
    }

    /** Add every key in [keys] to favorites. */
    fun bulkAddFavorites(favorites: Set<String>, keys: List<String>): Set<String> =
        favorites + keys.filter { it.isNotBlank() }

    /** Remove every key in [keys] from favorites. */
    fun bulkRemoveFavorites(favorites: Set<String>, keys: List<String>): Set<String> =
        favorites - keys.toSet()

    /**
     * Clear all favorites and drop the "Favorites" collection mirror if
     * present. Returns empty favorites set + updated collections map.
     * Pure; host-tested.
     */
    fun clearAllFavorites(
        favorites: Set<String>,
        collections: Map<String, List<String>>,
    ): Pair<Set<String>, Map<String, List<String>>> {
        // favorites arg documents the input; result is always empty.
        @Suppress("UNUSED_PARAMETER")
        val _fav = favorites
        val cols = collections.filterKeys { !it.equals("Favorites", ignoreCase = true) }
        return emptySet<String>() to cols
    }

    /** Add every key to a named collection (creating the list if needed). */
    fun bulkAddToCollection(
        collections: Map<String, List<String>>,
        name: String,
        keys: List<String>,
    ): Map<String, List<String>> {
        var c = collections
        keys.filter { it.isNotBlank() }.forEach { k ->
            c = addToCollection(c, name, k)
        }
        return c
    }

    /** Remove every key from a named collection (drops the rail when emptied). */
    fun bulkRemoveFromCollection(
        collections: Map<String, List<String>>,
        name: String,
        keys: List<String>,
    ): Map<String, List<String>> {
        val n = name.trim()
        if (n.isEmpty() || keys.isEmpty()) return collections
        var c = collections
        keys.filter { it.isNotBlank() }.forEach { k ->
            c = removeFromCollection(c, n, k)
        }
        return c
    }

    /**
     * Active collection rail name for remove actions: named COLLECTION mode,
     * or the Favorites mirror when browsing Favorites.
     */
    fun activeCollectionName(modeName: String, collectionName: String?): String? {
        val mode = modeName.trim().uppercase()
        return when (mode) {
            "COLLECTION" -> collectionName?.trim()?.takeIf { it.isNotEmpty() }
            "FAVORITES" -> "Favorites"
            else -> null
        }
    }

    /**
     * Add [key] to [name], creating the collection when missing. Returns the
     * updated map and whether the key was newly inserted (false if blank,
     * already a member, or empty name).
     */
    fun addToCollectionResult(
        collections: Map<String, List<String>>,
        name: String,
        key: String,
    ): Pair<Map<String, List<String>>, Boolean> {
        val n = name.trim()
        val k = key.trim()
        if (n.isEmpty() || k.isEmpty()) return collections to false
        val existing = collections[n].orEmpty()
        if (k in existing) return collections to false
        val next = if (n in collections) {
            collections + (n to (existing + k))
        } else {
            collections + (n to listOf(k))
        }
        return next to true
    }

    /**
     * Move [key] within named collection to [toIndex] (clamped after removal).
     * No-op when name/key missing or blank.
     */
    fun moveMember(
        collections: Map<String, List<String>>,
        name: String,
        key: String,
        toIndex: Int,
    ): Map<String, List<String>> {
        val n = name.trim()
        val k = key.trim()
        if (n.isEmpty() || k.isEmpty()) return collections
        val list = collections[n]?.toMutableList() ?: return collections
        val from = list.indexOf(k)
        if (from < 0) return collections
        list.removeAt(from)
        val dest = toIndex.coerceIn(0, list.size)
        if (dest == from) {
            // Re-insert at same spot for identity (list was mutated).
            list.add(dest, k)
            return collections + (n to list.toList())
        }
        list.add(dest, k)
        return collections + (n to list.toList())
    }

    /** Move [key] by [delta] slots (−1 = up / earlier, +1 = down / later). */
    fun moveMemberBy(
        collections: Map<String, List<String>>,
        name: String,
        key: String,
        delta: Int,
    ): Map<String, List<String>> {
        if (delta == 0) return collections
        val n = name.trim()
        val k = key.trim()
        val from = collections[n]?.indexOf(k) ?: return collections
        if (from < 0) return collections
        return moveMember(collections, n, k, from + delta)
    }

    /** Pin [key] to the front or end of the named collection. */
    fun moveMemberToEdge(
        collections: Map<String, List<String>>,
        name: String,
        key: String,
        toFront: Boolean,
    ): Map<String, List<String>> {
        val n = name.trim()
        val k = key.trim()
        val list = collections[n] ?: return collections
        if (k !in list) return collections
        return moveMember(collections, n, k, if (toFront) 0 else Int.MAX_VALUE)
    }

    /**
     * True when the browse mode exposes an ordered named collection list that
     * can be reordered (COLLECTION only; FAVORITES set is unordered).
     */
    fun canReorderCollection(modeName: String, collectionName: String?): Boolean {
        if (modeName.trim().uppercase() != "COLLECTION") return false
        return !collectionName.isNullOrBlank()
    }
}
