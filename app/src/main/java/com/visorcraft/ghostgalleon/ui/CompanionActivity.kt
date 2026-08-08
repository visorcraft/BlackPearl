package com.visorcraft.ghostgalleon.ui

import android.app.ActivityOptions
import android.content.Intent
import android.os.Bundle
import com.visorcraft.ghostgalleon.display.AndroidDisplayProbe
import com.visorcraft.ghostgalleon.display.SurfaceMode

class CompanionActivity : BaseDeckActivity() {

    // True while this instance is closing itself for an internal reason
    // (display redirect or absorbed duplicate SECONDARY_HOME).
    private var selfClosing = false

    // Set before super.onCreate when this start is a duplicate swipe-up
    // redelivery and we will finish without painting a full deck.
    private var absorbDuplicate = false

    // Companion is the secondary home panel. Its death (redirect, absorb,
    // OEM SECONDARY_HOME storm) must NEVER cascade-finish Main — that left
    // both panels as opaque black windows with no live process.
    override fun skipExitCascade(): Boolean = true

    override fun shouldRenderOnCreate(): Boolean = !absorbDuplicate

    /** Internal close without the exit cascade. */
    fun closeQuietly() {
        selfClosing = true
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val topo = app.refreshDisplayConfig()
        // Single-display: SECONDARY_HOME is rare; finish quietly without cascade.
        if (topo.mode != SurfaceMode.DUAL) {
            selfClosing = true
            super.onCreate(savedInstanceState)
            finish()
            return
        }
        // Activity placement = non-default panel (Sugar bottom). Content roles
        // follow primaryDisplayId separately (grid vs hero).
        val target = topo.secondaryHomeDisplayId
            ?: topo.allIds.firstOrNull { it != (display?.displayId ?: -1) }
            ?: run {
                selfClosing = true
                super.onCreate(savedInstanceState)
                finish()
                return
            }

        // Prefer the healthy companion already on the secondary-home target.
        val existing = app.liveCompanions().filter { !it.isFinishing }
        val keepOnTarget = existing.firstOrNull { it.display?.displayId == target }
        if (keepOnTarget != null) {
            absorbDuplicate = true
            selfClosing = true
            super.onCreate(savedInstanceState)
            existing.filter { it !== keepOnTarget }.forEach { it.closeQuietly() }
            // Debounced app-wide: cold-start SECONDARY_HOME storms no-op.
            keepOnTarget.requestAppDrawer()
            finish()
            return
        }

        super.onCreate(savedInstanceState)

        // No healthy target companion yet: we are the real one. Close leftovers
        // on the wrong display only (do not kill peers mid-redirect).
        existing.filter { it.display?.displayId != target }.forEach { it.closeQuietly() }

        // System SECONDARY_HOME often lands on the focused (default) display.
        // display?.displayId can still be default during onCreate even when
        // launched with setLaunchDisplayId — defer the check until attached.
        val currentDisplay = display?.displayId
        if (currentDisplay != null && currentDisplay != target) {
            redirectToSecondary(target)
        } else {
            // Confirm after attach: if we are still on the wrong panel, move.
            window.decorView.post {
                if (isFinishing) return@post
                val now = display?.displayId
                if (now != null && now != target &&
                    AndroidDisplayProbe.hasDisplay(this, target)
                ) {
                    redirectToSecondary(target)
                }
            }
        }
    }

    private fun redirectToSecondary(target: Int) {
        if (selfClosing || isFinishing) return
        if (!AndroidDisplayProbe.hasDisplay(this, target)) return
        // Plain component + MULTIPLE_TASK + launch display. No SECONDARY_HOME
        // category (Sugar ignores setLaunchDisplayId for that category).
        val intent = Intent(this, CompanionActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
        val options = ActivityOptions.makeBasic().setLaunchDisplayId(target)
        selfClosing = true
        runCatching { startActivity(intent, options.toBundle()) }
        finish()
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
