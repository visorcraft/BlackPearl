package com.visorcraft.ghostgalleon.ui.deck

import android.content.Context
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.visorcraft.ghostgalleon.settings.Action

/**
 * Modal list of folder members. A launches the selected member; B closes.
 * Long-press or Y removes the selected member. D-pad navigates rows.
 */
class FolderPanel(
    private val context: Context,
    private val accentColor: Int,
    private val title: String,
    private val members: List<Pair<String, String>>, // key -> label
    private val onLaunch: (String) -> Unit,
    private val onClose: () -> Unit,
    private val onRemoveMember: ((String) -> Unit)? = null,
) {
    private var selection = 0
    private val rows = mutableListOf<TextView>()
    private var memberKeys: MutableList<Pair<String, String>> = members.toMutableList()

    val view: View by lazy {
        val density = context.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()

        val overlay = FrameLayout(context).apply {
            setBackgroundColor(0x99000000.toInt())
            isClickable = true
            setOnClickListener { onClose() }
        }
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = TileBackgrounds.card(context)
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        card.addView(TextView(context).apply {
            text = title
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(8))
        })
        if (memberKeys.isEmpty()) {
            card.addView(TextView(context).apply {
                text = "Empty folder\nLong-press → Add member"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                setTextColor(0x99FFFFFF.toInt())
                gravity = Gravity.CENTER
                setPadding(dp(16), dp(20), dp(16), dp(20))
            })
        } else {
            val list = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
            }
            memberKeys.forEachIndexed { index, (key, label) ->
                val row = TextView(context).apply {
                    text = label
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                    setTextColor(0xFFFFFFFF.toInt())
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(16), dp(12), dp(16), dp(12))
                    setOnClickListener {
                        selection = index
                        paintRows()
                        onLaunch(key)
                    }
                    setOnLongClickListener {
                        selection = index
                        paintRows()
                        removeSelected()
                        true
                    }
                }
                rows.add(row)
                list.addView(row, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply {
                    topMargin = dp(2)
                    bottomMargin = dp(2)
                })
            }
            paintRows()
            val scroll = ScrollView(context).apply {
                addView(list)
            }
            card.addView(scroll, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(280),
            ))
        }
        card.addView(TextView(context).apply {
            text = if (onRemoveMember != null) {
                "A · Launch  ·  Y / long-press · Remove  ·  B · Close"
            } else {
                "B · Close"
            }
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(0x66FFFFFF.toInt())
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, 0)
        })
        overlay.addView(card, FrameLayout.LayoutParams(
            dp(300),
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER,
        ))
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

    private fun removeSelected() {
        val remove = onRemoveMember ?: return
        if (memberKeys.isEmpty()) return
        val key = memberKeys[selection].first
        remove(key)
        // Close so the host rebuilds with fresh membership (keeps UI honest).
        onClose()
    }

    fun handleAction(action: Action): Boolean {
        when (action) {
            Action.NAV_UP -> {
                if (memberKeys.isNotEmpty()) {
                    selection = (selection + memberKeys.size - 1) % memberKeys.size
                    paintRows()
                }
            }
            Action.NAV_DOWN -> {
                if (memberKeys.isNotEmpty()) {
                    selection = (selection + 1) % memberKeys.size
                    paintRows()
                }
            }
            Action.CONFIRM -> {
                if (memberKeys.isNotEmpty()) {
                    onLaunch(memberKeys[selection].first)
                }
            }
            Action.TOGGLE_MODE -> {
                // Y: remove selected member (same as long-press).
                removeSelected()
            }
            Action.BACK -> onClose()
            else -> {}
        }
        return true
    }
}
