package com.visorcraft.ghostgalleon.settings

/**
 * Companion (non-interactive) panel presentation mode. Pure; host-tested.
 *
 * [PINNED_APP] degrades when dual-screen emulators claim both displays
 * (nds/3ds) or when no pin package is set.
 */
enum class CompanionRole {
    HERO,
    NOW_PLAYING,
    PERF_HUD,
    PINNED_APP,
    ;

    companion object {
        fun parse(raw: String?): CompanionRole =
            entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) }
                ?: HERO
    }
}

object CompanionRoleResolve {

    /**
     * Platforms that typically claim both displays; pinned apps must not
     * fight them — fall back to Now Playing when a session is open, else Hero.
     */
    val DUAL_CLAIM_PLATFORMS: Set<String> = setOf("nds", "3ds")

    data class Context(
        val preferred: CompanionRole,
        val openSessionKey: String? = null,
        val pinnedPackage: String? = null,
        /** Platform id of the open session ROM, if known. */
        val openSessionPlatformId: String? = null,
        val pinnedPackageInstalled: Boolean = true,
    )

    /**
     * Effective role to render. Never returns [CompanionRole.PINNED_APP] when
     * the pin is unusable or a dual-claim platform owns the session.
     */
    fun effective(ctx: Context): CompanionRole {
        val dualClaim = ctx.openSessionPlatformId?.lowercase() in DUAL_CLAIM_PLATFORMS
        return when (ctx.preferred) {
            CompanionRole.HERO -> CompanionRole.HERO
            CompanionRole.NOW_PLAYING ->
                if (ctx.openSessionKey != null) CompanionRole.NOW_PLAYING
                else CompanionRole.HERO
            CompanionRole.PERF_HUD -> CompanionRole.PERF_HUD
            CompanionRole.PINNED_APP -> when {
                dualClaim && ctx.openSessionKey != null -> CompanionRole.NOW_PLAYING
                dualClaim -> CompanionRole.HERO
                ctx.pinnedPackage.isNullOrBlank() -> CompanionRole.HERO
                !ctx.pinnedPackageInstalled -> CompanionRole.HERO
                else -> CompanionRole.PINNED_APP
            }
        }
    }
}
