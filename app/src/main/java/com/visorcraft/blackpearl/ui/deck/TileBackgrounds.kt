package com.visorcraft.blackpearl.ui.deck

import android.content.Context
import android.graphics.drawable.GradientDrawable

object TileBackgrounds {

    private const val FILL = 0xFF1C1C22.toInt()

    private fun dp(context: Context, value: Int): Float =
        value * context.resources.displayMetrics.density

    fun card(context: Context): GradientDrawable = GradientDrawable().apply {
        setColor(FILL)
        cornerRadius = dp(context, 24)
    }

    fun selected(context: Context, accent: Int): GradientDrawable = card(context).apply {
        setStroke(dp(context, 4).toInt(), accent)
    }

    /** Rounded strip for dock / status containers. */
    fun pill(context: Context): GradientDrawable = GradientDrawable().apply {
        setColor(FILL)
        cornerRadius = dp(context, 28)
    }
}
