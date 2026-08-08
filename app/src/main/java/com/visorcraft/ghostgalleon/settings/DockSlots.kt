package com.visorcraft.ghostgalleon.settings

// Pure slot-list operations for the auto-growing dock (schema v6).
//
// In memory the dock is a canonical `List<String?>` of CAPACITY entries:
// the filled keys in slot order FIRST, then trailing nulls. The store
// persists only the filled keys (SettingsStore writes filled(slots) and
// parse pads back to CAPACITY), so blanks exist only at render time.
//
// The bar renders visibleCount(slots) slots: one "+" placeholder past the
// filled count, at least MIN_VISIBLE, capped at CAPACITY — an empty dock
// shows 4 placeholders, filling the 4th reveals a 5th, and a full dock of
// 9 shows no "+". Host-tested in DockSlotsTest.
object DockSlots {

    const val CAPACITY = 9
    const val MIN_VISIBLE = 4

    fun blank(): List<String?> = List(CAPACITY) { null }

    fun filled(slots: List<String?>): List<String> = slots.filterNotNull()

    // Visible slots = max(MIN_VISIBLE, min(filled + 1, CAPACITY)).
    fun visibleCount(slots: List<String?>): Int =
        maxOf(MIN_VISIBLE, minOf(filled(slots).size + 1, CAPACITY))

    // Canonical form: filled keys first (order kept), nulls trailing,
    // exactly CAPACITY entries. Any stored/interim list (legacy
    // dockPackages, v4/v5 slot arrays with interior nulls, a move-mode
    // working copy) collapses to this; overflow beyond CAPACITY is dropped.
    fun compact(slots: List<String?>): List<String?> {
        val keys = filled(slots).take(CAPACITY)
        return keys + List(CAPACITY - keys.size) { null }
    }

    fun fill(slots: List<String?>, index: Int, key: String): List<String?> {
        if (index !in 0 until CAPACITY) return slots
        return compact(slots.toMutableList().apply { set(index, key) })
    }

    // Removing compacts: the slots after the removed key shift left.
    fun remove(slots: List<String?>, index: Int): List<String?> {
        if (index !in slots.indices) return slots
        return compact(slots.toMutableList().apply { set(index, null) })
    }

    // Same 3DS-style swap as the grid's move mode. NOT compacted here: the
    // move-mode working copy may park the lifted tile on the visible "+"
    // placeholder; the deck compacts when the move is dropped.
    fun moveSwap(slots: List<String?>, from: Int, to: Int): List<String?> =
        GridSlots.moveSwap(slots, from, to)

    // First blank slot for "Pin to dock" (the filled count in canonical
    // form); null when the dock is at CAPACITY.
    fun firstBlank(slots: List<String?>): Int? =
        slots.indexOfFirst { it == null }.takeIf { it >= 0 }

    /** Outcome of [pinKey] — pure; host-tested. */
    enum class PinStatus { PINNED, ALREADY, FULL }

    data class PinResult(val slots: List<String?>, val status: PinStatus)

    /**
     * Pin [key] into the first blank dock slot (canonical form).
     * Blank / whitespace keys are ignored (treated as already handled → ALREADY).
     */
    fun pinKey(slots: List<String?>, key: String): PinResult {
        val k = key.trim()
        val canon = compact(slots)
        if (k.isEmpty()) return PinResult(canon, PinStatus.ALREADY)
        if (k in filled(canon)) return PinResult(canon, PinStatus.ALREADY)
        val blank = firstBlank(canon) ?: return PinResult(canon, PinStatus.FULL)
        return PinResult(fill(canon, blank, k), PinStatus.PINNED)
    }

    /**
     * Pin many keys in order; skips already-present and stops filling when full.
     * Returns updated slots and how many keys were newly pinned.
     */
    fun pinKeys(slots: List<String?>, keys: List<String>): Pair<List<String?>, Int> {
        var next = compact(slots)
        var added = 0
        for (raw in keys) {
            val r = pinKey(next, raw)
            next = r.slots
            if (r.status == PinStatus.PINNED) added++
            if (r.status == PinStatus.FULL) break
        }
        return next to added
    }

    /** True when [key] is already a filled dock member. */
    fun containsKey(slots: List<String?>, key: String): Boolean {
        val k = key.trim()
        if (k.isEmpty()) return false
        return k in filled(slots)
    }

    /**
     * Remove [key] from the dock (first match) and compact. Missing key →
     * unchanged slots (caller may treat as no-op).
     */
    fun unpinKey(slots: List<String?>, key: String): List<String?> {
        val k = key.trim()
        if (k.isEmpty()) return compact(slots)
        val canon = compact(slots)
        val idx = canon.indexOfFirst { it == k }
        if (idx < 0) return canon
        return remove(canon, idx)
    }

    /**
     * Unpin many keys; returns updated slots and how many were removed.
     */
    fun unpinKeys(slots: List<String?>, keys: List<String>): Pair<List<String?>, Int> {
        var next = compact(slots)
        var removed = 0
        val want = keys.map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        if (want.isEmpty()) return next to 0
        val kept = filled(next).filter { k ->
            if (k in want) {
                removed++
                false
            } else true
        }
        return compact(kept) to removed
    }
}
