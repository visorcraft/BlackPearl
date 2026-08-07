package com.visorcraft.ghostgalleon.system

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemInfoFormatTest {

    @Test
    fun `formatBytes scales`() {
        assertEquals("512 B", SystemInfoFormat.formatBytes(512))
        assertEquals("1.0 KB", SystemInfoFormat.formatBytes(1024))
        assertEquals("1.5 MB", SystemInfoFormat.formatBytes((1.5 * 1024 * 1024).toLong()))
    }

    @Test
    fun `formatWatts converts microwatts or unavailable`() {
        assertEquals("Unavailable", SystemInfoFormat.formatWatts(null))
        assertEquals("Unavailable", SystemInfoFormat.formatWatts(-1L))
        assertEquals("12.50 W", SystemInfoFormat.formatWatts(12_500_000L))
    }

    @Test
    fun `powerMicroWatts from current and voltage`() {
        // 1_000_000 µA * 5000 mV / 1000 = 5_000_000 µW = 5 W
        assertEquals(5_000_000L, SystemInfoFormat.powerMicroWatts(1_000_000L, 5000))
        // Negative discharge current still yields positive power.
        assertEquals(5_000_000L, SystemInfoFormat.powerMicroWatts(-1_000_000L, 5000))
        assertEquals(null, SystemInfoFormat.powerMicroWatts(null, 5000))
        assertEquals(null, SystemInfoFormat.powerMicroWatts(1000L, null))
    }

    @Test
    fun `rows include hardware ram storage battery power`() {
        val r = SystemReadings(
            manufacturer = "OneXPlayer",
            model = "One X Sugar",
            device = "onexsugar",
            hardware = "qcom",
            androidRelease = "14",
            sdkInt = 34,
            cpuSummary = "8 cores · arm64-v8a",
            ramTotalBytes = 8L * 1024 * 1024 * 1024,
            ramAvailBytes = 3L * 1024 * 1024 * 1024,
            internalTotalBytes = 128L * 1024 * 1024 * 1024,
            internalFreeBytes = 40L * 1024 * 1024 * 1024,
            secondaryLabel = "microSD (7F7E-2949)",
            secondaryTotalBytes = 512L * 1024 * 1024 * 1024,
            secondaryFreeBytes = 200L * 1024 * 1024 * 1024,
            batteryPercent = 77,
            charging = true,
            powerSource = "AC",
            powerMicroWatts = 8_000_000L,
        )
        val rows = SystemInfoFormat.rows(r)
        val map = rows.toMap()
        assertEquals("OneXPlayer One X Sugar", map["Hardware"])
        assertTrue(map["RAM"]!!.contains("GB"))
        assertTrue(map["Internal storage"]!!.contains("free"))
        assertTrue(map["microSD (7F7E-2949)"]!!.contains("free"))
        assertTrue(map["Battery"]!!.startsWith("77%"))
        assertEquals("8.00 W", map["Power draw"])
        assertTrue(map["CPU"]!!.contains("8 cores"))
    }

    @Test
    fun `rows mark missing microSD and power`() {
        val r = SystemReadings(
            manufacturer = "X",
            model = "Y",
            batteryPercent = 50,
            charging = false,
            powerSource = "BATTERY",
            powerMicroWatts = null,
        )
        val map = SystemInfoFormat.rows(r).toMap()
        assertEquals("Not present", map["microSD"])
        assertEquals("Unavailable", map["Power draw"])
    }
}
