package com.visorcraft.blackpearl.ui

import android.app.ActivityOptions
import android.content.Intent
import android.hardware.display.DisplayManager
import android.os.Bundle

class CompanionActivity : BaseDeckActivity() {

    // True while this instance is closing itself for an internal reason
    // (display redirect or superseded by a newer instance). Such a finish()
    // is not the user leaving home and must not cascade into
    // requestExitAll().
    private var selfClosing = false

    override fun skipExitCascade(): Boolean = selfClosing

    /** Internal close without the exit cascade (superseded instance). */
    fun closeQuietly() {
        selfClosing = true
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The ROM fires SECONDARY_HOME with FLAG_ACTIVITY_MULTIPLE_TASK on
        // every bottom HOME/swipe, spawning a fresh instance+task despite
        // singleInstance. Do NOT finish that fresh instance here — the
        // ROM's dual-display watcher notices and re-fires HOME, looping
        // (verified 2026-08-06: one press -> ~90 STARTs). Instead the
        // newest instance wins: supersede every OTHER live companion so old
        // instances finish and their tasks are reaped. Application
        // onActivityCreated fires after onCreate, so liveCompanions() here
        // can only hold previously created instances.
        app.liveCompanions().filter { it !== this && !it.isFinishing }
            .forEach { it.closeQuietly() }
        // SECONDARY_HOME starts land on whichever display is focused. This
        // activity belongs on display 1; when it arrives anywhere else,
        // relaunch it there and close the misplaced task. MULTIPLE_TASK is
        // required HERE: a plain NEW_TASK start would resolve to this
        // instance's own task (singleInstance task reuse) and never move
        // displays. Duplicates spawned by the new task converge through the
        // supersede rule above.
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
}
