package com.visorcraft.ghostgalleon.display

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.util.DisplayMetrics
import android.view.Display

/**
 * Thin Android adapter: [DisplayManager] + [Build] → pure [DisplayReadings].
 */
object AndroidDisplayProbe {

    fun read(context: Context): DisplayReadings {
        val dm = context.getSystemService(DisplayManager::class.java)
        val list = dm.displays.map { d ->
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            d.getRealMetrics(metrics)
            DisplayInfo(
                id = d.displayId,
                widthPx = metrics.widthPixels,
                heightPx = metrics.heightPixels,
                densityDpi = metrics.densityDpi,
                isDefault = d.displayId == Display.DEFAULT_DISPLAY,
                isPrivate = (d.flags and Display.FLAG_PRIVATE) != 0,
                name = d.name ?: "",
            )
        }
        return DisplayReadings(
            displays = list,
            manufacturer = Build.MANUFACTURER ?: "",
            model = Build.MODEL ?: "",
            device = Build.DEVICE ?: "",
            timestampMs = System.currentTimeMillis(),
        )
    }

    fun hasDisplay(context: Context, id: Int): Boolean {
        val dm = context.getSystemService(DisplayManager::class.java)
        return dm.displays.any { it.displayId == id }
    }
}
