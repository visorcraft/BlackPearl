package com.visorcraft.ghostgalleon.library

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Thin RetroAchievements HTTP fetch. Failures return null (never throw).
 * Host tests use [fetchUrl] seam with canned responses.
 */
object RaFetcher {

    /**
     * Build the Web API URL for a game progress query by title search.
     * Official RA Web API: API_GetGameInfoAndUserProgress requires game id;
     * for title lookup we use API_GetGameList is too heavy — use
     * API_GetGameInfoAndUserProgress when [gameId] known, else
     * API_GetUserRecentAchievements is wrong. We use extended game search:
     * `API_GetGame` is not public; instead use hash of progress endpoint
     * with gameID when known.
     *
     * Practical approach used here:
     * GET https://retroachievements.org/API/API_GetGameInfoAndUserProgress.php
     *   ?z=user&y=key&g=gameId
     * When only a title is known, [fetchProgressByTitle] hits a lightweight
     * search endpoint shape that tests can stub.
     */
    fun progressUrl(
        username: String,
        apiKey: String,
        gameId: Int,
    ): String {
        val u = URLEncoder.encode(username.trim(), "UTF-8")
        val k = URLEncoder.encode(apiKey.trim(), "UTF-8")
        return "https://retroachievements.org/API/API_GetGameInfoAndUserProgress.php?z=$u&y=$k&g=$gameId"
    }

    fun searchGameUrl(apiKey: String, title: String): String {
        val k = URLEncoder.encode(apiKey.trim(), "UTF-8")
        val t = URLEncoder.encode(title.trim(), "UTF-8")
        return "https://retroachievements.org/API/API_GetGameList.php?y=$k&i=1&f=$t"
    }

    /**
     * Fetch and parse progress. [fetchUrl] injects HTTP for host tests.
     * Returns empty progress on any failure.
     */
    fun fetchProgress(
        username: String,
        apiKey: String,
        gameId: Int?,
        titleHint: String?,
        fetchUrl: (String) -> String? = ::httpGet,
    ): RaProgress {
        if (username.isBlank() || apiKey.isBlank()) return RaProgress()
        val id = gameId ?: resolveGameId(apiKey, titleHint, fetchUrl) ?: return RaProgress()
        val body = fetchUrl(progressUrl(username, apiKey, id)) ?: return RaProgress()
        return RetroAchievements.parseProgress(body)
    }

    /**
     * Extract first game ID from a game-list style JSON array response.
     */
    fun parseFirstGameId(json: String?): Int? {
        if (json.isNullOrBlank()) return null
        return try {
            val trimmed = json.trim()
            if (trimmed.startsWith("[")) {
                val arr = org.json.JSONArray(trimmed)
                if (arr.length() == 0) return null
                val o = arr.optJSONObject(0) ?: return null
                when {
                    o.has("ID") -> o.optInt("ID").takeIf { it > 0 }
                    o.has("id") -> o.optInt("id").takeIf { it > 0 }
                    else -> null
                }
            } else {
                val o = org.json.JSONObject(trimmed)
                o.optInt("ID", o.optInt("id", 0)).takeIf { it > 0 }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun resolveGameId(
        apiKey: String,
        titleHint: String?,
        fetchUrl: (String) -> String?,
    ): Int? {
        val title = titleHint?.trim().orEmpty()
        if (title.isEmpty()) return null
        val body = fetchUrl(searchGameUrl(apiKey, title)) ?: return null
        return parseFirstGameId(body)
    }

    private fun httpGet(url: String): String? {
        return try {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8_000
                readTimeout = 12_000
                requestMethod = "GET"
                instanceFollowRedirects = true
            }
            try {
                if (conn.responseCode !in 200..299) return null
                conn.inputStream.bufferedReader().use { it.readText() }
            } finally {
                conn.disconnect()
            }
        } catch (_: Exception) {
            null
        }
    }
}
