package com.visorcraft.ghostgalleon.rom

/**
 * Pure hero/detail lines for ROM selection: platform, player, description,
 * and screenshot URI. Host-tested; no Android view types.
 */
object HeroDetail {

    /** Non-blank description for display, or null when absent/whitespace. */
    fun descriptionText(description: String?): String? =
        description?.trim()?.takeIf { it.isNotEmpty() }

    /**
     * Preferred/installed player label, e.g. "Player: Eden" or
     * "Player: RetroArch (Snes9x) (not installed)" / null when no platform.
     */
    fun playerLine(
        platform: Platform?,
        preferredPlayerId: String?,
        installed: (packageName: String) -> Boolean,
    ): String? {
        if (platform == null) return null
        val resolved = PlayerResolver.resolve(platform, preferredPlayerId, installed)
        if (resolved != null) {
            val preferred = preferredPlayerId?.let { PlayerResolver.byId(platform, it) }
            return if (preferred != null && preferred.id == resolved.id) {
                "Player: ${resolved.displayName}"
            } else if (preferred != null && !installed(PlayerResolver.packageName(preferred))) {
                "Player: ${resolved.displayName} (default offline)"
            } else {
                "Player: ${resolved.displayName}"
            }
        }
        val fallback = preferredPlayerId?.let { PlayerResolver.byId(platform, it) }
            ?: platform.player
        return "Player: ${fallback.displayName} (not installed)"
    }

    fun platformLine(platform: Platform?, platformId: String): String =
        platform?.displayName ?: platformId

    /** Screenshot URI to bind, or null. */
    fun screenshotUri(rom: RomEntry): String? =
        rom.screenshotUri?.takeIf { it.isNotBlank() }

    /** Logo / wheel URI when present. */
    fun logoUri(rom: RomEntry): String? =
        rom.logoUri?.takeIf { it.isNotBlank() }
}
