package com.visorcraft.ghostgalleon.display

import kotlin.math.min
import kotlin.math.roundToInt

enum class WidthClass { COMPACT, MEDIUM, EXPANDED }
enum class HeightClass { COMPACT, MEDIUM, EXPANDED }

enum class CompanionHeroStyle {
    SECOND_DISPLAY,
    TOP_STRIP,
    NONE,
}

/**
 * Pure layout suggestions from window geometry (dp). Does not read Settings;
 * callers merge with user gridColumns/cardSizeDp without clobbering customs.
 */
data class LayoutMetrics(
    val widthDp: Float,
    val heightDp: Float,
    val widthClass: WidthClass,
    val heightClass: HeightClass,
    val suggestedGridColumns: Int,
    val suggestedCardSizeDp: Int,
    val suggestedDockSlotDp: Int,
    val companionHeroStyle: CompanionHeroStyle,
)

object LayoutMetricsResolver {

    fun fromWindow(
        windowWidthPx: Int,
        windowHeightPx: Int,
        densityDpi: Int,
        topologyMode: SurfaceMode,
        isCompanionRole: Boolean,
    ): LayoutMetrics {
        val dpi = densityDpi.coerceAtLeast(1)
        val wDp = windowWidthPx * 160f / dpi
        val hDp = windowHeightPx * 160f / dpi
        val widthClass = when {
            wDp < 600f -> WidthClass.COMPACT
            wDp < 840f -> WidthClass.MEDIUM
            else -> WidthClass.EXPANDED
        }
        val heightClass = when {
            hDp < 480f -> HeightClass.COMPACT
            hDp < 720f -> HeightClass.MEDIUM
            else -> HeightClass.EXPANDED
        }
        val columns = (wDp / 96f).roundToInt().coerceIn(4, 7)
        val cardDp = (min(wDp, hDp) * 0.42f).roundToInt().coerceIn(140, 280)
        val dockSlot = (wDp / 12f).roundToInt().coerceIn(48, 72)
        // Dual companion panel: full second-display hero. Single-display:
        // selection context as a TOP_STRIP above the interactive deck when
        // the window is tall enough (not compact height). Dual primary has
        // no in-pane strip (hero lives on the other display).
        val hero = when {
            topologyMode == SurfaceMode.DUAL && isCompanionRole ->
                CompanionHeroStyle.SECOND_DISPLAY
            topologyMode == SurfaceMode.SINGLE &&
                !isCompanionRole &&
                heightClass != HeightClass.COMPACT ->
                CompanionHeroStyle.TOP_STRIP
            else -> CompanionHeroStyle.NONE
        }
        return LayoutMetrics(
            widthDp = wDp,
            heightDp = hDp,
            widthClass = widthClass,
            heightClass = heightClass,
            suggestedGridColumns = columns,
            suggestedCardSizeDp = cardDp,
            suggestedDockSlotDp = dockSlot,
            companionHeroStyle = hero,
        )
    }
}
