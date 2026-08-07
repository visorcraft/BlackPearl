package com.visorcraft.blackpearl.art

import com.visorcraft.blackpearl.rom.RomEntry

/**
 * Effective local art URI for a ROM: explicit user override wins, else the
 * scanner-discovered [RomEntry.artUri]. Pure; host-tested.
 */
object ArtOverride {

    fun effectiveArtUri(entry: RomEntry, overrides: Map<String, String>): String? =
        overrides[entry.id]?.takeIf { it.isNotBlank() } ?: entry.artUri

    fun setOverride(
        overrides: Map<String, String>,
        romId: String,
        uri: String,
    ): Map<String, String> = overrides + (romId to uri)

    fun clearOverride(overrides: Map<String, String>, romId: String): Map<String, String> =
        overrides - romId

    /**
     * Normalize a stem for local art matching (lowercase, strip common
     * region tags in parentheses for fuzzy local match helpers).
     */
    fun normalizeStem(name: String): String {
        val stripped = name
            .replace(Regex("""\s*[\(\[].*?[\)\]]\s*"""), " ")
            .trim()
            .lowercase()
        return stripped.replace(Regex("""\s+"""), " ")
    }
}
