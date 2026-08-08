package com.visorcraft.ghostgalleon.ui

import android.app.role.RoleManager
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.visorcraft.ghostgalleon.BuildConfig
import android.text.InputType
import android.view.HapticFeedbackConstants
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import android.widget.LinearLayout
import com.visorcraft.ghostgalleon.GhostGalleonApp
import com.visorcraft.ghostgalleon.display.CompanionHeroStyle
import com.visorcraft.ghostgalleon.display.LayoutMetricsResolver
import com.visorcraft.ghostgalleon.display.SurfaceMode
import com.visorcraft.ghostgalleon.input.KeyMap
import com.visorcraft.ghostgalleon.input.NavRepeater
import com.visorcraft.ghostgalleon.library.AppLibrary
import com.visorcraft.ghostgalleon.library.PackageManagerAppsSource
import com.visorcraft.ghostgalleon.library.SetupNeeds
import com.visorcraft.ghostgalleon.sensor.OrientationController
import com.visorcraft.ghostgalleon.settings.Action
import com.visorcraft.ghostgalleon.settings.Settings
import com.visorcraft.ghostgalleon.settings.SlotKey
import com.visorcraft.ghostgalleon.state.DeckState
import com.visorcraft.ghostgalleon.state.UIMode
import com.visorcraft.ghostgalleon.rom.isInstalled
import com.visorcraft.ghostgalleon.ui.deck.AppIconLoader
import com.visorcraft.ghostgalleon.ui.deck.AppPicker
import com.visorcraft.ghostgalleon.ui.deck.DeckOverlays
import com.visorcraft.ghostgalleon.ui.deck.CompanionPanel
import com.visorcraft.ghostgalleon.ui.deck.Deck
import com.visorcraft.ghostgalleon.ui.deck.GameDeck
import com.visorcraft.ghostgalleon.ui.deck.GridDeck
import com.visorcraft.ghostgalleon.ui.deck.QuickPanel
import com.visorcraft.ghostgalleon.ui.deck.launchOnOtherDisplay
import com.visorcraft.ghostgalleon.ui.deck.launchSlotKey
import com.visorcraft.ghostgalleon.ui.settings.SettingsActivity
import com.visorcraft.ghostgalleon.ui.settings.SetupCard

// Stick hysteresis thresholds: engage at 0.7, stay engaged until under 0.5.
private const val AXIS_ENGAGE_THRESHOLD = 0.7f
private const val AXIS_RELEASE_THRESHOLD = 0.5f

abstract class BaseDeckActivity : AppCompatActivity() {

    companion object {
        /** Logcat tag for full-paint diagnostics (`adb logcat -s GGPaint`). */
        const val PAINT_TAG = "GGPaint"
    }

    protected val app: GhostGalleonApp get() = application as GhostGalleonApp
    protected val deckState: DeckState get() = app.deckState
    protected val settings: Settings get() = app.settings

    private val stateListener = DeckState.DeckStateListener { onDeckStateChanged() }

    // Selection-only changes update the already-built views in place;
    // everything else (mode, display, settings) keeps the full rebuild.
    private fun onDeckStateChanged() {
        // Never re-enter setContentView while a full paint is in progress —
        // that left both physical panels pure black. Queue a deferred paint
        // instead of dropping the mutation (browse chips / SETTINGS must land).
        if (rendering) {
            scheduleDeferredFullRender(0L, "nested-state ${deckState.lastChange}")
            return
        }
        if (deckState.lastChange == DeckState.Change.SELECTION && ::currentDeck.isInitialized) {
            val role = DisplayRole.roleFor(display?.displayId ?: 0, deckState)
            val content = findViewById<ViewGroup>(android.R.id.content)
            // updateSelection returns false when structure must rebuild (e.g.
            // ROM↔app). Never treat deck-only success as enough when a hero
            // strip is present and failed to update — that leaves a stale TOP_STRIP.
            val stripPresent = content != null &&
                content.findViewWithTag<View>(CompanionPanel.TAG_TOP_STRIP) != null
            val panelUpdated = content != null && content.childCount > 0 &&
                CompanionPanel.updateSelection(
                    content, this, deckState, appLibrary, app.romEntries, settings)
            val updated = when (role) {
                DisplayRole.PRIMARY -> {
                    val deckOk = currentDeck.updateSelection()
                    when {
                        stripPresent && !panelUpdated -> false
                        stripPresent -> deckOk && panelUpdated
                        else -> deckOk
                    }
                }
                // Full dual-screen companion is NOT the TOP_STRIP; in-place
                // hero update is enough — do not fall through to full rebuild
                // when panelUpdated is true.
                DisplayRole.COMPANION -> panelUpdated
            }
            if (updated) return
        }
        renderFromState("state ${deckState.lastChange}")
    }

