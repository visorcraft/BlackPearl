package com.visorcraft.ghostgalleon.ui.deck

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StatusPillTest {

    @Test
    fun `formatBatteryLabel accepts 0 through 100`() {
        assertEquals("0%", StatusPill.formatBatteryLabel(0))
        assertEquals("42%", StatusPill.formatBatteryLabel(42))
        assertEquals("100%", StatusPill.formatBatteryLabel(100))
    }

    @Test
    fun `formatBatteryLabel rejects out of range`() {
        assertNull(StatusPill.formatBatteryLabel(-1))
        assertNull(StatusPill.formatBatteryLabel(101))
        assertNull(StatusPill.formatBatteryLabel(999))
    }

    @Test
    fun `formatBatteryLabel marks charging`() {
        assertEquals("88%⚡", StatusPill.formatBatteryLabel(88, charging = true))
        assertEquals("88%", StatusPill.formatBatteryLabel(88, charging = false))
    }
}
