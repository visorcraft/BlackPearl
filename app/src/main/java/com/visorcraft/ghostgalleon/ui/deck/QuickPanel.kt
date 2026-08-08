package com.visorcraft.ghostgalleon.ui.deck

import android.content.Intent
import android.graphics.Color
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.visorcraft.ghostgalleon.GhostGalleonApp
import com.visorcraft.ghostgalleon.library.HiddenRoms
import com.visorcraft.ghostgalleon.library.LibraryBrowse
import com.visorcraft.ghostgalleon.rom.RomEntry
import com.visorcraft.ghostgalleon.settings.Action
import com.visorcraft.ghostgalleon.settings.SlotKey
import com.visorcraft.ghostgalleon.settings.ThemePack
import com.visorcraft.ghostgalleon.state.DeckState
import com.visorcraft.ghostgalleon.ui.ControllerLabActivity
import com.visorcraft.ghostgalleon.ui.settings.SettingsActivity
import com.visorcraft.ghostgalleon.ui.dp

/**
 * Full-screen dim (~80%) overlay with a chip grid (Wi‑Fi, Bluetooth,
 * Display, Settings, Continue, optional browse rails, Theme, Controller lab,
 * Close). Browse shortcuts (Random/Top/Fav/Games/Installed/Week/Month/A–Z/New)
 * opt in via BrowseChrome. Layout is 4 columns; incomplete last row is fine.
 * D-pad + A navigate / activate; B / Close dismisses.
 */
