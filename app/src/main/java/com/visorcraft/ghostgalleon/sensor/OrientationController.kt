package com.visorcraft.ghostgalleon.sensor

import android.app.Activity
import android.content.pm.ActivityInfo
import com.visorcraft.ghostgalleon.GhostGalleonApp
import com.visorcraft.ghostgalleon.display.ActivityOrientation
import com.visorcraft.ghostgalleon.display.DeviceProfileCatalog
import com.visorcraft.ghostgalleon.display.OrientationPolicy
import com.visorcraft.ghostgalleon.display.SurfaceMode
import com.visorcraft.ghostgalleon.settings.Settings

class OrientationController(
    private val activity: Activity,
    private val settings: () -> Settings,
) {

    fun start() {
        val s = settings()
        val app = activity.application as? GhostGalleonApp
        val topo = app?.displayConfig
        val dual = topo?.mode == SurfaceMode.DUAL
        val profile = DeviceProfileCatalog.byId(s.deviceProfileId)
        val policy = OrientationPolicy.resolve(
            orientationMode = s.orientationMode,
            profileOrientation = profile.orientation,
            dual = dual,
            angleLock = s.angleLock,
            gyroEnabled = s.gyroEnabled,
        )
        val target = when (policy) {
            ActivityOrientation.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            ActivityOrientation.SENSOR_LANDSCAPE ->
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
        if (activity.requestedOrientation != target) {
            activity.requestedOrientation = target
        }
    }

    fun stop() = Unit
}
