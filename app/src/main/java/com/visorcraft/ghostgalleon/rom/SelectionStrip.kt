package com.visorcraft.ghostgalleon.rom

import com.visorcraft.ghostgalleon.library.RaProgress
import com.visorcraft.ghostgalleon.library.RetroAchievements

/**
 * Pure selection-context model for the single-display TOP_STRIP hero.
 * Always selection-focused (never Perf HUD / pinned app). Host-tested.
 */
object SelectionStrip {

    data class Model(
        /** Primary title (ROM name or app label). */
        val title: String,
        /** Platform display name, "App", or empty brand fallback. */
        val subtitle: String,
        /** Player / playtime line, or null. */
        val detail: String?,
        /** RA progress line when credentials + progress present. */
        val raLine: String?,
        val platformId: String?,
        val isRom: Boolean,
        val isEmpty: Boolean,
    )

    fun empty(brand: String = "Ghost Galleon"): Model = Model(
        title = brand,
        subtitle = "Select a game or app",
        detail = null,
        raLine = null,
        platformId = null,
        isRom = false,
        isEmpty = true,
    )

    fun forApp(label: String): Model = Model(
        title = label.ifBlank { "App" },
        subtitle = "App",
        detail = null,
        raLine = null,
        platformId = null,
        isRom = false,
        isEmpty = false,
    )

    fun forRom(
        rom: RomEntry,
        preferredPlayerId: String?,
        installed: (packageName: String) -> Boolean,
        playMeta: String?,
        raProgress: RaProgress?,
        hasRaCredentials: Boolean,
    ): Model {
        val platform = Platforms.byId(rom.platformId)
        val player = HeroDetail.playerLine(platform, preferredPlayerId, installed)
        val detail = listOfNotNull(
            player,
            playMeta?.trim()?.takeIf { it.isNotEmpty() },
        ).joinToString(" · ").ifEmpty { null }
        return Model(
            title = rom.name.ifBlank { rom.id },
            subtitle = HeroDetail.platformLine(platform, rom.platformId),
            detail = detail,
            raLine = RetroAchievements.heroLine(raProgress, hasRaCredentials),
            platformId = rom.platformId,
            isRom = true,
            isEmpty = false,
        )
    }

    /**
     * Fixed strip height in dp so art + 3 text lines fit without clipping.
     * Callers convert to px via density.
     */
    const val STRIP_HEIGHT_DP = 120

    /** Square art edge in dp (must leave room for text column). */
    const val ART_SIZE_DP = 88
}
