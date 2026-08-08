package com.visorcraft.ghostgalleon.ui.deck

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import com.visorcraft.ghostgalleon.GhostGalleonApp
import com.visorcraft.ghostgalleon.ui.toast

/** Full-screen picker / slot-menu attach helpers shared by Grid and Game. */
object DeckOverlays {

    fun attach(root: FrameLayout?, child: View) {
        root?.addView(
            child,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
    }

    fun detach(root: FrameLayout?, child: View?) {
        child?.let { root?.removeView(it) }
    }

    fun hideIme(activity: Activity, root: View?) {
        val token = root?.windowToken ?: return
        activity.getSystemService(InputMethodManager::class.java)
            ?.hideSoftInputFromWindow(token, 0)
    }

    fun hideApp(activity: Activity, packageName: String) {
        val app = activity.application as GhostGalleonApp
        app.updateSettings(
            app.settings.copy(hiddenPackages = app.settings.hiddenPackages + packageName),
        )
        activity.toast("App hidden")
    }
}
