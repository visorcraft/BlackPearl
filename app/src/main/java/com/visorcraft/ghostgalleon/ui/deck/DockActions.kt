package com.visorcraft.ghostgalleon.ui.deck

import android.content.Context
import android.widget.Toast
import com.visorcraft.ghostgalleon.GhostGalleonApp
import com.visorcraft.ghostgalleon.settings.DockSlots

/**
 * Shared dock pin / unpin / persist for Grid and Game decks.
 * Callers supply [onSlots] so each deck can refresh its own dock bar.
 */
object DockActions {

    fun pin(
        context: Context,
        app: GhostGalleonApp,
        key: String,
        onSlots: (List<String?>, toast: String) -> Unit,
    ) {
        val result = DockSlots.pinKey(app.settings.dockSlots, key)
        when (result.status) {
            DockSlots.PinStatus.ALREADY ->
                Toast.makeText(context, "Already in dock", Toast.LENGTH_SHORT).show()
            DockSlots.PinStatus.FULL ->
                Toast.makeText(context, "Dock is full", Toast.LENGTH_SHORT).show()
            DockSlots.PinStatus.PINNED ->
                onSlots(result.slots, "Pinned to dock")
        }
    }

    fun unpin(
        context: Context,
        app: GhostGalleonApp,
        key: String,
        onSlots: (List<String?>, toast: String) -> Unit,
    ) {
        if (!DockSlots.containsKey(app.settings.dockSlots, key)) {
            Toast.makeText(context, "Not in dock", Toast.LENGTH_SHORT).show()
            return
        }
        onSlots(DockSlots.unpinKey(app.settings.dockSlots, key), "Unpinned from dock")
    }

    fun persist(
        context: Context,
        app: GhostGalleonApp,
        slots: List<String?>,
        toast: String? = null,
    ) {
        app.updateSettings(app.settings.copy(dockSlots = slots))
        toast?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }
}
