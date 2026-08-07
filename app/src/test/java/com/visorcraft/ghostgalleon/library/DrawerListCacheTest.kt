package com.visorcraft.ghostgalleon.library

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DrawerListCacheTest {

    @Test
    fun `matches when key fields identical`() {
        val a = DrawerListCache.key(1, 100, setOf("x"), listOf("a", "b"))
        val b = DrawerListCache.key(1, 100, setOf("x"), listOf("b", "a"))
        assertTrue(DrawerListCache.matches(a, b))
    }

    @Test
    fun `mismatches on contentEpoch or romCount`() {
        val base = DrawerListCache.key(1, 10, emptySet(), listOf("p"))
        assertFalse(
            DrawerListCache.matches(
                base,
                DrawerListCache.key(2, 10, emptySet(), listOf("p")),
            ),
        )
        assertFalse(
            DrawerListCache.matches(
                base,
                DrawerListCache.key(1, 11, emptySet(), listOf("p")),
            ),
        )
    }

    @Test
    fun `mismatches on hidden or apps set`() {
        val base = DrawerListCache.key(0, 0, setOf("hide.me"), listOf("com.app"))
        assertFalse(
            DrawerListCache.matches(
                base,
                DrawerListCache.key(0, 0, emptySet(), listOf("com.app")),
            ),
        )
        assertFalse(
            DrawerListCache.matches(
                base,
                DrawerListCache.key(0, 0, setOf("hide.me"), listOf("com.other")),
            ),
        )
    }

    @Test
    fun `null cached never matches`() {
        val cur = DrawerListCache.key(0, 0, emptySet(), emptyList())
        assertFalse(DrawerListCache.matches(null, cur))
    }
}
