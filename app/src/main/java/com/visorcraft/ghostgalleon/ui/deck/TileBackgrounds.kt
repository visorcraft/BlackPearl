package com.visorcraft.ghostgalleon.ui.deck

import android.content.Context
import android.graphics.drawable.GradientDrawable
import com.visorcraft.ghostgalleon.settings.ThemePack
import com.visorcraft.ghostgalleon.settings.ThemeTokens

object TileBackgrounds {

    private const val FALLBACK_FILL = 0xFF1C1C22.toInt()

    private fun dp(context: Context, value: Int): Float =
        value * context.resources.displayMetrics.density

    private fun tokens(context: Context): ThemeTokens {
        val app = context.applicationContext
        return if (app is com.visorcraft.ghostgalleon.GhostGalleonApp) {
            ThemePack.resolve(app.settings)
        } else {
            ThemePack.GHOST
        }
    }

    fun card(context: Context): GradientDrawable {
        val t = tokens(context)
        // Ghost keeps the classic card fill; other packs recolor via panelLift.
        val fill = if (t.id == ThemePack.GHOST.id) FALLBACK_FILL else t.panelLift
        return GradientDrawable().apply {
            setColor(fill)
            cornerRadius = dp(context, t.cardRadiusDp)
        }
    }

    fun selected(context: Context, accent: Int): GradientDrawable = card(context).apply {
        setStroke(dp(context, 4).toInt(), accent)
    }

    /** Idle chip fill from the active theme pack. */
    fun chipIdleColor(context: Context): Int = tokens(context).chipIdle

    /** Rounded strip for dock / status containers. */
    fun pill(context: Context): GradientDrawable {
        val t = tokens(context)
        return GradientDrawable().apply {
            setColor(if (t.id == ThemePack.GHOST.id) FALLBACK_FILL else t.panelLift)
            cornerRadius = dp(context, (t.cardRadiusDp + 4).coerceAtMost(32))
        }
    }
}
