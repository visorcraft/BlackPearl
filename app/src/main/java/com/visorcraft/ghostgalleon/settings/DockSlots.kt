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
}
