package com.visorcraft.ghostgalleon.ui

import android.os.Build
import android.view.View
import android.view.Window
import android.view.WindowInsets
import android.view.WindowInsetsController

// Hide the Android status bar (deck windows show the hero panel's own
// clock/battery pill; settings is just as immersive). Bars still reveal
// transiently on an edge swipe; the gesture/navigation bar stays visible.
// Call from onResume so a transient reveal re-hides when the window
// regains focus.
fun hideStatusBar(window: Window) {
    if (Build.VERSION.SDK_INT >= 30) {
        window.insetsController?.let { controller ->
            controller.systemBarsBehavior = WindowInsetsController
                .BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsets.Type.statusBars())
        }
    } else {
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
    }
}
