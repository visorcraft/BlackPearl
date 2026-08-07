package com.visorcraft.ghostgalleon.settings

// Grid slot / dock values are app package names, ROM references
// ("rom:<entry id>"), or folder tiles ("folder:<id>"). Pure helpers so every
// UI layer parses them the same way; host-tested in SlotKeyTest.
object SlotKey {

    const val ROM_PREFIX = "rom:"
    const val FOLDER_PREFIX = Folders.FOLDER_PREFIX

    fun rom(entryId: String): String = ROM_PREFIX + entryId

    fun folder(folderId: String): String = Folders.key(folderId)

    fun isRom(key: String?): Boolean = key?.startsWith(ROM_PREFIX) == true

    fun isFolder(key: String?): Boolean = Folders.isFolder(key)

    /** The ROM entry id inside a "rom:<id>" key, else null. */
    fun romId(key: String?): String? =
        key?.takeIf { isRom(it) }
            ?.removePrefix(ROM_PREFIX)
            ?.takeIf { it.isNotEmpty() }

    fun folderId(key: String?): String? = Folders.folderId(key)

    /**
     * The platform id inside a "rom:<platformId>:<path>" key (entry ids are
     * `platformId:relativePath`, see RomScanner), else null. Works even when
     * the entry is no longer in the library — that is exactly when the grid
     * needs it, to tint the "Missing" tile.
     */
    fun platformIdOf(key: String?): String? =
        romId(key)?.substringBefore(':', "")?.takeIf { it.isNotEmpty() }
}
