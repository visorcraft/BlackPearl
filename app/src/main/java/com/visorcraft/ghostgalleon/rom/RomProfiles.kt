package com.visorcraft.ghostgalleon.rom

/**
 * Per-ROM launch profile resolution. Pure; host-tested.
 *
 * [romProfiles] maps rom entry id → preferred [PlayerTemplate.id]. Empty /
 * unknown player ids fall through to platform default resolution.
 */
object RomProfiles {

    /**
     * Player id to pass into [PlayerResolver]: per-ROM profile wins over
     * platform default when non-blank.
     */
    fun preferredPlayerId(
        romId: String,
        romProfiles: Map<String, String>,
        platformDefaultPlayerId: String?,
    ): String? {
        val profile = romProfiles[romId]?.trim()?.takeIf { it.isNotEmpty() }
        return profile ?: platformDefaultPlayerId?.trim()?.takeIf { it.isNotEmpty() }
    }

    fun setProfile(
        romProfiles: Map<String, String>,
        romId: String,
        playerId: String?,
    ): Map<String, String> {
        val id = romId.trim()
        if (id.isEmpty()) return romProfiles
        val pid = playerId?.trim().orEmpty()
        return if (pid.isEmpty()) romProfiles - id
        else romProfiles + (id to pid)
    }

    fun clearProfile(romProfiles: Map<String, String>, romId: String): Map<String, String> =
        romProfiles - romId.trim()
}
