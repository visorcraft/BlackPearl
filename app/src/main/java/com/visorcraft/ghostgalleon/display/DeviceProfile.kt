package com.visorcraft.ghostgalleon.display

/**
 * Optional device quirk profile. Pure match + catalog selection.
 */
data class ProfileMatch(
    val manufacturers: List<String> = emptyList(),
    val modelContains: List<String> = emptyList(),
    val deviceContains: List<String> = emptyList(),
    val minDisplays: Int? = null,
    val maxDisplays: Int? = null,
)

enum class PrimaryPreference {
    AUTO,
    DEFAULT_DISPLAY,
    SECONDARY,
    EXPLICIT_ID,
}

enum class OrientationPref {
    AUTO,
    SENSOR_LANDSCAPE,
    LOCK_LANDSCAPE,
}

data class DeviceProfile(
    val id: String,
    val displayName: String,
    val match: ProfileMatch? = null,
    val preferPrimary: PrimaryPreference = PrimaryPreference.AUTO,
    val orientation: OrientationPref = OrientationPref.AUTO,
    val quirks: Set<String> = emptySet(),
)

object DeviceProfileCatalog {

    val AUTO = DeviceProfile(
        id = "auto",
        displayName = "Auto",
        preferPrimary = PrimaryPreference.AUTO,
        orientation = OrientationPref.AUTO,
    )

    val ONEX_SUGAR = DeviceProfile(
        id = "onex-sugar",
        displayName = "One X Sugar",
        match = ProfileMatch(
            manufacturers = listOf("ONEXSUGAR", "ONEXPLAYER", "OneXPlayer", "onexplayer"),
            modelContains = listOf("Sugar", "SUGAR"),
        ),
        preferPrimary = PrimaryPreference.SECONDARY,
        orientation = OrientationPref.SENSOR_LANDSCAPE,
        quirks = setOf(
            "secondary_home_multiple_task",
            "absorb_duplicate_companion",
            "global_input_multi_activity",
        ),
    )

    val GENERIC_DUAL = DeviceProfile(
        id = "generic-dual",
        displayName = "Generic dual",
        match = ProfileMatch(minDisplays = 2),
        preferPrimary = PrimaryPreference.SECONDARY,
        orientation = OrientationPref.SENSOR_LANDSCAPE,
    )

    val SINGLE = DeviceProfile(
        id = "single",
        displayName = "Single display",
        preferPrimary = PrimaryPreference.DEFAULT_DISPLAY,
        orientation = OrientationPref.AUTO,
        quirks = setOf("skip_companion_launch"),
    )

    /** Catalog order = match priority (first match wins). */
    val ALL: List<DeviceProfile> = listOf(ONEX_SUGAR, GENERIC_DUAL, SINGLE, AUTO)

    fun byId(id: String?): DeviceProfile =
        ALL.firstOrNull { it.id.equals(id?.trim(), ignoreCase = true) } ?: AUTO

    fun matchProfile(readings: DisplayReadings): DeviceProfile? {
        val usable = readings.displays.count { !it.isPrivate && it.widthPx > 0 && it.heightPx > 0 }
        for (p in ALL) {
            val m = p.match ?: continue
            if (matches(readings, usable, m)) return p
        }
        return null
    }

    fun effective(profileId: String?, readings: DisplayReadings): DeviceProfile {
        val id = profileId?.trim().orEmpty()
        if (id.isNotEmpty() && !id.equals("auto", ignoreCase = true)) {
            return byId(id)
        }
        return matchProfile(readings) ?: AUTO
    }

    fun matches(readings: DisplayReadings, usableCount: Int, match: ProfileMatch): Boolean {
        if (match.minDisplays != null && usableCount < match.minDisplays) return false
        if (match.maxDisplays != null && usableCount > match.maxDisplays) return false
        if (match.manufacturers.isNotEmpty()) {
            val man = readings.manufacturer
            if (match.manufacturers.none { man.contains(it, ignoreCase = true) }) return false
        }
        if (match.modelContains.isNotEmpty()) {
            val model = readings.model
            if (match.modelContains.none { model.contains(it, ignoreCase = true) }) return false
        }
        if (match.deviceContains.isNotEmpty()) {
            val device = readings.device
            if (match.deviceContains.none { device.contains(it, ignoreCase = true) }) return false
        }
        // Auto-match generic-dual only when user didn't force a named profile:
        // require at least manufacturer or model constraint OR minDisplays alone is OK
        // for explicit catalog entry selection; for auto match, GENERIC_DUAL matches
        // any dual device only if no earlier profile matched (priority order).
        return true
    }
}
