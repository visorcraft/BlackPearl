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
import android.os.BatteryManager
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextClock
import android.widget.TextView
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.children
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
import com.visorcraft.ghostgalleon.rom.PlatformTile
import com.visorcraft.ghostgalleon.rom.Platforms
import com.visorcraft.ghostgalleon.rom.RomEntry
import com.visorcraft.ghostgalleon.rom.RomProfiles
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
    private const val TAG_PANEL_ROOT = "panel_root"
    private const val TAG_ROLE_CHIPS = "role_chips"

    // Layered depth background: a vertical gradient lifting to #FF202028 in
    // the center band, plus a huge soft radial glow behind the hero icon
    // tinted with the glow color at ~18% alpha.
    private fun panelBackground(context: Context, glowColor: Int): Drawable {
        val gradient = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(
                0xFF000000.toInt(),
                0xFF202028.toInt(),
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
            bindRomHeroArt(
                banner,
                tileFrame,
                (context.applicationContext as GhostGalleonApp).artCache,
                rom,
                settings.artOverrides,
            )
            name.text = rom.name
            val platform = Platforms.byId(rom.platformId)
            sub.text = HeroDetail.platformLine(platform, rom.platformId)
            view.findViewWithTag<TextView>(TAG_HERO_META)?.text =
                romMetaLine(settings, SlotKey.rom(rom.id))
            bindMetadataLine(view.findViewWithTag(TAG_HERO_METADATA), rom)
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
            view.findViewWithTag<TextView>(TAG_HERO_PLAYER)?.text =
                HeroDetail.playerLine(platform, preferred, installed) ?: ""
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
            view.findViewWithTag<View>(TAG_PANEL_ROOT)?.background =
                panelBackground(context, PlatformTile.colorFor(rom.platformId))
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
    internal fun romMetaLine(settings: Settings, slotKey: String): String {
        val parts = mutableListOf<String>()
        SessionMath.formatLastPlayed(
            settings.lastLaunchedMs[slotKey],
            System.currentTimeMillis(),
        )?.let { parts.add(it) }
        val played = settings.playtimeMs[slotKey] ?: 0L
        if (played > 0L) parts.add("Played ${SessionMath.formatPlaytime(played)}")
        return parts.joinToString(" · ").ifEmpty { "Never played" }
    }

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
    ) {
        val context = bannerFrame.context
        val metrics = context.resources.displayMetrics
        val image = bannerFrame.children.filterIsInstance<ImageView>().first()
        bannerFrame.visibility = View.GONE
        image.setImageDrawable(null)
        image.tag = rom.id
        tileFrame.visibility = View.VISIBLE
        ArtTile.overlay(tileFrame)?.let { overlay ->
            ArtTile.bind(
                overlay, cache, rom,
                targetPx = (240 * metrics.density).toInt(),
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

        // FrameLayout root so the fallback brand scene (clouds/sea behind,
        // rain in front) can span the WHOLE panel; all normal content lives
        // in the vertical `content` column, which carries TAG_PANEL_ROOT.
        val root = FrameLayout(context)
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            tag = TAG_PANEL_ROOT
            setPadding(dp(24), dp(20), dp(24), 0)
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
                setOnClickListener { state.swapDisplays() }
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

        // Status pill, top-right.
        val pillRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }
        val pill = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = TileBackgrounds.pill(context)
            setPadding(dp(20), dp(8), dp(20), dp(8))
        }
        val batteryManager = context.getSystemService(BatteryManager::class.java)
        val batteryPct = batteryManager
            ?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
        if (batteryPct in 0..100) {
            pill.addView(TextView(context).apply {
                text = "$batteryPct%"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
                setTextColor(Color.WHITE)
                setPadding(0, 0, dp(12), 0)
            })
        }
        pill.addView(TextClock(context).apply {
            format12Hour = "h:mm a"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            setTextColor(Color.WHITE)
        })
        pillRow.addView(pill)
        content.addView(pillRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

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
            gravity = Gravity.CENTER
        }
        // Continue chip near hero when a continue target is known.
        run {
            val available = buildList {
                addAll(settings.gridSlots.filterNotNull())
                addAll(settings.dockSlots.filterNotNull())
                addAll(roms.filter { it.visibleInUi }.map { SlotKey.rom(it.id) })
                addAll(library.visible(settings).map { it.packageName })
                addAll(settings.lastLaunchedMs.keys)
            }
            val cont = LibraryBrowse.continueKey(available, settings.lastLaunchedMs)
            if (cont != null) {
                val contName = when {
                    SlotKey.isRom(cont) -> {
                        val id = SlotKey.romId(cont)
                        roms.firstOrNull { it.id == id }?.name ?: cont
                    }
                    else -> library.visible(settings)
                        .firstOrNull { it.packageName == cont }?.label ?: cont
                }
                hero.addView(TextView(context).apply {
                    text = "Continue: $contName"
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                    setTextColor(Color.BLACK)
                    background = TileBackgrounds.selected(context, settings.accentColor)
                    setPadding(dp(16), dp(8), dp(16), dp(8))
                    gravity = Gravity.CENTER
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    setOnClickListener {
                        val idx = settings.gridSlots.indexOf(cont)
                        if (idx >= 0) state.selectSlot(idx, cont) else state.select(cont)
                        launchSlotKey(activity, state, roms, cont)
                    }
                }, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply {
                    gravity = Gravity.CENTER_HORIZONTAL
                    bottomMargin = dp(12)
                })
            }
        }
        if (selectedRom != null) {
            // ROM hero: wide HERO banner when cached (async swap-in),
            // otherwise the square tile — cached grid art over the platform
            // placeholder — then ROM name and platform label.
            val cache = (activity.application as GhostGalleonApp).artCache
            val banner = bannerFrame(context)
            hero.addView(banner, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                context.resources.displayMetrics.heightPixels * 2 / 5,
            ))
            val artFrame = ArtTile.view(
                context,
                cache,
                selectedRom,
                targetPx = dp(240),
                // bindRomHeroArt below owns the single art bind for this
                // tile; binding GRID art here too would queue a redundant
                // decode that the rebind immediately obsoletes.
                bindNow = false,
            ) as FrameLayout
            // updateSelection finds the placeholder tile by this tag for
            // in-place restyle; the art overlay sits next to it in the frame.
            artFrame.children.filterIsInstance<TextView>().first().tag = TAG_HERO_ICON
            hero.addView(artFrame, LinearLayout.LayoutParams(dp(240), dp(240)))
            bindRomHeroArt(banner, artFrame, cache, selectedRom, settings.artOverrides)
            hero.addView(TextView(context).apply {
                tag = TAG_HERO_NAME
                text = selectedRom.name
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 32f)
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
                setPadding(0, dp(24), 0, 0)
            })
            hero.addView(TextView(context).apply {
                tag = TAG_HERO_SUB
                text = Platforms.byId(selectedRom.platformId)?.displayName
                    ?: selectedRom.platformId
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                setTextColor(0x99FFFFFF.toInt())
                gravity = Gravity.CENTER
                setPadding(0, dp(6), 0, 0)
            })
            hero.addView(TextView(context).apply {
                tag = TAG_HERO_META
                text = romMetaLine(settings, SlotKey.rom(selectedRom.id))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setTextColor(0x88FFFFFF.toInt())
                gravity = Gravity.CENTER
                setPadding(0, dp(4), 0, 0)
            })
            val metadataText = HeroDetail.metadataLine(selectedRom)
            hero.addView(TextView(context).apply {
                tag = TAG_HERO_METADATA
                text = metadataText.orEmpty()
                visibility = if (metadataText != null) View.VISIBLE else View.GONE
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setTextColor(0x88FFFFFF.toInt())
                gravity = Gravity.CENTER
                setPadding(0, dp(2), 0, 0)
            })
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
            hero.addView(TextView(context).apply {
                tag = TAG_HERO_PLAYER
                text = HeroDetail.playerLine(
                    platform,
                    preferredPlayer,
                    installed,
                ).orEmpty()
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setTextColor(0x99FFFFFF.toInt())
                gravity = Gravity.CENTER
                setPadding(0, dp(2), 0, 0)
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
                setPadding(0, dp(2), 0, 0)
            })
            val descText = HeroDetail.descriptionText(selectedRom.description)
            hero.addView(TextView(context).apply {
                tag = TAG_HERO_DESC
                text = descText.orEmpty()
                visibility = if (descText != null) View.VISIBLE else View.GONE
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setTextColor(0xA0FFFFFF.toInt())
                gravity = Gravity.START
                // Scrollable multi-line blurb (up to ~8 lines before ellipsis).
                maxLines = 8
                ellipsize = android.text.TextUtils.TruncateAt.END
                setPadding(dp(16), dp(8), dp(16), 0)
            })
            val shot = ImageView(context).apply {
                tag = TAG_HERO_SHOT
                scaleType = ImageView.ScaleType.CENTER_CROP
                visibility = View.GONE
                clipToOutline = true
                outlineProvider = object : ViewOutlineProvider() {
                    override fun getOutline(view: View, outline: Outline) {
                        outline.setRoundRect(0, 0, view.width, view.height, dp(12).toFloat())
                    }
                }
            }
            hero.addView(shot, LinearLayout.LayoutParams(dp(320), dp(180)).apply {
                topMargin = dp(10)
                gravity = Gravity.CENTER_HORIZONTAL
            })
            bindScreenshot(shot, cache, selectedRom)
            // Optional video snap (muted loop) below/alongside screenshot.
            val video = VideoView(context).apply {
                tag = TAG_HERO_VIDEO
                visibility = View.GONE
                clipToOutline = true
                outlineProvider = object : ViewOutlineProvider() {
                    override fun getOutline(view: View, outline: Outline) {
                        outline.setRoundRect(0, 0, view.width, view.height, dp(12).toFloat())
                    }
                }
            }
            hero.addView(video, LinearLayout.LayoutParams(dp(320), dp(180)).apply {
                topMargin = dp(10)
                gravity = Gravity.CENTER_HORIZONTAL
            })
            bindHeroVideo(video, selectedRom)
            // Hero quick actions for the selected ROM (Phase 3).
            val quick = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                setPadding(0, dp(12), 0, 0)
            }
            fun quickChip(label: String, onClick: () -> Unit) =
                TextView(context).apply {
                    text = label
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                    setTextColor(Color.WHITE)
                    setBackgroundColor(0xFF2A2A32.toInt())
                    setPadding(dp(12), dp(8), dp(12), dp(8))
                    setOnClickListener { onClick() }
                }
            val romKey = SlotKey.rom(selectedRom.id)
            quick.addView(quickChip(
                if (romKey in settings.favorites) "Unfav" else "Fav",
            ) {
                val next = CollectionsOps.toggleFavorite(settings.favorites, romKey)
                app.updateSettings(settings.copy(favorites = next))
            })
            quick.addView(View(context), LinearLayout.LayoutParams(dp(8), 1))
            quick.addView(quickChip("Pin") {
                val filled = CollectionsOps.bulkFillSlots(
                    settings.gridSlots, listOf(romKey))
                app.updateSettings(settings.copy(gridSlots = filled))
                android.widget.Toast.makeText(
                    activity, "Pinned to grid", android.widget.Toast.LENGTH_SHORT).show()
            })
            quick.addView(View(context), LinearLayout.LayoutParams(dp(8), 1))
            quick.addView(quickChip("Art") {
                (activity as? com.visorcraft.ghostgalleon.ui.BaseDeckActivity)
                    ?.requestCustomIcon { uri ->
                        app.artCache.invalidate(selectedRom.id)
                        app.updateSettings(settings.copy(
                            artOverrides = settings.artOverrides +
                                (selectedRom.id to uri.toString())))
                    }
            })
            quick.addView(View(context), LinearLayout.LayoutParams(dp(8), 1))
            quick.addView(quickChip("Open with") {
                // Reuse grid's player picker via a small inline dialog.
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
            })
            hero.addView(quick)
        } else if (selectedEntry != null) {
            val icon = ImageView(context)
            icon.tag = TAG_HERO_ICON
            CustomIcon.bind(
                icon, AppIconLoader(context.packageManager),
                (activity.application as GhostGalleonApp).artCache,
                settings, selectedEntry.packageName, dp(240))
            hero.addView(icon, LinearLayout.LayoutParams(dp(240), dp(240)))
            hero.addView(TextView(context).apply {
                tag = TAG_HERO_NAME
                text = selectedEntry.label
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 32f)
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                setPadding(0, dp(24), 0, 0)
            })
        } else {
            // Layered brand fallback, full-panel: clouds + sea behind the
            // content column (which goes transparent so the scene shows
            // edge to edge), ship at a fixed 240dp inside the hero area,
            // rain IN FRONT of everything (added after `content` at the
            // end of build). Clouds/sea are exact vertical slices of one
            // 1280×720 scene (432px sky, 288px water), stacked in a 3:2
            // weighted column so the horizon keeps its authored 60/40
            // split at any panel size (CENTER_CROP trims width overflow).
            // Every layer is independently optional: no sky/sea = normal
            // glow panel, no ship anim (or pre-API-28) = static
            // ic_brand_ship, no rain = none. hero_ocean_anim is the legacy
            // single-file background, used only when the slice pair is
            // incomplete.
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
            hero.addView(ImageView(context).apply {
                val ship = loadAnimated(context, "hero_ship_anim")
                if (ship != null) {
                    setImageDrawable(ship)
                    ship.start()
                } else {
                    setImageResource(R.drawable.ic_brand_ship)
                }
            }, LinearLayout.LayoutParams(dp(240), dp(240)).apply {
                // Nudge the ship below panel center so its hull rides the
                // horizon line (~32px below center at this density).
                topMargin = dp(16)
            })
            frontRain = loadAnimated(context, "hero_rain_anim")
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
                state.swapDisplays()
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
        fun chip(role: CompanionRole, label: String) {
            row.addView(TextView(context).apply {
                text = label
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setTextColor(if (current == role) Color.BLACK else Color.WHITE)
                setBackgroundColor(
                    if (current == role) settings.accentColor else 0xFF2A2A32.toInt())
                setPadding(dp(10), dp(6), dp(10), dp(6))
                setOnClickListener { onPick(role) }
            }, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
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
            setOnClickListener { state.swapDisplays() }
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
