package com.visorcraft.ghostgalleon.display

/**
 * Pure topology resolution: SINGLE/DUAL roles + launch target.
 * No Android types. Host-tested via constructed [DisplayReadings].
 */
object DisplayTopology {

    /**
     * Interactive display preference from settings:
     * - `auto` — use profile.preferPrimary
     * - `default` — default/system display
     * - `secondary` — first non-default usable display
     * - `id:N` — explicit id N
     */
    fun resolve(
        readings: DisplayReadings,
        profile: DeviceProfile,
        interactiveDisplayMode: String = "auto",
        userPinnedPrimaryId: Int? = null,
    ): ResolvedTopology {
        val displays = readings.displays
            .filter { !it.isPrivate && it.widthPx > 0 && it.heightPx > 0 }
            .sortedBy { it.id }
        if (displays.isEmpty()) {
            return ResolvedTopology(
                mode = SurfaceMode.SINGLE,
                primaryDisplayId = 0,
                companionDisplayId = null,
                launchDisplayId = 0,
                secondaryHomeDisplayId = null,
                largerDisplayId = 0,
                allIds = listOf(0),
                reason = "no usable displays; synthetic id 0",
            )
        }
        val allIds = displays.map { it.id }
        val largerId = largerDisplayId(displays)
        val defaultId = displays.firstOrNull { it.isDefault }?.id ?: displays.first().id
        val secondaries = displays.filter { it.id != defaultId }

        val forceSingle = profile.id == "single" ||
            profile.quirks.contains("skip_companion_launch") ||
            secondaries.isEmpty() ||
            displays.size == 1

        if (forceSingle) {
            val primary = pickPrimarySingle(
                defaultId, allIds, interactiveDisplayMode, userPinnedPrimaryId,
            )
            return ResolvedTopology(
                mode = SurfaceMode.SINGLE,
                primaryDisplayId = primary,
                companionDisplayId = null,
                launchDisplayId = primary,
                secondaryHomeDisplayId = null,
                largerDisplayId = largerId,
                allIds = allIds,
                reason = "SINGLE profile=${profile.id} displays=${allIds.size} primary=$primary",
            )
        }

        val secondaryHome = secondaries.first() // activity placement for SECONDARY_HOME
        val companionCandidate = secondaryHome

        // Sticky pin wins when still present.
        if (userPinnedPrimaryId != null && userPinnedPrimaryId in allIds) {
            val primary = userPinnedPrimaryId
            val companion = allIds.first { it != primary }
            return ResolvedTopology(
                mode = SurfaceMode.DUAL,
                primaryDisplayId = primary,
                companionDisplayId = companion,
                launchDisplayId = companion,
                secondaryHomeDisplayId = secondaryHome.id,
                largerDisplayId = largerId,
                allIds = allIds,
                reason = "DUAL pinned primary=$primary companion=$companion secondaryHome=${secondaryHome.id}",
            )
        }

        val mode = interactiveDisplayMode.trim().lowercase()
        val primary = when {
            mode == "default" -> defaultId
            mode == "secondary" -> companionCandidate.id
            mode.startsWith("id:") -> {
                val n = mode.removePrefix("id:").toIntOrNull()
                if (n != null && n in allIds) n else defaultId
            }
            else -> when (profile.preferPrimary) {
                PrimaryPreference.DEFAULT_DISPLAY -> defaultId
                PrimaryPreference.SECONDARY -> companionCandidate.id
                PrimaryPreference.EXPLICIT_ID -> defaultId
                PrimaryPreference.AUTO -> heuristicPrimary(defaultId, companionCandidate, displays)
            }
        }

        val companion = if (primary == companionCandidate.id) {
            defaultId
        } else {
            companionCandidate.id
        }
        // If somehow primary equals companion (shouldn't), pick another.
        val companionFinal = if (companion == primary) {
            allIds.firstOrNull { it != primary } ?: primary
        } else {
            companion
        }
        if (companionFinal == primary) {
            return ResolvedTopology(
                mode = SurfaceMode.SINGLE,
                primaryDisplayId = primary,
                companionDisplayId = null,
                launchDisplayId = primary,
                secondaryHomeDisplayId = null,
                largerDisplayId = largerId,
                allIds = allIds,
                reason = "collapsed to SINGLE after resolve primary=$primary",
            )
        }

        return ResolvedTopology(
            mode = SurfaceMode.DUAL,
            primaryDisplayId = primary,
            companionDisplayId = companionFinal,
            launchDisplayId = companionFinal,
            secondaryHomeDisplayId = secondaryHome.id,
            largerDisplayId = largerId,
            allIds = allIds,
            reason = "DUAL profile=${profile.id} mode=$mode primary=$primary companion=$companionFinal secondaryHome=${secondaryHome.id}",
        )
    }

