package com.visorcraft.ghostgalleon.ui.deck

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.visorcraft.ghostgalleon.GhostGalleonApp
import com.visorcraft.ghostgalleon.R
import com.visorcraft.ghostgalleon.display.DisplayTopology
import com.visorcraft.ghostgalleon.state.DeckState
import com.visorcraft.ghostgalleon.ui.settings.SettingsActivity

/**
 * Swap + Settings affordances. In DUAL they host only on the physically
 * larger panel (not “whichever paints companion content”). SINGLE always
 * shows them. Pure placement policy lives in [DisplayTopology].
 */
internal fun shouldHostSystemChromeIcons(activity: Activity): Boolean {
    val app = activity.application as? GhostGalleonApp ?: return true
    val topo = app.displayConfig
    return DisplayTopology.shouldShowSystemChromeIcons(
        mode = topo.mode,
        thisDisplayId = activity.display?.displayId,
        largerDisplayId = topo.largerDisplayId,
    )
}

/** Horizontal row: swap (left) · spacer · settings (right). */
internal fun buildSystemChromeRow(
    context: Context,
    activity: AppCompatActivity,
    state: DeckState,
): View {
    val density = context.resources.displayMetrics.density
    fun dp(v: Int) = (v * density).toInt()
    val row = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(8), 0, dp(8), 0)
    }
    row.addView(
        iconButton(context, R.drawable.ic_swap, "Swap screens") {
            val appCtx = activity.application as? GhostGalleonApp
            if (appCtx != null && !appCtx.swapInteractiveDisplay()) {
                Toast.makeText(
                    context,
                    "Only one display — swap unavailable",
                    Toast.LENGTH_SHORT,
                ).show()
            }
        },
        LinearLayout.LayoutParams(dp(40), dp(40)),
    )
    row.addView(View(context), LinearLayout.LayoutParams(0, 1, 1f))
    row.addView(
        iconButton(context, R.drawable.ic_settings, "Settings") {
            launchOnOtherDisplay(
                activity,
                state,
                Intent(activity, SettingsActivity::class.java),
            )
        },
        LinearLayout.LayoutParams(dp(40), dp(40)),
    )
    return row
}

internal fun systemChromeRowLayoutParams(): LinearLayout.LayoutParams =
    LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
    )
