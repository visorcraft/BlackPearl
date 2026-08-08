package com.visorcraft.ghostgalleon.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DualPaintPolicyTest {

    @Test
    fun `re-entrancy blocks full render`() {
        assertFalse(
            DualPaintPolicy.allowFullRender(
                rendering = true,
                hasPainted = false,
                nowUptimeMs = 1000,
                lastFullRenderUptimeMs = 0,
            ),
        )
    }

    @Test
    fun `first paint always allowed when not rendering`() {
        assertTrue(
            DualPaintPolicy.allowFullRender(
                rendering = false,
                hasPainted = false,
                nowUptimeMs = 1000,
                lastFullRenderUptimeMs = 999,
            ),
        )
    }

    @Test
    fun `coalesce denies paint inside gap`() {
        assertFalse(
            DualPaintPolicy.allowFullRender(
                rendering = false,
                hasPainted = true,
                nowUptimeMs = 1000,
                lastFullRenderUptimeMs = 990,
                minGapMs = 32,
            ),
        )
        assertTrue(
            DualPaintPolicy.allowFullRender(
                rendering = false,
                hasPainted = true,
                nowUptimeMs = 1032,
                lastFullRenderUptimeMs = 1000,
                minGapMs = 32,
            ),
        )
    }

    @Test
    fun `deferred delay is null when paint allowed`() {
        assertEquals(
            null,
            DualPaintPolicy.deferredFullRenderDelayMs(
                rendering = false,
                hasPainted = true,
                nowUptimeMs = 1100,
                lastFullRenderUptimeMs = 1000,
                minGapMs = 32,
            ),
        )
    }

    @Test
    fun `deferred delay posts after nest and remaining coalesce gap`() {
        assertEquals(
            0L,
            DualPaintPolicy.deferredFullRenderDelayMs(
                rendering = true,
                hasPainted = true,
                nowUptimeMs = 1000,
                lastFullRenderUptimeMs = 900,
                minGapMs = 32,
            ),
        )
        assertEquals(
            22L,
            DualPaintPolicy.deferredFullRenderDelayMs(
                rendering = false,
                hasPainted = true,
                nowUptimeMs = 1010,
                lastFullRenderUptimeMs = 1000,
                minGapMs = 32,
            ),
        )
    }

    @Test
    fun `needsPaintForDisplay on epoch or display change`() {
        assertTrue(
            DualPaintPolicy.needsPaintForDisplay(
                hasPainted = false,
                paintedDisplayId = null,
                currentDisplayId = 1,
                appliedEpoch = -1,
                contentEpoch = 0,
            ),
        )
        assertTrue(
            DualPaintPolicy.needsPaintForDisplay(
                hasPainted = true,
                paintedDisplayId = 0,
                currentDisplayId = 1,
                appliedEpoch = 3,
                contentEpoch = 3,
            ),
        )
        assertTrue(
            DualPaintPolicy.needsPaintForDisplay(
                hasPainted = true,
                paintedDisplayId = 1,
                currentDisplayId = 1,
                appliedEpoch = 2,
                contentEpoch = 3,
            ),
        )
        assertFalse(
            DualPaintPolicy.needsPaintForDisplay(
                hasPainted = true,
                paintedDisplayId = 1,
                currentDisplayId = 1,
                appliedEpoch = 3,
                contentEpoch = 3,
            ),
        )
    }

    @Test
    fun `absorb never opens drawer`() {
        assertFalse(DualPaintPolicy.absorbMayOpenDrawer())
    }

    @Test
    fun `drawer open-only never closes during storm`() {
        assertEquals(
            DualPaintPolicy.DrawerAction.NONE,
            DualPaintPolicy.drawerAction(
                withinDebounce = false,
                drawerAlreadyOpen = true,
                allowToggle = false,
            ),
        )
        assertEquals(
            DualPaintPolicy.DrawerAction.CLOSE,
            DualPaintPolicy.drawerAction(
                withinDebounce = false,
                drawerAlreadyOpen = true,
                allowToggle = true,
            ),
        )
        assertEquals(
            DualPaintPolicy.DrawerAction.OPEN,
            DualPaintPolicy.drawerAction(
                withinDebounce = false,
                drawerAlreadyOpen = false,
                allowToggle = false,
            ),
        )
        assertEquals(
            DualPaintPolicy.DrawerAction.NONE,
            DualPaintPolicy.drawerAction(
                withinDebounce = true,
                drawerAlreadyOpen = false,
                allowToggle = true,
            ),
        )
    }

    @Test
    fun `heal debounced and launch only when no peer on target`() {
        assertFalse(DualPaintPolicy.allowHeal(1000, 500, minGapMs = 2000))
        assertTrue(DualPaintPolicy.allowHeal(3000, 500, minGapMs = 2000))
        assertFalse(DualPaintPolicy.shouldLaunchCompanion(anyPeerOnTarget = true))
        assertTrue(DualPaintPolicy.shouldLaunchCompanion(anyPeerOnTarget = false))
    }
}