    /**
     * Largest usable panel by pixel area (width×height). Tie → lower id.
     * Host-tested; used for Swap/Settings chrome placement in DUAL.
     */
    fun largerDisplayId(displays: List<DisplayInfo>): Int? =
        displays
            .filter { !it.isPrivate && it.widthPx > 0 && it.heightPx > 0 }
            .maxWithOrNull(
                compareBy<DisplayInfo> { it.widthPx.toLong() * it.heightPx.toLong() }
                    .thenBy { -it.id }, // lower id wins ties (stable)
            )
            ?.id

    /**
     * Swap/Settings icons host on the physically larger panel in DUAL.
     * SINGLE always shows them (only one surface).
     */
    fun shouldShowSystemChromeIcons(
        mode: SurfaceMode,
        thisDisplayId: Int?,
        largerDisplayId: Int?,
    ): Boolean {
        if (mode != SurfaceMode.DUAL) return true
        if (thisDisplayId == null || largerDisplayId == null) return true
        return thisDisplayId == largerDisplayId
    }

    fun swap(topology: ResolvedTopology): ResolvedTopology {
        if (topology.mode != SurfaceMode.DUAL) return topology
        val companion = topology.companionDisplayId ?: return topology
        return topology.copy(
            primaryDisplayId = companion,
            companionDisplayId = topology.primaryDisplayId,
            launchDisplayId = topology.primaryDisplayId,
            // secondaryHomeDisplayId unchanged — activity still on non-default panel
            reason = "swapped primary=$companion companion=${topology.primaryDisplayId}",
        )
    }

    /**
     * After swap: pin the new primary so Auto refresh does not undo it.
     */
    fun pinAfterSwap(swapped: ResolvedTopology): Int = swapped.primaryDisplayId

    private fun pickPrimarySingle(
        defaultId: Int,
        allIds: List<Int>,
        interactiveDisplayMode: String,
        userPinnedPrimaryId: Int?,
    ): Int {
        if (userPinnedPrimaryId != null && userPinnedPrimaryId in allIds) {
            return userPinnedPrimaryId
        }
        val mode = interactiveDisplayMode.trim().lowercase()
        return when {
            mode == "default" -> defaultId
            mode.startsWith("id:") -> {
                val n = mode.removePrefix("id:").toIntOrNull()
                if (n != null && n in allIds) n else defaultId
            }
            else -> defaultId
        }
    }

    /**
     * Prefer secondary if it looks like a control panel (shorter side 400–800dp)
     * and the default is larger — clamshell bottom pad pattern.
     */
    private fun heuristicPrimary(
        defaultId: Int,
        secondary: DisplayInfo,
        displays: List<DisplayInfo>,
    ): Int {
        val def = displays.firstOrNull { it.id == defaultId } ?: return defaultId
        val secShort = minOf(secondary.widthDp, secondary.heightDp)
        val defShort = minOf(def.widthDp, def.heightDp)
        return if (secShort in 400f..800f && defShort > secShort) {
            secondary.id
        } else {
            defaultId
        }
    }
}
