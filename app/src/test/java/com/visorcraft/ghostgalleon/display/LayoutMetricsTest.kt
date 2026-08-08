package com.visorcraft.ghostgalleon.display

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LayoutMetricsTest {

    @Test
    fun `compact width class and column clamps`() {
        // 400dp @ 160dpi = 400px
        val m = LayoutMetricsResolver.fromWindow(
            windowWidthPx = 400,
            windowHeightPx = 800,
            densityDpi = 160,
            topologyMode = SurfaceMode.SINGLE,
            isCompanionRole = false,
        )
        assertEquals(WidthClass.COMPACT, m.widthClass)
        assertTrue(m.suggestedGridColumns in 4..7)
        assertTrue(m.suggestedCardSizeDp in 140..280)
        assertTrue(m.suggestedDockSlotDp in 48..72)
        // 800dp height is MEDIUM → single-screen TOP_STRIP selection hero.
        assertEquals(CompanionHeroStyle.TOP_STRIP, m.companionHeroStyle)
    }

    @Test
    fun `single compact height has no top strip`() {
        // 400dp height → COMPACT → no strip (too short).
        val m = LayoutMetricsResolver.fromWindow(
            windowWidthPx = 400,
            windowHeightPx = 400,
            densityDpi = 160,
            topologyMode = SurfaceMode.SINGLE,
            isCompanionRole = false,
        )
        assertEquals(HeightClass.COMPACT, m.heightClass)
        assertEquals(CompanionHeroStyle.NONE, m.companionHeroStyle)
    }

    @Test
    fun `expanded dual companion hero`() {
        val m = LayoutMetricsResolver.fromWindow(
            windowWidthPx = 2160,
            windowHeightPx = 1080,
            densityDpi = 320,
            topologyMode = SurfaceMode.DUAL,
            isCompanionRole = true,
        )
        assertEquals(WidthClass.EXPANDED, m.widthClass) // 2160*160/320 = 1080dp
        assertEquals(CompanionHeroStyle.SECOND_DISPLAY, m.companionHeroStyle)
    }

    @Test
    fun `dual primary is not top strip`() {
        val m = LayoutMetricsResolver.fromWindow(
            1080, 1080, 320, SurfaceMode.DUAL, isCompanionRole = false,
        )
        assertEquals(CompanionHeroStyle.NONE, m.companionHeroStyle)
    }

    @Test
    fun `medium width`() {
        // 700dp
        val m = LayoutMetricsResolver.fromWindow(
            700, 400, 160, SurfaceMode.SINGLE, false,
        )
        assertEquals(WidthClass.MEDIUM, m.widthClass)
    }
}
