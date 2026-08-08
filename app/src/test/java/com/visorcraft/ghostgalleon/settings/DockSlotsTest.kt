package com.visorcraft.ghostgalleon.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DockSlotsTest {

    @Test
    fun `blank returns CAPACITY null slots`() {
        assertEquals(List<String?>(DockSlots.CAPACITY) { null }, DockSlots.blank())
    }

    @Test
    fun `visibleCount is MIN_VISIBLE on an empty dock`() {
        assertEquals(4, DockSlots.visibleCount(DockSlots.blank()))
    }

    @Test
    fun `visibleCount is one placeholder past the filled count`() {
        assertEquals(4, DockSlots.visibleCount(DockSlots.compact(listOf("a", "b", "c"))))
        assertEquals(5, DockSlots.visibleCount(DockSlots.compact(listOf("a", "b", "c", "d"))))
        assertEquals(6, DockSlots.visibleCount(
            DockSlots.compact(listOf("a", "b", "c", "d", "e"))))
        assertEquals(9, DockSlots.visibleCount(
            DockSlots.compact(listOf("a", "b", "c", "d", "e", "f", "g", "h"))))
    }

    @Test
    fun `visibleCount caps at CAPACITY with no placeholder when full`() {
        val full = DockSlots.compact(listOf("a", "b", "c", "d", "e", "f", "g", "h", "i"))
        assertEquals(9, DockSlots.visibleCount(full))
    }

    @Test
    fun `compact moves filled keys first and pads to CAPACITY`() {
        assertEquals(
            listOf("a.b", "c.d", null, null, null, null, null, null, null),
            DockSlots.compact(listOf("a.b", null, "c.d", null, null)),
        )
    }

    @Test
    fun `compact drops overflow beyond CAPACITY`() {
        val over = (1..11).map { "a.$it" }
        assertEquals((1..9).map { "a.$it" } , DockSlots.compact(over).filterNotNull())
        assertEquals(DockSlots.CAPACITY, DockSlots.compact(over).size)
    }

    @Test
    fun `fill sets the slot and keeps canonical form`() {
        val slots = DockSlots.compact(listOf("a.b"))
        assertEquals(
            listOf("a.b", "c.d", null, null, null, null, null, null, null),
            DockSlots.fill(slots, 1, "c.d"),
        )
    }

    @Test
    fun `fill overwrites an occupied slot`() {
        val slots = DockSlots.compact(listOf("a.b"))
        assertEquals("c.d", DockSlots.fill(slots, 0, "c.d")[0])
        assertEquals(1, DockSlots.filled(DockSlots.fill(slots, 0, "c.d")).size)
    }

    @Test
    fun `fill out of range leaves the list unchanged`() {
        val slots = DockSlots.blank()
        assertEquals(slots, DockSlots.fill(slots, -1, "a.b"))
        assertEquals(slots, DockSlots.fill(slots, DockSlots.CAPACITY, "a.b"))
    }

    @Test
    fun `remove compacts the remaining keys left`() {
        val slots = DockSlots.compact(listOf("a.b", "c.d", "e.f"))
        assertEquals(
            listOf("a.b", "e.f") + List<String?>(7) { null },
            DockSlots.remove(slots, 1),
        )
    }

    @Test
    fun `remove out of range leaves the list unchanged`() {
        val slots = DockSlots.compact(listOf("a.b"))
        assertEquals(slots, DockSlots.remove(slots, 9))
    }

    @Test
    fun `moveSwap swaps two slots including blanks`() {
        val slots = DockSlots.compact(listOf("a.b", "c.d"))
        assertEquals(
            listOf(null, "c.d", "a.b") + List<String?>(6) { null },
            DockSlots.moveSwap(slots, 0, 2),
        )
    }

    @Test
    fun `firstBlank is the filled count in canonical form, null when full`() {
        assertEquals(0, DockSlots.firstBlank(DockSlots.blank()))
        assertEquals(2, DockSlots.firstBlank(DockSlots.compact(listOf("a.b", "c.d"))))
        assertNull(DockSlots.firstBlank(
            DockSlots.compact((1..9).map { "a.$it" })))
    }

    @Test
    fun `pinKey appends to first blank`() {
        val r = DockSlots.pinKey(DockSlots.blank(), "com.example.game")
        assertEquals(DockSlots.PinStatus.PINNED, r.status)
        assertEquals(listOf("com.example.game"), DockSlots.filled(r.slots))
    }

    @Test
    fun `pinKey ALREADY when present or blank key`() {
        val base = DockSlots.compact(listOf("a.b"))
        assertEquals(DockSlots.PinStatus.ALREADY, DockSlots.pinKey(base, "a.b").status)
        assertEquals(DockSlots.PinStatus.ALREADY, DockSlots.pinKey(base, "  ").status)
    }

    @Test
    fun `pinKey FULL when capacity reached`() {
        val full = DockSlots.compact((1..9).map { "a.$it" })
        val r = DockSlots.pinKey(full, "a.extra")
        assertEquals(DockSlots.PinStatus.FULL, r.status)
        assertEquals(9, DockSlots.filled(r.slots).size)
    }

    @Test
    fun `pinKeys adds until full and counts new pins`() {
        val (slots, added) = DockSlots.pinKeys(
            DockSlots.compact(listOf("keep")),
            listOf("keep", "new1", "new2"),
        )
        assertEquals(2, added)
        assertEquals(listOf("keep", "new1", "new2"), DockSlots.filled(slots))
        val almostFull = DockSlots.compact((1..8).map { "a.$it" })
        val (s2, added2) = DockSlots.pinKeys(almostFull, listOf("x", "y", "z"))
        assertEquals(1, added2)
        assertEquals(9, DockSlots.filled(s2).size)
    }

    @Test
    fun `containsKey and unpinKey compact after remove`() {
        val base = DockSlots.compact(listOf("a", "b", "c"))
        assertTrue(DockSlots.containsKey(base, "b"))
        assertFalse(DockSlots.containsKey(base, "missing"))
        val unpinned = DockSlots.unpinKey(base, "b")
        assertEquals(listOf("a", "c"), DockSlots.filled(unpinned))
        assertEquals(base, DockSlots.unpinKey(base, "missing"))
    }

    @Test
    fun `unpinKeys removes members and counts`() {
        val base = DockSlots.compact(listOf("a", "b", "c", "d"))
        val (slots, n) = DockSlots.unpinKeys(base, listOf("b", "d", "zzz"))
        assertEquals(2, n)
        assertEquals(listOf("a", "c"), DockSlots.filled(slots))
    }
}
