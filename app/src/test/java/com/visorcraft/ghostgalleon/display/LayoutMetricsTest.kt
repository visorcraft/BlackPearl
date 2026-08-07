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
    fun `medium width`() {
        // 700dp
        val m = LayoutMetricsResolver.fromWindow(
            700, 400, 160, SurfaceMode.SINGLE, false,
        )
        assertEquals(WidthClass.MEDIUM, m.widthClass)
    }
}
