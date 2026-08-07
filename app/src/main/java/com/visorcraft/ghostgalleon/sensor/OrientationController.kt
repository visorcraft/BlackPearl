package com.visorcraft.ghostgalleon.sensor

import android.app.Activity
import android.content.pm.ActivityInfo
import com.visorcraft.ghostgalleon.settings.Settings

class OrientationController(
    private val activity: Activity,
    private val settings: () -> Settings,
) {

    fun start() {
        val s = settings()
        val target = when {
            s.angleLock -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            !s.gyroEnabled -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            else -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
        if (activity.requestedOrientation != target) {
            activity.requestedOrientation = target
        }
    }

    fun stop() = Unit
}
