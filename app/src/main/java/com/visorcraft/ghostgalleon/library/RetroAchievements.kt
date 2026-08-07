package com.visorcraft.ghostgalleon.library

import org.json.JSONObject

/**
 * Optional RetroAchievements progress parse. Pure; host-tested.
 * Network transport is a thin Android/HttpURLConnection wrapper elsewhere;
 * this module only shapes canned JSON into [RaProgress].
 */
data class RaProgress(
    val gameId: Int? = null,
    val title: String? = null,
    val numAwarded: Int = 0,
    val numPossible: Int = 0,
    val userScore: Int? = null,
    val hardcore: Boolean = false,
) {
    val percent: Int
        get() = if (numPossible <= 0) 0
        else ((numAwarded * 100L) / numPossible).toInt().coerceIn(0, 100)

    val label: String
        get() = when {
            numPossible <= 0 -> "No achievements"
            else -> "$numAwarded / $numPossible ($percent%)"
        }

    val isEmpty: Boolean get() = numPossible <= 0 && title.isNullOrBlank()
}

object RetroAchievements {

    /**
     * Parse a game progress payload. Accepts shapes like:
     * `{ "ID":123, "Title":"…", "NumAwardedToUser":3, "NumAchievements":10,
     *    "UserCompletion":"30.00%", "HardcoreMode":0 }`
     * or nested under `"Game"` / `"game"`. Malformed → empty progress, never throws.
     */
    fun parseProgress(json: String?): RaProgress {
        if (json.isNullOrBlank()) return RaProgress()
        return try {
            val root = JSONObject(json)
            val o = when {
                root.has("Game") && root.optJSONObject("Game") != null ->
                    root.getJSONObject("Game")
                root.has("game") && root.optJSONObject("game") != null ->
                    root.getJSONObject("game")
                else -> root
            }
            val awarded = firstInt(o, "NumAwardedToUser", "NumAwarded", "awarded", "NumAwardedHardcore")
            val possible = firstInt(o, "NumAchievements", "NumPossibleAchievements", "possible", "achievements")
            val title = firstString(o, "Title", "title", "GameTitle")
            val id = firstInt(o, "ID", "GameID", "id").takeIf { it > 0 }
            val score = firstInt(o, "Score", "UserScore", "score").takeIf { it > 0 }
            val hardcore = when {
                o.has("HardcoreMode") -> o.optInt("HardcoreMode", 0) != 0 ||
                    o.optBoolean("HardcoreMode", false)
                o.has("hardcore") -> o.optBoolean("hardcore", false)
                else -> false
            }
            RaProgress(
                gameId = id,
                title = title,
                numAwarded = awarded.coerceAtLeast(0),
                numPossible = possible.coerceAtLeast(0),
                userScore = score,
                hardcore = hardcore,
            )
        } catch (_: Exception) {
            RaProgress()
        }
    }

    /** Format a short hero line, or null when empty / no credentials path. */
    fun heroLine(progress: RaProgress?, hasCredentials: Boolean): String? {
        if (!hasCredentials) return null
        if (progress == null || progress.isEmpty) return null
        return "RA ${progress.label}"
    }

    private fun firstInt(o: JSONObject, vararg keys: String): Int {
        for (k in keys) {
            if (!o.has(k) || o.isNull(k)) continue
            return when (val v = o.get(k)) {
                is Number -> v.toInt()
                is String -> v.toIntOrNull() ?: continue
                else -> continue
            }
        }
        return 0
    }

    private fun firstString(o: JSONObject, vararg keys: String): String? {
        for (k in keys) {
            if (!o.has(k) || o.isNull(k)) continue
            val s = o.optString(k, "").trim()
            if (s.isNotEmpty()) return s
        }
        return null
    }
}
