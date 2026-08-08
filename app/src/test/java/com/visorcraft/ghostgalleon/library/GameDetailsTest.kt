package com.visorcraft.ghostgalleon.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameDetailsTest {

    @Test
    fun `collectionsContaining finds membership sorted`() {
        val cols = mapOf(
            "Zebra" to listOf("rom:a"),
            "Alpha" to listOf("pkg", "rom:a"),
            "Empty" to listOf("other"),
        )
        assertEquals(
            listOf("Alpha", "Zebra"),
            GameDetails.collectionsContaining(cols, "rom:a"),
        )
        assertEquals(listOf("Alpha"), GameDetails.collectionsContaining(cols, "pkg"))
        assertTrue(GameDetails.collectionsContaining(cols, "missing").isEmpty())
        assertTrue(GameDetails.collectionsContaining(cols, "  ").isEmpty())
    }

    @Test
    fun `body includes type playtime favorite and collections`() {
        val body = GameDetails.body(
            GameDetails.Input(
                title = "Celeste",
                key = "rom:celeste",
                kind = "ROM",
                platformId = "switch",
                genre = "Platform / Indie",
                developer = "Maddy Makes Games",
                year = "2018",
                rating = "9.5",
                lastLaunchedMs = 1_000L,
                playtimeMs = 90 * 60_000L, // 1h 30m
                favorite = true,
                collections = listOf("Indie", "Speedrun"),
                nowMs = 1_000L + 5 * 60_000L, // 5m ago
            ),
        )
        assertTrue(body.contains("Celeste"))
        assertTrue(body.contains("Type: ROM"))
        assertTrue(body.contains("Platform: switch"))
        assertTrue(body.contains("Year: 2018"))
        assertTrue(body.contains("Genre: Platform / Indie"))
        assertTrue(body.contains("Developer: Maddy Makes Games"))
        assertTrue(body.contains("Rating: 9.5"))
        assertTrue(body.contains("Last played: 5m ago"))
        assertTrue(body.contains("Playtime: 1h 30m"))
        assertTrue(body.contains("Favorite: Yes"))
        assertTrue(body.contains("Collections: Indie, Speedrun"))
        assertTrue(body.contains("Key: rom:celeste"))
    }

    @Test
    fun `body never-played and empty collections`() {
        val body = GameDetails.body(
            GameDetails.Input(
                title = "App",
                key = "com.example",
                kind = "App",
                lastLaunchedMs = null,
                playtimeMs = 0L,
                favorite = false,
                collections = emptyList(),
                nowMs = 99L,
            ),
        )
        assertTrue(body.contains("Last played: Never"))
        assertTrue(body.contains("Playtime: 0m"))
        assertTrue(body.contains("Favorite: No"))
        assertTrue(body.contains("Collections: —"))
        assertFalse(body.contains("Platform:"))
        assertFalse(body.contains("Genre:"))
    }
}
