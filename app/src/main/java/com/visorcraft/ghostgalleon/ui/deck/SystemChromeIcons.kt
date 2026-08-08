package com.visorcraft.ghostgalleon.ui.deck

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.view.Gravity
import android.widget.FrameLayout
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
 *
 * Icons are **overlaid** at the bottom-left (Swap) and bottom-right
 * (Settings) of the panel root so Grid/Game dock + hint chrome never
 * shove them mid-screen.
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

/**
 * Pin Swap (bottom-start) and Settings (bottom-end) on [root].
 * Call after content is attached so they paint above the deck/dock.
 */
internal fun attachSystemChromeOverlay(
    root: FrameLayout,
    context: Context,
    activity: AppCompatActivity,
    state: DeckState,
) {
    val density = context.resources.displayMetrics.density
    fun dp(v: Int) = (v * density).toInt()
    val size = dp(40)
    val edge = dp(8)
    val bottom = dp(12)

    val swap = iconButton(context, R.drawable.ic_swap, "Swap screens") {
        val appCtx = activity.application as? GhostGalleonApp
        if (appCtx != null && !appCtx.swapInteractiveDisplay()) {
            Toast.makeText(
                context,
                "Only one display — swap unavailable",
                Toast.LENGTH_SHORT,
            ).show()
        }
    }
    root.addView(
        swap,
        FrameLayout.LayoutParams(size, size).apply {
            gravity = Gravity.BOTTOM or Gravity.START
            setMargins(edge, 0, 0, bottom)
        },
    )

    val settingsBtn = iconButton(context, R.drawable.ic_settings, "Settings") {
        launchOnOtherDisplay(
            activity,
            state,
            Intent(activity, SettingsActivity::class.java),
        )
    }
    root.addView(
        settingsBtn,
        FrameLayout.LayoutParams(size, size).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            setMargins(0, 0, edge, bottom)
        },
    )
}
