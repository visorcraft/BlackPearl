package com.visorcraft.ghostgalleon.ui

import android.app.ActivityOptions
import android.content.Intent
import android.os.Bundle
import androidx.lifecycle.Lifecycle
import com.visorcraft.ghostgalleon.display.AndroidDisplayProbe
import com.visorcraft.ghostgalleon.display.SurfaceMode

/**
 * Secondary panel (Sugar bottom by default). System SECONDARY_HOME storms
 * must never thrash setContentView or open the all-apps drawer — that left
 * both physical displays pure black.
 *
 * See AGENTS.md "Black screens / dual paint invariants" and
 * [DualPaintPolicy].
 */
class CompanionActivity : BaseDeckActivity() {

    private var selfClosing = false
    private var absorbDuplicate = false
    /** At most one display redirect per instance (prevents redirect loops). */
    private var didRedirect = false

    override fun skipExitCascade(): Boolean = true

    override fun shouldRenderOnCreate(): Boolean = !absorbDuplicate

    fun closeQuietly() {
        selfClosing = true
        finish()
    }

    fun isHealthyCompanion(targetDisplayId: Int): Boolean {
        if (isFinishing || isDestroyed) return false
        val id = display?.displayId ?: return false
        if (id != targetDisplayId) return false
        return lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val topo = app.refreshDisplayConfig()
        if (topo.mode != SurfaceMode.DUAL) {
            selfClosing = true
            super.onCreate(savedInstanceState)
            finish()
            return
        }
        val target = topo.secondaryHomeDisplayId
            ?: topo.allIds.firstOrNull { it != (display?.displayId ?: -1) }
            ?: run {
                selfClosing = true
                super.onCreate(savedInstanceState)
                finish()
                return
            }

        val existing = app.liveCompanions().filter { !it.isFinishing && it !== this }
        // Keep any live peer already on the target — do not require STARTED
        // yet (race with attach). Prefer not to kill/recreate.
        val keepOnTarget = existing.firstOrNull {
            it.display?.displayId == target || it.isHealthyCompanion(target)
        }
        if (DualPaintPolicy.shouldAbsorbDuplicate(hasPeerOnTarget = keepOnTarget != null)) {
            absorbDuplicate = true
            selfClosing = true
            super.onCreate(savedInstanceState)
            // Absorb is silent (DualPaintPolicy.absorbMayOpenDrawer() == false):
            // no All-apps, no peer massacre, no re-paint storm on survivor.
            finish()
            return
        }

        super.onCreate(savedInstanceState)

        val currentDisplay = display?.displayId
        if (currentDisplay != null && currentDisplay != target) {
            redirectToSecondary(target)
        } else {
            window.decorView.post {
                if (isFinishing || selfClosing) return@post
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
        if (selfClosing || isFinishing || didRedirect) return
        if (!AndroidDisplayProbe.hasDisplay(this, target)) return
        didRedirect = true
        val intent = Intent(this, CompanionActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
        val options = ActivityOptions.makeBasic().setLaunchDisplayId(target)
        selfClosing = true
        runCatching { startActivity(intent, options.toBundle()) }
        finish()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        // All-apps is Main-only (AGENTS + DualPaintPolicy). Companion must
        // never open the drawer — SECONDARY_HOME redelivery storms would
        // flash/glitch All-apps and thrash paints.
    }
}
