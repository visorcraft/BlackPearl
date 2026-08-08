package com.visorcraft.ghostgalleon.ui.deck

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Outline
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.media.MediaPlayer
import android.net.Uri
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.children
import kotlin.math.abs
import com.visorcraft.ghostgalleon.GhostGalleonApp
import com.visorcraft.ghostgalleon.R
import com.visorcraft.ghostgalleon.art.ArtCache
import com.visorcraft.ghostgalleon.art.ArtTile
import com.visorcraft.ghostgalleon.library.AppLibrary
import com.visorcraft.ghostgalleon.library.CollectionsOps
import com.visorcraft.ghostgalleon.library.LibraryBrowse
import com.visorcraft.ghostgalleon.library.RetroAchievements
import com.visorcraft.ghostgalleon.library.SessionMath
import com.visorcraft.ghostgalleon.library.SessionTracker
import com.visorcraft.ghostgalleon.rom.HeroDetail
import com.visorcraft.ghostgalleon.rom.PlatformLook
import com.visorcraft.ghostgalleon.rom.PlatformTile
import com.visorcraft.ghostgalleon.rom.Platforms
import com.visorcraft.ghostgalleon.rom.RomEntry
import com.visorcraft.ghostgalleon.rom.RomProfiles
import com.visorcraft.ghostgalleon.rom.SelectionStrip
import com.visorcraft.ghostgalleon.settings.CompanionRole
import com.visorcraft.ghostgalleon.settings.CompanionRoleResolve
import com.visorcraft.ghostgalleon.settings.Settings
import com.visorcraft.ghostgalleon.settings.SlotKey
import com.visorcraft.ghostgalleon.state.DeckState
import com.visorcraft.ghostgalleon.system.SystemInfoCollector
import com.visorcraft.ghostgalleon.system.SystemInfoFormat
import com.visorcraft.ghostgalleon.ui.settings.SettingsActivity

object CompanionPanel {

    private const val TAG_HERO_ICON = "hero_icon"
    private const val TAG_HERO_NAME = "hero_name"
    private const val TAG_HERO_SUB = "hero_sub"
    private const val TAG_HERO_META = "hero_meta"
    private const val TAG_HERO_METADATA = "hero_metadata"
    private const val TAG_HERO_PLAYER = "hero_player"
    private const val TAG_HERO_RA = "hero_ra"
    private const val TAG_HERO_DESC = "hero_desc"
    private const val TAG_HERO_SHOT = "hero_shot"
    private const val TAG_HERO_VIDEO = "hero_video"
    private const val TAG_HERO_BANNER = "hero_banner"
    /** Visible so PRIMARY TOP_STRIP paths can detect a hero panel in the tree. */
    const val TAG_PANEL_ROOT = "panel_root"
    /**
     * Marker on the compact single-display selection strip. Presence means
     * [updateSelection] must use the strip layout (not full dual hero).
     */
    const val TAG_TOP_STRIP = "top_strip"
    private const val TAG_ROLE_CHIPS = "role_chips"
    private const val TAG_STRIP_ART_HOST = "strip_art_host"
    private const val TAG_STRIP_DETAIL = "strip_detail"

