package com.visorcraft.blackpearl.rom

import java.net.URLDecoder

/**
 * One scanned ROM. `id` is stable: "<platformId>:<relativePath>" — it
 * survives rescans as long as the file's path under its tree root does.
 * `uri` is the SAF document URI string; `path` is the raw filesystem path
 * reconstructed from the document id when the tree sits on a known storage
 * volume (needed by path-only players like RetroArch), else null.
 * `artUri` is the SAF document URI of locally discovered box art (a
 * sibling `images/`/`media/`/`art/` file matched by stem during the scan),
 * else null. `visibleInUi` is false for Switch update/DLC entries whose
 * base game is also in the library (see SwitchDedupe): they stay stored
 * and addressable by id but are excluded from the picker/carousel lists.
 */
data class RomEntry(
    val id: String,
    val name: String,
    val platformId: String,
    val uri: String,
    val path: String?,
    val artUri: String? = null,
    val visibleInUi: Boolean = true,
    /** Optional description from local gamelist.xml (offline meta). */
    val description: String? = null,
)

/** Pure helpers for SAF ExternalStorageProvider document-id shapes. */
object StoragePaths {

    /**
     * URL-decoded document id ("7F7E-2949:roms/snes/x.smc",
     * "primary:Emulation/ROMs") from a document or tree URI string, or null
     * for foreign providers.
     */
    fun documentId(uri: String): String? {
        val raw = uri.substringAfter("/document/", uri.substringAfter("/tree/", ""))
        if (raw.isEmpty()) return null
        return runCatching { URLDecoder.decode(raw, "UTF-8") }.getOrNull()
    }

    /**
     * Root folder name of a tree URI: "…/tree/7F7E-2949%3Aroms" → "roms",
     * "…/tree/primary%3AEmulation%2FROMs" → "ROMs". Null when unknown.
     */
    fun treeRootName(treeUri: String): String? {
        val id = documentId(treeUri) ?: return null
        return id.substringAfter(':', "").trimEnd('/')
            .substringAfterLast('/').ifEmpty { null }
    }

    /**
     * Raw filesystem path for a document URI on a known volume:
     * "primary:Emulation/ROMs/x.gba" → "/storage/emulated/0/Emulation/ROMs/x.gba";
     * "7F7E-2949:roms/snes/x.smc" → "/storage/7F7E-2949/roms/snes/x.smc".
     * Null when the URI is not an ExternalStorageProvider document URI.
     */
    fun filesystemPath(documentUri: String): String? {
        val id = documentId(documentUri) ?: return null
        val volume = id.substringBefore(':', "")
        val rel = id.substringAfter(':', "")
        if (volume.isEmpty() || rel.isEmpty()) return null
        val root = if (volume == "primary") "/storage/emulated/0" else "/storage/$volume"
        return "$root/$rel"
    }
}
