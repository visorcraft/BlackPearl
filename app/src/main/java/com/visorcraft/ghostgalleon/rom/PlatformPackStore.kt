package com.visorcraft.ghostgalleon.rom

import java.io.File

/**
 * Persists the last successfully imported platform pack JSON beside app files
 * and installs it into [Platforms] overlay. Pure file IO; host-testable via
 * TemporaryFolder.
 */
class PlatformPackStore(private val file: File) {

    fun loadIntoRegistry(): Boolean {
        if (!file.exists()) {
            Platforms.clearPackOverlay()
            return false
        }
        val text = runCatching { file.readText() }.getOrNull() ?: return false
        val parsed = PlatformPack.parse(text) ?: run {
            Platforms.clearPackOverlay()
            return false
        }
        Platforms.setPackOverlay(parsed.platforms)
        return true
    }

    /**
     * Validate [json], persist on success, install overlay. Returns the
     * parse result or null when rejected (registry left unchanged on reject).
     */
    fun importJson(json: String): PlatformPack.ParseResult? {
        val parsed = PlatformPack.parse(json) ?: return null
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(json)
        if (!tmp.renameTo(file)) {
            tmp.copyTo(file, overwrite = true)
            tmp.delete()
        }
        Platforms.setPackOverlay(parsed.platforms)
        return parsed
    }

    fun clear() {
        if (file.exists()) file.delete()
        Platforms.clearPackOverlay()
    }

    fun hasPack(): Boolean = file.exists()
}
