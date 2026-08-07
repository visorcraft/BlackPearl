package com.visorcraft.ghostgalleon.library

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager

class PackageManagerAppsSource(
    private val pm: PackageManager,
    private val selfPackage: String,
) : InstalledAppsSource {

    override fun query(): List<AppEntry> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .map { it.activityInfo.applicationInfo }
            .distinctBy { it.packageName }
            .filter { it.packageName != selfPackage }
            // Belt-and-braces: some apps (stock "Music") match the LAUNCHER
            // query yet have no usable launch intent; tapping them no-ops.
            .filter { pm.getLaunchIntentForPackage(it.packageName) != null }
            .map { info ->
                AppEntry(
                    packageName = info.packageName,
                    label = pm.getApplicationLabel(info).toString(),
                    isGame = info.category == ApplicationInfo.CATEGORY_GAME ||
                        (info.flags and ApplicationInfo.FLAG_IS_GAME) != 0,
                )
            }
    }
}
