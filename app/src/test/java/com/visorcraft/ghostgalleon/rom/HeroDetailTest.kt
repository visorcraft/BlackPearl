package com.visorcraft.ghostgalleon.rom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HeroDetailTest {

    @Test
    fun `descriptionText trims and blanks to null`() {
        assertEquals("Hello", HeroDetail.descriptionText("  Hello  "))
        assertNull(HeroDetail.descriptionText("   "))
        assertNull(HeroDetail.descriptionText(null))
    }

    @Test
    fun `playerLine reports installed preferred player`() {
        val platform = Platforms.SNES
        val pref = platform.players.first().id
        val line = HeroDetail.playerLine(platform, pref) { true }
        assertTrue(line!!.startsWith("Player:"))
        assertTrue(line.contains(platform.players.first().displayName))
    }

    @Test
    fun `playerLine not installed when none match`() {
        val platform = Platforms.SNES
        val line = HeroDetail.playerLine(platform, platform.players.first().id) { false }
        assertTrue(line!!.contains("not installed"))
    }

    @Test
    fun `screenshotUri reads rom field`() {
        val rom = RomEntry(
            id = "snes:x.smc",
            name = "x",
            platformId = "snes",
            uri = "content://r",
            path = null,
            screenshotUri = "content://shot",
        )
        assertEquals("content://shot", HeroDetail.screenshotUri(rom))
        assertNull(HeroDetail.screenshotUri(rom.copy(screenshotUri = null)))
    }
}
