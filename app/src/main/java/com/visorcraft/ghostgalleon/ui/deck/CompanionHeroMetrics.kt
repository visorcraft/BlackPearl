package com.visorcraft.ghostgalleon.ui.deck

/**
 * Pure layout sizes for the dual-screen companion hero. Host-tested.
 *
 * Bottom Sugar panel is short (~500–560dp usable height). A fixed 240dp art
 * tile + 32sp title + chrome (role chips, actions, hints) clipped the title
 * mid-glyph. Scale art/name down so the title always fits above the action row.
 */
object CompanionHeroMetrics {

    data class Spec(
        /** Square art / platform tile edge in dp. */
        val artSizeDp: Int,
        /** Hero title size in sp. */
        val nameSp: Float,
        /** Top padding on the title in dp. */
        val nameTopPadDp: Int,
        /** Side padding on the title in dp. */
        val nameSidePadDp: Int,
        /** Max title lines. */
        val nameMaxLines: Int,
        /**
         * Banner height as a fraction of panel height when wide HERO art is
         * shown (0 = never show banner).
         */
        val bannerHeightFraction: Float,
        /** Show screenshot / video snaps under the title. */
        val showExtraMedia: Boolean,
        /** Show Open-with / Favorite quick chips under ROM meta. */
        val showQuickChips: Boolean,
    )

    /**
     * @param panelHeightDp usable window height in dp (prefer app bounds).
     * @param panelWidthDp usable window width in dp.
     */
    @Suppress("UNUSED_PARAMETER")
    fun forPanel(panelHeightDp: Float, panelWidthDp: Float = panelHeightDp): Spec {
        val h = panelHeightDp.coerceAtLeast(1f)
        // Compact: Sugar secondary / short dual panel.
        // Medium: tall dual companion or phone landscape.
        // Expanded: full top panel style.
        return when {
            h < 500f -> Spec(
                artSizeDp = 132,
                nameSp = 20f,
                nameTopPadDp = 8,
                nameSidePadDp = 16,
                nameMaxLines = 2,
                bannerHeightFraction = 0.22f,
                showExtraMedia = false,
                showQuickChips = false,
            )
            h < 600f -> Spec(
                artSizeDp = 160,
                nameSp = 22f,
                nameTopPadDp = 10,
                nameSidePadDp = 20,
                nameMaxLines = 2,
                bannerHeightFraction = 0.28f,
                showExtraMedia = false,
                showQuickChips = true,
            )
            else -> Spec(
                artSizeDp = 240,
                nameSp = 32f,
                nameTopPadDp = 24,
                nameSidePadDp = 24,
                nameMaxLines = 2,
                bannerHeightFraction = 0.40f,
                showExtraMedia = true,
                showQuickChips = true,
            )
        }.let { spec ->
            // Never let art exceed ~half the panel height (title needs room).
            val maxArt = (h * 0.42f).toInt().coerceIn(100, 240)
            if (spec.artSizeDp <= maxArt) spec
            else spec.copy(artSizeDp = maxArt)
        }
    }

    /** Banner height in px from panel height and [Spec.bannerHeightFraction]. */
    fun bannerHeightPx(panelHeightPx: Int, spec: Spec): Int {
        if (spec.bannerHeightFraction <= 0f) return 0
        return (panelHeightPx * spec.bannerHeightFraction).toInt().coerceAtLeast(0)
    }
}
