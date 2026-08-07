package com.visorcraft.ghostgalleon.display

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class DeviceProfileTest {

    @Test
    fun `sugar manufacturer and model match`() {
        val r = DisplayReadings(
            displays = listOf(
                DisplayInfo(0, 2160, 1080, 320, true),
                DisplayInfo(1, 1240, 1080, 320, false),
            ),
            manufacturer = "ONEXSUGAR",
            model = "SUGAR 1",
            device = "pineapple",
        )
        val matched = DeviceProfileCatalog.matchProfile(r)
        assertEquals("onex-sugar", matched!!.id)
        assertEquals(
            DeviceProfileCatalog.ONEX_SUGAR,
            DeviceProfileCatalog.effective("auto", r),
        )
    }

    @Test
    fun `no match returns null then effective auto`() {
        val r = DisplayReadings(
            displays = listOf(DisplayInfo(0, 1080, 1920, 420, true)),
            manufacturer = "Google",
            model = "Pixel",
        )
        // single display: generic-dual needs min 2
        assertNull(DeviceProfileCatalog.matchProfile(r))
        assertEquals("auto", DeviceProfileCatalog.effective("auto", r).id)
    }

    @Test
    fun `user force single`() {
        val r = DisplayReadings(
            listOf(
                DisplayInfo(0, 100, 100, 160, true),
                DisplayInfo(1, 100, 100, 160, false),
            ),
        )
        assertEquals("single", DeviceProfileCatalog.effective("single", r).id)
    }

    @Test
    fun `priority sugar before generic dual`() {
        val r = DisplayReadings(
            listOf(
                DisplayInfo(0, 2160, 1080, 320, true),
                DisplayInfo(1, 1240, 1080, 320, false),
            ),
            manufacturer = "ONEXSUGAR",
            model = "SUGAR 1",
        )
        assertEquals("onex-sugar", DeviceProfileCatalog.matchProfile(r)!!.id)
    }

    @Test
    fun `generic dual matches unknown dual`() {
        val r = DisplayReadings(
            listOf(
                DisplayInfo(0, 1920, 1080, 320, true),
                DisplayInfo(2, 1280, 720, 320, false),
            ),
            manufacturer = "ACME",
            model = "Clamshell",
        )
        assertNotNull(DeviceProfileCatalog.matchProfile(r))
        assertEquals("generic-dual", DeviceProfileCatalog.matchProfile(r)!!.id)
    }
}
