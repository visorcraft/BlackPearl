package com.visorcraft.ghostgalleon.display

/**
 * Pure orientation policy → activity orientation intent.
 * Android maps [ActivityOrientation] in OrientationController.
 */
enum class ActivityOrientation {
    LANDSCAPE,
    SENSOR_LANDSCAPE,
}

object OrientationPolicy {

    /**
     * @param orientationMode settings string: auto | sensor_landscape | lock_landscape
     * @param profileOrientation from effective device profile
     * @param dual whether topology is dual (affects AUTO heuristic)
     * @param minSideDp shorter window side in dp (handheld detect)
     */
    fun resolve(
        orientationMode: String?,
        profileOrientation: OrientationPref,
        dual: Boolean = false,
        minSideDp: Float = 500f,
        /** Legacy: angleLock forces landscape; gyroEnabled false same. */
        angleLock: Boolean = false,
        gyroEnabled: Boolean = true,
    ): ActivityOrientation {
        if (angleLock || !gyroEnabled) return ActivityOrientation.LANDSCAPE
        val mode = orientationMode?.trim()?.lowercase().orEmpty()
        val pref = when (mode) {
            "lock_landscape", "lock" -> OrientationPref.LOCK_LANDSCAPE
            "sensor_landscape", "sensor" -> OrientationPref.SENSOR_LANDSCAPE
            "auto", "" -> profileOrientation
            else -> profileOrientation
        }
        return when (pref) {
            OrientationPref.LOCK_LANDSCAPE -> ActivityOrientation.LANDSCAPE
            OrientationPref.SENSOR_LANDSCAPE -> ActivityOrientation.SENSOR_LANDSCAPE
            OrientationPref.AUTO -> {
                // v1: landscape-first handhelds; dual or compact short side → sensor landscape
                if (dual || minSideDp < 600f) ActivityOrientation.SENSOR_LANDSCAPE
                else ActivityOrientation.SENSOR_LANDSCAPE
            }
        }
    }
}
