package com.visorcraft.ghostgalleon.ui.deck

import org.junit.Assert.assertEquals
import org.junit.Test

class EnsureVisibleScrollTest {

    @Test
    fun `fully visible item does not scroll`() {
        assertEquals(0, EnsureVisibleScroll.delta(100, 200, 0, 500))
    }

    @Test
    fun `item touching viewport edges does not scroll`() {
        assertEquals(0, EnsureVisibleScroll.delta(0, 500, 0, 500))
    }

    @Test
    fun `item above viewport scrolls up just enough`() {
        // Item sits fully above the viewport: scroll up exactly 100 so its
        // top aligns with the viewport top.
        assertEquals(-100, EnsureVisibleScroll.delta(-100, -40, 0, 500))
    }

    @Test
    fun `item partially above viewport scrolls up just enough`() {
        // Top 20px clipped: scroll up exactly 20 so the top aligns.
        assertEquals(-20, EnsureVisibleScroll.delta(-20, 180, 0, 500))
    }

    @Test
    fun `item below viewport scrolls down just enough`() {
        // Item starts 60px below the viewport bottom: scroll down exactly 160
        // so its bottom (660) aligns with the viewport bottom (500).
        assertEquals(160, EnsureVisibleScroll.delta(560, 660, 0, 500))
    }

    @Test
    fun `item partially below viewport scrolls down just enough`() {
        // Bottom 30px clipped: scroll down exactly 30.
        assertEquals(30, EnsureVisibleScroll.delta(400, 530, 0, 500))
    }

    @Test
    fun `item taller than viewport aligns its top`() {
        // Cannot fit fully; prefer showing from the top.
        assertEquals(-50, EnsureVisibleScroll.delta(-50, 700, 0, 500))
    }

    @Test
    fun `scrolled viewport keeps visible item put`() {
        // Viewport scrolled 1000px down; item fully inside it: no jump.
        assertEquals(0, EnsureVisibleScroll.delta(1100, 1200, 1000, 1500))
    }
}
