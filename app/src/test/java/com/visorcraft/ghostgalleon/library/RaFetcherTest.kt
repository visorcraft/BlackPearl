package com.visorcraft.ghostgalleon.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun `fetchProgress resolves game id via search then progress`() {
        val progress = RaFetcher.fetchProgress(
            username = "u",
            apiKey = "k",
            gameId = null,
            titleHint = "Super Demo",
            fetchUrl = { url ->
                when {
                    url.contains("API_GetGameList") -> """[{"ID":55,"Title":"Super Demo"}]"""
                    url.contains("g=55") ->
                        """{"ID":55,"Title":"Super Demo","NumAwardedToUser":1,"NumAchievements":5}"""
                    else -> null
                }
            },
        )
        assertEquals(55, progress.gameId)
        assertEquals(1, progress.numAwarded)
    }
}
