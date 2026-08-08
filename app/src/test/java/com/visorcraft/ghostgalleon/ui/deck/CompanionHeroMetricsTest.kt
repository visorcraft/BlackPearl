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

    @Test
    fun `brand ship size scales with panel height not game-hero art`() {
        val density = 2f
        val tall = CompanionHeroMetrics.brandShipLayout(panelHeightPx = 1080, density = density)
        val short = CompanionHeroMetrics.brandShipLayout(panelHeightPx = 540, density = density)
        // Taller panel → larger ship (until max clamp).
        assertTrue(tall.sizePx > short.sizePx)
        // Independent of forPanel compact art (game selection tiles).
        val compactArt = CompanionHeroMetrics.forPanel(540f).artSizeDp * density
        assertTrue(
            "brand ship must not use compact game art size",
            tall.sizePx != compactArt.toInt() || short.sizePx > compactArt,
        )
        // Min/max clamps at 2x density: 120dp–280dp → 240–560px.
        assertTrue(tall.sizePx in 240..560)
        assertTrue(short.sizePx in 240..560)
    }

    @Test
    fun `brand ship hull sits on waterline of 3-2 scene`() {
        val panelH = 1080
        val layout = CompanionHeroMetrics.brandShipLayout(panelH, density = 2f)
        val hullY = layout.topMarginPx +
            layout.sizePx * CompanionHeroMetrics.BRAND_SHIP_HULL_FRACTION
        val waterline = panelH * CompanionHeroMetrics.BRAND_WATERLINE_FRACTION
        // Integer px layout may be ±1 from pure float waterline.
        assertTrue(
            "hullY=$hullY waterline=$waterline",
            kotlin.math.abs(hullY - waterline) < 1.5f,
        )
        // Not floating in the upper sky (regression: chrome-column topMargin).
        assertTrue("ship top too high: ${layout.topMarginPx}", layout.topMarginPx > panelH / 4)
    }

    @Test
    fun `brand ship layout empty for zero height`() {
        val z = CompanionHeroMetrics.brandShipLayout(0, density = 2f)
        assertEquals(0, z.sizePx)
        assertEquals(0, z.topMarginPx)
        assertEquals(0, CompanionHeroMetrics.brandShipTopMarginPx(0, 480))
        assertEquals(0, CompanionHeroMetrics.brandShipTopMarginPx(1080, 0))
    }
}
