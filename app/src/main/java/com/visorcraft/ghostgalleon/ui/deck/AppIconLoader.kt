package com.visorcraft.ghostgalleon.ui.deck

import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.util.LruCache

class AppIconLoader(private val pm: PackageManager) {

    private val cache = LruCache<String, Drawable>(128)

    fun load(packageName: String): Drawable =
        cache.get(packageName) ?: loadUncached(packageName).also { cache.put(packageName, it) }

    private fun loadUncached(packageName: String): Drawable = try {
        pm.getApplicationIcon(packageName)
    } catch (e: PackageManager.NameNotFoundException) {
        pm.defaultActivityIcon
    }
}