    private var rendering: Boolean = false
    private var lastFullRenderUptimeMs: Long = 0L
    private var fullRenderCount: Int = 0
    // When allowFullRender coalesces/blocks a SETTINGS rebuild, retry after
    // the gap so browse chips (All / platform) never leave a stale carousel.
    private var pendingFullRender: Boolean = false
    private val paintHandler = Handler(Looper.getMainLooper())
    private val deferredPaintRunnable = Runnable {
        if (!pendingFullRender) return@Runnable
        pendingFullRender = false
        if (!isFinishing && !isDestroyed) {
            renderFromState("deferred")
        }
    }

    private val orientationController by lazy { OrientationController(this) { settings } }

    // Unified hold-to-repeat for NAV actions: Android does not auto-repeat
    // gamepad buttons, so both the key path (onPress on ACTION_DOWN,
    // onRelease on ACTION_UP) and the stick path (hysteresis edges below)
    // drive this engine, which routes repeats into handleAction.
    private val navRepeater by lazy {
        NavRepeater(NavRepeater.HandlerScheduler(Handler(Looper.getMainLooper()))) {
            handleAction(it)
        }
    }

    // True after onStop until the next onResume. Used to distinguish
    // "still on home" HOME redelivery (open drawer) from returning from
    // another app (land on grid). Does NOT force a UI rebuild by itself.
    private var stoppedSinceResume: Boolean = false

    /** Whether this activity left the foreground since the last resume. */
    protected fun leftHomeSinceResume(): Boolean = stoppedSinceResume

    // Content epoch applied by the last renderFromState(). Resume rebuilds
    // only when settings/ROMs changed while we were backgrounded — not on
    // every SECONDARY_HOME flash from Quickstep.
    private var appliedContentEpoch: Int = -1

    // Display we last painted for. Multi-display setLaunchDisplayId often
    // leaves displayId wrong/null in onCreate; painting then produces a live
    // view tree with a pure-black hardware buffer on the real panel.
    private var paintedForDisplayId: Int? = null

    // Swipe-up / re-HOME drawer: launch any app or ROM without reloading
    // the deck. Separate from the per-slot "Add to grid/dock" pickers.
    private var appDrawer: AppPicker? = null

    // Select-button Quick Panel overlay (Wi‑Fi / Continue / theme / …).
    private var quickPanel: QuickPanel? = null

    // Open drawer on next resume (set when a discarded SECONDARY_HOME
    // duplicate asks the surviving companion to show the drawer).
    private var pendingAppDrawer: Boolean = false

    // Subclasses (CompanionActivity) can skip the initial render when they
    // are about to finish immediately as a duplicate SECONDARY_HOME.
    protected open fun shouldRenderOnCreate(): Boolean = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Opaque window so a dual-display HOME transition cannot peek the
        // system/Quickstep wallpaper (robot-with-box) through Ghost Galleon.
        window.setBackgroundDrawable(ColorDrawable(0xFF000000.toInt()))
        // Only paint in onCreate when this window already has a real display
        // id (Main on default display usually does). Companion launched with
        // setLaunchDisplayId often still reports display 0/null here — painting
        // then blacks the secondary panel. onResume / onAttachedToWindow paint.
        if (shouldRenderOnCreate() && display != null) {
            renderFromState("onCreate d=${display?.displayId}")
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        val d = display?.displayId
        if (shouldRenderOnCreate() &&
            !isFinishing &&
            DualPaintPolicy.needsPaintForDisplay(
                hasPainted = ::currentDeck.isInitialized,
                paintedDisplayId = paintedForDisplayId,
                currentDisplayId = d,
                appliedEpoch = appliedContentEpoch,
                contentEpoch = app.contentEpoch,
            )
        ) {
            renderFromState("attached display=$d")
        }
    }

