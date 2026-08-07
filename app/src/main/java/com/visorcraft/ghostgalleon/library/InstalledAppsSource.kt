package com.visorcraft.ghostgalleon.library

fun interface InstalledAppsSource {
    fun query(): List<AppEntry>
}
