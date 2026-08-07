package com.visorcraft.blackpearl.library

fun interface InstalledAppsSource {
    fun query(): List<AppEntry>
}
