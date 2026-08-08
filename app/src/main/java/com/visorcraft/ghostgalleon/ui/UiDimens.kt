package com.visorcraft.ghostgalleon.ui

import android.content.Context
import android.view.View

/** Density-independent pixel conversion shared by deck and settings UI. */
object UiDimens {
    fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    fun dpF(context: Context, value: Int): Float =
        value * context.resources.displayMetrics.density
}

fun Context.dp(value: Int): Int = UiDimens.dp(this, value)

fun Context.dpF(value: Int): Float = UiDimens.dpF(this, value)

fun View.dp(value: Int): Int = context.dp(value)

fun View.dpF(value: Int): Float = context.dpF(value)
