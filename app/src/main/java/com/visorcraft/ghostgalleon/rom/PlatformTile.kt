package com.visorcraft.ghostgalleon.rom

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.widget.TextView
import androidx.core.widget.TextViewCompat
import kotlin.math.abs

/**
 * Programmatic placeholder art for ROM tiles (Stage 3 replaces it with real
 * covers): a rounded card in a deterministic per-platform color with the
 * platform short name centered (bold, white, auto-sized to fit). Used by the
 * grid, the picker, the carousel, and the companion hero.
 *
 * [colorFor]/[shortText] are pure (no Android framework classes) so host
 * tests can pin determinism; String.hashCode is spec-stable across JVM/ART.
 */
object PlatformTile {

    // Fixed saturation/value: dark enough that white text reads on every hue.
    private const val SATURATION = 0.55f
    private const val VALUE = 0.42f

    /** Deterministic color for a platform id: id hash -> hue, fixed S/V. */
    fun colorFor(platformId: String): Int {
        val hue = (platformId.hashCode() and 0x7FFFFFFF) % 360
        return hsvToColor(hue.toFloat(), SATURATION, VALUE)
    }

    /** Tile text: the platform's shortName, uppercased ("SNES", "3DS",
     *  "SWITCH"); unknown ids fall back to the uppercased id itself. */
    fun shortText(platformId: String): String =
        (Platforms.byId(platformId)?.shortName ?: platformId).uppercase()

    /** Rounded platform-colored background, same 24dp card radius style as
     *  TileBackgrounds.card by default. */
    fun background(context: Context, platformId: String, cornerRadiusDp: Int = 24) =
        GradientDrawable().apply {
            setColor(colorFor(platformId))
            cornerRadius =
                cornerRadiusDp * context.resources.displayMetrics.density
        }

    /** The placeholder tile view: auto-sized bold white short name centered
     *  on the platform color. Caller sets the layout size. */
    fun view(context: Context, platformId: String, cornerRadiusDp: Int = 24): TextView =
        TextView(context).apply {
            text = shortText(platformId)
            setTextColor(0xFFFFFFFF.toInt())
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            maxLines = 1
            background = background(context, platformId, cornerRadiusDp)
            TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
                this, 8, 28, 2, TypedValue.COMPLEX_UNIT_SP)
        }

    /** Re-text/re-color an existing tile created by [view] (in-place hero
     *  updates). */
    fun restyle(tile: TextView, context: Context, platformId: String) {
        tile.text = shortText(platformId)
        tile.background = background(context, platformId)
    }

    // Integer HSV->RGB (h in [0,360)); implemented by hand instead of
    // android.graphics.Color so colorFor stays host-testable.
    private fun hsvToColor(h: Float, s: Float, v: Float): Int {
        val c = v * s
        val x = c * (1f - abs((h / 60f) % 2f - 1f))
        val (r, g, b) = when ((h / 60f).toInt()) {
            0 -> Triple(c, x, 0f)
            1 -> Triple(x, c, 0f)
            2 -> Triple(0f, c, x)
            3 -> Triple(0f, x, c)
            4 -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        val m = v - c
        fun channel(f: Float) = ((f + m) * 255f).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or
            (channel(r) shl 16) or (channel(g) shl 8) or channel(b)
    }
}
