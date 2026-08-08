package com.visorcraft.ghostgalleon.ui

/**
 * Pure dual-screen paint / thrash policy. Host-tested — no Android types.
 *
 * Violating these gates caused pure-black physical panels: view hierarchy
 * still present (a11y) while the GPU buffer never presented non-black pixels.
 *
 * See AGENTS.md "Black screens / dual paint invariants".
 */
object DualPaintPolicy {

    /** Minimum gap between full setContentView rebuilds (ms). */
    const val MIN_FULL_RENDER_GAP_MS = 32L

    /** Minimum gap between companion heal launch attempts (ms). */
    const val MIN_HEAL_GAP_MS = 2_000L

    /** App-wide drawer open/toggle debounce (ms). */
    const val DRAWER_DEBOUNCE_MS = 450L

    /**
     * Whether a full deck rebuild is allowed now.
     * - [rendering] true → never (re-entrancy).
     * - First paint ([hasPainted] false) → always allow (ignore gap).
     * - Within [MIN_FULL_RENDER_GAP_MS] of last full paint → deny coalesce.
     */
    fun allowFullRender(
        rendering: Boolean,
        hasPainted: Boolean,
        nowUptimeMs: Long,
        lastFullRenderUptimeMs: Long,
        minGapMs: Long = MIN_FULL_RENDER_GAP_MS,
    ): Boolean {
        if (rendering) return false
        if (!hasPainted) return true
        return nowUptimeMs - lastFullRenderUptimeMs >= minGapMs
    }

    /**
     * When [allowFullRender] is false, how long to wait before retrying a
     * dropped full paint (SETTINGS browse chips, etc.). Never discard those
     * permanently — stale UI is worse than a short delay.
     * - Nested / re-entrant ([rendering]): 0 → post after current paint.
     * - Coalesce gap: remaining ms until [minGapMs] elapses.
     * Returns null when paint may run immediately.
     */
    fun deferredFullRenderDelayMs(
        rendering: Boolean,
        hasPainted: Boolean,
        nowUptimeMs: Long,
        lastFullRenderUptimeMs: Long,
        minGapMs: Long = MIN_FULL_RENDER_GAP_MS,
    ): Long? {
        if (allowFullRender(
                rendering, hasPainted, nowUptimeMs, lastFullRenderUptimeMs, minGapMs,
            )
        ) {
            return null
        }
        if (rendering || !hasPainted) return 0L
        val elapsed = nowUptimeMs - lastFullRenderUptimeMs
        return (minGapMs - elapsed).coerceAtLeast(0L)
    }

    /**
     * Whether to paint on attach/resume for multi-display.
     * Re-paint when never painted, content epoch moved, or display id changed.
     */
    fun needsPaintForDisplay(
        hasPainted: Boolean,
        paintedDisplayId: Int?,
        currentDisplayId: Int?,
        appliedEpoch: Int,
        contentEpoch: Int,
    ): Boolean {
        if (!hasPainted) return true
        if (appliedEpoch != contentEpoch) return true
        if (currentDisplayId != null && paintedDisplayId != currentDisplayId) return true
        return false
    }

    /**
     * Companion onCreate absorb: keep existing peer on [targetDisplayId]?
     * [peerDisplayId] null = peer not yet attached (still eligible to keep if
     * we only have one peer — caller passes target match only when known).
     */
    fun shouldAbsorbDuplicate(
        hasPeerOnTarget: Boolean,
    ): Boolean = hasPeerOnTarget

    /**
     * Process-wide companion seat: SECONDARY_HOME storms spawn many instances
     * before [ActivityLifecycleCallbacks.onActivityCreated] registers them.
     * Only the seat holder may paint / redirect; all others absorb silently.
     *
     * @param seatHeldByOther true when another non-finishing instance holds
     *   the seat (or claimed it earlier in this process).
     */
    fun shouldAbsorbSeat(seatHeldByOther: Boolean): Boolean = seatHeldByOther

    /**
     * Absorb path must never open All-apps. Only Main deliberate HOME
     * redelivery may open/toggle the drawer.
     */
    fun absorbMayOpenDrawer(): Boolean = false

    /**
     * Drawer request after debounce window:
     * - [drawerAlreadyOpen] + [allowToggle] → close
     * - [drawerAlreadyOpen] + !allowToggle → no-op (storm safe)
     * - closed → open
     */
    enum class DrawerAction { OPEN, CLOSE, NONE }

    fun drawerAction(
        withinDebounce: Boolean,
        drawerAlreadyOpen: Boolean,
        allowToggle: Boolean,
    ): DrawerAction {
        if (withinDebounce) return DrawerAction.NONE
        if (drawerAlreadyOpen) {
            return if (allowToggle) DrawerAction.CLOSE else DrawerAction.NONE
        }
        return DrawerAction.OPEN
    }

    fun withinDebounce(
        nowUptimeMs: Long,
        lastRequestUptimeMs: Long,
        debounceMs: Long = DRAWER_DEBOUNCE_MS,
    ): Boolean = nowUptimeMs - lastRequestUptimeMs < debounceMs

    fun allowHeal(
        nowUptimeMs: Long,
        lastHealUptimeMs: Long,
        minGapMs: Long = MIN_HEAL_GAP_MS,
    ): Boolean = nowUptimeMs - lastHealUptimeMs >= minGapMs

    /**
     * Heal should launch a new companion only when no non-finishing peer
     * claims the secondary target (attached or not).
     */
    fun shouldLaunchCompanion(
        anyPeerOnTarget: Boolean,
    ): Boolean = !anyPeerOnTarget
}
