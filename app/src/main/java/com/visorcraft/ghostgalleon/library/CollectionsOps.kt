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
}
