package com.visorcraft.ghostgalleon.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupNeedsTest {

    private fun snap(
        dismissed: Boolean = false,
        trees: Int = 0,
        roms: Int = 0,
        players: Int = 0,
        sgdb: Boolean = false,
    ) = SetupNeeds.Snapshot(dismissed, trees, roms, players, sgdb)

    @Test
    fun `shouldShow when empty and not dismissed`() {
        assertTrue(SetupNeeds.shouldShow(snap()))
    }

    @Test
    fun `shouldShow false when dismissed or has trees or entries`() {
        assertFalse(SetupNeeds.shouldShow(snap(dismissed = true)))
        assertFalse(SetupNeeds.shouldShow(snap(trees = 1)))
        assertFalse(SetupNeeds.shouldShow(snap(roms = 5)))
    }

    @Test
    fun `checklist reflects progress`() {
        val list = SetupNeeds.checklist(snap(trees = 1, players = 2, sgdb = true))
        assertEquals(3, list.size)
        assertTrue(list[0].second) // ROM folder
        assertTrue(list[1].second) // emulator
        assertTrue(list[2].second) // sgdb
    }

    @Test
    fun `allRequiredDone only needs a tree`() {
        assertFalse(SetupNeeds.allRequiredDone(snap()))
        assertTrue(SetupNeeds.allRequiredDone(snap(trees = 1)))
    }
}