    // Layered depth background: a vertical gradient lifting to #FF202028 in
    // the center band, plus a huge soft radial glow behind the hero icon
    // tinted with the glow color at ~18% alpha.
    private fun panelBackground(context: Context, glowColor: Int): Drawable {
        val lift = com.visorcraft.ghostgalleon.settings.ThemePack.resolve(
            (context.applicationContext as? GhostGalleonApp)?.settings
                ?: com.visorcraft.ghostgalleon.settings.Settings.DEFAULT,
        ).panelLift
        val gradient = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(
                0xFF000000.toInt(),
                lift,
                0xFF000000.toInt(),
            ),
        )
        val metrics = context.resources.displayMetrics
        val glow = GradientDrawable().apply {
            gradientType = GradientDrawable.RADIAL_GRADIENT
            colors = intArrayOf(
                (glowColor and 0x00FFFFFF) or (0x2E shl 24),
                Color.TRANSPARENT,
            )
            setGradientCenter(0.5f, 0.45f)
            gradientRadius =
                maxOf(metrics.widthPixels, metrics.heightPixels) * 0.8f
        }
        return LayerDrawable(arrayOf(gradient, glow))
    }

    // Cheap Palette stand-in: draw the icon at 16x16 and average the opaque
    // pixels. Null when the icon cannot be rasterized.
    private fun dominantColor(drawable: Drawable): Int? = runCatching {
        val bmp = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        drawable.setBounds(0, 0, 16, 16)
        drawable.draw(canvas)
        var r = 0L; var g = 0L; var b = 0L; var n = 0L
        for (y in 0 until 16) {
            for (x in 0 until 16) {
                val p = bmp.getPixel(x, y)
                if (p ushr 24 < 0x40) continue
                r += (p shr 16) and 0xFF
                g += (p shr 8) and 0xFF
                b += p and 0xFF
                n++
            }
        }
        if (n == 0L) return null
        Color.rgb((r / n).toInt(), (g / n).toInt(), (b / n).toInt())
    }.getOrNull()

    // Glow tint: dominant color of the selected app's icon when available,
    // otherwise the accent color.
    private fun glowColor(context: Context, packageName: String?, settings: Settings): Int {
        if (packageName != null) {
            runCatching { context.packageManager.getApplicationIcon(packageName) }
                .getOrNull()
                ?.let { dominantColor(it) }
                ?.let { return it }
        }
        return settings.accentColor
    }

    // Selection-only update on an already-built panel: swap the hero icon
    // and name in place. Returns false when the current hero structure does
    // not match the new selection (wordmark shown but an entry selected, or
    // an app hero showing while a ROM is now selected — the hero views
    // differ) so the caller falls back to a full rebuild.
    fun updateSelection(
        view: View,
        context: Context,
        state: DeckState,
        library: AppLibrary,
        roms: List<RomEntry>,
        settings: Settings,
    ): Boolean {
        // Compact TOP_STRIP path: always selection-context, never full dual roles.
        if (view.findViewWithTag<View>(TAG_TOP_STRIP) != null) {
            return updateTopStrip(view, context, state, library, roms, settings)
        }
        val rom = selectedRom(state.selectedKey, roms)
        if (rom != null) {
            // In-place only when the hero is already in ROM shape (banner
            // frame + tile TextView + name + platform subtitle).
            val tile = view.findViewWithTag<View>(TAG_HERO_ICON) as? TextView
                ?: return false
            val name = view.findViewWithTag<TextView>(TAG_HERO_NAME) ?: return false
            val sub = view.findViewWithTag<TextView>(TAG_HERO_SUB) ?: return false
            val banner = view.findViewWithTag<View>(TAG_HERO_BANNER) as? FrameLayout
                ?: return false
            val tileFrame = tile.parent as? FrameLayout ?: return false
            PlatformTile.restyle(tile, context, rom.platformId)
            // Rebind the art chain: stale art clears immediately, HERO
            // banner / grid art fill in async, placeholder shows on a miss.
            val dens = context.resources.displayMetrics.density
            val hDp = context.resources.displayMetrics.heightPixels / dens
            val artPx = (
                CompanionHeroMetrics.forPanel(hDp).artSizeDp * dens
                ).toInt()
            bindRomHeroArt(
                banner,
                tileFrame,
                (context.applicationContext as GhostGalleonApp).artCache,
                rom,
                settings.artOverrides,
                artPx,
            )
            name.text = rom.name
            val platform = Platforms.byId(rom.platformId)
            val installed = { pkg: String ->
                runCatching {
                    context.packageManager.getPackageInfo(pkg, 0)
                    true
                }.getOrDefault(false)
            }
            val preferred = RomProfiles.preferredPlayerId(
                rom.id,
                settings.romProfiles,
                settings.defaultPlayers[rom.platformId],
            )
            // One compact subline (platform · play · player) — not three stacked rows.
            sub.text = HeroDetail.compactSubline(
                HeroDetail.platformLine(platform, rom.platformId),
                romMetaLine(settings, SlotKey.rom(rom.id)),
                HeroDetail.playerShortName(platform, preferred, installed),
            )
            view.findViewWithTag<TextView>(TAG_HERO_META)?.visibility = View.GONE
            view.findViewWithTag<TextView>(TAG_HERO_PLAYER)?.visibility = View.GONE
            bindMetadataLine(view.findViewWithTag(TAG_HERO_METADATA), rom)
            val appCtx = context.applicationContext as GhostGalleonApp
            bindRaLine(
                view.findViewWithTag(TAG_HERO_RA),
                appCtx.raProgressFor(rom.id),
                !settings.raApiKey.isNullOrBlank(),
            )
            val desc = HeroDetail.descriptionText(rom.description)
            view.findViewWithTag<TextView>(TAG_HERO_DESC)?.let { tv ->
                if (desc != null) {
                    tv.visibility = View.VISIBLE
                    tv.text = desc
                } else {
                    tv.visibility = View.GONE
                    tv.text = ""
                }
            }
            bindScreenshot(
                view.findViewWithTag(TAG_HERO_SHOT),
                (context.applicationContext as GhostGalleonApp).artCache,
                rom,
            )
            bindHeroVideo(view.findViewWithTag(TAG_HERO_VIDEO), rom)
            // Platform-tinted glow (stronger atmosphere via PlatformLook accent).
            view.findViewWithTag<View>(TAG_PANEL_ROOT)?.background =
                panelBackground(context, PlatformLook.accentColor(rom.platformId))
            appCtx.requestRaProgress(rom.id, rom.name)
            return true
        }
        val entry = library.visible(settings)
            .firstOrNull { it.packageName == state.selectedKey }
        // Find as View first: the ROM hero tags a TextView tile with
        // TAG_HERO_ICON, and findViewWithTag<ImageView> would throw a
        // ClassCastException on it instead of returning null.
        val heroIcon = view.findViewWithTag<View>(TAG_HERO_ICON)
        val name = view.findViewWithTag<TextView>(TAG_HERO_NAME)
        if (entry == null) {
            // Only already showing the wordmark counts as up to date.
            return heroIcon == null && name == null
        }
        // A ROM-shaped hero showing while an app is selected is a structure
        // mismatch -> full rebuild.
        val icon = heroIcon as? ImageView ?: return false
        if (name == null) return false
        val targetPx = (240 * context.resources.displayMetrics.density).toInt()
        val iconDrawable = runCatching {
            context.packageManager.getApplicationIcon(entry.packageName)
        }.getOrNull()
        CustomIcon.bind(
            icon, AppIconLoader(context.packageManager),
            (context.applicationContext as GhostGalleonApp).artCache,
            settings, entry.packageName, targetPx)
        name.text = entry.label
        // Retint the glow with the newly selected icon's dominant color.
        view.findViewWithTag<View>(TAG_PANEL_ROOT)?.background = panelBackground(
            context,
            iconDrawable?.let { dominantColor(it) } ?: settings.accentColor,
        )
        return true
    }

    // The ROM referenced by a "rom:<id>" selection key, if still indexed.
    private fun selectedRom(key: String?, roms: List<RomEntry>): RomEntry? {
        val id = SlotKey.romId(key) ?: return null
        return roms.firstOrNull { it.id == id }
    }

    private fun resumeLabel(
        key: String,
        library: AppLibrary,
        roms: List<RomEntry>,
        settings: Settings,
    ): String = when {
        SlotKey.isRom(key) -> {
            val id = SlotKey.romId(key)
            roms.firstOrNull { it.id == id }?.name ?: key
        }
        else -> library.visible(settings)
            .firstOrNull { it.packageName == key }?.label ?: key
    }

    /**
     * Tap = launch. Horizontal drag ≥ [swipeSlopPx] (either direction) =
     * dismiss Resume chip until next launch (no lastLaunched thrash).
     * Uses raw dx, not fling velocity — small OLED chips rarely hit 400px/s.
     */
    private fun bindResumeChipGestures(
        view: TextView,
        cont: String,
        activity: AppCompatActivity,
        state: DeckState,
        roms: List<RomEntry>,
        app: GhostGalleonApp,
        swipeSlopPx: Float,
    ) {
        var downX = 0f
        var downY = 0f
        var tracking = false
        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.x
                    downY = event.y
                    tracking = true
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!tracking) return@setOnTouchListener false
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (!tracking) return@setOnTouchListener false
                    tracking = false
                    val dx = event.x - downX
                    val dy = event.y - downY
                    if (abs(dx) >= swipeSlopPx && abs(dx) > abs(dy)) {
                        // Horizontal swipe either way → hide Resume.
                        app.dismissResumeChip()
                        true
                    } else if (abs(dx) < swipeSlopPx * 0.5f && abs(dy) < swipeSlopPx * 0.5f &&
                        event.actionMasked == MotionEvent.ACTION_UP
                    ) {
                        val idx = app.settings.gridSlots.indexOf(cont)
                        if (idx >= 0) state.selectSlot(idx, cont) else state.select(cont)
                        launchSlotKey(activity, state, roms, cont)
                        true
                    } else {
                        true
                    }
                }
                else -> false
            }
        }
    }

    // Loads res/raw/<name> (animated WebP/GIF) as a started-or-startable
    // AnimatedImageDrawable; null when the asset is absent, undecodable, or
    // the platform predates ImageDecoder (API 28). Looked up by name so a
    // drop-in asset never needs a code change.
    private fun loadAnimated(context: Context, rawName: String):
        android.graphics.drawable.AnimatedImageDrawable? {
        if (android.os.Build.VERSION.SDK_INT < 28) return null
        val id = context.resources.getIdentifier(
            rawName, "raw", context.packageName)
        if (id == 0) return null
        return runCatching {
            android.graphics.ImageDecoder.decodeDrawable(
                android.graphics.ImageDecoder.createSource(context.resources, id))
        }.getOrNull() as? android.graphics.drawable.AnimatedImageDrawable
    }

    // Wide banner frame for HERO art: GONE until wide art arrives, ~40% of
    // the display height, rounded corners via an outline clip (CENTER_CROP
    // keeps the corners true, unlike a scaled RoundedBitmapDrawable).
    private fun bannerFrame(context: Context): FrameLayout {
        val radiusPx = 24 * context.resources.displayMetrics.density
        val image = ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            clipToOutline = true
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, radiusPx)
                }
            }
        }
        return FrameLayout(context).apply {
            tag = TAG_HERO_BANNER
            visibility = View.GONE
            addView(image, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ))
        }
    }

    /** Platform subtitle companion line: last played + playtime when known. */
    internal fun romMetaLine(settings: Settings, slotKey: String): String =
        SessionMath.cardMetaLine(
            settings.lastLaunchedMs[slotKey],
            settings.playtimeMs[slotKey] ?: 0L,
            System.currentTimeMillis(),
            playtimePrefix = "Played ",
        )

    private fun bindMetadataLine(tv: TextView?, rom: RomEntry) {
        if (tv == null) return
        val line = HeroDetail.metadataLine(rom)
        if (line != null) {
            tv.visibility = View.VISIBLE
            tv.text = line
        } else {
            tv.visibility = View.GONE
            tv.text = ""
        }
    }

    private fun bindRaLine(tv: TextView?, progress: com.visorcraft.ghostgalleon.library.RaProgress?, hasCreds: Boolean) {
        if (tv == null) return
        val line = RetroAchievements.heroLine(progress, hasCreds)
        if (line != null) {
            tv.visibility = View.VISIBLE
            tv.text = line
        } else {
            tv.visibility = View.GONE
            tv.text = ""
        }
    }

    // Async screenshot under the meta block when [RomEntry.screenshotUri] is set.
    private fun bindScreenshot(
        image: ImageView?,
        cache: ArtCache,
        rom: RomEntry,
    ) {
        if (image == null) return
        val uri = HeroDetail.screenshotUri(rom)
        if (uri == null) {
            image.visibility = View.GONE
            image.setImageDrawable(null)
            image.tag = null
            return
        }
        image.visibility = View.VISIBLE
        image.tag = uri
        image.setImageDrawable(null)
        val targetPx = (320 * image.resources.displayMetrics.density).toInt()
        cache.loadUri(
            image.context,
            key = "shot:${rom.id}",
            uriString = uri,
            maxDimension = targetPx,
            isStillValid = { image.tag == uri },
        ) { bmp ->
            image.post {
                if (bmp != null && image.tag == uri && image.isAttachedToWindow) {
                    image.setImageBitmap(bmp)
                }
            }
        }
    }

    /**
     * Muted looping VideoView for [RomEntry.videoUri]. Starts after 300ms;
     * hides silently on error; stops/releases on detach or rebind.
     * [VideoView.tag] holds the bound URI string (same pattern as screenshot).
     */
    private fun bindHeroVideo(video: VideoView?, rom: RomEntry) {
        if (video == null) return
        // Cancel any pending delayed start from a previous bind.
        (video.getTag(android.R.id.message) as? Runnable)?.let { video.removeCallbacks(it) }
        runCatching { video.stopPlayback() }
        val uri = HeroDetail.videoUri(rom)
        if (uri == null) {
            video.visibility = View.GONE
            video.tag = null
            return
        }
        video.tag = uri
        video.visibility = View.VISIBLE
        val startRunnable = Runnable {
            if (!video.isAttachedToWindow) return@Runnable
            if (video.tag != uri) return@Runnable
            runCatching {
                video.setVideoURI(Uri.parse(uri))
                video.setOnPreparedListener { mp: MediaPlayer ->
                    runCatching {
                        mp.isLooping = true
                        mp.setVolume(0f, 0f)
                    }
                    if (video.isAttachedToWindow && video.tag == uri) {
                        video.start()
                    }
                }
                video.setOnErrorListener { _, _, _ ->
                    video.visibility = View.GONE
                    true
                }
            }.onFailure {
                video.visibility = View.GONE
            }
        }
        // Stash the runnable so a rebind can cancel it.
        video.setTag(android.R.id.message, startRunnable)
        video.postDelayed(startRunnable, 300L)
        // Ensure cleanup when the view leaves the window (selection rebuild).
        if (video.getTag(android.R.id.background) == null) {
            val listener = object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {}
                override fun onViewDetachedFromWindow(v: View) {
                    (v.getTag(android.R.id.message) as? Runnable)?.let {
                        v.removeCallbacks(it)
                    }
                    (v as? VideoView)?.let { vv ->
                        runCatching { vv.stopPlayback() }
                    }
                }
            }
            video.addOnAttachStateChangeListener(listener)
            video.setTag(android.R.id.background, listener)
        }
    }

    // ROM hero art chain: wide cached HERO art wins and swaps the square
    // tile for the banner; anything else (no hero, square-ish hero) keeps
    // the tile with grid art over the platform placeholder. Both loads are
    // async with the ArtTile-style stale guard (tag + attach check).
    private fun bindRomHeroArt(
        bannerFrame: FrameLayout,
        tileFrame: FrameLayout,
        cache: ArtCache,
        rom: RomEntry,
        artOverrides: Map<String, String> = emptyMap(),
        artSizePx: Int = 0,
    ) {
        val context = bannerFrame.context
        val metrics = context.resources.displayMetrics
        val image = bannerFrame.children.filterIsInstance<ImageView>().first()
        bannerFrame.visibility = View.GONE
        image.setImageDrawable(null)
        image.tag = rom.id
        tileFrame.visibility = View.VISIBLE
        val tilePx = if (artSizePx > 0) artSizePx else (240 * metrics.density).toInt()
        ArtTile.overlay(tileFrame)?.let { overlay ->
            ArtTile.bind(
                overlay, cache, rom,
                targetPx = tilePx,
                artOverrides = artOverrides,
            )
        }
        cache.load(
            context, rom,
            maxDimension = metrics.widthPixels,
            kind = ArtCache.ArtKind.HERO,
            isStillValid = { image.tag == rom.id },
        ) { bitmap ->
            image.post {
                if (bitmap != null && bitmap.width >= bitmap.height * 4 / 3 &&
                    image.tag == rom.id && image.isAttachedToWindow
                ) {
                    image.setImageBitmap(bitmap)
                    tileFrame.visibility = View.GONE
                    bannerFrame.visibility = View.VISIBLE
                }
            }
        }
    }

    /**
     * Compact selection hero for single-display TOP_STRIP. Always HERO /
     * selection context (ignores companionRole PERF_HUD / PINNED_APP).
     * Horizontal: art (fixed dp) + name / platform / player / RA — all
     * content fits inside [SelectionStrip.STRIP_HEIGHT_DP].
     */
    fun buildTopStrip(
        activity: AppCompatActivity,
        state: DeckState,
        library: AppLibrary,
        roms: List<RomEntry>,
        settings: Settings,
    ): View {
        val context: Context = activity
        val density = context.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        val app = activity.application as GhostGalleonApp
        val model = resolveStripModel(state, library, roms, settings, app)

        val root = FrameLayout(context).apply {
            tag = TAG_TOP_STRIP
            clipChildren = true
            clipToPadding = true
        }
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            tag = TAG_PANEL_ROOT
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = panelBackground(
                context,
                model.platformId?.let { PlatformLook.accentColor(it) }
                    ?: settings.accentColor,
            )
        }

        // Art host: square; holds ImageView (app) or platform tile (ROM).
        val artSize = dp(SelectionStrip.ART_SIZE_DP)
        val artHost = FrameLayout(context).apply {
            tag = TAG_STRIP_ART_HOST
        }
        bindStripArt(artHost, context, state, library, roms, settings, app, artSize)
        row.addView(artHost, LinearLayout.LayoutParams(artSize, artSize).apply {
            marginEnd = dp(12)
        })

        val texts = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
        }
        texts.addView(TextView(context).apply {
            tag = TAG_HERO_NAME
            text = model.title
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setTextColor(Color.WHITE)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ))
        texts.addView(TextView(context).apply {
            tag = TAG_HERO_SUB
            text = model.subtitle
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(0xCCFFFFFF.toInt())
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = dp(2) })
        texts.addView(TextView(context).apply {
            tag = TAG_STRIP_DETAIL
            text = model.detail.orEmpty()
            visibility = if (model.detail != null) View.VISIBLE else View.GONE
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(0x99FFFFFF.toInt())
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = dp(2) })
        texts.addView(TextView(context).apply {
            tag = TAG_HERO_RA
            text = model.raLine.orEmpty()
            visibility = if (model.raLine != null) View.VISIBLE else View.GONE
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor((settings.accentColor and 0x00FFFFFF) or (0xBB shl 24))
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = dp(2) })

        row.addView(texts, LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f,
        ))
        root.addView(row, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        ))

        // Kick live RA for ROM selection (same as full hero).
        if (model.isRom) {
            val rom = selectedRom(state.selectedKey, roms)
            if (rom != null) app.requestRaProgress(rom.id, rom.name, rom.platformId)
        }
        return root
    }

    private fun resolveStripModel(
        state: DeckState,
        library: AppLibrary,
        roms: List<RomEntry>,
        settings: Settings,
        app: GhostGalleonApp,
    ): SelectionStrip.Model {
        val rom = selectedRom(state.selectedKey, roms)
        if (rom != null) {
            val pmInstalled = { pkg: String ->
                runCatching {
                    app.packageManager.getPackageInfo(pkg, 0)
                    true
                }.getOrDefault(false)
            }
            val preferred = RomProfiles.preferredPlayerId(
                rom.id,
                settings.romProfiles,
                settings.defaultPlayers[rom.platformId],
            )
            return SelectionStrip.forRom(
                rom = rom,
                preferredPlayerId = preferred,
                installed = pmInstalled,
                playMeta = romMetaLine(settings, SlotKey.rom(rom.id))
                    .takeUnless { it.isBlank() },
                raProgress = app.raProgressFor(rom.id),
                hasRaCredentials = !settings.raApiKey.isNullOrBlank(),
            )
        }
        val entry = library.visible(settings)
            .firstOrNull { it.packageName == state.selectedKey }
        if (entry != null) return SelectionStrip.forApp(entry.label)
        return SelectionStrip.empty()
    }

    private fun bindStripArt(
        host: FrameLayout,
        context: Context,
        state: DeckState,
        library: AppLibrary,
        roms: List<RomEntry>,
        settings: Settings,
        app: GhostGalleonApp,
        artSize: Int,
    ) {
        host.removeAllViews()
        val rom = selectedRom(state.selectedKey, roms)
        if (rom != null) {
            val tile = PlatformTile.view(context, rom.platformId, cornerRadiusDp = 12).apply {
                tag = TAG_HERO_ICON
            }
            host.addView(tile, FrameLayout.LayoutParams(artSize, artSize))
            // Prefer grid art when available; platform tile stays as placeholder.
            val image = ImageView(context).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                visibility = View.GONE
            }
            host.addView(image, FrameLayout.LayoutParams(artSize, artSize))
            val romId = rom.id
            app.artCache.load(
                context, rom,
                maxDimension = artSize,
                kind = ArtCache.ArtKind.GRID,
                artOverrides = settings.artOverrides,
                isStillValid = { image.isAttachedToWindow },
            ) { bitmap ->
                image.post {
                    if (bitmap != null && image.isAttachedToWindow &&
                        selectedRom(state.selectedKey, roms)?.id == romId
                    ) {
                        image.setImageBitmap(bitmap)
                        image.visibility = View.VISIBLE
                        tile.visibility = View.GONE
                    }
                }
            }
            return
        }
        val entry = library.visible(settings)
            .firstOrNull { it.packageName == state.selectedKey }
        val image = ImageView(context).apply {
            tag = TAG_HERO_ICON
            scaleType = ImageView.ScaleType.CENTER_INSIDE
        }
        host.addView(image, FrameLayout.LayoutParams(artSize, artSize))
        if (entry != null) {
            CustomIcon.bind(
                image,
                AppIconLoader(context.packageManager),
                app.artCache,
                settings,
                entry.packageName,
                artSize,
            )
        } else {
            image.setImageResource(R.drawable.ic_brand_ship)
        }
    }

    private fun updateTopStrip(
        view: View,
        context: Context,
        state: DeckState,
        library: AppLibrary,
        roms: List<RomEntry>,
        settings: Settings,
    ): Boolean {
        val name = view.findViewWithTag<TextView>(TAG_HERO_NAME) ?: return false
        val sub = view.findViewWithTag<TextView>(TAG_HERO_SUB) ?: return false
        val detail = view.findViewWithTag<TextView>(TAG_STRIP_DETAIL) ?: return false
        val ra = view.findViewWithTag<TextView>(TAG_HERO_RA) ?: return false
        val artHost = view.findViewWithTag<FrameLayout>(TAG_STRIP_ART_HOST) ?: return false
        val app = context.applicationContext as GhostGalleonApp
        val model = resolveStripModel(state, library, roms, settings, app)
        name.text = model.title
        sub.text = model.subtitle
        detail.text = model.detail.orEmpty()
        detail.visibility = if (model.detail != null) View.VISIBLE else View.GONE
        ra.text = model.raLine.orEmpty()
        ra.visibility = if (model.raLine != null) View.VISIBLE else View.GONE
        view.findViewWithTag<View>(TAG_PANEL_ROOT)?.background = panelBackground(
            context,
            model.platformId?.let { PlatformLook.accentColor(it) } ?: settings.accentColor,
        )
        val density = context.resources.displayMetrics.density
        val artSize = (SelectionStrip.ART_SIZE_DP * density).toInt()
        bindStripArt(artHost, context, state, library, roms, settings, app, artSize)
        if (model.isRom) {
            val rom = selectedRom(state.selectedKey, roms)
            if (rom != null) app.requestRaProgress(rom.id, rom.name, rom.platformId)
        }
        return true
    }

    fun build(
        activity: AppCompatActivity,
        state: DeckState,
        library: AppLibrary,
        roms: List<RomEntry>,
        settings: Settings,
    ): View {
        val context: Context = activity
        val density = context.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        // Usable panel size in dp — bottom Sugar is short; scale hero art/name
        // so the title is never clipped by actions/hints (see CompanionHeroMetrics).
        val dm = context.resources.displayMetrics
        val panelHeightDp = dm.heightPixels / density
        val panelWidthDp = dm.widthPixels / density
        val heroSpec = CompanionHeroMetrics.forPanel(panelHeightDp, panelWidthDp)
        val artPx = dp(heroSpec.artSizeDp)

        // FrameLayout root so the fallback brand scene (clouds/sea behind,
        // rain in front) can span the WHOLE panel; all normal content lives
        // in the vertical `content` column, which carries TAG_PANEL_ROOT.
        val root = FrameLayout(context)
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            tag = TAG_PANEL_ROOT
            setPadding(dp(24), dp(12), dp(24), 0)
            clipChildren = false
            clipToPadding = false
        }

        val app = activity.application as GhostGalleonApp

        // Companion role chips (Hero / Now Playing / Perf / Pin).
        val preferredRole = CompanionRole.parse(settings.companionRole)
        val sessionPlatform = app.openSession?.key?.let { k ->
            SlotKey.platformIdOf(k)
        }
        val pinPkg = settings.companionPinnedPackage
        val pinInstalled = pinPkg != null && runCatching {
            context.packageManager.getPackageInfo(pinPkg, 0); true
        }.getOrDefault(false)
        val effectiveRole = CompanionRoleResolve.effective(
            CompanionRoleResolve.Context(
                preferred = preferredRole,
                openSessionKey = app.openSession?.key,
                pinnedPackage = pinPkg,
                openSessionPlatformId = sessionPlatform,
                pinnedPackageInstalled = pinInstalled,
            ),
        )
        val toDp: (Int) -> Int = { v -> dp(v) }
        content.addView(roleChipRow(context, settings, preferredRole, toDp) { role ->
            app.updateSettings(settings.copy(companionRole = role.name))
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { bottomMargin = dp(8) })

        when (effectiveRole) {
            CompanionRole.PERF_HUD -> {
                content.addView(buildPerfHud(context, settings, toDp), LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ))
                content.background = panelBackground(context, settings.accentColor)
                root.addView(content, FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ))
                return root
            }
            CompanionRole.PINNED_APP -> {
                content.addView(
                    buildPinnedAppPanel(activity, settings, pinPkg, pinInstalled, toDp),
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    ),
                )
                content.background = panelBackground(context, settings.accentColor)
                root.addView(content, FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ))
                return root
            }
            CompanionRole.NOW_PLAYING -> {
                // Full Now Playing as primary content when role is set.
                val session = app.openSession
                if (session != null) {
                    content.addView(
                        buildNowPlayingCard(activity, state, library, roms, settings, session, toDp),
                        LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                        ),
                    )
                    content.background = panelBackground(context, settings.accentColor)
                    root.addView(content, FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    ))
                    return root
                }
                // Fall through to hero when no session.
            }
            CompanionRole.HERO -> { /* default hero below */ }
        }

        // Now Playing banner when a session is open (game on other display).
        app.openSession?.let { session ->
            val nowPlaying = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                background = TileBackgrounds.card(context)
                setPadding(dp(16), dp(12), dp(16), dp(12))
            }
            nowPlaying.addView(TextView(context).apply {
                text = "NOW PLAYING"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setTextColor(
                    (settings.accentColor and 0x00FFFFFF) or (0xCC shl 24))
                letterSpacing = 0.12f
                gravity = Gravity.CENTER
            })
            val label = when {
                SlotKey.isRom(session.key) -> {
                    val id = SlotKey.romId(session.key)
                    roms.firstOrNull { it.id == id }?.name ?: session.key
                }
                else -> library.visible(settings)
                    .firstOrNull { it.packageName == session.key }?.label
                    ?: session.key
            }
            nowPlaying.addView(TextView(context).apply {
                text = label
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            })
            val elapsed = SessionTracker.activeElapsedMs(session, System.currentTimeMillis())
            nowPlaying.addView(TextView(context).apply {
                text = "Session ${SessionMath.formatPlaytime(elapsed)}" +
                    if (!session.isActive) " (paused)" else ""
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setTextColor(0x99FFFFFF.toInt())
                gravity = Gravity.CENTER
            })
            val npActions = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                setPadding(0, dp(8), 0, 0)
            }
            npActions.addView(TextView(context).apply {
                text = "Swap"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setTextColor(Color.BLACK)
                background = TileBackgrounds.selected(context, settings.accentColor)
                setPadding(dp(16), dp(8), dp(16), dp(8))
                setOnClickListener {
                    (activity.application as? com.visorcraft.ghostgalleon.GhostGalleonApp)
                        ?.swapInteractiveDisplay()
                }
            })
            npActions.addView(View(context), LinearLayout.LayoutParams(dp(12), 1))
            npActions.addView(TextView(context).apply {
                text = "End session"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setTextColor(Color.WHITE)
                setPadding(dp(16), dp(8), dp(16), dp(8))
                setOnClickListener { app.clearOpenSession() }
            })
            nowPlaying.addView(npActions)
            content.addView(nowPlaying, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { bottomMargin = dp(12) })
        }

        // Status pill (time + battery), top-right. Same Browse chrome flag as
        // Grid/Game overlays — off under Minimal so lower/upper hero stays clean.
        if (settings.browseChrome.deckStatusPill) {
            val pillRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.END
            }
            pillRow.addView(StatusPill.build(context, compact = false))
            content.addView(pillRow, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }

        // Hero area.
        val selected = state.selectedKey
        val selectedRom = selectedRom(selected, roms)
        val selectedEntry = if (selectedRom == null) {
            library.visible(settings).firstOrNull { it.packageName == selected }
        } else {
            null
        }
        content.background = panelBackground(
            context,
            selectedRom?.let { PlatformTile.colorFor(it.platformId) }
                ?: glowColor(context, selectedEntry?.packageName, settings),
        )
        var frontRain: android.graphics.drawable.AnimatedImageDrawable? = null
        val hero = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            // TOP + horizontal center: when space is tight, prefer keeping the
            // title (below art) over vertical centering that clips mid-glyph.
            gravity = Gravity.CENTER_HORIZONTAL or Gravity.TOP
            clipChildren = false
            clipToPadding = false
            setPadding(0, dp(4), 0, dp(4))
        }
        // Resume chip = jump to a *different* last-played title. Opt-in via
        // Browse chrome [resumeChip]. Never show for the already-selected key,
        // open session, or after user swipe-dismiss ([Settings.hideResumeChip]
        // until the next real launch). Horizontal swipe dismisses the pill;
        // tap still launches.
        run {
            if (!settings.browseChrome.resumeChip) return@run
            if (app.openSession != null) return@run
            if (settings.hideResumeChip) return@run
            val available = buildList {
                addAll(settings.gridSlots.filterNotNull())
                addAll(settings.dockSlots.filterNotNull())
                addAll(roms.filter { it.visibleInUi }.map { SlotKey.rom(it.id) })
                addAll(library.visible(settings).map { it.packageName })
                addAll(settings.lastLaunchedMs.keys)
            }
            val candidates = LibraryBrowse.continueCandidates(
                availableKeys = available,
                lastLaunchedMs = settings.lastLaunchedMs,
                excludeKey = state.selectedKey,
            )
            val cont = candidates.firstOrNull() ?: return@run
            val contName = resumeLabel(cont, library, roms, settings)
            // Filled accent pill + white text (dark card + black text was
            // unreadable on the secondary OLED).
            hero.addView(TextView(context).apply {
                text = "Resume $contName"
                contentDescription =
                    "Resume $contName. Swipe left or right to hide until next launch."
                setTextSize(TypedValue.COMPLEX_UNIT_PX, dp(14).toFloat())
                setTextColor(Color.WHITE)
                background = TileBackgrounds.accentPill(context, settings.accentColor)
                setPadding(dp(18), dp(10), dp(18), dp(10))
                gravity = Gravity.CENTER
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                isClickable = true
                isFocusable = true
                bindResumeChipGestures(
                    view = this,
                    cont = cont,
                    activity = activity,
                    state = state,
                    roms = roms,
                    app = app,
                    swipeSlopPx = dp(40).toFloat(),
                )
            }, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = dp(10)
                marginStart = dp(24)
                marginEnd = dp(24)
            })
        }
        if (selectedRom != null) {
            // ROM hero: wide HERO banner when cached (async swap-in),
            // otherwise the square tile — cached grid art over the platform
            // placeholder — then ROM name and platform label.
            val cache = (activity.application as GhostGalleonApp).artCache
            val banner = bannerFrame(context)
            val bannerH = CompanionHeroMetrics.bannerHeightPx(dm.heightPixels, heroSpec)
            hero.addView(banner, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                bannerH,
            ))
            val artFrame = ArtTile.view(
                context,
                cache,
                selectedRom,
                targetPx = artPx,
                // bindRomHeroArt below owns the single art bind for this
                // tile; binding GRID art here too would queue a redundant
                // decode that the rebind immediately obsoletes.
                bindNow = false,
            ) as FrameLayout
            // updateSelection finds the placeholder tile by this tag for
            // in-place restyle; the art overlay sits next to it in the frame.
            artFrame.children.filterIsInstance<TextView>().first().tag = TAG_HERO_ICON
            hero.addView(artFrame, LinearLayout.LayoutParams(artPx, artPx))
            bindRomHeroArt(
                banner, artFrame, cache, selectedRom, settings.artOverrides, artPx,
            )
            // Title must fit above actions on short secondary panels.
            hero.addView(TextView(context).apply {
                tag = TAG_HERO_NAME
                text = selectedRom.name
                setTextSize(TypedValue.COMPLEX_UNIT_SP, heroSpec.nameSp)
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                maxLines = heroSpec.nameMaxLines
                ellipsize = android.text.TextUtils.TruncateAt.END
                setPadding(
                    dp(heroSpec.nameSidePadDp),
                    dp(heroSpec.nameTopPadDp),
                    dp(heroSpec.nameSidePadDp),
                    dp(4),
                )
            }, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ))
            val platform = Platforms.byId(selectedRom.platformId)
            val installed = { pkg: String ->
                runCatching {
                    context.packageManager.getPackageInfo(pkg, 0)
                    true
                }.getOrDefault(false)
            }
            val preferredPlayer = RomProfiles.preferredPlayerId(
                selectedRom.id,
                settings.romProfiles,
                settings.defaultPlayers[selectedRom.platformId],
            )
            // Single compact subline saves ~2 rows on the short secondary panel.
            // Tags META/PLAYER kept GONE so updateSelection paths stay stable.
            val compact = HeroDetail.compactSubline(
                HeroDetail.platformLine(platform, selectedRom.platformId),
                romMetaLine(settings, SlotKey.rom(selectedRom.id)),
                HeroDetail.playerShortName(platform, preferredPlayer, installed),
            )
            hero.addView(TextView(context).apply {
                tag = TAG_HERO_SUB
                text = compact
                setTextSize(
                    TypedValue.COMPLEX_UNIT_SP,
                    if (heroSpec.artSizeDp < 180) 13f else 15f,
                )
                setTextColor(0xB3FFFFFF.toInt())
                gravity = Gravity.CENTER
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
                setPadding(dp(heroSpec.nameSidePadDp), dp(4), dp(heroSpec.nameSidePadDp), 0)
            }, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ))
            hero.addView(TextView(context).apply {
                tag = TAG_HERO_META
                visibility = View.GONE
            })
            val metadataText = HeroDetail.metadataLine(selectedRom)
            hero.addView(TextView(context).apply {
                tag = TAG_HERO_METADATA
                text = metadataText.orEmpty()
                visibility = if (metadataText != null && heroSpec.showExtraMedia) {
                    View.VISIBLE
                } else {
                    View.GONE
                }
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setTextColor(0x88FFFFFF.toInt())
                gravity = Gravity.CENTER
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                setPadding(dp(heroSpec.nameSidePadDp), dp(2), dp(heroSpec.nameSidePadDp), 0)
            }, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ))
            hero.addView(TextView(context).apply {
                tag = TAG_HERO_PLAYER
                visibility = View.GONE
            })
            val raLine = RetroAchievements.heroLine(
                app.raProgressFor(selectedRom.id),
                !settings.raApiKey.isNullOrBlank(),
            )
            hero.addView(TextView(context).apply {
                tag = TAG_HERO_RA
                text = raLine.orEmpty()
                visibility = if (raLine != null) View.VISIBLE else View.GONE
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setTextColor(
                    (settings.accentColor and 0x00FFFFFF) or (0xBB shl 24))
                gravity = Gravity.CENTER
                maxLines = 1
                setPadding(0, dp(2), 0, 0)
            })
            val descText = HeroDetail.descriptionText(selectedRom.description)
            hero.addView(TextView(context).apply {
                tag = TAG_HERO_DESC
                text = descText.orEmpty()
                visibility = if (descText != null && heroSpec.showExtraMedia) {
                    View.VISIBLE
                } else {
                    View.GONE
                }
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setTextColor(0xA0FFFFFF.toInt())
                gravity = Gravity.CENTER
                maxLines = if (heroSpec.showExtraMedia) 4 else 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                setPadding(dp(heroSpec.nameSidePadDp), dp(4), dp(heroSpec.nameSidePadDp), 0)
            })
            if (heroSpec.showExtraMedia) {
                val shot = ImageView(context).apply {
                    tag = TAG_HERO_SHOT
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    visibility = View.GONE
                    clipToOutline = true
                    outlineProvider = object : ViewOutlineProvider() {
                        override fun getOutline(view: View, outline: Outline) {
                            outline.setRoundRect(
                                0, 0, view.width, view.height, dp(12).toFloat())
                        }
                    }
                }
                hero.addView(shot, LinearLayout.LayoutParams(dp(280), dp(140)).apply {
                    topMargin = dp(6)
                    gravity = Gravity.CENTER_HORIZONTAL
                })
                bindScreenshot(shot, cache, selectedRom)
                val video = VideoView(context).apply {
                    tag = TAG_HERO_VIDEO
                    visibility = View.GONE
                    clipToOutline = true
                    outlineProvider = object : ViewOutlineProvider() {
                        override fun getOutline(view: View, outline: Outline) {
                            outline.setRoundRect(
                                0, 0, view.width, view.height, dp(12).toFloat())
                        }
                    }
                }
                hero.addView(video, LinearLayout.LayoutParams(dp(280), dp(140)).apply {
                    topMargin = dp(6)
                    gravity = Gravity.CENTER_HORIZONTAL
                })
                bindHeroVideo(video, selectedRom)
            }
            // Hero quick actions — same dark rounded idle chips as Hero/Now/
            // Perf/Pin (not solid accent bricks). Fixed height + baseline off
            // so glyphs paint on the Sugar secondary (see dual-paint notes).
            if (heroSpec.showQuickChips) {
                val chipH = dp(34)
                val quick = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER
                    isBaselineAligned = false
                    setPadding(0, dp(8), 0, dp(2))
                }
                fun quickChip(label: String, onClick: () -> Unit): TextView =
                    TextView(context).apply {
                        text = label
                        contentDescription = label
                        setTextSize(TypedValue.COMPLEX_UNIT_PX, dp(12).toFloat())
                        setTextColor(Color.WHITE)
                        background = TileBackgrounds.chip(context)
                        gravity = Gravity.CENTER
                        setPadding(dp(12), 0, dp(12), 0)
                        includeFontPadding = true
                        setSingleLine()
                        isClickable = true
                        isFocusable = true
                        setOnClickListener { onClick() }
                    }
                fun addChip(label: String, onClick: () -> Unit) {
                    if (quick.childCount > 0) {
                        quick.addView(
                            View(context),
                            LinearLayout.LayoutParams(dp(6), chipH),
                        )
                    }
                    quick.addView(
                        quickChip(label, onClick),
                        LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            chipH,
                        ),
                    )
                }
                val romKey = SlotKey.rom(selectedRom.id)
                addChip(
                    if (romKey in settings.favorites) "Unfavorite" else "Favorite",
                ) {
                    val next = CollectionsOps.toggleFavorite(settings.favorites, romKey)
                    app.updateSettings(settings.copy(favorites = next))
                }
                addChip("Pin") {
                    val filled = CollectionsOps.bulkFillSlots(
                        settings.gridSlots, listOf(romKey))
                    app.updateSettings(settings.copy(gridSlots = filled))
                    android.widget.Toast.makeText(
                        activity, "Pinned to grid", android.widget.Toast.LENGTH_SHORT).show()
                }
                addChip("Art") {
                    (activity as? com.visorcraft.ghostgalleon.ui.BaseDeckActivity)
                        ?.requestCustomIcon { uri ->
                            app.artCache.invalidate(selectedRom.id)
                            app.updateSettings(settings.copy(
                                artOverrides = settings.artOverrides +
                                    (selectedRom.id to uri.toString())))
                        }
                }
                addChip("Open with") {
                    val openPlatform = Platforms.byId(selectedRom.platformId)
                    val players = openPlatform?.players.orEmpty()
                    if (players.isEmpty()) {
                        android.widget.Toast.makeText(
                            activity, "No players", android.widget.Toast.LENGTH_SHORT).show()
                    } else {
                        android.app.AlertDialog.Builder(activity)
                            .setTitle("Open with")
                            .setItems(players.map { it.displayName }.toTypedArray()) { _, which ->
                                val p = players[which]
                                app.updateSettings(
                                    settings.copy(
                                        defaultPlayers = settings.defaultPlayers +
                                            (selectedRom.platformId to p.id),
                                    ),
                                    notify = false,
                                )
                                launchSlotKey(
                                    activity, state, roms, romKey, playerId = p.id)
                            }
                            .setNegativeButton("Cancel", null)
                            .show()
                    }
                }
                hero.addView(
                    quick,
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ),
                )
            }
        } else if (selectedEntry != null) {
            val icon = ImageView(context)
            icon.tag = TAG_HERO_ICON
            CustomIcon.bind(
                icon, AppIconLoader(context.packageManager),
                (activity.application as GhostGalleonApp).artCache,
                settings, selectedEntry.packageName, artPx)
            hero.addView(icon, LinearLayout.LayoutParams(artPx, artPx))
            hero.addView(TextView(context).apply {
                tag = TAG_HERO_NAME
                text = selectedEntry.label
                setTextSize(TypedValue.COMPLEX_UNIT_SP, heroSpec.nameSp)
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                maxLines = heroSpec.nameMaxLines
                ellipsize = android.text.TextUtils.TruncateAt.END
                setPadding(
                    dp(heroSpec.nameSidePadDp),
                    dp(heroSpec.nameTopPadDp),
                    dp(heroSpec.nameSidePadDp),
                    dp(4),
                )
            }, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ))
        } else {
            // Layered brand fallback, full-panel: clouds + sea behind the
            // content column (transparent so the scene shows edge to edge).
            // Ship is a root FrameLayout overlay (not inside the hero column)
            // so role chips / status pill never pull it off the waterline.
            // Size scales with panel height; hull sits on the clouds:sea 3:2
            // horizon (CompanionHeroMetrics.brandShipLayout) — works on any
            // dual/single panel, not only Sugar. Rain is added LAST so it
            // falls in front of ship + UI.
            // Clouds/sea are exact vertical slices of one 1280×720 scene
            // (432px sky, 288px water), stacked 3:2 so the horizon keeps its
            // authored 60/40 split (CENTER_CROP trims width overflow). Every
            // layer is optional: no sky/sea = glow panel, no ship anim (or
            // pre-API-28) = ic_brand_ship, no rain = none. hero_ocean_anim is
            // the legacy single-file background when the slice pair is incomplete.
            val clouds = loadAnimated(context, "hero_clouds_anim")
            val sea = loadAnimated(context, "hero_sea_anim")
            val ocean = if (clouds == null || sea == null) {
                loadAnimated(context, "hero_ocean_anim")
            } else {
                null
            }
            if (clouds != null && sea != null) {
                val bgColumn = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                }
                listOf(clouds to 3f, sea to 2f).forEach { (anim, weight) ->
                    bgColumn.addView(ImageView(context).apply {
                        scaleType = ImageView.ScaleType.CENTER_CROP
                        setImageDrawable(anim)
                        anim.start()
                    }, LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, 0, weight))
                }
                root.addView(bgColumn, FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT))
                content.background = null
            } else {
                val singles = listOfNotNull(clouds, sea ?: ocean)
                singles.forEach { anim ->
                    root.addView(ImageView(context).apply {
                        scaleType = ImageView.ScaleType.CENTER_CROP
                        setImageDrawable(anim)
                        anim.start()
                    }, FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT))
                }
                if (singles.isNotEmpty()) content.background = null
            }
            // z-order: bg (already added) → content (later) → ship → rain.
            // Transparent content regions show the ship on the waterline.
            val shipLayout = CompanionHeroMetrics.brandShipLayout(
                panelHeightPx = dm.heightPixels,
                density = density,
            )
            if (shipLayout.sizePx > 0) {
                root.addView(ImageView(context).apply {
                    tag = TAG_HERO_ICON
                    contentDescription = "Ghost Galleon"
                    val ship = loadAnimated(context, "hero_ship_anim")
                    if (ship != null) {
                        setImageDrawable(ship)
                        ship.start()
                    } else {
                        setImageResource(R.drawable.ic_brand_ship)
                    }
                }, FrameLayout.LayoutParams(shipLayout.sizePx, shipLayout.sizePx).apply {
                    gravity = Gravity.CENTER_HORIZONTAL or Gravity.TOP
                    topMargin = shipLayout.topMarginPx
                })
            }
            val tokens = com.visorcraft.ghostgalleon.settings.ThemePack.resolve(settings)
            if (tokens.heroRain) {
                frontRain = loadAnimated(context, "hero_rain_anim")
            }
        }
        content.addView(hero, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        // Tappable swap/settings buttons: swap at the FAR LEFT of the row,
        // settings at the right (the grid dock no longer carries either).
        val actions = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), 0, dp(8), 0)
        }
        actions.addView(
            iconButton(context, R.drawable.ic_swap, "Swap screens") {
                val appCtx = activity.application as? GhostGalleonApp
                if (appCtx != null && !appCtx.swapInteractiveDisplay()) {
                    android.widget.Toast.makeText(
                        context,
                        "Only one display — swap unavailable",
                        android.widget.Toast.LENGTH_SHORT,
                    ).show()
                }
            },
            LinearLayout.LayoutParams(dp(40), dp(40)))
        actions.addView(View(context), LinearLayout.LayoutParams(0, 1, 1f))
        actions.addView(
            iconButton(context, R.drawable.ic_settings, "Settings") {
                // Same display routing as START: settings opens opposite the
                // interactive deck.
                launchOnOtherDisplay(
                    activity, state, Intent(activity, SettingsActivity::class.java))
            },
            LinearLayout.LayoutParams(dp(40), dp(40)))
        content.addView(actions, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        if (settings.showHints) {
            content.addView(HintBar.build(context), LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        root.addView(content, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT))
        // Rain falls in front of the ship (and everything else).
        frontRain?.let { rain ->
            root.addView(ImageView(context).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                setImageDrawable(rain)
                rain.start()
            }, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT))
        }
        return root
    }

    private fun roleChipRow(
        context: Context,
        settings: Settings,
        current: CompanionRole,
        dp: (Int) -> Int,
        onPick: (CompanionRole) -> Unit,
    ): View {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        row.isBaselineAligned = false
        fun chip(role: CompanionRole, label: String) {
            row.addView(TextView(context).apply {
                text = label
                setTextSize(TypedValue.COMPLEX_UNIT_PX, dp(12).toFloat())
                setTextColor(if (current == role) Color.BLACK else Color.WHITE)
                setBackgroundColor(
                    if (current == role) settings.accentColor
                    else TileBackgrounds.chipIdleColor(context))
                gravity = Gravity.CENTER
                setPadding(dp(10), 0, dp(10), 0)
                setSingleLine()
                setOnClickListener { onPick(role) }
            }, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(32),
            ).apply { marginEnd = dp(6) })
        }
        chip(CompanionRole.HERO, "Hero")
        chip(CompanionRole.NOW_PLAYING, "Now")
        chip(CompanionRole.PERF_HUD, "Perf")
        chip(CompanionRole.PINNED_APP, "Pin")
        return row
    }

    private fun buildPerfHud(
        context: Context,
        settings: Settings,
        dp: (Int) -> Int,
    ): View {
        val col = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(8), dp(16), dp(8), dp(16))
        }
        col.addView(TextView(context).apply {
            text = "PERF HUD"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor((settings.accentColor and 0x00FFFFFF) or (0xCC shl 24))
            letterSpacing = 0.12f
            gravity = Gravity.CENTER
        })
        val readings = SystemInfoCollector.collect(context)
        SystemInfoFormat.rows(readings).forEach { (label, value) ->
            col.addView(TextView(context).apply {
                text = "$label"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setTextColor(0x88FFFFFF.toInt())
                setPadding(0, dp(10), 0, 0)
            })
            col.addView(TextView(context).apply {
                text = value
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                setTextColor(Color.WHITE)
            })
        }
        return col
    }

    private fun buildPinnedAppPanel(
        activity: AppCompatActivity,
        settings: Settings,
        pinPkg: String?,
        installed: Boolean,
        dp: (Int) -> Int,
    ): View {
        val col = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(24), dp(16), dp(16))
        }
        col.addView(TextView(activity).apply {
            text = "PINNED APP"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor((settings.accentColor and 0x00FFFFFF) or (0xCC shl 24))
            letterSpacing = 0.12f
            gravity = Gravity.CENTER
        })
        if (pinPkg.isNullOrBlank() || !installed) {
            col.addView(TextView(activity).apply {
                text = if (pinPkg.isNullOrBlank()) {
                    "Set pin in Settings → Companion"
                } else {
                    "Pinned app not installed"
                }
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                setTextColor(0x99FFFFFF.toInt())
                gravity = Gravity.CENTER
                setPadding(0, dp(16), 0, 0)
            })
        } else {
            val label = runCatching {
                val pm = activity.packageManager
                pm.getApplicationLabel(pm.getApplicationInfo(pinPkg, 0)).toString()
            }.getOrDefault(pinPkg)
            col.addView(TextView(activity).apply {
                text = label
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                setPadding(0, dp(12), 0, dp(16))
            })
            col.addView(TextView(activity).apply {
                text = "Launch pin"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                setTextColor(Color.BLACK)
                background = TileBackgrounds.selected(activity, settings.accentColor)
                setPadding(dp(20), dp(12), dp(20), dp(12))
                gravity = Gravity.CENTER
                setOnClickListener {
                    val intent = activity.packageManager.getLaunchIntentForPackage(pinPkg)
                        ?: return@setOnClickListener
                    val displayId = activity.display?.displayId ?: 0
                    val options = ActivityOptions.makeBasic().setLaunchDisplayId(displayId)
                    runCatching {
                        activity.startActivity(intent, options.toBundle())
                    }
                }
            })
        }
        return col
    }

    private fun buildNowPlayingCard(
        activity: AppCompatActivity,
        state: DeckState,
        library: AppLibrary,
        roms: List<RomEntry>,
        settings: Settings,
        session: com.visorcraft.ghostgalleon.library.OpenSession,
        dp: (Int) -> Int,
    ): View {
        val app = activity.application as GhostGalleonApp
        val nowPlaying = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = TileBackgrounds.card(activity)
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        nowPlaying.addView(TextView(activity).apply {
            text = "NOW PLAYING"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor((settings.accentColor and 0x00FFFFFF) or (0xCC shl 24))
            letterSpacing = 0.12f
            gravity = Gravity.CENTER
        })
        val label = when {
            SlotKey.isRom(session.key) -> {
                val id = SlotKey.romId(session.key)
                roms.firstOrNull { it.id == id }?.name ?: session.key
            }
            else -> library.visible(settings)
                .firstOrNull { it.packageName == session.key }?.label
                ?: session.key
        }
        nowPlaying.addView(TextView(activity).apply {
            text = label
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
        })
        val elapsed = SessionTracker.activeElapsedMs(session, System.currentTimeMillis())
        nowPlaying.addView(TextView(activity).apply {
            text = "Session ${SessionMath.formatPlaytime(elapsed)}" +
                if (!session.isActive) " (paused)" else ""
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(0x99FFFFFF.toInt())
            gravity = Gravity.CENTER
        })
        val actions = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, 0)
        }
        actions.addView(TextView(activity).apply {
            text = "Swap"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(Color.BLACK)
            background = TileBackgrounds.selected(activity, settings.accentColor)
            setPadding(dp(16), dp(8), dp(16), dp(8))
            setOnClickListener {
                (activity.application as? com.visorcraft.ghostgalleon.GhostGalleonApp)
                    ?.swapInteractiveDisplay()
            }
        })
        actions.addView(View(activity), LinearLayout.LayoutParams(dp(12), 1))
        actions.addView(TextView(activity).apply {
            text = "End session"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(Color.WHITE)
            setPadding(dp(16), dp(8), dp(16), dp(8))
            setOnClickListener { app.clearOpenSession() }
        })
        nowPlaying.addView(actions)
        return nowPlaying
    }
}
