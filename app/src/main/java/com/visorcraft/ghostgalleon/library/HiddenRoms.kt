package com.visorcraft.ghostgalleon.library

import com.visorcraft.ghostgalleon.rom.RomEntry

/**
 * User-hidden ROM entry ids (Settings.hiddenRomIds). Pure; host-tested.
 * Distinct from SwitchDedupe's [RomEntry.visibleInUi] flag.
 */
object HiddenRoms {

    fun hide(hiddenIds: Set<String>, romId: String): Set<String> {
        val id = romId.trim()
        if (id.isEmpty()) return hiddenIds
        return hiddenIds + id
    }

    fun unhide(hiddenIds: Set<String>, romId: String): Set<String> =
        hiddenIds - romId.trim()

    fun isHidden(hiddenIds: Set<String>, romId: String): Boolean =
        romId in hiddenIds

    /**
     * True when the ROM may appear in browse/picker lists: scanner-visible
     * and not user-hidden.
     */
    fun isListed(rom: RomEntry, hiddenIds: Set<String>): Boolean =
        rom.visibleInUi && rom.id !in hiddenIds

    fun listed(roms: List<RomEntry>, hiddenIds: Set<String>): List<RomEntry> =
        roms.filter { isListed(it, hiddenIds) }
}
