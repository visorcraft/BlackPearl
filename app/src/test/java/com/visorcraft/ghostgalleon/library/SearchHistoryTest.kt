package com.visorcraft.ghostgalleon.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchHistoryTest {

    @Test
    fun `normalize trims and rejects blank`() {
        assertEquals("zelda", SearchHistory.normalize("  zelda  "))
        assertNull(SearchHistory.normalize(""))
        assertNull(SearchHistory.normalize("   "))
    }

    @Test
    fun `push newest first and case insensitive dedupe`() {
        val a = SearchHistory.push(emptyList(), "Zelda")
        assertEquals(listOf("Zelda"), a)
        val b = SearchHistory.push(a, "mario")
        assertEquals(listOf("mario", "Zelda"), b)
        // Re-pushing Zelda with new casing moves it front and drops old
        val c = SearchHistory.push(b, "zelda")
        assertEquals(listOf("zelda", "mario"), c)
        assertEquals(b, SearchHistory.push(b, "  "))
    }

    @Test
    fun `push respects limit`() {
        var h = emptyList<String>()
        for (i in 1..5) {
            h = SearchHistory.push(h, "q$i", limit = 3)
        }
        assertEquals(listOf("q5", "q4", "q3"), h)
        assertTrue(SearchHistory.push(listOf("a"), "b", limit = 0).isEmpty())
    }

    @Test
    fun `remove drops first match only`() {
        val h = listOf("a", "B", "c")
        assertEquals(listOf("a", "c"), SearchHistory.remove(h, "b"))
        assertEquals(h, SearchHistory.remove(h, "missing"))
        assertEquals(emptyList<String>(), SearchHistory.clear())
    }
}
