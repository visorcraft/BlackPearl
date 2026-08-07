package com.visorcraft.ghostgalleon.ui.deck

import android.content.Context
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.TextView

object HintBar {

    const val TEXT = "A Launch · B Back · X Swap · Y Mode · Start Settings"

    // Shown while the dock holds focus: A launches the focused slot (or
    // opens the picker on a "+" placeholder), B/UP return to the deck.
    const val DOCK_TEXT = "A Launch / Add · B Back · Hold Move / Remove"

    // Shown while a dock tile is lifted (the grid move keeps its own hint
    // inside the dock bar - the dock's slots stay visible during a dock
    // move so the swap targets remain readable).
    const val MOVE_TEXT = "Moving - A drop · B cancel"

    fun textFor(dockFocused: Boolean): String = if (dockFocused) DOCK_TEXT else TEXT

    fun build(context: Context): View {
        val density = context.resources.displayMetrics.density
        return TextView(context).apply {
            text = TEXT
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(0xB3FFFFFF.toInt())
            gravity = Gravity.CENTER
            setPadding(0, (8 * density).toInt(), 0, (12 * density).toInt())
        }
    }
}
