package com.visorcraft.ghostgalleon.ui

import android.app.ActivityOptions
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.visorcraft.ghostgalleon.display.AndroidDisplayProbe
import com.visorcraft.ghostgalleon.display.SurfaceMode

class MainActivity : BaseDeckActivity() {

    private var lastHealUptimeMs: Long = 0L
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        launchCompanionIfPresent()
    }

    override fun onResume() {
        // Capture before super clears the flag at end of BaseDeckActivity.onResume.
        val returningFromElsewhere = leftHomeSinceResume()
        super.onResume()
        app.refreshDisplayConfig(debounce = true)
        if (returningFromElsewhere) {
            // Emulators (Eden/Azahar/…) often leave the secondary panel pure
            // black after return. Recreate Companion — does not require the
            // system Force Stop UI.
            restartCompanionPanel("return-from-app")
        } else {
            healCompanionIfMissing()
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        healCompanionIfMissing()
        // Still on home (never onStop'd): swipe-up / re-HOME opens all-apps.
        if (!leftHomeSinceResume()) {
            requestAppDrawer(allowToggle = true)
        }
    }

    private fun healCompanionIfMissing() {
        if (!isHomeRole()) return
        val now = SystemClock.uptimeMillis()
        if (!DualPaintPolicy.allowHeal(now, lastHealUptimeMs)) return
        lastHealUptimeMs = now

        val topo = app.refreshDisplayConfig(debounce = true)
        if (topo.mode != SurfaceMode.DUAL) return
        val target = topo.secondaryHomeDisplayId
            ?: topo.allIds.firstOrNull { it != (display?.displayId ?: -1) }
            ?: return
        val live = app.liveCompanions().filter { !it.isFinishing }
        val anyOnTarget = live.any {
            it.display?.displayId == target || it.isHealthyCompanion(target)
        }
        // Pure policy: do not kill peers; only launch when target is empty.
        if (!DualPaintPolicy.shouldLaunchCompanion(anyPeerOnTarget = anyOnTarget)) {
            return
        }
        launchCompanionIfPresent()
    }

    /**
     * Close every Companion and launch a fresh one on the secondary target.
     * Recovery for pure-black secondary panels without system Force Stop.
     * Debounced with the same heal window so storms cannot thrash paint.
     */
    fun restartCompanionPanel(reason: String) {
        if (!isHomeRole()) return
        val now = SystemClock.uptimeMillis()
        if (!DualPaintPolicy.allowHeal(now, lastHealUptimeMs)) return
        lastHealUptimeMs = now
        val topo = app.refreshDisplayConfig(debounce = true)
        if (topo.mode != SurfaceMode.DUAL) return
        Log.i(PAINT_TAG, "restartCompanion reason=$reason")
        app.liveCompanions().toList().forEach { it.closeQuietly() }
        // Let finish() detach before launching a peer on the same display.
        mainHandler.postDelayed({
            if (!isFinishing && !isDestroyed) launchCompanionIfPresent()
        }, 180L)
    }

    private fun launchCompanionIfPresent() {
        val topo = app.refreshDisplayConfig()
        if (topo.mode != SurfaceMode.DUAL) return
        val secondaryHomeId = topo.secondaryHomeDisplayId
            ?: topo.allIds.firstOrNull { it != (display?.displayId ?: -1) }
            ?: return
        if (!AndroidDisplayProbe.hasDisplay(this, secondaryHomeId)) return
        // Plain component + setLaunchDisplayId only (no SECONDARY_HOME category).
        val intent = Intent(this, CompanionActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val options = ActivityOptions.makeBasic().setLaunchDisplayId(secondaryHomeId)
        startActivity(intent, options.toBundle())
    }
}
