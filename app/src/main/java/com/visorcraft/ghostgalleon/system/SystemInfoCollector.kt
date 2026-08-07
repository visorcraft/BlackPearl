package com.visorcraft.ghostgalleon.system

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import java.io.File
import java.util.Locale

/**
 * Android seams → [SystemReadings]. Thin wrapper; formatting stays pure in
 * [SystemInfoFormat].
 */
object SystemInfoCollector {

    fun collect(context: Context): SystemReadings {
        val am = context.getSystemService(ActivityManager::class.java)
        val mem = ActivityManager.MemoryInfo()
        am?.getMemoryInfo(mem)
        val data = StatFs(Environment.getDataDirectory().absolutePath)
        val internalTotal = data.totalBytes
        val internalFree = data.availableBytes

        val secondary = findSecondaryVolume()
        val battery = readBattery(context)

        return SystemReadings(
            manufacturer = Build.MANUFACTURER.orEmpty(),
            model = Build.MODEL.orEmpty(),
            device = Build.DEVICE.orEmpty(),
            hardware = Build.HARDWARE.orEmpty(),
            androidRelease = Build.VERSION.RELEASE.orEmpty(),
            sdkInt = Build.VERSION.SDK_INT,
            cpuSummary = cpuSummary(),
            ramTotalBytes = mem.totalMem,
            ramAvailBytes = mem.availMem,
            internalTotalBytes = internalTotal,
            internalFreeBytes = internalFree,
            secondaryLabel = secondary?.first,
            secondaryTotalBytes = secondary?.second,
            secondaryFreeBytes = secondary?.third,
            batteryPercent = battery.percent,
            charging = battery.charging,
            powerSource = battery.source,
            powerMicroWatts = battery.powerUw,
        )
    }

    private data class BatterySnap(
        val percent: Int?,
        val charging: Boolean?,
        val source: String?,
        val powerUw: Long?,
    )

    private fun readBattery(context: Context): BatterySnap {
        val bm = context.getSystemService(BatteryManager::class.java)
        val pct = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            ?.takeIf { it in 0..100 }
        val intent = context.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
        )
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = when (status) {
            BatteryManager.BATTERY_STATUS_CHARGING,
            BatteryManager.BATTERY_STATUS_FULL,
            -> true
            BatteryManager.BATTERY_STATUS_DISCHARGING,
            BatteryManager.BATTERY_STATUS_NOT_CHARGING,
            -> false
            else -> null
        }
        val plugged = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val source = when {
            plugged and BatteryManager.BATTERY_PLUGGED_AC != 0 -> "AC"
            plugged and BatteryManager.BATTERY_PLUGGED_USB != 0 -> "USB"
            plugged and BatteryManager.BATTERY_PLUGGED_WIRELESS != 0 -> "WIRELESS"
            charging == false -> "BATTERY"
            else -> "UNKNOWN"
        }
        val voltageMv = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)
            ?.takeIf { it > 0 }
        val currentUa = bm?.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
            ?.takeIf { it != Long.MIN_VALUE && it != 0L }
        val powerUw = SystemInfoFormat.powerMicroWatts(currentUa, voltageMv)
        return BatterySnap(pct, charging, source, powerUw)
    }

    private fun cpuSummary(): String {
        val cores = Runtime.getRuntime().availableProcessors()
        val abi = Build.SUPPORTED_ABIS?.firstOrNull().orEmpty()
        val freq = readMaxCpuFreqMhz()
        val parts = mutableListOf<String>()
        parts += if (cores == 1) "1 core" else "$cores cores"
        if (abi.isNotBlank()) parts += abi
        if (freq != null) parts += String.format(Locale.US, "up to %d MHz", freq)
        return parts.joinToString(" · ")
    }

    private fun readMaxCpuFreqMhz(): Int? {
        // Best-effort: cpu0 scaling max (kHz). Unprivileged; may be missing.
        return runCatching {
            val f = File("/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq")
            if (!f.canRead()) return null
            val kHz = f.readText().trim().toLongOrNull() ?: return null
            (kHz / 1000L).toInt().takeIf { it > 0 }
        }.getOrNull()
    }

    /**
     * First removable/secondary storage volume under /storage that is not
     * emulated primary. Returns label, total bytes, free bytes.
     */
    private fun findSecondaryVolume(): Triple<String, Long, Long>? {
        val base = File("/storage")
        if (!base.isDirectory) return null
        val kids = base.listFiles() ?: return null
        for (f in kids) {
            val name = f.name ?: continue
            if (name == "emulated" || name == "self" || name.startsWith(".")) continue
            if (!f.isDirectory || !f.canRead()) continue
            val stat = runCatching { StatFs(f.absolutePath) }.getOrNull() ?: continue
            val total = runCatching { stat.totalBytes }.getOrNull() ?: continue
            if (total <= 0L) continue
            val free = runCatching { stat.availableBytes }.getOrNull() ?: 0L
            val label = if (name.matches(Regex("[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}"))) {
                "microSD ($name)"
            } else {
                "Storage ($name)"
            }
            return Triple(label, total, free)
        }
        return null
    }
}
