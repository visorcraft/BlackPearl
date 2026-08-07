package com.visorcraft.ghostgalleon.ui

import android.app.ActivityOptions
import android.content.Intent
import android.os.Bundle
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
        if (app.liveCompanion() == null && isHomeRole()) {
            launchCompanionIfPresent()
        }
    }

    private fun launchCompanionIfPresent() {
        val topo = app.refreshDisplayConfig()
        if (topo.mode != SurfaceMode.DUAL) return
        val companionId = topo.companionDisplayId ?: return
        if (!com.visorcraft.ghostgalleon.display.AndroidDisplayProbe.hasDisplay(this, companionId)) {
            return
        }
        // NEW_TASK without MULTIPLE_TASK: singleInstance then reuses the
        // existing companion task instead of leaking a new one per call.
        val intent = Intent(this, CompanionActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val options = ActivityOptions.makeBasic().setLaunchDisplayId(companionId)
        startActivity(intent, options.toBundle())
    }
}
