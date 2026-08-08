package com.visorcraft.ghostgalleon.display

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DisplayTopologyTest {

    private fun dualReadings(
        defaultId: Int = 0,
        secondaryId: Int = 1,
    ) = DisplayReadings(
        displays = listOf(
            DisplayInfo(defaultId, 2160, 1080, 320, isDefault = true, name = "top"),
            DisplayInfo(secondaryId, 1240, 1080, 320, isDefault = false, name = "bottom"),
        ),
        manufacturer = "ONEXSUGAR",
        model = "SUGAR 1",
    )

    @Test
    fun `single display yields SINGLE launch equals primary`() {
        val r = DisplayReadings(
            listOf(DisplayInfo(0, 1080, 1920, 420, isDefault = true)),
        )
        val t = DisplayTopology.resolve(r, DeviceProfileCatalog.AUTO)
        assertEquals(SurfaceMode.SINGLE, t.mode)
        assertEquals(0, t.primaryDisplayId)
        assertNull(t.companionDisplayId)
        assertEquals(0, t.launchDisplayId)
    }

    @Test
    fun `prefer secondary makes secondary interactive`() {
        val t = DisplayTopology.resolve(
            dualReadings(),
            DeviceProfileCatalog.ONEX_SUGAR,
            interactiveDisplayMode = "auto",
        )
        assertEquals(SurfaceMode.DUAL, t.mode)
        assertEquals(1, t.primaryDisplayId)
        assertEquals(0, t.companionDisplayId)
        assertEquals(0, t.launchDisplayId)
        // Activity placement stays on the non-default panel even when hero
        // *content* lives on default (companionDisplayId=0).
        assertEquals(1, t.secondaryHomeDisplayId)
        // 2160×1080 > 1240×1080 → chrome icons host on default (top).
        assertEquals(0, t.largerDisplayId)
    }

    @Test
    fun `largerDisplayId picks pixel area winner`() {
        val displays = listOf(
            DisplayInfo(0, 2160, 1080, 320, isDefault = true),
            DisplayInfo(1, 1240, 1080, 320, isDefault = false),
        )
        assertEquals(0, DisplayTopology.largerDisplayId(displays))
        assertEquals(
            5,
            DisplayTopology.largerDisplayId(
                listOf(
                    DisplayInfo(1, 800, 600, 320, isDefault = true),
                    DisplayInfo(5, 1920, 1080, 320, isDefault = false),
                ),
            ),
        )
    }

    @Test
    fun `system chrome icons only on larger panel in dual`() {
        assertTrue(
            DisplayTopology.shouldShowSystemChromeIcons(
                SurfaceMode.SINGLE, thisDisplayId = 1, largerDisplayId = 0,
            ),
        )
        assertTrue(
            DisplayTopology.shouldShowSystemChromeIcons(
                SurfaceMode.DUAL, thisDisplayId = 0, largerDisplayId = 0,
            ),
        )
        assertFalse(
            DisplayTopology.shouldShowSystemChromeIcons(
                SurfaceMode.DUAL, thisDisplayId = 1, largerDisplayId = 0,
            ),
        )
    }

    @Test
    fun `prefer default interactive`() {
        val t = DisplayTopology.resolve(
            dualReadings(),
            DeviceProfileCatalog.AUTO,
            interactiveDisplayMode = "default",
        )
        assertEquals(0, t.primaryDisplayId)
        assertEquals(1, t.companionDisplayId)
        assertEquals(1, t.launchDisplayId)
        assertEquals(1, t.secondaryHomeDisplayId)
    }

    @Test
    fun `explicit id mode`() {
        val t = DisplayTopology.resolve(
            dualReadings(defaultId = 0, secondaryId = 5),
            DeviceProfileCatalog.AUTO,
            interactiveDisplayMode = "id:5",
        )
        assertEquals(5, t.primaryDisplayId)
        assertEquals(0, t.companionDisplayId)
    }

    @Test
    fun `invalid explicit id falls back to default`() {
        val t = DisplayTopology.resolve(
            dualReadings(),
            DeviceProfileCatalog.AUTO,
            interactiveDisplayMode = "id:99",
        )
        assertEquals(0, t.primaryDisplayId)
    }

    @Test
    fun `non zero one ids dual`() {
        val t = DisplayTopology.resolve(
            dualReadings(defaultId = 10, secondaryId = 20),
            DeviceProfileCatalog.ONEX_SUGAR,
        )
        assertEquals(20, t.primaryDisplayId)
        assertEquals(10, t.companionDisplayId)
        assertEquals(10, t.launchDisplayId)
    }

    @Test
    fun `swap exchanges roles and launch`() {
        val t = DisplayTopology.resolve(
            dualReadings(),
            DeviceProfileCatalog.ONEX_SUGAR,
        )
        val s = DisplayTopology.swap(t)
        assertEquals(t.companionDisplayId, s.primaryDisplayId)
        assertEquals(t.primaryDisplayId, s.companionDisplayId)
        assertEquals(t.primaryDisplayId, s.launchDisplayId)
        // Activity placement is sticky: SECONDARY_HOME stays on the panel.
        assertEquals(t.secondaryHomeDisplayId, s.secondaryHomeDisplayId)
    }

    @Test
    fun `swap on single is no-op`() {
        val r = DisplayReadings(listOf(DisplayInfo(0, 800, 600, 240, true)))
        val t = DisplayTopology.resolve(r, DeviceProfileCatalog.SINGLE)
        assertEquals(t, DisplayTopology.swap(t))
    }

    @Test
    fun `user pin overrides auto`() {
        val t = DisplayTopology.resolve(
            dualReadings(),
            DeviceProfileCatalog.ONEX_SUGAR,
            interactiveDisplayMode = "auto",
            userPinnedPrimaryId = 0,
        )
        assertEquals(0, t.primaryDisplayId)
        assertEquals(1, t.companionDisplayId)
        assertEquals(1, t.secondaryHomeDisplayId)
    }

    @Test
    fun `single has null secondary home`() {
        val t = DisplayTopology.resolve(
            DisplayReadings(listOf(DisplayInfo(0, 1080, 1920, 420, isDefault = true))),
            DeviceProfileCatalog.AUTO,
        )
        assertNull(t.secondaryHomeDisplayId)
    }

    @Test
    fun `force single profile ignores second display`() {
        val t = DisplayTopology.resolve(
            dualReadings(),
            DeviceProfileCatalog.SINGLE,
        )
        assertEquals(SurfaceMode.SINGLE, t.mode)
        assertNull(t.companionDisplayId)
    }

    @Test
    fun `empty displays synthetic single`() {
        val t = DisplayTopology.resolve(
            DisplayReadings(emptyList()),
            DeviceProfileCatalog.AUTO,
        )
        assertEquals(SurfaceMode.SINGLE, t.mode)
        assertEquals(0, t.primaryDisplayId)
        assertTrue(t.reason.contains("synthetic") || t.reason.contains("no usable"))
    }
}
