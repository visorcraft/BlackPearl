package com.visorcraft.ghostgalleon.ui

import android.app.ActivityOptions
import android.content.Intent
import android.os.Bundle
import com.visorcraft.ghostgalleon.display.AndroidDisplayProbe
import com.visorcraft.ghostgalleon.display.SurfaceMode

class MainActivity : BaseDeckActivity() {

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
        // HOME redelivery lands here when MainActivity never paused; the
        // companion may have been reaped meanwhile, so heal here too.
        healCompanionIfMissing()
        // Still on home (never onStop'd): swipe-up / re-HOME opens the
        // all-apps drawer (forwards to the PRIMARY deck if the interactive
        // grid lives on the other display). Returning from another app
        // just lands on the existing grid.
        if (!leftHomeSinceResume()) {
            requestAppDrawer()
        }
    }

    // Self-heal a lost companion (its activity reaped or its task removed
    // while MainActivity stayed alive). Gated on the HOME role so an
    // intentional exit while NOT home — which exit-cascades through
    // onDestroy and never resumes — is not undone here.
    private fun healCompanionIfMissing() {
        if (!isHomeRole()) return
        val topo = app.refreshDisplayConfig(debounce = true)
        if (topo.mode != SurfaceMode.DUAL) return
        val target = topo.secondaryHomeDisplayId
            ?: topo.allIds.firstOrNull { it != (display?.displayId ?: -1) }
            ?: return
        val live = app.liveCompanions().filter { !it.isFinishing }
        // Already healthy on the secondary panel — do not re-launch (that
        // multiplies SINGLE_INSTANCE thrash with system SECONDARY_HOME).
        if (live.any { it.display?.displayId == target }) return
        launchCompanionIfPresent()
    }

    private fun launchCompanionIfPresent() {
        val topo = app.refreshDisplayConfig()
        if (topo.mode != SurfaceMode.DUAL) return
        // SECONDARY_HOME activity must land on the non-default panel.
        // Content roles (grid vs hero) follow primaryDisplayId separately —
        // do NOT use companionDisplayId here (that is the hero *content*
        // surface, often the default display where Main already lives).
        val secondaryHomeId = topo.secondaryHomeDisplayId
            ?: topo.allIds.firstOrNull { it != (display?.displayId ?: -1) }
            ?: return
        if (!AndroidDisplayProbe.hasDisplay(this, secondaryHomeId)) return
        // Plain component start + setLaunchDisplayId. Do NOT attach
        // CATEGORY_SECONDARY_HOME here: on Sugar that forces a home-typed
        // task onto display 0 and ignores the launch display id, so the
        // bottom panel stays empty (launcher3/recents). System-fired
        // SECONDARY_HOME intents still hit Companion via the manifest filter.
        // NEW_TASK without MULTIPLE_TASK: singleInstance reuses the existing
        // companion task instead of leaking a new one per call.
        val intent = Intent(this, CompanionActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val options = ActivityOptions.makeBasic().setLaunchDisplayId(secondaryHomeId)
        startActivity(intent, options.toBundle())
    }
}
