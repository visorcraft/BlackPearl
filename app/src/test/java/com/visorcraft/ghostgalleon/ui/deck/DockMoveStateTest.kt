package com.visorcraft.ghostgalleon.ui.deck

import com.visorcraft.ghostgalleon.settings.DockSlots
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DockMoveStateTest {

    @Test
    fun `start then swap then drop compacts and focuses key`() {
        val state = DockMoveState()
        val slots = DockSlots.compact(listOf("a", "b", "c"))
        state.start(0, slots)
        assertTrue(state.active)
        assertEquals(0, state.index)

        assertEquals(1, state.swap(0, 1))
        assertEquals("b", state.working!![0])
        assertEquals("a", state.working!![1])

        val drop = state.drop()
        assertFalse(state.active)
        assertNull(state.working)
        requireNotNull(drop)
        assertEquals(listOf("b", "a", "c"), DockSlots.filled(drop.compacted))
        assertEquals(1, drop.focusIndex) // key "a" after swap sits at index 1
    }

    @Test
    fun `drop with tapSlot swaps then commits`() {
        val state = DockMoveState()
        val slots = DockSlots.compact(listOf("a", "b", "c"))
        state.start(0, slots)
        val drop = state.drop(tapSlot = 2)
        requireNotNull(drop)
        assertEquals(listOf("c", "b", "a"), DockSlots.filled(drop.compacted))
        assertEquals(2, drop.focusIndex)
    }

    @Test
    fun `clear cancels without drop`() {
        val state = DockMoveState()
        state.start(1, DockSlots.compact(listOf("x", "y")))
        state.clear()
        assertFalse(state.active)
        assertNull(state.drop())
    }

    @Test
    fun `swap inactive returns null`() {
        assertNull(DockMoveState().swap(0, 1))
    }
}
