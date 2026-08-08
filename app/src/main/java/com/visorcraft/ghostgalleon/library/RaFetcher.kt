package com.visorcraft.ghostgalleon.library

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Thin RetroAchievements HTTP fetch. Failures return null / empty (never throw).
 * Host tests use [fetchUrl] seam with canned responses.
 *
 * Title resolve uses the official game list for a console id (`i=`), then
 * client-side title match. (`f=` is "games with achievements only", not a
 * title filter — do not encode titles into `f`.)
 */
object RaFetcher {

    /**
     * Progress for a known game id.
     * GET API_GetGameInfoAndUserProgress.php?z=user&y=key&g=gameId
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

    /**
     * Official game list for a console. [consoleId] is the RA system id
     * (e.g. 3 = SNES). `f=1` limits to games that have achievements.
     */
    fun gameListUrl(apiKey: String, consoleId: Int): String {
        val k = URLEncoder.encode(apiKey.trim(), "UTF-8")
        return "https://retroachievements.org/API/API_GetGameList.php?y=$k&i=$consoleId&f=1"
    }

    /**
     * Map Ghost Galleon [platformId] to a RetroAchievements console id.
     * Null when the platform has no reliable mapping.
     */
    fun consoleIdForPlatform(platformId: String?): Int? = when (platformId?.lowercase()) {
        "nes" -> 7
        "snes" -> 3
        "n64" -> 2
        "gb" -> 4
        "gbc" -> 6
        "gba" -> 5
        "nds" -> 18
        "genesis", "megadrive" -> 1
        "ps1" -> 12
        "psp" -> 41
        "pcengine", "tg16" -> 8
        "dreamcast" -> 40
        "arcade" -> 27
        "saturn" -> 39
        else -> null
    }

    /**
     * Fetch and parse progress. [fetchUrl] injects HTTP for host tests.
     * When [gameId] is null, resolves via console game list + title match
     * using [platformId] / [titleHint].
     */
    fun fetchProgress(
        username: String,
        apiKey: String,
        gameId: Int?,
        titleHint: String?,
        platformId: String? = null,
        fetchUrl: (String) -> String? = ::httpGet,
    ): RaProgress {
        if (username.isBlank() || apiKey.isBlank()) return RaProgress()
        val id = gameId
            ?: resolveGameId(apiKey, titleHint, platformId, fetchUrl)
            ?: return RaProgress()
        val body = fetchUrl(progressUrl(username, apiKey, id)) ?: return RaProgress()
        return RetroAchievements.parseProgress(body)
    }

    /**
     * Pick the first game whose Title contains [titleHint] (case-insensitive).
     * Prefer exact (ignore case) matches over substring.
     */
    fun parseGameIdMatchingTitle(json: String?, titleHint: String?): Int? {
        if (json.isNullOrBlank() || titleHint.isNullOrBlank()) return null
        val needle = titleHint.trim().lowercase()
        if (needle.isEmpty()) return null
        return try {
            val arr = org.json.JSONArray(json.trim().let {
                if (it.startsWith("[")) it else "[]"
            })
            var substringId: Int? = null
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val title = o.optString("Title", o.optString("title", "")).trim()
                if (title.isEmpty()) continue
                val id = when {
                    o.has("ID") -> o.optInt("ID")
                    o.has("id") -> o.optInt("id")
                    else -> 0
                }.takeIf { it > 0 } ?: continue
                val lower = title.lowercase()
                if (lower == needle) return id
                if (substringId == null && lower.contains(needle)) substringId = id
            }
            substringId
        } catch (_: Exception) {
            null
        }
    }

    /** @deprecated Prefer [parseGameIdMatchingTitle]; kept for older tests. */
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
        platformId: String?,
        fetchUrl: (String) -> String?,
    ): Int? {
        val title = titleHint?.trim().orEmpty()
        if (title.isEmpty()) return null
        val consoleId = consoleIdForPlatform(platformId) ?: return null
        val body = fetchUrl(gameListUrl(apiKey, consoleId)) ?: return null
        return parseGameIdMatchingTitle(body, title)
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
