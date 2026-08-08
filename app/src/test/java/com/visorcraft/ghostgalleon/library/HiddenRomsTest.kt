package com.visorcraft.ghostgalleon.library

import com.visorcraft.ghostgalleon.rom.RomEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HiddenRomsTest {

    private fun rom(id: String, name: String = id, visible: Boolean = true) = RomEntry(
        id = id,
        name = name,
        platformId = "snes",
        uri = "content://x/$id",
        path = "/x/$id",
        visibleInUi = visible,
    )

    @Test
    fun `hide and unhide are pure set ops`() {
        val a = HiddenRoms.hide(emptySet(), "snes:a.sfc")
        assertEquals(setOf("snes:a.sfc"), a)
        assertEquals(a, HiddenRoms.hide(a, "snes:a.sfc"))
        assertEquals(emptySet<String>(), HiddenRoms.unhide(a, "snes:a.sfc"))
        assertEquals(emptySet<String>(), HiddenRoms.hide(emptySet(), "  "))
    }

    @Test
    fun `isListed requires visibleInUi and not hidden`() {
        val hidden = setOf("snes:h.sfc")
        assertTrue(HiddenRoms.isListed(rom("snes:a.sfc"), hidden))
        assertFalse(HiddenRoms.isListed(rom("snes:h.sfc"), hidden))
        assertFalse(HiddenRoms.isListed(rom("snes:a.sfc", visible = false), emptySet()))
    }

    @Test
    fun `listed filters both scanner and user hides`() {
        val roms = listOf(
            rom("snes:a.sfc", "Alpha"),
            rom("snes:h.sfc", "Hidden"),
            rom("snes:d.sfc", "Dedupe", visible = false),
        )
        val out = HiddenRoms.listed(roms, setOf("snes:h.sfc"))
        assertEquals(listOf("Alpha"), out.map { it.name })
    }
}
