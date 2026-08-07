package com.visorcraft.ghostgalleon.ui

import android.app.role.RoleManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.visorcraft.ghostgalleon.GhostGalleonApp
import com.visorcraft.ghostgalleon.input.KeyMap
import com.visorcraft.ghostgalleon.input.NavRepeater
import com.visorcraft.ghostgalleon.library.AppLibrary
import com.visorcraft.ghostgalleon.library.PackageManagerAppsSource
import com.visorcraft.ghostgalleon.sensor.OrientationController
import com.visorcraft.ghostgalleon.settings.Action
import com.visorcraft.ghostgalleon.settings.Settings
import com.visorcraft.ghostgalleon.state.DeckState
import com.visorcraft.ghostgalleon.state.UIMode
import com.visorcraft.ghostgalleon.ui.deck.AppIconLoader
import com.visorcraft.ghostgalleon.ui.deck.CompanionPanel
import com.visorcraft.ghostgalleon.ui.deck.Deck
import com.visorcraft.ghostgalleon.ui.deck.GameDeck
import com.visorcraft.ghostgalleon.ui.deck.GridDeck
import com.visorcraft.ghostgalleon.ui.deck.launchOnOtherDisplay
import com.visorcraft.ghostgalleon.ui.settings.SettingsActivity

// Stick hysteresis thresholds: engage at 0.7, stay engaged until under 0.5.
private const val AXIS_ENGAGE_THRESHOLD = 0.7f
private const val AXIS_RELEASE_THRESHOLD = 0.5f

abstract class BaseDeckActivity : AppCompatActivity() {

    protected val app: GhostGalleonApp get() = application as GhostGalleonApp
    protected val deckState: DeckState get() = app.deckState
    protected val settings: Settings get() = app.settings

    private val stateListener = DeckState.DeckStateListener { onDeckStateChanged() }

    // Selection-only changes update the already-built views in place;
    // everything else (mode, display, settings) keeps the full rebuild.
    private fun onDeckStateChanged() {
        if (deckState.lastChange == DeckState.Change.SELECTION && ::currentDeck.isInitialized) {
            val role = DisplayRole.roleFor(display?.displayId ?: 0, deckState)
            val updated = when (role) {
                DisplayRole.PRIMARY -> currentDeck.updateSelection()
                DisplayRole.COMPANION -> {
                    val content = findViewById<ViewGroup>(android.R.id.content)
                    content != null && content.childCount > 0 &&
                        CompanionPanel.updateSelection(
                            content, this, deckState, appLibrary, app.romEntries, settings)
                }
            }
            if (updated) return
        }
        renderFromState()
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        renderFromState()
    }

    override fun onResume() {
        super.onResume()
        hideStatusBar(window)
        deckState.addListener(stateListener)
        renderFromState()
        orientationController.start()
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

    protected open fun renderFromState() {
        val role = DisplayRole.roleFor(display?.displayId ?: 0, deckState)
        currentDeck = deckForMode()
        setContentView(
            when (role) {
                DisplayRole.PRIMARY -> currentDeck.primaryView(this)
                DisplayRole.COMPANION ->
                    CompanionPanel.build(
                        this, deckState, appLibrary, app.romEntries, settings)
            }
        )
    }

    protected open fun handleAction(action: Action): Boolean {
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
                deckState.swapDisplays()
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
