package com.visorcraft.ghostgalleon.rom

/**
 * Pure per-platform visual cues for Game Mode filter / hero tint.
 * Host-tested; no Android view types.
 */
object PlatformLook {

    /** Accent color for a platform id (deterministic, same as PlatformTile). */
    fun accentColor(platformId: String): Int = PlatformTile.colorFor(platformId)

    /**
     * Soft panel background tint: accent at ~12% alpha over black.
     * ARGB packed int.
     */
    fun panelTint(platformId: String): Int {
        val c = accentColor(platformId)
        val a = 0x1F // ~12%
        return (a shl 24) or (c and 0x00FFFFFF)
    }

    /** Chip/badge label for the active platform filter. */
    fun filterBadge(platformId: String): String {
        val p = Platforms.byId(platformId)
        return p?.shortName ?: platformId.uppercase()
    }

    fun hasFilter(platformId: String?): Boolean =
        !platformId.isNullOrBlank()
}
