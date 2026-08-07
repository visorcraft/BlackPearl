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
import com.visorcraft.ghostgalleon.library.LibraryBrowse
import com.visorcraft.ghostgalleon.rom.RomEntry
import com.visorcraft.ghostgalleon.settings.Action
import com.visorcraft.ghostgalleon.settings.SlotKey
import com.visorcraft.ghostgalleon.settings.ThemePack
import com.visorcraft.ghostgalleon.state.DeckState
import com.visorcraft.ghostgalleon.ui.ControllerLabActivity
import com.visorcraft.ghostgalleon.ui.settings.SettingsActivity

/**
 * Full-screen dim (~80%) overlay with a 2×4 chip grid (Wi‑Fi, Bluetooth,
 * Display, Ghost Settings, Continue, Theme cycle, Controller lab, Close).
 * D-pad + A navigate/activate; B / Close dismisses.
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
        val density = context.resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()
        val app = activity.application as GhostGalleonApp
        accent = app.settings.accentColor

        cells = listOf(
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
            Cell("Theme") { cycleTheme(app) },
            Cell("Controller") {
                activity.startActivity(Intent(activity, ControllerLabActivity::class.java))
                onClose()
            },
            Cell("Close") { onClose() },
        )

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
            setPadding(dp(20), dp(20), dp(20), dp(20))
            isClickable = true
        }

        sheet.addView(TextView(context).apply {
            text = "Quick Panel"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(16))
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
                    setPadding(dp(8), dp(16), dp(8), dp(16))
                    setOnClickListener {
                        selection = index
                        paint()
                        cell.onClick()
                    }
                }
                rowViews.add(btn)
                row.addView(btn, LinearLayout.LayoutParams(0, dp(72), 1f).apply {
                    if (colIndex > 0) marginStart = dp(8)
                })
            }
            sheet.addView(row, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                if (rowIndex > 0) topMargin = dp(8)
            })
        }

        paint()
        root.addView(sheet, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER,
        ).apply {
            marginStart = dp(24)
            marginEnd = dp(24)
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
            addAll(roms.filter { it.visibleInUi }.map { SlotKey.rom(it.id) })
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

    private fun cycleTheme(app: GhostGalleonApp) {
        val builtins = ThemePack.BUILTINS
        val current = ThemePack.resolve(app.settings).id
        val i = builtins.indexOfFirst { it.id == current }.let { if (it < 0) 0 else it }
        val next = builtins[(i + 1) % builtins.size]
        app.updateSettings(ThemePack.applyToSettings(app.settings, next))
        Toast.makeText(activity, "Theme: ${next.displayName}", Toast.LENGTH_SHORT).show()
    }
}
