package com.visorcraft.ghostgalleon.rom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocalMediaTest {

    @Test
    fun `lookupArt matches stem and suffixes`() {
        val docs = listOf(
            DocFile("smw-image.png", "content://art/smw-image.png", "images/smw-image.png"),
            DocFile("other.png", "content://art/other.png", "images/other.png"),
        )
        val idx = LocalMedia.indexImages(docs, rootIsPlatform = true)
        assertEquals("content://art/smw-image.png", LocalMedia.lookupArt(idx, "", "smw"))
    }

    @Test
    fun `screenshotUri prefers screenshot suffix over bare art`() {
        val docs = listOf(
            DocFile("zelda.png", "content://art/zelda.png", "images/zelda.png"),
            DocFile("zelda-screenshot.png", "content://shot/zelda.png", "screenshots/zelda-screenshot.png"),
        )
        val idx = LocalMedia.indexImages(docs, rootIsPlatform = true)
        val art = LocalMedia.lookupArt(idx, "", "zelda")
        assertEquals("content://art/zelda.png", art)
        assertEquals(
            "content://shot/zelda.png",
            LocalMedia.screenshotUri(idx, "", "zelda", art),
        )
    }

    @Test
    fun `screenshotUri null when only art match exists`() {
        val docs = listOf(
            DocFile("zelda.png", "content://art/zelda.png", "images/zelda.png"),
        )
        val idx = LocalMedia.indexImages(docs, rootIsPlatform = true)
        val art = LocalMedia.lookupArt(idx, "", "zelda")
        assertNull(LocalMedia.screenshotUri(idx, "", "zelda", art))
    }

    @Test
    fun `parseMediaPath accepts nested media screenshots`() {
        val nested = LocalMedia.parseMediaPath("media/screenshots/zelda.png", rootIsPlatform = true)
        assertEquals(Triple("", "screenshots", "zelda.png"), nested)
        val container = LocalMedia.parseMediaPath(
            "snes/media/screenshots/zelda.png",
            rootIsPlatform = false,
        )
        assertEquals(Triple("snes", "screenshots", "zelda.png"), container)
    }

    @Test
    fun `indexImages finds nested screenshot and logo`() {
        val docs = listOf(
            DocFile("zelda.png", "content://art/z.png", "images/zelda.png"),
            DocFile(
                "zelda-screenshot.png",
                "content://shot/z.png",
                "media/screenshots/zelda-screenshot.png",
            ),
            DocFile("zelda-logo.png", "content://logo/z.png", "media/logos/zelda-logo.png"),
        )
        val idx = LocalMedia.indexImages(docs, rootIsPlatform = true)
        val art = LocalMedia.lookupArt(idx, "", "zelda")
        assertEquals("content://art/z.png", art)
        assertEquals(
            "content://shot/z.png",
            LocalMedia.screenshotUri(idx, "", "zelda", art),
        )
        assertEquals("content://logo/z.png", LocalMedia.lookupLogo(idx, "", "zelda"))
    }

    @Test
    fun `indexVideos matches flat and nested media paths`() {
        val flat = listOf(
            DocFile("chrono.mp4", "content://vid/chrono.mp4", "videos/chrono.mp4"),
        )
        val idxFlat = LocalMedia.indexVideos(flat, rootIsPlatform = true)
        assertEquals("content://vid/chrono.mp4", LocalMedia.lookupVideo(idxFlat, "", "chrono"))

        val nested = listOf(
            DocFile(
                "zelda.webm",
                "content://vid/zelda.webm",
                "snes/media/videos/zelda.webm",
            ),
        )
        val idxNested = LocalMedia.indexVideos(nested, rootIsPlatform = false)
        assertEquals(
            "content://vid/zelda.webm",
            LocalMedia.lookupVideo(idxNested, "snes", "zelda"),
        )
        assertNull(LocalMedia.lookupVideo(idxNested, "snes", "missing"))
    }
}
