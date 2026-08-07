package com.visorcraft.ghostgalleon.settings

// Pure slot-list operations for the curated grid. Slots are ordered; null
// means blank. All operations return new lists. The deck RENDERS the list
// padded up to a whole number of rows (paddedCount) so no incomplete row
// ever shows; filling a padded slot extends the stored list to match.
object GridSlots {

    const val DEFAULT_COUNT = 12

    fun blank(count: Int = DEFAULT_COUNT): List<String?> = List(count) { null }

    // Rendered slot count: [size] rounded up to a whole multiple of
    // [columns] so the grid never shows an incomplete row. The extra cells
    // render and behave as ordinary blank slots.
    fun paddedCount(size: Int, columns: Int): Int {
        if (columns <= 0 || size <= 0) return size
        val remainder = size % columns
        return if (remainder == 0) size else size + columns - remainder
    }

    // Filling past the current end (a padded blank) extends the list with
    // nulls up to [index], then sets it — the padded slot becomes real.
    fun fill(slots: List<String?>, index: Int, packageName: String): List<String?> {
        if (index < 0) return slots
        if (index >= slots.size) {
            return slots + List(index - slots.size) { null } + packageName
        }
        return slots.toMutableList().apply { set(index, packageName) }
    }

    fun remove(slots: List<String?>, index: Int): List<String?> {
        if (index !in slots.indices) return slots
        return slots.toMutableList().apply { set(index, null) }
    }

    // 3DS-style move: the lifted tile swaps contents with the target slot.
    fun moveSwap(slots: List<String?>, from: Int, to: Int): List<String?> {
        if (from !in slots.indices || to !in slots.indices) return slots
        if (from == to) return slots
        return slots.toMutableList().apply {
            val tmp = this[from]
            this[from] = this[to]
            this[to] = tmp
        }
    }
}
