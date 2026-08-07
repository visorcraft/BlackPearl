package com.visorcraft.blackpearl.rom

import org.junit.Assert.assertEquals
import org.junit.Test

class TreeLabelsTest {

    @Test
    fun `sd card tree shows last segment with SD card suffix`() {
        assertEquals(
            "roms (SD card)",
            TreeLabels.label(
                "content://com.android.externalstorage.documents/tree/7F7E-2949%3Aroms"),
        )
    }

    @Test
    fun `nested sd card tree shows deepest segment`() {
        assertEquals(
            "nds (SD card)",
            TreeLabels.label(
                "content://com.android.externalstorage.documents/tree/7F7E-2949%3Aroms%2Fnds"),
        )
    }

    @Test
    fun `primary volume tree has no suffix`() {
        assertEquals(
            "ROMs",
            TreeLabels.label(
                "content://com.android.externalstorage.documents/tree/primary%3AEmulation%2FROMs"),
        )
    }

    @Test
    fun `volume root tree shows the volume`() {
        assertEquals(
            "7F7E-2949 (SD card)",
            TreeLabels.label(
                "content://com.android.externalstorage.documents/tree/7F7E-2949%3A"),
        )
        assertEquals(
            "primary",
            TreeLabels.label(
                "content://com.android.externalstorage.documents/tree/primary%3A"),
        )
    }

    @Test
    fun `unparseable uri falls back to raw string`() {
        assertEquals("not a tree uri", TreeLabels.label("not a tree uri"))
    }
}