    override fun onStop() {
        stoppedSinceResume = true
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        hideStatusBar(window)
        deckState.addListener(stateListener)
        // Rebuild when: never painted, library/settings epoch changed, OR we
        // are now on a different display than last paint (multi-display attach).
        val d = display?.displayId
        if (DualPaintPolicy.needsPaintForDisplay(
                hasPainted = ::currentDeck.isInitialized,
                paintedDisplayId = paintedForDisplayId,
                currentDisplayId = d,
                appliedEpoch = appliedContentEpoch,
                contentEpoch = app.contentEpoch,
            )
        ) {
            closeAppDrawer()
            closeQuickPanel()
            renderFromState("resume d=$d epoch=${app.contentEpoch}")
        }
        stoppedSinceResume = false
        orientationController.start()
        // Quiet rescan / RA network intentionally NOT on resume: both caused
        // contentEpoch / selection notify storms that left physical panels
        // pure black (view tree alive, buffer never presented).
        if (pendingAppDrawer) {
            pendingAppDrawer = false
            openAppDrawer()
        }
    }

    override fun onPause() {
        // A held direction whose key-up gets lost (focus change, activity
        // switch) must not repeat forever.
        navRepeater.cancelAll()
        resetAxisEngagement()
        orientationController.stop()
        deckState.removeListener(stateListener)
        super.onPause()
    }

    /** True while the swipe-up all-apps drawer is showing on this activity. */
    fun isAppDrawerOpen(): Boolean = appDrawer != null

    /**
     * Feed a key/stick action into this activity's all-apps drawer.
     * Returns false when this activity is not hosting an open drawer.
     */
    fun routeAppDrawerAction(action: Action): Boolean {
        val drawer = appDrawer ?: return false
        drawer.handleAction(action)
        return true
    }

    /**
     * Resolve the open all-apps drawer across both deck activities.
     * Input can land on either display (topResumed is often Main while the
     * PRIMARY-role Companion hosts the drawer); never gate on isPrimary.
     */
    private fun routeOpenAppDrawerAction(action: Action): Boolean {
        if (routeAppDrawerAction(action)) return true
        for (other in app.liveDeckActivities()) {
            if (other !== this && other.routeAppDrawerAction(action)) return true
        }
        return false
    }

    /**
     * Request the all-apps drawer from a HOME / SECONDARY_HOME redelivery.
     * Debounced: a single swipe often fires multiple intents; only the first
     * in a short window counts. A later deliberate request toggles closed.
     * Safe across activity instances (pending open until resumed).
     *
     * @param allowToggle when false (absorb / system storm), only open — never
     * close. OEM SECONDARY_HOME storms were open/close glitching the modal.
     */
    fun requestAppDrawer(allowToggle: Boolean = true) {
        // Absorb path must never call this with intent to open (see DualPaintPolicy).
        val now = SystemClock.uptimeMillis()
        val within = DualPaintPolicy.withinDebounce(
            now, app.lastDrawerRequestUptimeMs, DualPaintPolicy.DRAWER_DEBOUNCE_MS,
        )
        val primary = when (
            DisplayRole.roleFor(display?.displayId ?: 0, deckState)
        ) {
            DisplayRole.PRIMARY -> this
            DisplayRole.COMPANION ->
                app.primaryDeckActivity()?.takeIf { it !== this } ?: return
        }
        when (DualPaintPolicy.drawerAction(within, primary.isAppDrawerOpen(), allowToggle)) {
            DualPaintPolicy.DrawerAction.NONE -> return
            DualPaintPolicy.DrawerAction.CLOSE -> {
                app.lastDrawerRequestUptimeMs = now
                primary.closeAppDrawer()
            }
            DualPaintPolicy.DrawerAction.OPEN -> {
                app.lastDrawerRequestUptimeMs = now
                primary.pendingAppDrawer = true
                if (primary.lifecycle.currentState.isAtLeast(
                        androidx.lifecycle.Lifecycle.State.RESUMED,
                    )
                ) {
                    primary.pendingAppDrawer = false
                    primary.openAppDrawer()
                }
            }
        }
    }

    /**
     * Re-paint only when this window has no content children. Does not run
     * during [rendering] and is not called on every resume (that thrashed
     * Vulkan buffers into permanent pure black).
     */
    fun ensureDeckPainted() {
        if (isFinishing || isDestroyed || rendering) return
        val content = findViewById<ViewGroup>(android.R.id.content) ?: return
        if (!::currentDeck.isInitialized || content.childCount == 0) {
            renderFromState("ensure empty")
        }
    }

