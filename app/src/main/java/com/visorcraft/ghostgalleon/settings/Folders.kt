package com.visorcraft.ghostgalleon.settings

/**
 * Curated-grid folder tiles. Slot keys use [FOLDER_PREFIX] + folder id.
 * Members are ordered slot keys (packages or rom: ids). Pure; host-tested.
 */
data class FolderSpec(
    val id: String,
    val name: String,
    val members: List<String> = emptyList(),
)

object Folders {

    const val FOLDER_PREFIX = "folder:"

    fun key(folderId: String): String = FOLDER_PREFIX + folderId

    fun isFolder(key: String?): Boolean =
        key != null &&
            key.startsWith(FOLDER_PREFIX) &&
            key.length > FOLDER_PREFIX.length

    fun folderId(key: String?): String? =
        key?.takeIf { isFolder(it) }
            ?.removePrefix(FOLDER_PREFIX)
            ?.takeIf { it.isNotEmpty() }

    fun create(
        folders: Map<String, FolderSpec>,
        id: String,
        name: String,
        members: List<String> = emptyList(),
    ): Map<String, FolderSpec> {
        val fid = id.trim()
        if (fid.isEmpty()) return folders
        val label = name.trim().ifEmpty { fid }
        return folders + (fid to FolderSpec(fid, label, members.filter { it.isNotBlank() }.distinct()))
    }

    fun rename(
        folders: Map<String, FolderSpec>,
        id: String,
        name: String,
    ): Map<String, FolderSpec> {
        val existing = folders[id] ?: return folders
        val label = name.trim().ifEmpty { return folders }
        return folders + (id to existing.copy(name = label))
    }

    fun delete(folders: Map<String, FolderSpec>, id: String): Map<String, FolderSpec> =
        folders - id

    fun members(folders: Map<String, FolderSpec>, id: String): List<String> =
        folders[id]?.members.orEmpty()

    fun addMember(
        folders: Map<String, FolderSpec>,
        id: String,
        memberKey: String,
    ): Map<String, FolderSpec> {
        val existing = folders[id] ?: return folders
        val k = memberKey.trim()
        if (k.isEmpty() || k in existing.members) return folders
        return folders + (id to existing.copy(members = existing.members + k))
    }

    fun removeMember(
        folders: Map<String, FolderSpec>,
        id: String,
        memberKey: String,
    ): Map<String, FolderSpec> {
        val existing = folders[id] ?: return folders
        val next = existing.members.filter { it != memberKey }
        return folders + (id to existing.copy(members = next))
    }

    fun setMembers(
        folders: Map<String, FolderSpec>,
        id: String,
        members: List<String>,
    ): Map<String, FolderSpec> {
        val existing = folders[id] ?: return folders
        return folders + (id to existing.copy(
            members = members.filter { it.isNotBlank() }.distinct(),
        ))
    }

    /** New unique folder id: lowest free `fN` starting at 1. */
    fun nextId(folders: Map<String, FolderSpec>): String {
        var n = 1
        while ("f$n" in folders) n++
        return "f$n"
    }
}
