package com.visorcraft.ghostgalleon.library

data class AppEntry(
    val packageName: String,
    val label: String,
    val isGame: Boolean,
    /** PackageManager firstInstallTime epoch ms; 0 when unknown. */
    val firstInstallMs: Long = 0L,
)
