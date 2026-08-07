package com.visorcraft.ghostgalleon.settings

import com.visorcraft.ghostgalleon.state.UIMode
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class SettingsStore(private val file: File) {

    fun load(): Settings {
        if (!file.exists()) return Settings.DEFAULT
        return try {
            val o = JSONObject(file.readText())
            val s = parse(o)
            // Persist migrations immediately: any file older than the
            // current schema is re-saved with the new stamp.
            if (o.optInt("schemaVersion", 1) < CURRENT_SCHEMA) save(s)
            s
        } catch (e: Exception) {
            Settings.DEFAULT
        }
    }

    fun save(s: Settings) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(toJson(s).toString(2))
        if (!tmp.renameTo(file)) {
            tmp.copyTo(file, overwrite = true)
            tmp.delete()
        }
    }

    companion object {
        const val CURRENT_SCHEMA = 6

        // Internal (not private) so SettingsBundle can pack/unpack settings
        // through the exact same codec the on-disk file uses; same-module
        // host tests cover the round-trip.
        internal fun parse(o: JSONObject): Settings {
        val schemaVersion = o.optInt("schemaVersion", 1)
        val keyMapObj = o.optJSONObject("keyMap")
        val keyMap = if (keyMapObj != null) {
            keyMapObj.keys().asSequence().associate { k ->
                k.toInt() to Action.valueOf(keyMapObj.getString(k))
            }
        } else Settings.DEFAULT_KEY_MAP
        return Settings(
            theme = o.optString("theme", "dark"),
            accentColor = o.optLong("accentColor", 0xFF3F51B5).toInt(),
            background = o.optString("background", "solid"),
            gridColumns = o.optInt("gridColumns", 5),
            iconSizeDp = o.optInt("iconSizeDp", 72),
            cardSizeDp = o.optInt("cardSizeDp", 200),
            animationMs = o.optInt("animationMs", 200),
            defaultMode = UIMode.valueOf(o.optString("defaultMode", "GRID")),
            primaryDisplay = if (schemaVersion < 2) 1 else o.optInt("primaryDisplay", 1),
            gyroEnabled = o.optBoolean("gyroEnabled", true),
            angleLock = o.optBoolean("angleLock", false),
            haptics = o.optBoolean("haptics", true),
            showHints = o.optBoolean("showHints", true),
            showLabels = o.optBoolean("showLabels", true),
            // Added within schema v3: files without the field keep the
            // vertical default, no schema bump needed.
            gridDirection = o.optString("gridDirection", "vertical"),
            wallpaperUri = if (!o.isNull("wallpaperUri") && o.has("wallpaperUri")) {
                o.getString("wallpaperUri")
            } else {
                null
            },
            // Added within schema v3: files without the field have no ROM
            // folder grants yet, no schema bump needed.
            romTreeUris = o.optJSONArray("romTreeUris").toStringList(),
            // Added within schema v3: files without the field simply have no
            // SteamGridDB key yet, no schema bump needed.
            sgdbApiKey = if (!o.isNull("sgdbApiKey") && o.has("sgdbApiKey")) {
                o.getString("sgdbApiKey")
            } else {
                null
            },
            // v6 makes the dock auto-growing (capacity 9, visible slots
            // derived at render) and stores only the filled keys in slot
            // order. Older files — v4/v5 fixed 5-slot arrays (nulls
            // included) and v3 dockPackages — collapse to their filled
            // keys in order via DockSlots.compact.
            dockSlots = DockSlots.compact(
                if (o.has("dockSlots")) {
                    o.getJSONArray("dockSlots").toNullableStringList()
                } else {
                    o.optJSONArray("dockPackages").toStringList()
                }
            ),
            // v3 introduces the curated grid. Older files have no gridSlots
            // and migrate to a fully blank grid (the all-apps grid is gone).
            gridSlots = if (schemaVersion >= 3 && o.has("gridSlots")) {
                o.getJSONArray("gridSlots").toNullableStringList()
            } else {
                GridSlots.blank()
            },
            hiddenPackages = o.optJSONArray("hiddenPackages").toStringList().toSet(),
            // Added within schema v3: absent = no per-app overrides.
            customNames = o.optJSONObject("customNames").toStringMap(),
            customIcons = o.optJSONObject("customIcons").toStringMap(),
            keyMap = keyMap,
            // Schema v5 library/play/collections (absent = empty defaults).
            lastLaunchedMs = o.optJSONObject("lastLaunchedMs").toLongMap(),
            playtimeMs = o.optJSONObject("playtimeMs").toLongMap(),
            defaultPlayers = o.optJSONObject("defaultPlayers").toStringMap(),
            artOverrides = o.optJSONObject("artOverrides").toStringMap(),
            favorites = o.optJSONArray("favorites").toStringList().toSet(),
            collections = o.optJSONObject("collections").toStringListMap(),
            schemaVersion = CURRENT_SCHEMA,
        )
        }

        internal fun toJson(s: Settings): JSONObject {
        val keyMapObj = JSONObject()
        s.keyMap.forEach { (code, action) -> keyMapObj.put(code.toString(), action.name) }
        return JSONObject()
            .put("theme", s.theme)
            .put("accentColor", s.accentColor.toLong() and 0xFFFFFFFFL)
            .put("background", s.background)
            .put("gridColumns", s.gridColumns)
            .put("iconSizeDp", s.iconSizeDp)
            .put("cardSizeDp", s.cardSizeDp)
            .put("animationMs", s.animationMs)
            .put("defaultMode", s.defaultMode.name)
            .put("primaryDisplay", s.primaryDisplay)
            .put("gyroEnabled", s.gyroEnabled)
            .put("angleLock", s.angleLock)
            .put("haptics", s.haptics)
            .put("showHints", s.showHints)
            .put("showLabels", s.showLabels)
            .put("gridDirection", s.gridDirection)
            .put("wallpaperUri", s.wallpaperUri ?: JSONObject.NULL)
            .put("romTreeUris", JSONArray(s.romTreeUris))
            .put("sgdbApiKey", s.sgdbApiKey ?: JSONObject.NULL)
            // v6: persist only the filled dock keys in slot order; blanks
            // are derived at render (visibleCount = max(4, min(filled+1, 9))).
            .put("dockSlots", JSONArray(DockSlots.filled(s.dockSlots)))
            .put("gridSlots", JSONArray().apply {
                s.gridSlots.forEach { put(it ?: JSONObject.NULL) }
            })
            .put("hiddenPackages", JSONArray(s.hiddenPackages.toList()))
            .put("customNames", JSONObject().apply {
                s.customNames.forEach { (pkg, name) -> put(pkg, name) }
            })
            .put("customIcons", JSONObject().apply {
                s.customIcons.forEach { (pkg, uri) -> put(pkg, uri) }
            })
            .put("keyMap", keyMapObj)
            .put("lastLaunchedMs", JSONObject().apply {
                s.lastLaunchedMs.forEach { (k, v) -> put(k, v) }
            })
            .put("playtimeMs", JSONObject().apply {
                s.playtimeMs.forEach { (k, v) -> put(k, v) }
            })
            .put("defaultPlayers", JSONObject().apply {
                s.defaultPlayers.forEach { (k, v) -> put(k, v) }
            })
            .put("artOverrides", JSONObject().apply {
                s.artOverrides.forEach { (k, v) -> put(k, v) }
            })
            .put("favorites", JSONArray(s.favorites.toList()))
            .put("collections", JSONObject().apply {
                s.collections.forEach { (name, keys) ->
                    put(name, JSONArray(keys))
                }
            })
            .put("schemaVersion", CURRENT_SCHEMA)
        }

        private fun JSONArray?.toStringList(): List<String> {
            if (this == null) return emptyList()
            return (0 until length()).map { getString(it) }
        }

        private fun JSONObject?.toStringMap(): Map<String, String> {
            if (this == null) return emptyMap()
            return keys().asSequence().associateWith { getString(it) }
        }

        private fun JSONObject?.toLongMap(): Map<String, Long> {
            if (this == null) return emptyMap()
            return keys().asSequence().associateWith { getLong(it) }
        }

        private fun JSONObject?.toStringListMap(): Map<String, List<String>> {
            if (this == null) return emptyMap()
            return keys().asSequence().associateWith { k ->
                val arr = optJSONArray(k)
                if (arr == null) emptyList()
                else (0 until arr.length()).map { arr.getString(it) }
            }
        }

        private fun JSONArray.toNullableStringList(): List<String?> =
            (0 until length()).map { if (isNull(it)) null else getString(it) }
    }
}
