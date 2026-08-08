package com.visorcraft.ghostgalleon.ui.deck

import android.content.Context
import android.widget.ImageView
import com.visorcraft.ghostgalleon.ui.dp

/** 40dp touch target; 24dp vector with 8dp padding. */
internal fun iconButton(
    context: Context,
    iconRes: Int,
    description: String,
    onClick: () -> Unit,
): ImageView {
    val pad = context.dp(8)
    return ImageView(context).apply {
        setImageResource(iconRes)
        contentDescription = description
        setPadding(pad, pad, pad, pad)
        setOnClickListener { onClick() }
    }
}
