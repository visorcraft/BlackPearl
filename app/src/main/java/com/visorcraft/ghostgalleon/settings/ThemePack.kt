package com.visorcraft.ghostgalleon.settings

/**
 * Visual theme tokens applied across decks/settings. Pure; host-tested.
 * Built-in packs cover Ghost (current), 3DS teal, OLED pure black, Neon.
 */
data class ThemeTokens(
    val id: String,
    val displayName: String,
    val accentColor: Int,
    val cardRadiusDp: Int = 24,
    val panelLift: Int = 0xFF202028.toInt(),
    val chipIdle: Int = 0xFF2A2A32.toInt(),
    val heroRain: Boolean = true,
    val fontScale: Float = 1f,
)

object ThemePack {

    val GHOST = ThemeTokens(
        id = "ghost",
        displayName = "Ghost",
        accentColor = 0xFF3F51B5.toInt(),
        heroRain = true,
    )
    val THREEDS = ThemeTokens(
        id = "threeds",
        displayName = "3DS Teal",
        accentColor = 0xFF00A8A0.toInt(),
        panelLift = 0xFF0D2A2A.toInt(),
        chipIdle = 0xFF143838.toInt(),
        heroRain = false,
    )
    val OLED = ThemeTokens(
        id = "oled",
        displayName = "OLED Black",
        accentColor = 0xFFE0E0E0.toInt(),
        panelLift = 0xFF101010.toInt(),
        chipIdle = 0xFF1A1A1A.toInt(),
        cardRadiusDp = 12,
        heroRain = false,
    )
    val NEON = ThemeTokens(
        id = "neon",
        displayName = "Neon",
        accentColor = 0xFFFF2D95.toInt(),
        panelLift = 0xFF1A0A20.toInt(),
        chipIdle = 0xFF2A1038.toInt(),
        heroRain = true,
        fontScale = 1.05f,
    )

    val BUILTINS: List<ThemeTokens> = listOf(GHOST, THREEDS, OLED, NEON)

    fun byId(id: String?): ThemeTokens =
        BUILTINS.firstOrNull { it.id.equals(id?.trim(), ignoreCase = true) } ?: GHOST

    /**
     * Parse a theme pack JSON object. Accepts:
     * `{ "id":"…", "displayName":"…", "accentColor":"#FF2D95" or long,
     *    "cardRadiusDp":24, "panelLift":…, "chipIdle":…, "heroRain":true,
     *    "fontScale":1.0 }`
     * Returns null on unusable input.
     */
    fun parseJson(json: String): ThemeTokens? {
        val o = try {
            org.json.JSONObject(json)
        } catch (_: Exception) {
            return null
        }
        val id = o.optString("id", "").trim()
        if (id.isEmpty()) return null
        val accent = parseColor(o, "accentColor") ?: return null
        return ThemeTokens(
            id = id,
            displayName = o.optString("displayName", id).ifBlank { id },
            accentColor = accent,
            cardRadiusDp = o.optInt("cardRadiusDp", 24).coerceIn(4, 48),
            panelLift = parseColor(o, "panelLift") ?: 0xFF202028.toInt(),
            chipIdle = parseColor(o, "chipIdle") ?: 0xFF2A2A32.toInt(),
            heroRain = o.optBoolean("heroRain", true),
            fontScale = o.optDouble("fontScale", 1.0).toFloat().coerceIn(0.85f, 1.3f),
        )
    }

    /**
     * Apply [tokens] onto [base] settings: theme pack id + accent color.
     * Other visual knobs live on the tokens object for UI consumers.
     */
    fun applyToSettings(base: Settings, tokens: ThemeTokens): Settings =
        base.copy(
            themePackId = tokens.id,
            accentColor = tokens.accentColor,
            themeCustomJson = null,
        )

    /**
     * Apply imported custom tokens: store JSON for re-load and set id/accent.
     */
    fun applyCustom(base: Settings, tokens: ThemeTokens, rawJson: String): Settings =
        base.copy(
            themePackId = tokens.id,
            accentColor = tokens.accentColor,
            themeCustomJson = rawJson,
        )

    /** Resolve tokens for current settings (custom JSON wins when valid). */
    fun resolve(settings: Settings): ThemeTokens {
        settings.themeCustomJson?.let { raw ->
            parseJson(raw)?.let { return it }
        }
        return byId(settings.themePackId)
    }

    private fun parseColor(o: org.json.JSONObject, key: String): Int? {
        if (!o.has(key) || o.isNull(key)) return null
        return when (val v = o.get(key)) {
            is Number -> v.toLong().toInt()
            is String -> {
                val s = v.trim().removePrefix("#")
                when (s.length) {
                    6 -> ("FF$s").toLongOrNull(16)?.toInt()
                    8 -> s.toLongOrNull(16)?.toInt()
                    else -> null
                }
            }
            else -> null
        }
    }
}
