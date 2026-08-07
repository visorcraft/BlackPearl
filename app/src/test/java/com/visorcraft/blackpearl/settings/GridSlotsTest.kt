package com.visorcraft.blackpearl.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class GridSlotsTest {

    @Test
    fun `blank produces the requested number of null slots`() {
        assertEquals(List<String?>(12) { null }, GridSlots.blank())
        assertEquals(3, GridSlots.blank(3).size)
    }

    @Test
    fun `fill places a package in the slot without touching others`() {
        val slots = listOf("a.b", null, "c.d")
        assertEquals(listOf("a.b", "x.y", "c.d"), GridSlots.fill(slots, 1, "x.y"))
    }

    @Test
    fun `fill replaces the existing content of the slot`() {
        val slots = listOf("a.b", "c.d")
        assertEquals(listOf("a.b", "x.y"), GridSlots.fill(slots, 1, "x.y"))
    }

    @Test
    fun `remove turns the slot blank`() {
        val slots = listOf("a.b", "c.d")
        assertEquals(listOf("a.b", null), GridSlots.remove(slots, 1))
    }

    @Test
    fun `moveSwap swaps the contents of two slots`() {
        val slots = listOf("a.b", null, "c.d")
        assertEquals(listOf(null, "a.b", "c.d"), GridSlots.moveSwap(slots, 0, 1))
        assertEquals(listOf("c.d", null, "a.b"), GridSlots.moveSwap(slots, 0, 2))
    }

    @Test
    fun `out of range indices leave the slots unchanged`() {
        val slots = listOf("a.b", null)
        assertEquals(slots, GridSlots.fill(slots, -1, "x.y"))
        assertEquals(slots, GridSlots.remove(slots, 5))
        assertEquals(slots, GridSlots.moveSwap(slots, 0, 2))
        assertEquals(slots, GridSlots.moveSwap(slots, -1, 1))
    }

    @Test
    fun `paddedCount rounds up to a whole number of rows`() {
        assertEquals(15, GridSlots.paddedCount(12, 5))
        assertEquals(15, GridSlots.paddedCount(11, 5))
        assertEquals(10, GridSlots.paddedCount(10, 5))
        assertEquals(15, GridSlots.paddedCount(15, 5))
        assertEquals(12, GridSlots.paddedCount(12, 4))
        assertEquals(0, GridSlots.paddedCount(0, 5))
    }

    @Test
    fun `paddedCount leaves degenerate inputs unchanged`() {
        assertEquals(12, GridSlots.paddedCount(12, 0))
        assertEquals(12, GridSlots.paddedCount(12, -1))
    }

    @Test
    fun `fill past the end extends with nulls up to the index`() {
        val slots = List<String?>(12) { "app.$it" }
        val filled = GridSlots.fill(slots, 14, "x.y")
        assertEquals(15, filled.size)
        assertEquals(null, filled[12])
        assertEquals(null, filled[13])
        assertEquals("x.y", filled[14])
        // The first padded slot extends by exactly one.
        assertEquals(13, GridSlots.fill(slots, 12, "x.y").size)
    }
}