    /**
     * Open the launch drawer (apps + ROMs) if not already open.
     * Forwards to the interactive PRIMARY deck when this activity is the
     * hero panel (primaryDisplay on the other screen).
     */
    fun openAppDrawer() {
        val role = DisplayRole.roleFor(display?.displayId ?: 0, deckState)
        if (role != DisplayRole.PRIMARY) {
            app.primaryDeckActivity()?.takeIf { it !== this }?.openAppDrawer()
            return
        }
        // Idempotent: multi-intent delivery must not toggle closed.
        if (appDrawer != null) return
        if (!::currentDeck.isInitialized) {
            pendingAppDrawer = true
            return
        }

        val apps = appLibrary.visible(settings)
        // Reuse cached empty-query rows when contentEpoch/apps/hidden unchanged.
        val cached = app.drawerPickerItems(apps)
        val picker = AppPicker(
            this,
            settings.accentColor,
            apps,
            app.romEntries,
            appIconLoader,
            title = "All apps",
            autoShowKeyboard = false,
            heightFraction = 0.88f,
            prebuiltItems = cached,
            onPick = { key ->
                closeAppDrawer()
                launchSlotKey(this, deckState, app.romEntries, key)
            },
            onHide = { packageName ->
                closeAppDrawer()
                DeckOverlays.hideApp(this, packageName)
            },
            onClose = { closeAppDrawer() },
        )
        appDrawer = picker
        val content = findViewById<ViewGroup>(android.R.id.content)
        content.addView(
            picker.view,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
    }

    fun closeAppDrawer() {
        val drawer = appDrawer ?: return
        val content = findViewById<ViewGroup>(android.R.id.content)
        content.removeView(drawer.view)
        getSystemService(InputMethodManager::class.java)
            ?.hideSoftInputFromWindow(content.windowToken, 0)
        appDrawer = null
        pendingAppDrawer = false
    }

    // A finish() triggered by an internal redirect (CompanionActivity
    // relaunching itself onto display 1) is not the user leaving home; it
    // must not cascade into killing every other Ghost Galleon activity.
    protected open fun skipExitCascade(): Boolean = false

    override fun onDestroy() {
        if (isFinishing && !isChangingConfigurations && !skipExitCascade()) {
            app.requestExitAll(this)
        }
        super.onDestroy()
    }

    protected val appLibrary: AppLibrary by lazy {
        AppLibrary(PackageManagerAppsSource(packageManager, packageName))
    }

    protected val appIconLoader: AppIconLoader by lazy { AppIconLoader(packageManager) }

    private lateinit var currentDeck: Deck

    protected open fun deckForMode(): Deck = when (deckState.mode) {
        UIMode.GRID -> GridDeck(
            this, deckState, settings, appLibrary, appIconLoader, app.romEntries)
        UIMode.GAME -> GameDeck(
            this, deckState, settings, appLibrary, appIconLoader, app.romEntries)
    }

    // SAF tree picker for first-run setup (same grant model as Settings).
    private val setupRomFolderPicker =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
            if (uri == null) return@registerForActivityResult
            runCatching {
                contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val trees = app.settings.romTreeUris
            if (uri.toString() !in trees) {
                app.updateSettings(app.settings.copy(romTreeUris = trees + uri.toString()))
            }
            Toast.makeText(this, "ROM folder added", Toast.LENGTH_SHORT).show()
            // Settings update rebuilds decks; re-evaluate setup (may hide).
            refreshSetupOverlay()
        }

    protected open fun renderFromState(reason: String = "unspecified") {
        if (isFinishing || isDestroyed) return
        val now = SystemClock.uptimeMillis()
        val hasPainted = ::currentDeck.isInitialized
        val deferMs = DualPaintPolicy.deferredFullRenderDelayMs(
            rendering = rendering,
            hasPainted = hasPainted,
            nowUptimeMs = now,
            lastFullRenderUptimeMs = lastFullRenderUptimeMs,
        )
        if (deferMs != null) {
            if (BuildConfig.DEBUG && rendering) {
                Log.e(PAINT_TAG, "BLOCKED nested full render reason=$reason " +
                    "count=$fullRenderCount ${javaClass.simpleName}")
                // Debug-only hard signal: nested setContentView is how both
                // panels went pure black. Never crash release.
                check(!rendering) {
                    "GGPaint nested renderFromState ($reason) — dual paint invariant"
                }
            }
            // Do not drop SETTINGS / browse-chip rebuilds permanently.
            scheduleDeferredFullRender(deferMs, reason)
            return
        }
        pendingFullRender = false
        paintHandler.removeCallbacks(deferredPaintRunnable)
        rendering = true
        lastFullRenderUptimeMs = now
        fullRenderCount++
        val role = DisplayRole.roleFor(display?.displayId ?: 0, deckState)
        Log.i(
            PAINT_TAG,
            "FULL #$fullRenderCount reason=$reason role=$role " +
                "d=${display?.displayId} epoch=${app.contentEpoch} " +
                "act=${javaClass.simpleName}",
        )
        try {
            // Rebuilding the content view detaches any activity-level overlay.
            appDrawer = null
            quickPanel = null
            // setContentView destroys the setup view; clear host + global flag so
            // we never leave input blocked when setup is no longer shown.
            setupOverlay = null
            app.setupBlockingInput = false
            currentDeck = deckForMode()
            val root = when (role) {
                DisplayRole.PRIMARY -> primaryContentWithOptionalHeroStrip()
                DisplayRole.COMPANION ->
                    CompanionPanel.build(
                        this, deckState, appLibrary, app.romEntries, settings)
            }
            // Opaque root — never rely on transparent window format alone.
            if (root.background == null) {
                root.setBackgroundColor(0xFF000000.toInt())
            }
            setContentView(root)
            // Force a present after attach; some Sugar paths left READY_TO_SHOW
            // with an all-black buffer until the next frame was requested.
            root.post {
                root.invalidate()
                root.requestLayout()
            }
            appliedContentEpoch = app.contentEpoch
            paintedForDisplayId = display?.displayId
            if (role == DisplayRole.PRIMARY) maybeShowSetup()
        } finally {
            rendering = false
        }
    }

    private fun scheduleDeferredFullRender(delayMs: Long, reason: String) {
        pendingFullRender = true
        paintHandler.removeCallbacks(deferredPaintRunnable)
        val wait = delayMs.coerceAtLeast(0L)
        Log.i(
            PAINT_TAG,
            "DEFER ${wait}ms reason=$reason count=$fullRenderCount " +
                "act=${javaClass.simpleName}",
        )
        if (wait == 0L) {
            paintHandler.post(deferredPaintRunnable)
        } else {
            paintHandler.postDelayed(deferredPaintRunnable, wait)
        }
    }

    /**
     * Interactive deck, optionally topped with a selection hero strip when
     * topology is SINGLE and [LayoutMetricsResolver] selects TOP_STRIP.
     */
    private fun primaryContentWithOptionalHeroStrip(): View {
        val deckView = currentDeck.primaryView(this)
        val dm = resources.displayMetrics
        val topo = app.displayConfig
        val metrics = LayoutMetricsResolver.fromWindow(
            windowWidthPx = dm.widthPixels,
            windowHeightPx = dm.heightPixels,
            densityDpi = dm.densityDpi,
            topologyMode = topo.mode,
            isCompanionRole = false,
        )
        if (metrics.companionHeroStyle != CompanionHeroStyle.TOP_STRIP ||
            topo.mode != SurfaceMode.SINGLE
        ) {
            return deckView
        }
        val density = dm.density
        // Compact strip: art + 3 text lines; never the full dual CompanionPanel
        // (whose banner alone is heightPixels*2/5 and would clip name/play).
        val stripHeight = (
            com.visorcraft.ghostgalleon.rom.SelectionStrip.STRIP_HEIGHT_DP * density
            ).toInt()
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
        }
        val strip = CompanionPanel.buildTopStrip(
            this, deckState, appLibrary, app.romEntries, settings,
        )
        column.addView(
            strip,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                stripHeight,
            ),
        )
        column.addView(
            deckView,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f,
            ),
        )
        return column
    }

    fun openQuickPanel() {
        val role = DisplayRole.roleFor(display?.displayId ?: 0, deckState)
        if (role != DisplayRole.PRIMARY) {
            app.primaryDeckActivity()?.takeIf { it !== this }?.openQuickPanel()
            return
        }
        if (quickPanel != null) {
            closeQuickPanel()
            return
        }
        if (!::currentDeck.isInitialized) return
        closeAppDrawer()
        val content = findViewById<ViewGroup>(android.R.id.content) ?: return
        val panel = QuickPanel(this, deckState, app.romEntries) { closeQuickPanel() }
        quickPanel = panel
        content.addView(
            panel.view,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
    }

    fun closeQuickPanel() {
        val panel = quickPanel ?: return
        findViewById<ViewGroup>(android.R.id.content)?.removeView(panel.view)
        quickPanel = null
    }

    /** Feed an action into this activity's open quick panel (cross-display). */
    fun routeQuickPanelAction(action: Action): Boolean {
        val panel = quickPanel ?: return false
        if (action == Action.OPEN_QUICK_PANEL) {
            closeQuickPanel()
            return true
        }
        panel.handleAction(action)
        return true
    }

    private var setupOverlay: View? = null

    private fun setupSnapshot(): SetupNeeds.Snapshot {
        val installed = { pkg: String -> packageManager.isInstalled(pkg) }
        return SetupCard.snapshot(app, installed)
    }

    private fun maybeShowSetup() {
        val snap = setupSnapshot()
        if (!SetupNeeds.shouldShow(snap)) {
            // Configured or dismissed: never leave blocking stuck.
            app.setupBlockingInput = false
            setupOverlay = null
            return
        }
        val content = findViewById<ViewGroup>(android.R.id.content) ?: return
        // Replace any stale overlay (e.g. after checklist refresh).
        setupOverlay?.let { content.removeView(it) }
        val card = SetupCard.build(
            this,
            settings.accentColor,
            snap,
            onAddRomFolder = { setupRomFolderPicker.launch(null) },
            onSgdbKey = { showSetupSgdbKeyDialog() },
            onOpenSettings = {
                launchOnOtherDisplay(
                    this, deckState, Intent(this, SettingsActivity::class.java))
            },
            onDismiss = {
                app.updateSettings(settings.copy(setupDismissed = true), notify = false)
                dismissSetup()
            },
        )
        setupOverlay = card
        app.setupBlockingInput = true
        content.addView(
            card,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
    }

    /** Rebuild or dismiss setup after a grant / key save without full deck flash when possible. */
    private fun refreshSetupOverlay() {
        val snap = setupSnapshot()
        if (!SetupNeeds.shouldShow(snap)) {
            dismissSetup()
            return
        }
        // Still needed: repaint checklist on the primary content root.
        maybeShowSetup()
    }

    private fun showSetupSgdbKeyDialog() {
        val density = resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setText(app.settings.sgdbApiKey ?: "")
            setSelectAllOnFocus(true)
            setTextColor(Color.WHITE)
            setHintTextColor(0x66FFFFFF)
            hint = "Paste API key"
        }
        val container = FrameLayout(this).apply {
            setPadding(dp(20), dp(12), dp(20), 0)
            addView(input)
        }
        AlertDialog.Builder(this)
            .setTitle("SteamGridDB API key")
            .setView(container)
            .setPositiveButton("Save") { _, _ ->
                val key = input.text.toString().trim().ifEmpty { null }
                // notify=false: avoid full deck rebuild; refresh setup card only.
                app.updateSettings(app.settings.copy(sgdbApiKey = key), notify = false)
                refreshSetupOverlay()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun dismissSetup() {
        val overlay = setupOverlay
        if (overlay != null) {
            findViewById<ViewGroup>(android.R.id.content)?.removeView(overlay)
        }
        setupOverlay = null
        app.setupBlockingInput = false
    }

    /** Cross-activity dismiss when BACK lands on the non-host display. */
    fun dismissSetupPublic() {
        dismissSetup()
    }

    protected open fun handleAction(action: Action): Boolean {
        // First-run setup: primary hosts the card; input may land elsewhere.
        if (setupOverlay != null || app.setupBlockingInput) {
            if (action == Action.BACK) {
                app.updateSettings(settings.copy(setupDismissed = true), notify = false)
                // Dismiss on the host activity if we own the overlay.
                dismissSetup()
                // If companion received BACK, ask primary to drop the card.
                app.primaryDeckActivity()?.let { host ->
                    if (host !== this) host.runOnUiThread { host.dismissSetupPublic() }
                }
            }
            return true
        }
        // Quick Panel on this or the other display (global input).
        if (quickPanel != null) {
            if (action == Action.OPEN_QUICK_PANEL) {
                closeQuickPanel()
                return true
            }
            quickPanel!!.handleAction(action)
            when (action) {
                Action.NAV_UP, Action.NAV_DOWN, Action.NAV_LEFT, Action.NAV_RIGHT,
                Action.CONFIRM ->
                    haptic(HapticFeedbackConstants.KEYBOARD_TAP)
                else -> {}
            }
            return true
        }
        for (other in app.liveDeckActivities()) {
            if (other !== this && other.quickPanel != null) {
                other.routeQuickPanelAction(action)
                return true
            }
        }
        if (action == Action.OPEN_QUICK_PANEL) {
            openQuickPanel()
            return true
        }
        // Swipe-up drawer must eat input before the deck. Host may be the
        // other display's activity (global input); still consume here.
        if (routeOpenAppDrawerAction(action)) {
            when (action) {
                Action.NAV_UP, Action.NAV_DOWN, Action.NAV_LEFT, Action.NAV_RIGHT,
                Action.PAGE_PREV, Action.PAGE_NEXT, Action.CONFIRM ->
                    haptic(HapticFeedbackConstants.KEYBOARD_TAP)
                else -> {}
            }
            return true
        }
        if (!::currentDeck.isInitialized) return true
        val handled = currentDeck.handleAction(action)
        // Central haptics hook (settings.haptics): one subtle KEYBOARD_TAP
        // per consumed action — selection moves (NAV + page flips, hold
        // repeats included) and CONFIRM launches/picks. Modals route through
        // the same deck handleAction, so their moves/choices tap too.
        if (handled) {
            when (action) {
                Action.NAV_UP, Action.NAV_DOWN, Action.NAV_LEFT, Action.NAV_RIGHT,
                Action.PAGE_PREV, Action.PAGE_NEXT, Action.CONFIRM ->
                    haptic(HapticFeedbackConstants.KEYBOARD_TAP)
                else -> {}
            }
        }
        return handled
    }

    // settings.haptics master switch, FLAG_IGNORE_GLOBAL_SETTING so the
    // launcher's own setting works regardless of the OS touch-feedback
    // toggle. Subtle single taps only.
    private fun haptic(feedbackConstant: Int) {
        if (!settings.haptics) return
        findViewById<View>(android.R.id.content)
            ?.performHapticFeedback(
                feedbackConstant, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)
    }

    // SAF image picker for per-app custom icons (grid slot menu). Lives on
    // the activity so it is registered exactly once; the deck hands over the
    // target package per pick. The read grant is persisted, same model as
    // the wallpaper and ROM tree grants.
    private var pendingIconPick: ((Uri) -> Unit)? = null
    private val customIconPicker =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            val callback = pendingIconPick
            pendingIconPick = null
            if (uri != null && callback != null) {
                runCatching {
                    contentResolver.takePersistableUriPermission(
                        uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                callback(uri)
            }
        }

    fun requestCustomIcon(onPicked: (Uri) -> Unit) {
        pendingIconPick = onPicked
        customIconPicker.launch(arrayOf("image/*"))
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            return routeKeyDown(event.keyCode, event.repeatCount) { super.dispatchKeyEvent(event) }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean =
        routeKeyDown(keyCode, event.repeatCount) { super.onKeyDown(keyCode, event) }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean =
        when (val action = KeyMap.resolve(keyCode, settings)) {
            Action.NAV_UP, Action.NAV_DOWN, Action.NAV_LEFT, Action.NAV_RIGHT -> {
                navRepeater.onRelease(action)
                true
            }
            else -> super.onKeyUp(keyCode, event)
        }

    // Stick hysteresis: a direction engages at |axis| >= ENGAGE and stays
    // engaged until |axis| < RELEASE, so wobble around one threshold can no
    // longer machine-gun fake edges. HAT axes take priority over AXIS_X/Y
    // (checked first, as before). Engagement edges drive the same
    // NavRepeater as held keys; the engine owns all repeat timing.
    private class AxisDirection(
        val axis: Int,
        val negative: Boolean,
        val action: Action,
        var engaged: Boolean = false,
    )

    private val axisDirections = listOf(
        AxisDirection(MotionEvent.AXIS_HAT_X, negative = true, Action.NAV_LEFT),
        AxisDirection(MotionEvent.AXIS_HAT_X, negative = false, Action.NAV_RIGHT),
        AxisDirection(MotionEvent.AXIS_HAT_Y, negative = true, Action.NAV_UP),
        AxisDirection(MotionEvent.AXIS_HAT_Y, negative = false, Action.NAV_DOWN),
        AxisDirection(MotionEvent.AXIS_X, negative = true, Action.NAV_LEFT),
        AxisDirection(MotionEvent.AXIS_X, negative = false, Action.NAV_RIGHT),
        AxisDirection(MotionEvent.AXIS_Y, negative = true, Action.NAV_UP),
        AxisDirection(MotionEvent.AXIS_Y, negative = false, Action.NAV_DOWN),
    )

    private fun resetAxisEngagement() {
        axisDirections.forEach { it.engaged = false }
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_MOVE ||
            event.source and InputDevice.SOURCE_JOYSTICK != InputDevice.SOURCE_JOYSTICK
        ) {
            return super.onGenericMotionEvent(event)
        }
        for (dir in axisDirections) {
            val raw = event.getAxisValue(dir.axis)
            val deflection = if (dir.negative) -raw else raw
            if (dir.engaged && deflection < AXIS_RELEASE_THRESHOLD) {
                dir.engaged = false
                navRepeater.onRelease(dir.action)
            } else if (!dir.engaged && deflection >= AXIS_ENGAGE_THRESHOLD) {
                dir.engaged = true
                navRepeater.onPress(dir.action)
            }
        }
        return true
    }

    protected fun isHomeRole(): Boolean =
        Build.VERSION.SDK_INT >= 29 &&
            getSystemService(RoleManager::class.java)?.isRoleHeld(RoleManager.ROLE_HOME) == true

    private inline fun routeKeyDown(
        keyCode: Int,
        repeatCount: Int,
        fallThrough: () -> Boolean,
    ): Boolean = when (val action = KeyMap.resolve(keyCode, settings)) {
        Action.NONE -> fallThrough()
        Action.NAV_UP, Action.NAV_DOWN, Action.NAV_LEFT, Action.NAV_RIGHT -> {
            // The repeater owns NAV repeats: the first key-down starts it;
            // platform-injected repeats (repeatCount > 0) are swallowed so
            // keyboard input cannot double-move.
            if (repeatCount == 0) navRepeater.onPress(action)
            true
        }
        Action.SWAP_SCREENS -> {
            if (repeatCount == 0) {
                haptic(HapticFeedbackConstants.KEYBOARD_TAP)
                // Prefer topology swap (rebuilds both roles). If single-display
                // or swap fails, still try companion recreate — recovers a pure
                // black secondary without system Force Stop.
                val swapped = app.swapInteractiveDisplay()
                if (!swapped) {
                    val main = this as? MainActivity
                        ?: app.liveDeckActivities().filterIsInstance<MainActivity>()
                            .firstOrNull()
                    if (main != null) {
                        main.restartCompanionPanel("swap-recover")
                        Toast.makeText(this, "Restarting bottom panel…", Toast.LENGTH_SHORT)
                            .show()
                    } else {
                        Toast.makeText(
                            this,
                            "Only one display — swap unavailable",
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                }
            }
            true
        }
        Action.TOGGLE_MODE -> {
            if (repeatCount == 0) {
                haptic(HapticFeedbackConstants.KEYBOARD_TAP)
                deckState.toggleMode()
                app.updateSettings(app.settings.copy(defaultMode = deckState.mode))
            }
            true
        }
        Action.OPEN_SETTINGS -> {
            if (repeatCount == 0) {
                // Settings opens on the display NOT hosting the interactive
                // deck, so the deck stays fully visible and interactive.
                launchOnOtherDisplay(
                    this, deckState, Intent(this, SettingsActivity::class.java))
            }
            true
        }
        Action.OPEN_QUICK_PANEL -> {
            if (repeatCount == 0) {
                haptic(HapticFeedbackConstants.KEYBOARD_TAP)
                handleAction(action)
            }
            true
        }
        Action.BACK -> when {
            // Decks get BACK first: an open picker/menu or an active tile
            // move consumes it (close/cancel). Otherwise the home-role
            // consume and the non-home fall-through behave as before.
            handleAction(action) -> true
            isHomeRole() -> true
            else -> fallThrough()
        }
        else -> handleAction(action) || fallThrough()
    }

}
