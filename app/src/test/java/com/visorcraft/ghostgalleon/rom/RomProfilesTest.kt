package com.visorcraft.ghostgalleon.rom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RomProfilesTest {

    @Test
    fun `preferredPlayerId profile beats platform default`() {
        val profiles = mapOf("snes:x.sfc" to "snes9x")
        assertEquals(
            "snes9x",
            RomProfiles.preferredPlayerId(
                romId = "snes:x.sfc",
                romProfiles = profiles,
                platformDefaultPlayerId = "retroarch-snes",
            ),
        )
    }

    @Test
    fun `preferredPlayerId empty profile falls through to platform default`() {
        val profiles = mapOf("snes:x.sfc" to "  ")
        assertEquals(
            "retroarch-snes",
            RomProfiles.preferredPlayerId(
                romId = "snes:x.sfc",
                romProfiles = profiles,
                platformDefaultPlayerId = "retroarch-snes",
            ),
        )
        assertEquals(
            "retroarch-snes",
            RomProfiles.preferredPlayerId(
                romId = "snes:missing.sfc",
                romProfiles = emptyMap(),
                platformDefaultPlayerId = "retroarch-snes",
            ),
        )
        assertNull(
            RomProfiles.preferredPlayerId(
                romId = "snes:x.sfc",
                romProfiles = emptyMap(),
                platformDefaultPlayerId = null,
            ),
        )
        assertNull(
            RomProfiles.preferredPlayerId(
                romId = "snes:x.sfc",
                romProfiles = emptyMap(),
                platformDefaultPlayerId = "  ",
            ),
        )
    }

    @Test
    fun `setProfile adds and clearProfile removes`() {
        val base = emptyMap<String, String>()
        val with = RomProfiles.setProfile(base, "snes:x.sfc", "snes9x")
        assertEquals(mapOf("snes:x.sfc" to "snes9x"), with)

        val updated = RomProfiles.setProfile(with, "snes:x.sfc", "bsnes")
        assertEquals(mapOf("snes:x.sfc" to "bsnes"), updated)

        val cleared = RomProfiles.clearProfile(updated, "snes:x.sfc")
        assertEquals(emptyMap<String, String>(), cleared)
    }

    @Test
    fun `setProfile blank playerId removes entry`() {
        val with = mapOf("snes:x.sfc" to "snes9x")
        assertEquals(emptyMap<String, String>(), RomProfiles.setProfile(with, "snes:x.sfc", null))
        assertEquals(emptyMap<String, String>(), RomProfiles.setProfile(with, "snes:x.sfc", "  "))
    }

    @Test
    fun `setProfile ignores blank romId`() {
        val base = mapOf("snes:x.sfc" to "snes9x")
        assertEquals(base, RomProfiles.setProfile(base, "  ", "other"))
        assertEquals(base, RomProfiles.setProfile(base, "", "other"))
    }
}
