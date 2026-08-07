package com.visorcraft.ghostgalleon.display

import org.junit.Assert.assertEquals
import org.junit.Test

class OrientationPolicyTest {

    @Test
    fun `lock landscape from settings`() {
        assertEquals(
            ActivityOrientation.LANDSCAPE,
            OrientationPolicy.resolve(
                orientationMode = "lock_landscape",
                profileOrientation = OrientationPref.SENSOR_LANDSCAPE,
            ),
        )
    }

    @Test
    fun `angleLock legacy wins`() {
        assertEquals(
            ActivityOrientation.LANDSCAPE,
            OrientationPolicy.resolve(
                orientationMode = "sensor_landscape",
                profileOrientation = OrientationPref.SENSOR_LANDSCAPE,
                angleLock = true,
            ),
        )
    }

    @Test
    fun `profile sensor when auto`() {
        assertEquals(
            ActivityOrientation.SENSOR_LANDSCAPE,
            OrientationPolicy.resolve(
                orientationMode = "auto",
                profileOrientation = OrientationPref.SENSOR_LANDSCAPE,
            ),
        )
    }

    @Test
    fun `gyro disabled locks`() {
        assertEquals(
            ActivityOrientation.LANDSCAPE,
            OrientationPolicy.resolve(
                orientationMode = "auto",
                profileOrientation = OrientationPref.AUTO,
                gyroEnabled = false,
            ),
        )
    }
}
