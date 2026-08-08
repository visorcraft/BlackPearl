package com.visorcraft.ghostgalleon.ui.deck

import android.app.AlertDialog
import android.content.Context
import android.widget.EditText
import com.visorcraft.ghostgalleon.GhostGalleonApp
import com.visorcraft.ghostgalleon.library.CollectionsOps
import com.visorcraft.ghostgalleon.library.LibraryBrowse
import com.visorcraft.ghostgalleon.ui.toast

/** Shared “Add to collection” picker for Grid and Game decks. */
object CollectionDialogs {

    fun promptAdd(
        context: Context,
        app: GhostGalleonApp,
        keys: List<String>,
        onDone: (() -> Unit)? = null,
    ) {
        if (keys.isEmpty()) {
            context.toast("Nothing selected")
            return
        }
        val live = app.settings
        val names = LibraryBrowse.presentCollectionRails(live.collections).toMutableList()
        names.add(0, "+ New collection")
        AlertDialog.Builder(context)
            .setTitle("Add to collection")
            .setItems(names.toTypedArray()) { _, which ->
                if (which == 0) {
                    val input = EditText(context).apply { hint = "Name" }
                    AlertDialog.Builder(context)
                        .setTitle("New collection")
                        .setView(input)
                        .setPositiveButton("Create") { _, _ ->
                            val name = input.text?.toString().orEmpty()
                            var cols = CollectionsOps.createCollection(live.collections, name)
                            cols = CollectionsOps.bulkAddToCollection(cols, name, keys)
                            app.updateSettings(app.settings.copy(collections = cols))
                            onDone?.invoke()
                            context.toast("Added to $name")
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                } else {
                    val name = names[which]
                    val cols = CollectionsOps.bulkAddToCollection(
                        live.collections, name, keys,
                    )
                    app.updateSettings(app.settings.copy(collections = cols))
                    onDone?.invoke()
                    context.toast("Added to $name")
                }
            }
            .show()
    }
}
