package com.visorcraft.ghostgalleon.rom

import android.content.pm.PackageManager

/** True when [packageName] is installed for the current user. */
fun PackageManager.isInstalled(packageName: String): Boolean =
    try {
        getPackageInfo(packageName, 0)
        true
    } catch (_: Exception) {
        false
    }
