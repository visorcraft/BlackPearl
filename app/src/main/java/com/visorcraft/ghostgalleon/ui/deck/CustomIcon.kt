package com.visorcraft.ghostgalleon.ui.deck

import android.widget.ImageView
import com.visorcraft.ghostgalleon.R
import com.visorcraft.ghostgalleon.art.ArtCache
import com.visorcraft.ghostgalleon.settings.Settings

/**
 * App icon binding with per-app custom-icon override: the package icon goes
 * in immediately as the placeholder; when settings.customIcons has a SAF
 * image URI for the package, the async ArtCache path (memory → disk →
 * downscaled decode, keyed "app:<package>") replaces it. Reset simply drops
 * the map entry — the package icon then binds again on the next render.
 */
object CustomIcon {

    /** ArtCache key namespace for app icons (ROM art keys are entry ids). */
    fun cacheKey(packageName: String): String = "app:$packageName"

    fun bind(
        image: ImageView,
        iconLoader: AppIconLoader,
        cache: ArtCache,
        settings: Settings,
        packageName: String,
        targetPx: Int,
    ) {
        image.setImageDrawable(iconLoader.load(packageName))
        val uri = settings.customIcons[packageName]
        if (uri == null) {
            image.setTag(R.id.custom_icon_key, null)
            return
        }
        image.setTag(R.id.custom_icon_key, packageName)
        cache.loadUri(
            image.context,
            key = cacheKey(packageName),
            uriString = uri,
            maxDimension = targetPx,
            isStillValid = { image.getTag(R.id.custom_icon_key) == packageName },
        ) { bitmap ->
            image.post {
                if (bitmap != null &&
                    image.getTag(R.id.custom_icon_key) == packageName &&
                    image.isAttachedToWindow
                ) {
                    image.setImageBitmap(bitmap)
                }
            }
        }
    }
}