class QuickPanel(
    private val activity: AppCompatActivity,
    private val state: DeckState,
    private val roms: List<RomEntry>,
    private val onClose: () -> Unit,
) {
    private data class Cell(val label: String, val onClick: () -> Unit)

    private val columns = 4
    private var selection = 0
    private val rowViews = mutableListOf<TextView>()
    private lateinit var cells: List<Cell>
    private var accent: Int = 0

    val view: View by lazy { build() }

    fun handleAction(action: Action): Boolean {
        ensureBuilt()
        when (action) {
            Action.NAV_LEFT -> {
                selection = (selection + cells.size - 1) % cells.size
                paint()
            }
            Action.NAV_RIGHT -> {
                selection = (selection + 1) % cells.size
                paint()
            }
            Action.NAV_UP -> {
                selection = (selection - columns + cells.size) % cells.size
                paint()
            }
            Action.NAV_DOWN -> {
                selection = (selection + columns) % cells.size
                paint()
            }
            Action.CONFIRM -> cells[selection].onClick()
            Action.BACK, Action.OPEN_QUICK_PANEL -> onClose()
            else -> {}
        }
        return true
    }

    private fun ensureBuilt() {
        // Force lazy view construction so cells/rowViews exist.
        view
    }

    private fun build(): View {
        val context = activity
        val app = activity.application as GhostGalleonApp
        accent = app.settings.accentColor

        // Minimal Quick Panel: system tiles + Continue + Theme + lab + Close.
        // Browse rails (Random/Top/Fav/Games/Installed) opt in via Settings.
        val chrome = app.settings.browseChrome
        val core = mutableListOf(
            Cell("Wi‑Fi") { openSystem(Settings.ACTION_WIFI_SETTINGS) },
            Cell("Bluetooth") { openSystem(Settings.ACTION_BLUETOOTH_SETTINGS) },
            Cell("Display") { openSystem(Settings.ACTION_DISPLAY_SETTINGS) },
            Cell("Settings") {
                launchOnOtherDisplay(
                    activity, state, Intent(activity, SettingsActivity::class.java))
                onClose()
            },
            Cell("Continue") {
                launchContinue(app)
                onClose()
            },
        )
        if (chrome.quickPanelBrowse) {
            if (chrome.randomChip) {
                core.add(Cell("Random") {
                    launchRandom(app)
                    onClose()
                })
            }
            if (chrome.topRail) {
                core.add(Cell("Top") {
                    openTopPlayed()
                    onClose()
                })
            }
            // Fav / Games / Installed / Week / Month / A–Z / New — each rail
            // still gated by its own BrowseChrome flag via quickPanelRailShortcuts.
            chrome.quickPanelRailShortcuts().forEach { (label, mode) ->
                core.add(Cell(label) {
                    openGameRail(mode, label)
                    onClose()
                })
            }
        }
        core.add(Cell("Theme") { cycleTheme(app) })
        core.add(Cell("Controller") {
            activity.startActivity(Intent(activity, ControllerLabActivity::class.java))
            onClose()
        })
        core.add(Cell("Close") { onClose() })
        cells = core

        val root = FrameLayout(context).apply {
            setBackgroundColor(0xCC000000.toInt())
            isClickable = true
            isFocusable = true
            setOnClickListener { onClose() }
        }

        val sheet = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = TileBackgrounds.card(context)
            setPadding(context.dp(20), context.dp(20), context.dp(20), context.dp(20))
            isClickable = true
        }

        sheet.addView(TextView(context).apply {
            text = "Quick Panel"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, context.dp(16))
        })

        cells.chunked(columns).forEachIndexed { rowIndex, rowCells ->
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }
            rowCells.forEachIndexed { colIndex, cell ->
                val index = rowIndex * columns + colIndex
                val btn = TextView(context).apply {
                    text = cell.label
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                    gravity = Gravity.CENTER
                    setPadding(context.dp(8), context.dp(16), context.dp(8), context.dp(16))
                    setOnClickListener {
                        selection = index
                        paint()
                        cell.onClick()
                    }
                }
                rowViews.add(btn)
                row.addView(btn, LinearLayout.LayoutParams(0, context.dp(72), 1f).apply {
                    if (colIndex > 0) marginStart = context.dp(8)
                })
            }
            sheet.addView(row, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                if (rowIndex > 0) topMargin = context.dp(8)
            })
        }

        paint()
        root.addView(sheet, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER,
        ).apply {
            marginStart = context.dp(24)
            marginEnd = context.dp(24)
        })
        return root
    }

    private fun paint() {
        rowViews.forEachIndexed { index, tv ->
            val selected = index == selection
            val isClose = cells.getOrNull(index)?.label == "Close"
            if (selected) {
                tv.background = TileBackgrounds.selected(activity, accent)
                tv.setTextColor(if (isClose) Color.WHITE else Color.BLACK)
            } else if (isClose) {
                tv.background = TileBackgrounds.card(activity)
                tv.setTextColor(Color.WHITE)
            } else {
                tv.background = TileBackgrounds.selected(activity, accent)
                tv.alpha = 0.55f
                tv.setTextColor(Color.BLACK)
            }
            if (selected) tv.alpha = 1f
            else if (!isClose) tv.alpha = 0.55f
            else tv.alpha = 1f
        }
    }

    private fun openSystem(action: String) {
        runCatching {
            activity.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure {
            Toast.makeText(activity, "Unavailable", Toast.LENGTH_SHORT).show()
        }
        onClose()
    }

    private fun launchContinue(app: GhostGalleonApp) {
        val keys = buildList {
            addAll(app.settings.gridSlots.filterNotNull())
            addAll(app.settings.dockSlots.filterNotNull())
            addAll(
                HiddenRoms.listed(roms, app.settings.hiddenRomIds)
                    .map { SlotKey.rom(it.id) },
            )
            addAll(app.settings.lastLaunchedMs.keys)
        }
        val cont = LibraryBrowse.continueKey(keys, app.settings.lastLaunchedMs)
        if (cont == null) {
            Toast.makeText(activity, "Nothing to continue", Toast.LENGTH_SHORT).show()
            return
        }
        val idx = app.settings.gridSlots.indexOf(cont)
        if (idx >= 0) state.selectSlot(idx, cont) else state.select(cont)
        launchSlotKey(activity, state, roms, cont)
    }

    /** Pick a random curated app or visible ROM and launch it immediately. */
    private fun launchRandom(app: GhostGalleonApp) {
        val pool = buildList {
            addAll(app.settings.gridSlots.filterNotNull())
            addAll(app.settings.dockSlots.filterNotNull())
            addAll(
                HiddenRoms.listed(roms, app.settings.hiddenRomIds)
                    .map { SlotKey.rom(it.id) },
            )
        }.distinct()
        val key = LibraryBrowse.pickRandom(pool) { size ->
            java.util.concurrent.ThreadLocalRandom.current().nextInt(size)
        }
        if (key == null) {
            Toast.makeText(activity, "Library empty", Toast.LENGTH_SHORT).show()
            return
        }
        val idx = app.settings.gridSlots.indexOf(key)
        if (idx >= 0) state.selectSlot(idx, key) else state.select(key, force = true)
        launchSlotKey(activity, state, roms, key)
    }

    /**
     * Jump into Game Mode on a named browse rail so Fav / Games / Installed /
     * Top / Week / Month / A–Z / New are reachable from Grid Mode via Select.
     */
    private fun openGameRail(
        mode: LibraryBrowse.Mode,
        toast: String,
        selectKey: String? = null,
    ) {
        state.setMode(com.visorcraft.ghostgalleon.state.UIMode.GAME)
        state.setLibraryBrowse(LibraryBrowse.railQuery(mode), force = true)
        if (selectKey != null) state.select(selectKey, force = true)
        Toast.makeText(activity, toast, Toast.LENGTH_SHORT).show()
    }

    /**
     * Most Played rail + select the top title when playtime exists.
     */
    private fun openTopPlayed() {
        val live = (activity.application as GhostGalleonApp).settings
        val top = LibraryBrowse.topPlayedKey(live.playtimeMs)
        openGameRail(
            LibraryBrowse.Mode.MOST_PLAYED,
            if (top != null) "Top played" else "No playtime yet",
            selectKey = top,
        )
    }

    private fun cycleTheme(app: GhostGalleonApp) {
        val builtins = ThemePack.BUILTINS
        val current = ThemePack.resolve(app.settings).id
        val i = builtins.indexOfFirst { it.id == current }.let { if (it < 0) 0 else it }
        val next = builtins[(i + 1) % builtins.size]
        app.updateSettings(ThemePack.applyToSettings(app.settings, next))
        Toast.makeText(activity, "Theme: ${next.displayName}", Toast.LENGTH_SHORT).show()
    }
}
