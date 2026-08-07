package com.visorcraft.ghostgalleon.system

/**
 * Pure device system-info aggregation/formatting. Readings are injected
 * (Android seams in production); host tests drive canned values.
 */
data class SystemReadings(
    val manufacturer: String = "",
    val model: String = "",
    val device: String = "",
    val hardware: String = "",
    val androidRelease: String = "",
    val sdkInt: Int = 0,
    val cpuSummary: String = "",
    val ramTotalBytes: Long = 0L,
    val ramAvailBytes: Long = 0L,
    val internalTotalBytes: Long = 0L,
    val internalFreeBytes: Long = 0L,
    /** Secondary volume label (e.g. microSD), null when absent. */
    val secondaryLabel: String? = null,
    val secondaryTotalBytes: Long? = null,
    val secondaryFreeBytes: Long? = null,
    val batteryPercent: Int? = null,
    val charging: Boolean? = null,
    /** AC, USB, WIRELESS, BATTERY, or UNKNOWN. */
    val powerSource: String? = null,
    /** Instantaneous power in microwatts when the platform reports it. */
    val powerMicroWatts: Long? = null,
)

object SystemInfoFormat {

    fun formatBytes(bytes: Long): String {
        if (bytes < 0L) return "Unavailable"
        if (bytes < 1024L) return "$bytes B"
        val units = arrayOf("KB", "MB", "GB", "TB")
        var v = bytes.toDouble()
        var u = -1
        while (v >= 1024.0 && u < units.lastIndex) {
            v /= 1024.0
            u++
        }
        return if (u < 0) "$bytes B" else String.format("%.1f %s", v, units[u])
    }

    /** µW → watts label, or "Unavailable" when null/negative. */
    fun formatWatts(microWatts: Long?): String {
        if (microWatts == null || microWatts < 0L) return "Unavailable"
        val w = microWatts / 1_000_000.0
        return String.format("%.2f W", w)
    }

    fun formatRam(total: Long, avail: Long): String {
        if (total <= 0L) return "Unavailable"
        val used = (total - avail).coerceAtLeast(0L)
        return "${formatBytes(used)} used / ${formatBytes(total)}"
    }

    fun formatStorage(free: Long, total: Long): String {
        if (total <= 0L) return "Unavailable"
        return "${formatBytes(free)} free / ${formatBytes(total)}"
    }

    fun formatBattery(percent: Int?, charging: Boolean?, source: String?): String {
        if (percent == null || percent !in 0..100) return "Unavailable"
        val charge = when (charging) {
            true -> "charging"
            false -> "discharging"
            null -> null
        }
        val src = source?.takeIf { it.isNotBlank() && it != "UNKNOWN" }
        val tail = listOfNotNull(charge, src).joinToString(", ")
        return if (tail.isEmpty()) "$percent%" else "$percent% ($tail)"
    }

    fun hardwareLine(r: SystemReadings): String {
        val parts = listOf(r.manufacturer, r.model)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        return when {
            parts.isNotEmpty() -> parts.joinToString(" ")
            r.device.isNotBlank() -> r.device
            r.hardware.isNotBlank() -> r.hardware
            else -> "Unavailable"
        }
    }

    /**
     * Ordered label → value rows for Settings / System UI.
     */
    fun rows(r: SystemReadings): List<Pair<String, String>> {
        val out = mutableListOf<Pair<String, String>>()
        out += "Hardware" to hardwareLine(r)
        if (r.device.isNotBlank() && r.device != r.model) {
            out += "Device" to r.device
        }
        if (r.hardware.isNotBlank()) {
            out += "SoC / board" to r.hardware
        }
        out += "Android" to when {
            r.androidRelease.isNotBlank() && r.sdkInt > 0 ->
                "${r.androidRelease} (API ${r.sdkInt})"
            r.androidRelease.isNotBlank() -> r.androidRelease
            r.sdkInt > 0 -> "API ${r.sdkInt}"
            else -> "Unavailable"
        }
        out += "CPU" to r.cpuSummary.ifBlank { "Unavailable" }
        out += "RAM" to formatRam(r.ramTotalBytes, r.ramAvailBytes)
        out += "Internal storage" to formatStorage(r.internalFreeBytes, r.internalTotalBytes)
        val secLabel = r.secondaryLabel?.takeIf { it.isNotBlank() } ?: "microSD"
        if (r.secondaryTotalBytes != null && r.secondaryTotalBytes > 0L) {
            out += secLabel to formatStorage(
                r.secondaryFreeBytes ?: 0L,
                r.secondaryTotalBytes,
            )
        } else {
            out += secLabel to "Not present"
        }
        out += "Battery" to formatBattery(r.batteryPercent, r.charging, r.powerSource)
        out += "Power draw" to formatWatts(r.powerMicroWatts)
        return out
    }

    /**
     * Instantaneous power in microwatts from battery current (µA) and
     * voltage (mV). Null when either reading is missing/non-positive.
     */
    fun powerMicroWatts(currentMicroamps: Long?, voltageMillivolts: Int?): Long? {
        if (currentMicroamps == null || voltageMillivolts == null) return null
        if (voltageMillivolts <= 0) return null
        // |I| * V: discharge is often reported negative on Android.
        val uA = kotlin.math.abs(currentMicroamps)
        if (uA <= 0L) return null
        // µA * mV = nW; /1000 → µW
        return uA * voltageMillivolts / 1000L
    }
}
