package com.visorcraft.blackpearl.library

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
}
