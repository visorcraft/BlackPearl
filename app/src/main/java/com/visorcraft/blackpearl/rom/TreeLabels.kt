package com.visorcraft.blackpearl.rom

import java.net.URLDecoder

/** Human-readable labels for SAF tree URIs. Pure string logic, host-testable. */
object TreeLabels {

    /**
     * `content://…/tree/7F7E-2949%3Aroms` → "roms (SD card)";
     * `…/tree/primary%3AEmulation%2FROMs` → "ROMs". Non-primary volumes are
     * removable, hence the "SD card" suffix.
     */
    fun label(treeUri: String): String {
        val raw = treeUri.substringAfter("/tree/", "")
        if (raw.isEmpty()) return treeUri
        val decoded = runCatching { URLDecoder.decode(raw, "UTF-8") }
            .getOrDefault(raw)
        val volume = decoded.substringBefore(':', "")
        if (volume.isEmpty()) return treeUri
        val path = decoded.substringAfter(':', "")
        val segment = path.trimEnd('/').substringAfterLast('/').ifEmpty { volume }
        return if (volume == "primary") segment else "$segment (SD card)"
    }
}
