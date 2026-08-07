package com.visorcraft.ghostgalleon.library

/**
 * Pure decision for first-run / empty-library setup polish.
 * Host-tested; no Android types.
 */
object SetupNeeds {

    data class Snapshot(
        val setupDismissed: Boolean,
        val romTreeCount: Int,
        val romEntryCount: Int,
        val installedPlayerCount: Int,
        val hasSgdbKey: Boolean,
    )

    /** True when the guided setup surface should be offered. */
    fun shouldShow(s: Snapshot): Boolean {
        if (s.setupDismissed) return false
        // Already has library trees or entries → configured enough.
        if (s.romTreeCount > 0 || s.romEntryCount > 0) return false
        return true
    }

    /** Checklist rows for the setup card (label, done). */
    fun checklist(s: Snapshot): List<Pair<String, Boolean>> = listOf(
        "Add a ROM folder" to (s.romTreeCount > 0),
        "Install an emulator" to (s.installedPlayerCount > 0),
        "Optional: SteamGridDB API key" to s.hasSgdbKey,
    )

    fun allRequiredDone(s: Snapshot): Boolean =
        s.romTreeCount > 0
}
