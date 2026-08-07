package com.visorcraft.ghostgalleon.settings

import org.json.JSONArray
import org.json.JSONObject

/**
 * Export/import bundle (Settings → Library): one JSON document holding the
 * settings.json object AND the rom_library.json array, so a single file
 * restores both stores. Pure pack/unpack — host-tested; the Android I/O
 * (SAF create/open document) lives in SettingsActivity.
 */
object SettingsBundle {

    const val BUNDLE_VERSION = 1

    fun pack(settingsJson: JSONObject, romLibraryJson: JSONArray): String =
        JSONObject()
            .put("bundle", "ghost-galleon-settings")
            .put("bundleVersion", BUNDLE_VERSION)
            .put("settings", settingsJson)
            .put("romLibrary", romLibraryJson)
            .toString(2)

    /**
     * Inverse of [pack]. Throws [IllegalArgumentException] on anything that
     * is not a well-formed bundle (bad JSON, wrong shape, missing parts) —
     * the caller toasts a rejection and leaves both stores untouched.
     * Structural validation only; codec-level validation happens when the
     * caller decodes the parts through SettingsStore/RomLibrary.
     */
    fun unpack(text: String): Pair<JSONObject, JSONArray> {
        val o = try {
            JSONObject(text)
        } catch (e: Exception) {
            throw IllegalArgumentException("not a JSON object", e)
        }
        val bid = o.optString("bundle")
        if (bid != "ghost-galleon-settings" && bid != "blackpearl-settings") {
            throw IllegalArgumentException("not a Ghost Galleon settings bundle")
        }
        val settings = o.optJSONObject("settings")
            ?: throw IllegalArgumentException("missing settings object")
        val romLibrary = o.optJSONArray("romLibrary")
            ?: throw IllegalArgumentException("missing romLibrary array")
        return settings to romLibrary
    }
}
