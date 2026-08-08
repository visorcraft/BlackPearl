package com.visorcraft.ghostgalleon.ui.deck

import android.content.Context
import com.visorcraft.ghostgalleon.GhostGalleonApp
import com.visorcraft.ghostgalleon.settings.DockSlots
import com.visorcraft.ghostgalleon.ui.toast

/**
 * Shared dock pin / unpin / persist / remove / fill for Grid and Game decks.
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
            DockSlots.PinStatus.ALREADY -> context.toast("Already in dock")
            DockSlots.PinStatus.FULL -> context.toast("Dock is full")
            DockSlots.PinStatus.PINNED -> onSlots(result.slots, "Pinned to dock")
        }
    }

    fun unpin(
        context: Context,
        app: GhostGalleonApp,
        key: String,
        onSlots: (List<String?>, toast: String) -> Unit,
    ) {
        if (!DockSlots.containsKey(app.settings.dockSlots, key)) {
            context.toast("Not in dock")
            return
        }
        onSlots(DockSlots.unpinKey(app.settings.dockSlots, key), "Unpinned from dock")
    }

    fun removeAt(
        app: GhostGalleonApp,
        index: Int,
    ): List<String?> = DockSlots.remove(app.settings.dockSlots, index)

    fun fill(
        app: GhostGalleonApp,
        slot: Int,
        key: String,
    ): List<String?> = DockSlots.fill(app.settings.dockSlots, slot, key)

    fun persist(
        context: Context,
        app: GhostGalleonApp,
        slots: List<String?>,
        toast: String? = null,
    ) {
        app.updateSettings(app.settings.copy(dockSlots = slots))
        toast?.let { context.toast(it) }
    }

    /** After remove, clamp dock focus to a still-visible slot. */
    fun clampFocus(focused: Int?, next: List<String?>): Int? {
        if (focused == null) return null
        val last = DockSlots.visibleCount(next) - 1
        return if (last < 0) null else focused.coerceAtMost(last)
    }
}
