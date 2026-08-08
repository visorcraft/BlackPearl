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
import com.visorcraft.ghostgalleon.ui.dp
import com.visorcraft.ghostgalleon.ui.settings.SettingsActivity

/** Swap + Settings on the larger DUAL panel (or always in SINGLE). */
internal fun shouldHostSystemChromeIcons(activity: Activity): Boolean {
    val app = activity.application as? GhostGalleonApp ?: return true
    val topo = app.displayConfig
    return DisplayTopology.shouldShowSystemChromeIcons(
        mode = topo.mode,
        thisDisplayId = activity.display?.displayId,
        largerDisplayId = topo.largerDisplayId,
    )
}

/** Overlay Swap (bottom-start) and Settings (bottom-end) above deck chrome. */
internal fun attachSystemChromeOverlay(
    root: FrameLayout,
    context: Context,
    activity: AppCompatActivity,
    state: DeckState,
) {
    val size = context.dp(40)
    val edge = context.dp(8)
    val bottom = context.dp(12)

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
