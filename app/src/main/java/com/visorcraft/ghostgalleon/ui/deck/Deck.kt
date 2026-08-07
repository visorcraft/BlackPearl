package com.visorcraft.ghostgalleon.ui.deck

import android.app.Activity
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.visorcraft.ghostgalleon.rom.RomEntry
import com.visorcraft.ghostgalleon.rom.RomLauncher
import com.visorcraft.ghostgalleon.rom.RomProfiles
import com.visorcraft.ghostgalleon.settings.Action
import com.visorcraft.ghostgalleon.settings.SlotKey
import com.visorcraft.ghostgalleon.state.DeckState

interface Deck {
    fun primaryView(context: Context): View

    // Applies a selection-only change to the already-built primary view
    // (ring/scale move, page dots, scroll alignment). Returns false when no
    // live view state exists to update — the caller then does a full rebuild.
    fun updateSelection(): Boolean

    fun handleAction(action: Action): Boolean
}

// Dual-screen launch model: apps open on the topology launch display
// (non-interactive panel) when dual; same-display fallback for single.
internal fun launchOnOtherDisplay(activity: Activity, state: DeckState, intent: Intent) {
    val app = activity.application as? com.visorcraft.ghostgalleon.GhostGalleonApp
    val topo = app?.displayConfig
    val launchId = topo?.launchDisplayId
        ?: run {
            // Soft fallback without topology: any display that is not primary.
            val dm = activity.getSystemService(DisplayManager::class.java)
            dm.displays.map { it.displayId }
                .firstOrNull { it != state.primaryDisplayId }
        }
    val dm = activity.getSystemService(DisplayManager::class.java)
    val current = activity.display?.displayId
    if (launchId != null &&
        launchId != current &&
        dm.displays.any { it.displayId == launchId }
    ) {
        val options = ActivityOptions.makeBasic().setLaunchDisplayId(launchId)
        activity.startActivity(intent, options.toBundle())
    } else {
        activity.startActivity(intent)
    }
}

// Shared slot/dock launch path: ROM references go through RomLauncher (the
// platform template fires on the non-interactive display), app packages
// through their launcher intent. A ROM that dropped out of the library
// toasts instead of launching. [playerId] forces Open-with; otherwise the
// platform's settings default is used. Records play sessions via noteLaunch.
internal fun launchSlotKey(
    activity: AppCompatActivity,
    state: DeckState,
    roms: List<RomEntry>,
    key: String,
    playerId: String? = null,
) {
    // Folder tiles are opened by GridDeck (member list), never launched.
    if (SlotKey.isFolder(key)) return
    val app = activity.application as? com.visorcraft.ghostgalleon.GhostGalleonApp
    if (SlotKey.isRom(key)) {
        val id = SlotKey.romId(key)
        val entry = roms.firstOrNull { it.id == id }
        if (entry != null) {
            val settings = app?.settings
            val preferred = RomProfiles.preferredPlayerId(
                entry.id,
                settings?.romProfiles.orEmpty(),
                settings?.defaultPlayers?.get(entry.platformId),
            )
            val ok = RomLauncher.launch(
                activity, state, entry,
                playerId = playerId,
                preferredPlayerId = preferred,
            )
            if (ok) app?.noteLaunch(key)
        } else {
            Toast.makeText(activity, "ROM no longer in library", Toast.LENGTH_SHORT).show()
        }
        return
    }
    activity.packageManager.getLaunchIntentForPackage(key)
        ?.let {
            launchOnOtherDisplay(activity, state, it)
            app?.noteLaunch(key)
        }
}
