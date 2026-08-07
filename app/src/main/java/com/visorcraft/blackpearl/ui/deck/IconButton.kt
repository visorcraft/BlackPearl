package com.visorcraft.blackpearl.ui.deck

import android.content.Context
import android.widget.ImageView

/**
 * 40dp tappable icon button for dock bars and the companion panel. The
 * vector is 24dp; 8dp padding keeps the touch target at the full 40dp.
 */
internal fun iconButton(
    context: Context,
    iconRes: Int,
    description: String,
    onClick: () -> Unit,
): ImageView {
    val pad = (8 * context.resources.displayMetrics.density).toInt()
    return ImageView(context).apply {
        setImageResource(iconRes)
        contentDescription = description
        setPadding(pad, pad, pad, pad)
        setOnClickListener { onClick() }
    }
}
