package com.visorcraft.ghostgalleon.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RaFetcherTest {

    @Test
    fun `progressUrl encodes credentials and game id`() {
        val url = RaFetcher.progressUrl("user name", "key+1", 1234)
        assertTrue(url.contains("g=1234"))
        assertTrue(url.contains("z="))
        assertTrue(url.contains("y="))
        assertTrue(url.startsWith("https://retroachievements.org/API/"))
    }

    @Test
    fun `gameListUrl uses console id i and f=1 achievements flag`() {
        val url = RaFetcher.gameListUrl("key", 3)
        assertTrue(url.contains("i=3"))
        assertTrue(url.contains("f=1"))
        assertFalse("title must not be stuffed into f=", url.contains("f=Super"))
        assertTrue(url.contains("API_GetGameList.php"))
    }

    @Test
    fun `consoleIdForPlatform maps common systems`() {
        assertEquals(3, RaFetcher.consoleIdForPlatform("snes"))
        assertEquals(5, RaFetcher.consoleIdForPlatform("gba"))
        assertNull(RaFetcher.consoleIdForPlatform("unknown-plat"))
    }

    @Test
    fun `parseGameIdMatchingTitle prefers exact then substring`() {
        val json = """
            [
              {"ID":1,"Title":"Super Demo World"},
              {"ID":2,"Title":"Demo"},
              {"ID":3,"Title":"Other"}
            ]
        """.trimIndent()
        assertEquals(2, RaFetcher.parseGameIdMatchingTitle(json, "Demo"))
        assertEquals(1, RaFetcher.parseGameIdMatchingTitle(json, "Super Demo"))
        assertNull(RaFetcher.parseGameIdMatchingTitle(json, "Missing"))
        assertNull(RaFetcher.parseGameIdMatchingTitle(null, "Demo"))
    }

    @Test
    fun `parseFirstGameId from array and object`() {
        assertEquals(42, RaFetcher.parseFirstGameId("""[{"ID":42,"Title":"T"}]"""))
        assertEquals(7, RaFetcher.parseFirstGameId("""{"id":7}"""))
        assertTrue(RaFetcher.parseFirstGameId(null) == null)
        assertTrue(RaFetcher.parseFirstGameId("[]") == null)
        assertTrue(RaFetcher.parseFirstGameId("not-json") == null)
    }

    @Test
    fun `fetchProgress uses inject seam and parseProgress`() {
        val body = """
            {"ID":99,"Title":"Demo","NumAwardedToUser":3,"NumAchievements":10}
        """.trimIndent()
        val progress = RaFetcher.fetchProgress(
            username = "u",
            apiKey = "k",
            gameId = 99,
            titleHint = null,
            fetchUrl = { body },
        )
        assertEquals(99, progress.gameId)
        assertEquals(3, progress.numAwarded)
        assertEquals(10, progress.numPossible)
        assertFalse(progress.isEmpty)
    }

    @Test
    fun `fetchProgress blank credentials returns empty`() {
        val p = RaFetcher.fetchProgress("", "k", 1, null) { error("no net") }
        assertTrue(p.isEmpty)
    }

    @Test
    fun `fetchProgress resolves via console game list then progress`() {
        val progress = RaFetcher.fetchProgress(
            username = "u",
            apiKey = "k",
            gameId = null,
            titleHint = "Super Demo",
            platformId = "snes",
            fetchUrl = { url ->
                when {
                    url.contains("API_GetGameList") && url.contains("i=3") ->
                        """[{"ID":55,"Title":"Super Demo"},{"ID":1,"Title":"Other"}]"""
                    url.contains("g=55") ->
                        """{"ID":55,"Title":"Super Demo","NumAwardedToUser":1,"NumAchievements":5}"""
                    else -> null
                }
            },
        )
        assertEquals(55, progress.gameId)
        assertEquals(1, progress.numAwarded)
    }

    @Test
    fun `fetchProgress without platform mapping cannot title-resolve`() {
        val p = RaFetcher.fetchProgress(
            username = "u",
            apiKey = "k",
            gameId = null,
            titleHint = "Anything",
            platformId = "not-a-platform",
            fetchUrl = { error("should not hit network") },
        )
        assertTrue(p.isEmpty)
    }
}
