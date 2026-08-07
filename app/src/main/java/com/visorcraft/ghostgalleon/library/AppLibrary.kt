package com.visorcraft.ghostgalleon.library

import com.visorcraft.ghostgalleon.settings.Settings
import com.visorcraft.ghostgalleon.settings.displayName

class AppLibrary(private val source: InstalledAppsSource) {

    private val cache: List<AppEntry> by lazy {
        source.query().sortedBy { it.label.lowercase() }
    }

    fun visible(settings: Settings): List<AppEntry> =
        cache.filter { it.packageName !in settings.hiddenPackages }
            .map { it.displayName(settings) }

    // Everything installed, including hidden apps: grid slots stay
    // launchable (and rendered) even when their app is hidden from the
    // picker. Custom names apply here too, so slots/hero show them.
    fun all(settings: Settings): List<AppEntry> =
        cache.map { it.displayName(settings) }

    // Game mode carousel: the curated grid content — non-null slots in
    // slot order, resolved through the full cache (hidden apps stay;
    // uninstalled ones drop out). Blank slots never appear.
    fun curated(settings: Settings): List<AppEntry> {
        val byPkg = all(settings).associateBy { it.packageName }
        return settings.gridSlots.mapNotNull { it?.let(byPkg::get) }
    }

    fun dock(settings: Settings): List<AppEntry> {
        val visibleByPkg = visible(settings).associateBy { it.packageName }
        return settings.dockSlots.filterNotNull().mapNotNull { visibleByPkg[it] }
    }
}
