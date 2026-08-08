package com.visorcraft.ghostgalleon.library

/**
 * Identity key for the all-apps drawer list. When the key matches a cached
 * build, the host may reuse the previous [PickerItems] list instead of
 * rebuilding from thousands of ROM entries. Pure; host-tested.
 */
data class DrawerListKey(
    val contentEpoch: Int,
    val romCount: Int,
    val hiddenFingerprint: Int,
    val appsFingerprint: Int,
    val hiddenRomsFingerprint: Int = 0,
)

object DrawerListCache {

    /**
     * Build a cache key from the inputs that affect the drawer list.
     * [hiddenPackages], [hiddenRomIds], and [appPackageNames] are hashed
     * order-independently.
     */
    fun key(
        contentEpoch: Int,
        romCount: Int,
        hiddenPackages: Collection<String>,
        appPackageNames: Collection<String>,
        hiddenRomIds: Collection<String> = emptyList(),
    ): DrawerListKey = DrawerListKey(
        contentEpoch = contentEpoch,
        romCount = romCount,
        hiddenFingerprint = stableHash(hiddenPackages),
        appsFingerprint = stableHash(appPackageNames),
        hiddenRomsFingerprint = stableHash(hiddenRomIds),
    )

    fun matches(cached: DrawerListKey?, current: DrawerListKey): Boolean =
        cached != null && cached == current

    private fun stableHash(items: Collection<String>): Int {
        if (items.isEmpty()) return 0
        return items.map { it.lowercase() }.sorted().fold(1) { acc, s ->
            31 * acc + s.hashCode()
        }
    }
}
