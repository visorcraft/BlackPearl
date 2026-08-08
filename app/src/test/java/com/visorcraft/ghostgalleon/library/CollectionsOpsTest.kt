package com.visorcraft.ghostgalleon.library

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

    @Test
    fun `create rename delete collection`() {
        val c0 = CollectionsOps.createCollection(emptyMap(), "RPGs")
        assertTrue(c0.containsKey("RPGs"))
        val c1 = CollectionsOps.addToCollection(c0, "RPGs", "rom:a")
        val c2 = CollectionsOps.renameCollection(c1, "RPGs", "Story")
        assertFalse(c2.containsKey("RPGs"))
        assertEquals(listOf("rom:a"), c2["Story"])
        assertTrue(CollectionsOps.deleteCollection(c2, "Story").isEmpty())
    }

    @Test
    fun `bulkAddToCollection creates and fills`() {
        val c = CollectionsOps.bulkAddToCollection(emptyMap(), "Co-op", listOf("a", "b", "a"))
        assertEquals(listOf("a", "b"), c["Co-op"])
    }

    @Test
    fun `addToCollectionResult reports new insert vs no-op`() {
        val (c1, ok1) = CollectionsOps.addToCollectionResult(emptyMap(), "RPGs", "rom:a")
        assertTrue(ok1)
        assertEquals(listOf("rom:a"), c1["RPGs"])
        val (c2, ok2) = CollectionsOps.addToCollectionResult(c1, "RPGs", "rom:a")
        assertFalse(ok2)
        assertEquals(c1, c2)
        val (c3, ok3) = CollectionsOps.addToCollectionResult(c1, "  ", "rom:b")
        assertFalse(ok3)
        assertEquals(c1, c3)
    }

    @Test
    fun `isUserCollection excludes Favorites mirror`() {
        assertTrue(CollectionsOps.isUserCollection("SmokeShelf"))
        assertFalse(CollectionsOps.isUserCollection("Favorites"))
        assertFalse(CollectionsOps.isUserCollection("favorites"))
        assertFalse(CollectionsOps.isUserCollection("  "))
    }

    @Test
    fun `bulkRemoveFromCollection removes members and drops empty`() {
        val c0 = mapOf("RPGs" to listOf("a", "b", "c"), "Keep" to listOf("x"))
        val c1 = CollectionsOps.bulkRemoveFromCollection(c0, "RPGs", listOf("b", "a"))
        assertEquals(listOf("c"), c1["RPGs"])
        assertEquals(listOf("x"), c1["Keep"])
        val c2 = CollectionsOps.bulkRemoveFromCollection(c1, "RPGs", listOf("c", "missing"))
        assertFalse(c2.containsKey("RPGs"))
        assertEquals(listOf("x"), c2["Keep"])
    }

    @Test
    fun `bulkRemoveFromCollection blank name or keys is identity`() {
        val c = mapOf("X" to listOf("a"))
        assertEquals(c, CollectionsOps.bulkRemoveFromCollection(c, "  ", listOf("a")))
        assertEquals(c, CollectionsOps.bulkRemoveFromCollection(c, "X", emptyList()))
    }

    @Test
    fun `activeCollectionName maps COLLECTION and FAVORITES`() {
        assertEquals("RPGs", CollectionsOps.activeCollectionName("COLLECTION", "RPGs"))
        assertEquals(null, CollectionsOps.activeCollectionName("COLLECTION", "  "))
        assertEquals("Favorites", CollectionsOps.activeCollectionName("FAVORITES", null))
        assertEquals(null, CollectionsOps.activeCollectionName("ALL", "RPGs"))
        assertEquals(null, CollectionsOps.activeCollectionName("MOST_PLAYED", null))
    }

    @Test
    fun `moveMember reorders within collection`() {
        val c0 = mapOf("Shelf" to listOf("a", "b", "c", "d"))
        val c1 = CollectionsOps.moveMember(c0, "Shelf", "c", 0)
        assertEquals(listOf("c", "a", "b", "d"), c1["Shelf"])
        val c2 = CollectionsOps.moveMember(c1, "Shelf", "a", 99)
        assertEquals(listOf("c", "b", "d", "a"), c2["Shelf"])
    }

    @Test
    fun `moveMemberBy and moveMemberToEdge`() {
        val c0 = mapOf("Shelf" to listOf("a", "b", "c"))
        assertEquals(
            listOf("b", "a", "c"),
            CollectionsOps.moveMemberBy(c0, "Shelf", "b", -1)["Shelf"],
        )
        assertEquals(
            listOf("a", "c", "b"),
            CollectionsOps.moveMemberBy(c0, "Shelf", "b", 1)["Shelf"],
        )
        assertEquals(
            listOf("c", "a", "b"),
            CollectionsOps.moveMemberToEdge(c0, "Shelf", "c", toFront = true)["Shelf"],
        )
        assertEquals(
            listOf("b", "c", "a"),
            CollectionsOps.moveMemberToEdge(c0, "Shelf", "a", toFront = false)["Shelf"],
        )
        // Unknown key / blank = identity
        assertEquals(c0, CollectionsOps.moveMemberBy(c0, "Shelf", "z", -1))
        assertEquals(c0, CollectionsOps.moveMemberToEdge(c0, "Shelf", "z", true))
    }

    @Test
    fun `canReorderCollection only for COLLECTION with name`() {
        assertTrue(CollectionsOps.canReorderCollection("COLLECTION", "RPGs"))
        assertFalse(CollectionsOps.canReorderCollection("COLLECTION", "  "))
        assertFalse(CollectionsOps.canReorderCollection("FAVORITES", "Favorites"))
        assertFalse(CollectionsOps.canReorderCollection("ALL", "RPGs"))
    }
}
