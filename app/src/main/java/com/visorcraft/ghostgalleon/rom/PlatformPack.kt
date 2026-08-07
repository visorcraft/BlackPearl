package com.visorcraft.ghostgalleon.rom

import android.content.Intent
import org.json.JSONArray
import org.json.JSONObject

/**
 * Importable platform/player pack (JSON). Pure parse + merge over built-in
 * [Platform] lists. Malformed input → null / empty without touching builtins.
 */
object PlatformPack {

    data class ParseResult(
        val platforms: List<Platform>,
        val schemaVersion: Int,
    )

    /**
     * Parse a pack document. Returns null when the root is not a JSON object,
     * platforms array is missing/empty of valid entries, or structure is
     * unusable. Partial bad player rows are skipped; a platform with zero
     * valid players after parse is dropped.
     */
    fun parse(json: String): ParseResult? {
        val root = try {
            JSONObject(json)
        } catch (_: Exception) {
            return null
        }
        val arr = root.optJSONArray("platforms") ?: return null
        val platforms = mutableListOf<Platform>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optString("id", "").trim()
            if (id.isEmpty()) continue
            val playersArr = o.optJSONArray("players") ?: JSONArray()
            val players = mutableListOf<PlayerTemplate>()
            for (j in 0 until playersArr.length()) {
                val p = playersArr.optJSONObject(j) ?: continue
                parsePlayer(p)?.let { players.add(it) }
            }
            if (players.isEmpty()) continue
            val folderNames = o.optJSONArray("folderNames").toStringList()
                .ifEmpty { listOf(id) }
            val extensions = o.optJSONArray("extensions").toStringList()
                .map { it.lowercase().removePrefix(".") }
                .filter { it.isNotEmpty() }
            if (extensions.isEmpty()) continue
            platforms.add(
                Platform(
                    id = id,
                    displayName = o.optString("displayName", id).ifBlank { id },
                    shortName = o.optString("shortName", id).ifBlank { id },
                    folderNames = folderNames,
                    extensions = extensions,
                    players = players,
                ),
            )
        }
        if (platforms.isEmpty()) return null
        return ParseResult(
            platforms = platforms,
            schemaVersion = root.optInt("schemaVersion", 1),
        )
    }

    /**
     * Merge [imported] into [builtins] by platform id. Pack players with the
     * same id replace built-ins; new player ids are prepended (preferred).
     * New platforms append after builtins. Built-ins are never deleted.
     */
    fun merge(builtins: List<Platform>, imported: List<Platform>): List<Platform> {
        if (imported.isEmpty()) return builtins
        val byId = builtins.associateBy { it.id }.toMutableMap()
        val newOrder = mutableListOf<String>()
        for (p in imported) {
            val existing = byId[p.id]
            if (existing == null) {
                byId[p.id] = p
                newOrder.add(p.id)
            } else {
                val mergedPlayers = LinkedHashMap<String, PlayerTemplate>()
                p.players.forEach { mergedPlayers[it.id] = it }
                existing.players.forEach { if (it.id !in mergedPlayers) mergedPlayers[it.id] = it }
                byId[p.id] = existing.copy(
                    displayName = p.displayName.ifBlank { existing.displayName },
                    shortName = p.shortName.ifBlank { existing.shortName },
                    folderNames = (p.folderNames + existing.folderNames).distinct(),
                    extensions = (p.extensions + existing.extensions)
                        .map { it.lowercase() }
                        .distinct(),
                    players = mergedPlayers.values.toList(),
                )
            }
        }
        val order = builtins.map { it.id } + newOrder
        return order.distinct().mapNotNull { byId[it] }
    }

    private fun parsePlayer(o: JSONObject): PlayerTemplate? {
        val id = o.optString("id", "").trim()
        val component = o.optString("component", "").trim()
        if (id.isEmpty() || component.isEmpty() || !component.contains('/')) return null
        val style = when (o.optString("uriStyle", "URI").uppercase()) {
            "PATH" -> UriStyle.PATH
            else -> UriStyle.URI
        }
        val extrasObj = o.optJSONObject("extras")
        val extras = if (extrasObj == null) {
            emptyMap()
        } else {
            extrasObj.keys().asSequence().associateWith { extrasObj.getString(it) }
        }
        val action = o.optString("action", "").ifBlank { null }
        val grantRead = if (o.has("grantRead")) o.getBoolean("grantRead") else style == UriStyle.URI
        val flags = if (o.has("flags")) {
            o.getInt("flags")
        } else {
            Intent.FLAG_ACTIVITY_NEW_TASK
        }
        return PlayerTemplate(
            id = id,
            displayName = o.optString("displayName", id).ifBlank { id },
            component = component,
            action = action,
            uriStyle = style,
            extras = extras,
            grantRead = grantRead,
            flags = flags,
        )
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { i ->
            if (isNull(i)) null else optString(i, "").trim().takeIf { it.isNotEmpty() }
        }
    }
}
