package com.visorcraft.ghostgalleon.library

/**
 * Pure RetroAchievements fetch/store gates. Host-tested.
 *
 * Full deck rebuilds must NEVER be driven by RA network success. Progress
 * updates are selection chrome only; one fetch attempt per ROM per process.
 */
object RaProgressGate {

    /** Whether a network fetch may start for [romId]. */
    fun mayFetch(
        romId: String,
        username: String?,
        apiKey: String?,
        inFlight: Set<String>,
        attempted: Set<String>,
    ): Boolean {
        val id = romId.trim()
        if (id.isEmpty()) return false
        if (username.isNullOrBlank() || apiKey.isNullOrBlank()) return false
        if (id in inFlight || id in attempted) return false
        return true
    }

    /**
     * True when [next] is the same progress we already show — skip notify.
     * Equality is field-wise (not identity).
     */
    fun isSameProgress(prev: RaProgress?, next: RaProgress): Boolean {
        if (prev == null) return false
        return prev.gameId == next.gameId &&
            prev.numAwarded == next.numAwarded &&
            prev.numPossible == next.numPossible &&
            prev.title == next.title &&
            prev.userScore == next.userScore &&
            prev.hardcore == next.hardcore
    }

    /**
     * Notify kind after a successful store: always selection chrome, never
     * a SETTINGS full rebuild.
     */
    enum class NotifyKind { NONE, SELECTION_ONLY }

    fun notifyAfterStore(prev: RaProgress?, next: RaProgress): NotifyKind {
        if (next.isEmpty) return NotifyKind.NONE
        if (isSameProgress(prev, next)) return NotifyKind.NONE
        return NotifyKind.SELECTION_ONLY
    }
}
