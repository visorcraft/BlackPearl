package com.visorcraft.ghostgalleon.ui

import android.app.ActivityOptions
import android.content.Intent
import android.hardware.display.DisplayManager
import android.os.Bundle

class CompanionActivity : BaseDeckActivity() {

    // True while this instance is closing itself for an internal reason
    // (display redirect or absorbed duplicate SECONDARY_HOME). Such a
    // finish() is not the user leaving home and must not cascade into
    // requestExitAll().
    private var selfClosing = false

    // Set before super.onCreate when this start is a duplicate swipe-up
    // redelivery and we will finish without painting a full deck.
    private var absorbDuplicate = false

    override fun skipExitCascade(): Boolean = selfClosing

    override fun shouldRenderOnCreate(): Boolean = !absorbDuplicate

    /** Internal close without the exit cascade. */
    fun closeQuietly() {
        selfClosing = true
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Quickstep SECONDARY_HOME uses FLAG_ACTIVITY_MULTIPLE_TASK, so
        // singleInstance does NOT reuse the existing companion — every
        // bottom swipe used to spawn a fresh activity and flash-reload the
        // grid. Prefer the healthy companion already on display 1: ask it
        // to open the all-apps drawer and finish this duplicate without
        // painting. (Application onActivityCreated runs after onCreate, so
        // liveCompanions() here only holds previously created instances.)
        val existing = app.liveCompanions().filter { !it.isFinishing }
        val keepOnBottom = existing.firstOrNull { it.display?.displayId == 1 }
        if (keepOnBottom != null) {
            absorbDuplicate = true
            selfClosing = true
            super.onCreate(savedInstanceState)
            // Drop any other stale companions not on display 1.
            existing.filter { it !== keepOnBottom }.forEach { it.closeQuietly() }
            keepOnBottom.requestAppDrawer()
            finish()
            return
        }

        super.onCreate(savedInstanceState)

        // No healthy display-1 companion yet: we are the real one. Close any
        // leftover companions stuck on the wrong display.
        existing.forEach { it.closeQuietly() }

        // SECONDARY_HOME starts land on whichever display is focused. This
        // activity belongs on display 1; when it arrives anywhere else,
        // relaunch it there and close the misplaced task. MULTIPLE_TASK is
        // required HERE: a plain NEW_TASK start would resolve to this
        // instance's own task (singleInstance task reuse) and never move
        // displays.
        val currentDisplay = display?.displayId
        if (currentDisplay != null && currentDisplay != 1) {
            val dm = getSystemService(DisplayManager::class.java)
            val hasSecond = dm.displays.any { it.displayId == 1 }
            if (hasSecond) {
                val intent = Intent(this, CompanionActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                val options = ActivityOptions.makeBasic().setLaunchDisplayId(1)
                selfClosing = true
                startActivity(intent, options.toBundle())
                finish()
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        // If singleInstance ever reuses us without MULTIPLE_TASK, treat
        // re-HOME as open-drawer (same as MainActivity).
        if (!leftHomeSinceResume()) {
            requestAppDrawer()
        }
    }
}
