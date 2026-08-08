package com.visorcraft.ghostgalleon.ui.deck

import com.visorcraft.ghostgalleon.settings.DockSlots

/**
 * Pure dock-move working copy (lifted tile swap). Shared by Grid and Game.
 * Host UI owns [DockBar] focus rings; this only tracks slots + lifted index.
 */
class DockMoveState {
    var index: Int? = null
        private set
    var working: MutableList<String?>? = null
        private set

    val active: Boolean get() = index != null

    fun start(slot: Int, slots: List<String?>) {
        index = slot
        working = slots.toMutableList()
    }

    /** Swap [from] with [to]; returns new lifted index, or null if inactive. */
    fun swap(from: Int, to: Int): Int? {
        val w = working ?: return null
        if (from !in w.indices || to !in w.indices || from == to) return index
        val tmp = w[from]
        w[from] = w[to]
        w[to] = tmp
        index = to
        return to
    }

    data class Drop(
        val compacted: List<String?>,
        val focusIndex: Int,
    )

    /**
     * Commit move: optional [tapSlot] swap first, then compact.
     * Clears state. Focus lands on the dropped key's post-compact index.
     */
    fun drop(tapSlot: Int? = null): Drop? {
        val from = index ?: return null
        val w = working ?: return null
        var finalSlot = from
        if (tapSlot != null && tapSlot in w.indices && tapSlot != from) {
            swap(from, tapSlot)
            finalSlot = tapSlot
        }
        val slots = w.toList()
        clear()
        val compacted = DockSlots.compact(slots)
        val droppedKey = slots.getOrNull(finalSlot)
        val focus = if (droppedKey != null) {
            compacted.indexOf(droppedKey).coerceAtLeast(0)
        } else {
            finalSlot.coerceIn(0, (DockSlots.visibleCount(compacted) - 1).coerceAtLeast(0))
        }
        return Drop(compacted, focus)
    }

    fun clear() {
        index = null
        working = null
    }
}
