package com.visorcraft.ghostgalleon.rom

/**
 * Chooses a [PlayerTemplate] for a platform given a preferred player id and
 * an "is package installed" predicate. Pure; host-tested.
 */
object PlayerResolver {

    fun packageName(template: PlayerTemplate): String =
        template.component.substringBefore('/')

    fun byId(platform: Platform, playerId: String): PlayerTemplate? =
        platform.players.firstOrNull { it.id == playerId }

    /**
     * Prefer [preferredPlayerId] when that player is installed; otherwise the
     * first installed player in registry order; null if none are installed.
     */
    fun resolve(
        platform: Platform,
        preferredPlayerId: String?,
        installed: (packageName: String) -> Boolean,
    ): PlayerTemplate? {
        if (preferredPlayerId != null) {
            byId(platform, preferredPlayerId)?.let { pref ->
                if (installed(packageName(pref))) return pref
            }
        }
        return platform.players.firstOrNull { installed(packageName(it)) }
    }

    /** All players whose package is installed, registry order. */
    fun installedPlayers(
        platform: Platform,
        installed: (packageName: String) -> Boolean,
    ): List<PlayerTemplate> =
        platform.players.filter { installed(packageName(it)) }
}
