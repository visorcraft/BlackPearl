package com.visorcraft.blackpearl.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CollectionsOpsTest {

    @Test
    fun `toggleFavorite adds and removes`() {
        val a = CollectionsOps.toggleFavorite(emptySet(), "rom:x")
        assertTrue(CollectionsOps.isFavorite(a, "rom:x"))
        val b = CollectionsOps.toggleFavorite(a, "rom:x")
        assertFalse(CollectionsOps.isFavorite(b, "rom:x"))
    }

    @Test
    fun `addToCollection dedupes and creates named list`() {
        val c1 = CollectionsOps.addToCollection(emptyMap(), "RPGs", "rom:a")
        val c2 = CollectionsOps.addToCollection(c1, "RPGs", "rom:b")
        val c3 = CollectionsOps.addToCollection(c2, "RPGs", "rom:a")
        assertEquals(listOf("rom:a", "rom:b"), CollectionsOps.members(c3, "RPGs"))
    }

    @Test
    fun `removeFromCollection drops empty lists`() {
        val c = mapOf("X" to listOf("a"))
        val next = CollectionsOps.removeFromCollection(c, "X", "a")
        assertFalse(next.containsKey("X"))
    }

    @Test
    fun `bulkFillSlots fills nulls left to right then appends overflow`() {
        val slots = listOf("keep", null, null, "end")
        val filled = CollectionsOps.bulkFillSlots(slots, listOf("a", "b", "c"))
        assertEquals(listOf("keep", "a", "b", "end", "c"), filled)
    }

    @Test
    fun `bulkFillSlots empty keys is identity`() {
        val slots = listOf<String?>(null, "x")
        assertEquals(slots, CollectionsOps.bulkFillSlots(slots, emptyList()))
    }

    @Test
    fun `emptySlotCount counts nulls`() {
        assertEquals(2, CollectionsOps.emptySlotCount(listOf(null, "a", null)))
    }
}
