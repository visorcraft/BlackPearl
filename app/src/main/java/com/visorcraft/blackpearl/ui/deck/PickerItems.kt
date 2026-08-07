package com.visorcraft.blackpearl.ui.deck

import com.visorcraft.blackpearl.library.AppEntry
import com.visorcraft.blackpearl.rom.Platforms
import com.visorcraft.blackpearl.rom.RomEntry

// Pure row model for the picker list: an "Apps" section followed by a
// "ROMs" section, each headed by a dim header row (omitted when empty).
// The search query filters both sections. Host-tested in PickerItemsTest;
// the Android list adapter in AppPicker only renders these.
sealed interface PickerItem {
    data class Header(val title: String) : PickerItem
    data class App(val entry: AppEntry) : PickerItem
    data class Rom(val entry: RomEntry) : PickerItem
}

object PickerItems {

    const val APPS_HEADER = "Apps"
    const val ROMS_HEADER = "ROMs"

    /** ROM display order everywhere (picker, carousel): platform display
     *  name, then ROM name, both case-insensitive. Entries flagged
     *  `visibleInUi = false` (deduped Switch updates/DLC) are excluded —
     *  they stay in the library but never appear in UI lists. */
    fun sortedRoms(roms: List<RomEntry>): List<RomEntry> = roms
        .filter { it.visibleInUi }
        .sortedWith(
            compareBy(
                { Platforms.byId(it.platformId)?.displayName ?: it.platformId },
                { it.name.lowercase() },
            )
        )

    fun build(
        apps: List<AppEntry>,
        roms: List<RomEntry>,
        query: String,
    ): List<PickerItem> {
        val q = query.trim()
        val matchedApps = if (q.isEmpty()) {
            apps
        } else {
            apps.filter {
                it.label.contains(q, ignoreCase = true) ||
                    it.packageName.contains(q, ignoreCase = true)
            }
        }
        val matchedRoms = if (q.isEmpty()) {
            sortedRoms(roms)
        } else {
            sortedRoms(roms).filter {
                it.name.contains(q, ignoreCase = true) ||
                    (Platforms.byId(it.platformId)?.displayName
                        ?.contains(q, ignoreCase = true) == true)
            }
        }
        val items = mutableListOf<PickerItem>()
        if (matchedApps.isNotEmpty()) {
            items += PickerItem.Header(APPS_HEADER)
            matchedApps.forEach { items += PickerItem.App(it) }
        }
        if (matchedRoms.isNotEmpty()) {
            items += PickerItem.Header(ROMS_HEADER)
            matchedRoms.forEach { items += PickerItem.Rom(it) }
        }
        return items
    }
}
