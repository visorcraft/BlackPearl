package com.visorcraft.ghostgalleon.art

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory
import androidx.core.view.children
import com.visorcraft.ghostgalleon.rom.PlatformTile
import com.visorcraft.ghostgalleon.rom.RomEntry

/**
 * ROM tile art with async cache fill: the PlatformTile placeholder with an
 * empty ImageView overlaid on top. The overlay fills in from ArtCache when
 * art exists for the ROM (disk cache or the entry's local artUri); without
 * art the placeholder simply shows through. Used by the grid, the carousel,
 * and the companion hero.
 */
object ArtTile {

    /**
     * ROM tile art view: placeholder + overlay. [targetPx] is the decode
     * target (the view's pixel size); it only matters on the first decode —
     * the downscaled result is then served from the disk cache. [bindNow]
     * starts the async grid-art load immediately; pass false when the
     * caller binds a different art kind right away (the companion hero
     * binds HERO art itself, so a GRID bind here would double-decode).
     */
    fun view(
        context: Context,
        cache: ArtCache,
        rom: RomEntry,
        targetPx: Int,
        cornerRadiusDp: Int = 24,
        bindNow: Boolean = true,
        artOverrides: Map<String, String> = emptyMap(),
    ): View {
        val frame = FrameLayout(context)
        frame.addView(
            PlatformTile.view(context, rom.platformId, cornerRadiusDp),
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        val overlay = ImageView(context).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        frame.addView(overlay, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        ))
        if (bindNow) bind(overlay, cache, rom, targetPx, cornerRadiusDp, artOverrides)
        return frame
    }

    /** The overlay ImageView inside a frame built by [view], if any. */
    fun overlay(frame: ViewGroup): ImageView? =
        frame.children.filterIsInstance<ImageView>().firstOrNull()

    /**
     * (Re)bind an overlay to a ROM: clears any stale bitmap, then kicks the
     * async load. The overlay's tag holds the RomEntry id the pending load
     * belongs to; the load is skipped pre-decode and the result applied
     * only when the tag still matches and the view is attached (recycled
     * cells, in-place hero swaps).
     */
    fun bind(
        overlay: ImageView,
        cache: ArtCache,
        rom: RomEntry,
        targetPx: Int,
        cornerRadiusDp: Int = 24,
        artOverrides: Map<String, String> = emptyMap(),
    ) {
        val context = overlay.context
        overlay.setImageDrawable(null)
        overlay.tag = rom.id
        val radiusPx = cornerRadiusDp * context.resources.displayMetrics.density
        cache.load(
            context, rom, targetPx,
            artOverrides = artOverrides,
            isStillValid = { overlay.tag == rom.id },
        ) { bitmap ->
            overlay.post {
                if (bitmap != null && overlay.tag == rom.id &&
                    overlay.isAttachedToWindow
                ) {
                    overlay.setImageDrawable(
                        RoundedBitmapDrawableFactory.create(context.resources, bitmap)
                            .apply { cornerRadius = radiusPx },
                    )
                }
            }
        }
    }
}
