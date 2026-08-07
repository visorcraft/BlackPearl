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
 * D-pad navigates rows.
 */
class FolderPanel(
    private val context: Context,
    private val accentColor: Int,
    private val title: String,
    private val members: List<Pair<String, String>>, // key -> label
    private val onLaunch: (String) -> Unit,
    private val onClose: () -> Unit,
) {
    private var selection = 0
    private val rows = mutableListOf<TextView>()

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
        if (members.isEmpty()) {
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
            members.forEachIndexed { index, (key, label) ->
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
            text = "B · Close"
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

    fun handleAction(action: Action): Boolean {
        when (action) {
            Action.NAV_UP -> {
                if (members.isNotEmpty()) {
                    selection = (selection + members.size - 1) % members.size
                    paintRows()
                }
            }
            Action.NAV_DOWN -> {
                if (members.isNotEmpty()) {
                    selection = (selection + 1) % members.size
                    paintRows()
                }
            }
            Action.CONFIRM -> {
                if (members.isNotEmpty()) {
                    onLaunch(members[selection].first)
                }
            }
            Action.BACK -> onClose()
            else -> {}
        }
        return true
    }
}
