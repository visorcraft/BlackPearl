package com.visorcraft.ghostgalleon.rom

/**
 * Pure remount / resume-rescan decisions. Host-tested.
 *
 * When the launcher resumes, we may want a quiet incremental rescan if
 * any granted tree was unreadable before and might be back (card remount).
 */
object RemountPolicy {

    /**
     * True when an automatic (non-force) rescan is warranted on resume:
     * there is at least one granted tree, and either we have never scanned
     * ([hadSuccessfulScan] false with empty library) or some trees were
     * known unreadable last time ([lastHadUnreadableTree]).
     */
    fun shouldQuietRescanOnResume(
        grantedTreeCount: Int,
        libraryEntryCount: Int,
        lastHadUnreadableTree: Boolean,
        hadSuccessfulScan: Boolean,
    ): Boolean {
        if (grantedTreeCount <= 0) return false
        if (lastHadUnreadableTree) return true
        if (!hadSuccessfulScan && libraryEntryCount == 0) return true
        return false
    }

    /**
     * After a rescan result, whether to remember "had unreadable tree".
     * Unreadable outcome → true. Success with zero skipped clean and zero
     * trees was all-readable → false. Success that retained unreadable
     * trees is tracked via [retainedUnreadableCount] if the caller knows.
     */
    fun nextHadUnreadableFlag(
        allUnreadable: Boolean,
        retainedUnreadableTreeCount: Int = 0,
    ): Boolean = allUnreadable || retainedUnreadableTreeCount > 0
}
