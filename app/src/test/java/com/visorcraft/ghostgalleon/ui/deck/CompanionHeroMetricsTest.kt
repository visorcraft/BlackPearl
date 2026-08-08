package com.visorcraft.ghostgalleon.ui.deck

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompanionHeroMetricsTest {

    @Test
    fun `sugar bottom short panel shrinks art so title fits`() {
        // ~540dp usable height on Sugar secondary after bars.
        val s = CompanionHeroMetrics.forPanel(panelHeightDp = 540f)
        assertTrue("art must leave room for title, got ${s.artSizeDp}", s.artSizeDp <= 180)
        assertTrue(s.nameSp <= 24f)
        assertFalse(s.showExtraMedia)
        assertTrue(s.nameMaxLines >= 2)
    }

    @Test
    fun `very short panel clamps art under half height`() {
        val s = CompanionHeroMetrics.forPanel(panelHeightDp = 400f)
        assertTrue(s.artSizeDp <= (400 * 0.42f).toInt())
        assertTrue(s.artSizeDp >= 100)
        assertTrue(s.bannerHeightFraction < 0.35f)
    }

    @Test
    fun `tall panel keeps full 240 art`() {
        val s = CompanionHeroMetrics.forPanel(panelHeightDp = 900f)
        assertEquals(240, s.artSizeDp)
        assertEquals(32f, s.nameSp, 0.01f)
        assertTrue(s.showExtraMedia)
        assertTrue(s.showQuickChips)
    }

    @Test
    fun `banner height scales with fraction`() {
        val s = CompanionHeroMetrics.forPanel(540f)
        val px = CompanionHeroMetrics.bannerHeightPx(1080, s)
        assertTrue(px in 1 until 1080)
        assertTrue(px < 1080 * 2 / 5) // smaller than old fixed 2/5 on compact
    }
}
