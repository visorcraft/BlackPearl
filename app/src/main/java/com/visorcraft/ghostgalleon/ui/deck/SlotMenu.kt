package com.visorcraft.ghostgalleon.ui.deck

import android.content.Context
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.visorcraft.ghostgalleon.settings.Action

// Small centered modal for a filled grid tile: Move / Pin to dock /
// Remove / Cancel, plus Rename / Custom icon (and their Reset variants) for
// app tiles. Dark dimmed overlay, dark card, accent-highlighted row. Touch
// (row tap) and gamepad (d-pad + A, B cancels) both work.
class SlotMenu(
    private val context: Context,
    private val accentColor: Int,
    // The caller trims the list per tile (rename/icon entries are app-only,
    // reset entries only when an override exists).
    private val choices: List<Choice> = Choice.entries,
    private val onChoice: (Choice) -> Unit,
) {
    enum class Choice(val label: String) {
        MOVE("Move"),
        PIN_TO_DOCK("Pin to dock"),
        RENAME("Rename"),
        RESET_NAME("Reset name"),
        CUSTOM_ICON("Custom icon"),
        RESET_ICON("Reset icon"),
        FAVORITE("Favorite"),
        UNFAVORITE("Unfavorite"),
        OPEN_WITH("Open with…"),
        PLAYER("Player…"),
        SET_ART("Set artwork"),
        ADD_TO_GRID("Add to grid"),
        ADD_TO_COLLECTION("Add to collection…"),
        REMOVE_FROM_COLLECTION("Remove from collection"),
        MOVE_TO_TOP("Move to top"),
        MOVE_UP("Move up"),
        MOVE_DOWN("Move down"),
        MOVE_TO_END("Move to end"),
        DETAILS("Details…"),
        APP_INFO("App info"),
        HIDE("Hide from library"),
        NEW_FOLDER("New folder"),
        ADD_MEMBER("Add member"),
        REMOVE("Remove"),
        CANCEL("Cancel"),
    }

    private var selection = 0
    private val rows = mutableListOf<TextView>()

    val view: View by lazy {
        val density = context.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()

        val overlay = FrameLayout(context).apply {
            setBackgroundColor(0x99000000.toInt())
            isClickable = true // swallow touches so the grid beneath stays inert
            setOnClickListener { onChoice(Choice.CANCEL) }
        }
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = TileBackgrounds.card(context)
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        choices.forEachIndexed { index, choice ->
            val row = TextView(context).apply {
                text = choice.label
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
                setTextColor(0xFFFFFFFF.toInt())
                gravity = Gravity.CENTER
                setPadding(dp(24), dp(12), dp(24), dp(12))
                setOnClickListener { onChoice(choice) }
            }
            rows.add(row)
            card.addView(row, LinearLayout.LayoutParams(
                dp(220), android.view.ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(4); bottomMargin = dp(4)
            })
        }
        paintRows()
        overlay.addView(card, FrameLayout.LayoutParams(
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER))
        overlay
    }

    private fun paintRows() {
        rows.forEachIndexed { index, row ->
            row.background = if (index == selection) {
                TileBackgrounds.selected(context, accentColor)
            } else {
                null
            }
        }
    }

    fun handleAction(action: Action): Boolean {
        when (action) {
            Action.NAV_UP -> {
                selection = (selection + choices.size - 1) % choices.size
                paintRows()
            }
            Action.NAV_DOWN -> {
                selection = (selection + 1) % choices.size
                paintRows()
            }
            Action.CONFIRM -> onChoice(choices[selection])
            Action.BACK -> onChoice(Choice.CANCEL)
            else -> {}
        }
        return true // the modal swallows every action while open
    }
}
