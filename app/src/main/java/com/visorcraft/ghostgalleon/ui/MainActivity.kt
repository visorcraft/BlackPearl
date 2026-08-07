package com.visorcraft.ghostgalleon.ui

import android.app.ActivityOptions
import android.content.Intent
import android.hardware.display.DisplayManager
import android.os.Bundle

class MainActivity : BaseDeckActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        launchCompanionIfPresent()
    }

    override fun onResume() {
        super.onResume()
        healCompanionIfMissing()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        // HOME redelivery lands here when MainActivity never paused; the
        // companion may have been reaped meanwhile, so heal here too.
        healCompanionIfMissing()
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
        val dm = getSystemService(DisplayManager::class.java)
        val hasSecond = dm.displays.any { it.displayId == 1 }
        if (!hasSecond) return
        // NEW_TASK without MULTIPLE_TASK: singleInstance then reuses the
        // existing companion task instead of leaking a new one per call.
        val intent = Intent(this, CompanionActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val options = ActivityOptions.makeBasic().setLaunchDisplayId(1)
        startActivity(intent, options.toBundle())
    }
}
