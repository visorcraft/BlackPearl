package com.visorcraft.ghostgalleon.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryStatsTest {

    @Test
    fun `mostPlayed ranks by playtime descending`() {
        val ranked = LibraryStats.mostPlayed(
            mapOf("a" to 1000L, "b" to 5000L, "c" to 0L, "d" to 3000L),
            limit = 10,
        )
        assertEquals(listOf("b", "d", "a"), ranked.map { it.key })
        assertEquals(5000L, ranked[0].score)
    }

    @Test
    fun `recentlyPlayed ranks by last launch`() {
        val ranked = LibraryStats.recentlyPlayed(
            mapOf("x" to 10L, "y" to 30L, "z" to 20L),
            limit = 2,
        )
        assertEquals(listOf("y", "z"), ranked.map { it.key })
    }

    @Test
    fun `knownKeys filters rankings`() {
        val ranked = LibraryStats.mostPlayed(
            mapOf("keep" to 9L, "drop" to 99L),
            knownKeys = listOf("keep"),
        )
        assertEquals(listOf("keep"), ranked.map { it.key })
    }

    @Test
    fun `hasAnySessions empty and non-empty`() {
        assertFalse(LibraryStats.hasAnySessions(emptyMap(), emptyMap()))
        assertTrue(LibraryStats.hasAnySessions(mapOf("a" to 1L), emptyMap()))
        assertTrue(LibraryStats.hasAnySessions(emptyMap(), mapOf("a" to 1L)))
    }
}
