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

    @Test
    fun `metadataLine joins year genre developer rating`() {
        val rom = RomEntry(
            id = "snes:x.smc",
            name = "x",
            platformId = "snes",
            uri = "content://r",
            path = null,
            year = "1995",
            genre = "RPG",
            developer = "Square",
            rating = "4.5",
        )
        assertEquals("1995 · RPG · Square · ★ 4.5", HeroDetail.metadataLine(rom))
        assertNull(HeroDetail.metadataLine(rom.copy(year = null, genre = null, developer = null, rating = null)))
    }

    @Test
    fun `videoUri reads rom field`() {
        val rom = RomEntry(
            id = "snes:x.smc",
            name = "x",
            platformId = "snes",
            uri = "content://r",
            path = null,
            videoUri = "content://vid/x.mp4",
        )
        assertEquals("content://vid/x.mp4", HeroDetail.videoUri(rom))
        assertNull(HeroDetail.videoUri(rom.copy(videoUri = "  ")))
    }

    @Test
    fun `compactSubline joins platform play and player`() {
        assertEquals(
            "Nintendo DS · Never played · melonDualDS",
            HeroDetail.compactSubline(
                "Nintendo DS",
                "Never played",
                "Player: melonDualDS",
            ),
        )
        assertEquals(
            "SNES · melonDS",
            HeroDetail.compactSubline("SNES", null, "melonDS"),
        )
        assertEquals("", HeroDetail.compactSubline(null, "  ", null))
    }

    @Test
    fun `playerShortName strips Player prefix`() {
        val platform = Platforms.SNES
        val pref = platform.players.first().id
        val short = HeroDetail.playerShortName(platform, pref) { true }
        assertEquals(platform.players.first().displayName, short)
    }
}
