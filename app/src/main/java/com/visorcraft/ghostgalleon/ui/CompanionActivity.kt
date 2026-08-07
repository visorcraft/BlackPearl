package com.visorcraft.ghostgalleon.ui

import android.app.ActivityOptions
import android.content.Intent
import android.os.Bundle
import com.visorcraft.ghostgalleon.display.AndroidDisplayProbe
import com.visorcraft.ghostgalleon.display.SurfaceMode

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
        val topo = app.refreshDisplayConfig()
        // Single-display: SECONDARY_HOME is rare; finish quietly without cascade.
        if (topo.mode != SurfaceMode.DUAL || topo.companionDisplayId == null) {
            selfClosing = true
            super.onCreate(savedInstanceState)
            finish()
            return
        }
        val target = topo.companionDisplayId

        // Prefer the healthy companion already on the topology target: ask it
        // to open the all-apps drawer and finish this duplicate without painting.
        val existing = app.liveCompanions().filter { !it.isFinishing }
        val keepOnTarget = existing.firstOrNull { it.display?.displayId == target }
        if (keepOnTarget != null) {
            absorbDuplicate = true
            selfClosing = true
            super.onCreate(savedInstanceState)
            existing.filter { it !== keepOnTarget }.forEach { it.closeQuietly() }
            keepOnTarget.requestAppDrawer()
            finish()
            return
        }

        super.onCreate(savedInstanceState)

        // No healthy target companion yet: we are the real one. Close leftovers.
        existing.forEach { it.closeQuietly() }

        // SECONDARY_HOME may land on the focused display; redirect to topology target.
        val currentDisplay = display?.displayId
        if (currentDisplay != null && currentDisplay != target) {
            if (AndroidDisplayProbe.hasDisplay(this, target)) {
                val intent = Intent(this, CompanionActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                val options = ActivityOptions.makeBasic().setLaunchDisplayId(target)
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
