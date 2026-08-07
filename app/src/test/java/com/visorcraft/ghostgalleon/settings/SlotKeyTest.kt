package com.visorcraft.ghostgalleon.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SlotKeyTest {

    @Test
    fun `rom key round trips`() {
        val key = SlotKey.rom("snes:roms/snes/game.smc")
        assertTrue(SlotKey.isRom(key))
        assertEquals("snes:roms/snes/game.smc", SlotKey.romId(key))
    }

    @Test
    fun `app packages are not rom keys`() {
        assertFalse(SlotKey.isRom("com.brave.browser"))
        assertNull(SlotKey.romId("com.brave.browser"))
    }

    @Test
    fun `null and blank values are safe`() {
        assertFalse(SlotKey.isRom(null))
        assertNull(SlotKey.romId(null))
        assertNull(SlotKey.romId(""))
    }

    @Test
    fun `bare prefix without an id is not a valid rom key`() {
        assertNull(SlotKey.romId("rom:"))
    }

    @Test
    fun `prefix match is case sensitive and anchored`() {
        assertFalse(SlotKey.isRom("ROM:snes:x"))
        assertFalse(SlotKey.isRom("xrom:snes:x"))
    }

    @Test
    fun `platform id resolves from a rom key without the library`() {
        assertEquals("snes", SlotKey.platformIdOf("rom:snes:snes/game.smc"))
        assertEquals(
            "new-nintendo-3ds",
            SlotKey.platformIdOf(SlotKey.rom("new-nintendo-3ds:3ds/a.3ds")))
    }

    @Test
    fun `platform id is null for app keys, null, and malformed rom keys`() {
        assertNull(SlotKey.platformIdOf("com.brave.browser"))
        assertNull(SlotKey.platformIdOf(null))
        assertNull(SlotKey.platformIdOf(""))
        assertNull(SlotKey.platformIdOf("rom:"))
        // An id with no ':' segment has no platform prefix to recover.
        assertNull(SlotKey.platformIdOf("rom:noPlatformHere"))
    }

    @Test
    fun `folder key round trips`() {
        val key = SlotKey.folder("f1")
        assertTrue(SlotKey.isFolder(key))
        assertEquals("f1", SlotKey.folderId(key))
        assertFalse(SlotKey.isRom(key))
    }

    @Test
    fun `folder prefix alone is not a valid folder id`() {
        assertNull(SlotKey.folderId("folder:"))
        assertFalse(SlotKey.isFolder(null))
    }
}
