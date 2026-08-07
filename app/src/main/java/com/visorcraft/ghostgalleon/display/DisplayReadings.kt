package com.visorcraft.ghostgalleon.display

/**
 * Pure display snapshot types. No Android framework types — host-testable.
 */
data class DisplayInfo(
    val id: Int,
    val widthPx: Int,
    val heightPx: Int,
    val densityDpi: Int,
    val isDefault: Boolean,
    val isPrivate: Boolean = false,
    val name: String = "",
) {
    val widthDp: Float
        get() = if (densityDpi <= 0) widthPx.toFloat() else widthPx * 160f / densityDpi
    val heightDp: Float
        get() = if (densityDpi <= 0) heightPx.toFloat() else heightPx * 160f / densityDpi
    val isLandscape: Boolean get() = widthPx >= heightPx
}

data class DisplayReadings(
    val displays: List<DisplayInfo>,
    val manufacturer: String = "",
    val model: String = "",
    val device: String = "",
    val timestampMs: Long = 0L,
)

enum class SurfaceMode { SINGLE, DUAL }

/**
 * Resolved dual/single launcher surface assignment.
 *
 * Invariants:
 * - [primaryDisplayId] is always in [allIds] when non-empty
 * - DUAL: [companionDisplayId] non-null and ≠ primary
 * - SINGLE: [companionDisplayId] null and [launchDisplayId] == primary
 */
data class ResolvedTopology(
    val mode: SurfaceMode,
    val primaryDisplayId: Int,
    val companionDisplayId: Int?,
    val launchDisplayId: Int,
    val allIds: List<Int>,
    val reason: String = "",
) {
    val isDual: Boolean get() = mode == SurfaceMode.DUAL
}
