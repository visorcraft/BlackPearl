package com.visorcraft.ghostgalleon.ui

import android.app.ActivityOptions
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import com.visorcraft.ghostgalleon.display.AndroidDisplayProbe
import com.visorcraft.ghostgalleon.display.SurfaceMode

class MainActivity : BaseDeckActivity() {

    private var lastHealUptimeMs: Long = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        launchCompanionIfPresent()
    }

    override fun onResume() {
        super.onResume()
        app.refreshDisplayConfig(debounce = true)
        healCompanionIfMissing()
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
