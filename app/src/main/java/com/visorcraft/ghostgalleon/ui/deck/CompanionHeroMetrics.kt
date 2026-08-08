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

    // --- Empty-selection brand scene (clouds / sea / ship) -----------------
    // Full-panel 3:2 sky/water stack. Ship size scales with panel height
    // (any dual / single device) and is placed so the hull rests on the
    // waterline. Must NOT reuse [forPanel] game-hero art sizes — those are
    // for selected ROM/app tiles under chrome, not the brand scene.

    /**
     * Horizon as fraction of panel height for clouds:sea weight 3:2
     * (sky 60%, water 40%).
     */
    const val BRAND_WATERLINE_FRACTION = 0.60f

    /**
     * Where the hull sits in the ship bitmap (0 = top, 1 = bottom). Masts
     * above, hull on the waterline.
     */
    const val BRAND_SHIP_HULL_FRACTION = 0.55f

    /** Ship edge as a fraction of panel height (before min/max clamps). */
    const val BRAND_SHIP_HEIGHT_FRACTION = 0.44f

    /** Min / max ship edge in dp so tiny and huge panels stay sane. */
    const val BRAND_SHIP_MIN_DP = 120
    const val BRAND_SHIP_MAX_DP = 280

    data class BrandShipLayout(
        val sizePx: Int,
        /** Top margin so hull Y ≈ waterline. */
        val topMarginPx: Int,
    )

    /**
     * Size + top margin for the brand galleon on a panel of [panelHeightPx].
     * [density] is display density (px = dp * density) for min/max clamps.
     */
    fun brandShipLayout(panelHeightPx: Int, density: Float): BrandShipLayout {
        if (panelHeightPx <= 0 || density <= 0f) {
            return BrandShipLayout(sizePx = 0, topMarginPx = 0)
        }
        val minPx = (BRAND_SHIP_MIN_DP * density).toInt().coerceAtLeast(1)
        val maxPx = (BRAND_SHIP_MAX_DP * density).toInt().coerceAtLeast(minPx)
        val size = (panelHeightPx * BRAND_SHIP_HEIGHT_FRACTION)
            .toInt()
            .coerceIn(minPx, maxPx)
        val waterline = panelHeightPx * BRAND_WATERLINE_FRACTION
        val top = (waterline - size * BRAND_SHIP_HULL_FRACTION)
            .toInt()
            .coerceAtLeast(0)
        return BrandShipLayout(sizePx = size, topMarginPx = top)
    }

    /** Top margin only (tests / callers that already know size). */
    fun brandShipTopMarginPx(panelHeightPx: Int, shipSizePx: Int): Int {
        if (panelHeightPx <= 0 || shipSizePx <= 0) return 0
        val waterline = panelHeightPx * BRAND_WATERLINE_FRACTION
        return (waterline - shipSizePx * BRAND_SHIP_HULL_FRACTION)
            .toInt()
            .coerceAtLeast(0)
    }
}
