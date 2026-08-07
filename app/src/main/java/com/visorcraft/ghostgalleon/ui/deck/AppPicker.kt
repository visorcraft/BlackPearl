package com.visorcraft.ghostgalleon.ui.deck

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.visorcraft.ghostgalleon.GhostGalleonApp
import com.visorcraft.ghostgalleon.library.AppEntry
import com.visorcraft.ghostgalleon.rom.PlatformTile
import com.visorcraft.ghostgalleon.rom.Platforms
import com.visorcraft.ghostgalleon.rom.RomEntry
import com.visorcraft.ghostgalleon.settings.Action
import com.visorcraft.ghostgalleon.settings.SlotKey

// Full-screen picker overlay: centered dark card with a live-filter search
// field above a two-section list — "Apps" (installed launchable, non-hidden)
// then "ROMs" (the scanned library, sorted by platform then name).
// Used for "Add to grid/dock" (pick fills a slot) and the swipe-up
// "All apps" drawer (pick launches). ROM rows show a platform tile thumb,
// the ROM name, and the platform label. Long-press hide stays apps-only.
// Touch (tap a row) and gamepad (d-pad highlight, A pick, B cancel) both
// work; d-pad/A/B keys are intercepted by BaseDeckActivity before the
// focused search field can see them, letters fall through to the field.
class AppPicker(
    private val activity: AppCompatActivity,
    private val accentColor: Int,
    private val allEntries: List<AppEntry>,
    private val roms: List<RomEntry>,
    private val iconLoader: AppIconLoader,
    // "Add to grid" / "Add to dock" / "All apps" (launch drawer).
    private val title: String = "Add to grid",
    // Swipe-up drawer must not pop the IME (keyboard flash); slot pickers
    // still focus search so typing to filter is one tap less.
    private val autoShowKeyboard: Boolean = true,
    // Fraction of screen height for the card (drawer wants taller).
    private val heightFraction: Float = 0.62f,
    // Prebuilt empty-query list (all-apps drawer cache). Null → build now.
    private val prebuiltItems: List<PickerItem>? = null,
    private val onPick: (String) -> Unit,
    private val onHide: (String) -> Unit,
    private val onClose: () -> Unit,
) {
    private var emptyQueryItems: List<PickerItem> =
        prebuiltItems ?: PickerItems.build(allEntries, roms, "")
    private var items: List<PickerItem> = emptyQueryItems
    private val app get() = activity.application as GhostGalleonApp
    // The highlight only ever rests on data rows, never section headers.
    private var highlight: Int =
        items.indexOfFirst { it !is PickerItem.Header }.coerceAtLeast(0)
    private var query = ""
    private var listView: ListView? = null
    private var adapter: PickerAdapter? = null
    private var overlayView: FrameLayout? = null
    private var hideMenu: HideMenu? = null

    val view: View by lazy {
        val context: Context = activity
        val density = context.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()

        val overlay = FrameLayout(context).apply {
            setBackgroundColor(0xCC000000.toInt())
            isClickable = true
            setOnClickListener { onClose() }
        }
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = TileBackgrounds.card(context)
            setPadding(dp(20), dp(16), dp(20), dp(16))
        }
        card.addView(TextView(context).apply {
            text = title
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(12))
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val search = EditText(context).apply {
            hint = "Search apps & ROMs"
            setHintTextColor(0x66FFFFFF)
            setTextColor(0xFFFFFFFF.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setSingleLine()
            // Darker than the card so the field reads as a field.
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(0xFF101014.toInt())
                cornerRadius = 28 * density
                setStroke((1 * density).toInt(), 0x33FFFFFF)
            }
            setPadding(dp(16), dp(4), dp(16), dp(4))
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    query = s?.toString() ?: ""
                    items = if (query.isBlank()) {
                        emptyQueryItems
                    } else {
                        PickerItems.build(allEntries, roms, query)
                    }
                    highlight =
                        items.indexOfFirst { it !is PickerItem.Header }.coerceAtLeast(0)
                    // Dataset actually changed here: a full notify is correct
                    // (and the list resets to the top of the new results).
                    adapter?.notifyDataSetChanged()
                    listView?.setSelection(0)
                }
            })
        }
        card.addView(search, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(12)
        })

        val list = ListView(context).apply {
            divider = null
            dividerHeight = dp(6)
            selector = android.graphics.drawable.ColorDrawable(
                android.graphics.Color.TRANSPARENT)
        }
        val pickerAdapter = PickerAdapter(context, dp(40), dp(12), dp(10))
        list.adapter = pickerAdapter
        // Row taps are handled by each row's own OnClickListener: the
        // long-press listener (hide menu) makes rows consume touches,
        // which would suppress an AdapterView-level OnItemClickListener.
        val empty = TextView(context).apply {
            text = "No matches"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(0x66FFFFFF)
            gravity = Gravity.CENTER
        }
        card.addView(list, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        list.emptyView = empty
        card.addView(empty, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        listView = list
        adapter = pickerAdapter
        overlayView = overlay
        val metrics = context.resources.displayMetrics
        // Top-anchored. Slot pickers stay short so the IME does not cover
        // results; the swipe-up drawer uses a taller card and no auto-IME.
        overlay.addView(card, FrameLayout.LayoutParams(
            minOf(dp(600), (metrics.widthPixels * 0.85f).toInt()),
            (metrics.heightPixels * heightFraction).toInt(),
            Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
            topMargin = dp(16)
        })
        // Only auto-focus search when requested (slot "Add to …" pickers).
        // The all-apps drawer leaves focus on the list so the keyboard does
        // not flash open/closed on every swipe-up.
        if (autoShowKeyboard) {
            search.post {
                search.requestFocus()
                activity.getSystemService(InputMethodManager::class.java)
                    ?.showSoftInput(search, InputMethodManager.SHOW_IMPLICIT)
            }
        } else {
            // Prevent the EditText from stealing focus on attach.
            search.isFocusable = true
            search.isFocusableInTouchMode = true
            list.isFocusable = true
            list.isFocusableInTouchMode = true
            list.post { list.requestFocus() }
        }
        overlay
    }

    fun handleAction(action: Action): Boolean {
        hideMenu?.let { return it.handleAction(action) }
        when (action) {
            Action.NAV_UP -> moveHighlight(-1)
            Action.NAV_DOWN -> moveHighlight(1)
            Action.PAGE_PREV -> moveHighlight(-5)
            Action.PAGE_NEXT -> moveHighlight(5)
            Action.CONFIRM -> when (val item = items.getOrNull(highlight)) {
                is PickerItem.App -> onPick(item.entry.packageName)
                is PickerItem.Rom -> onPick(SlotKey.rom(item.entry.id))
                else -> {}
            }
            Action.BACK -> onClose()
            else -> {}
        }
        return true // the modal swallows every action while open
    }

    // The highlight only ever rests on data rows: section headers are
    // skipped in both directions.
    private fun moveHighlight(delta: Int) {
        if (items.isEmpty()) return
        var next = highlight
        val step = if (delta > 0) 1 else -1
        var remaining = kotlin.math.abs(delta)
        while (remaining > 0) {
            val candidate = next + step
            if (candidate !in items.indices) break
            next = candidate
            if (items[next] !is PickerItem.Header) remaining--
        }
        if (next == highlight || items[next] is PickerItem.Header) return
        highlight = next
        rebindVisibleHighlight()
        val list = listView ?: return
        if (next < list.firstVisiblePosition || next > list.lastVisiblePosition) {
            list.smoothScrollToPosition(next)
        }
    }

    // Moves the accent ring to the highlighted row by updating the visible
    // rows in place; rows (re)attached while scrolling are bound with the
    // current highlight by getView.
    private fun rebindVisibleHighlight() {
        val list = listView ?: return
        val first = list.firstVisiblePosition
        for (i in 0 until list.childCount) {
            val row = list.getChildAt(i) ?: continue
            row.background = if (first + i == highlight) {
                TileBackgrounds.selected(list.context, accentColor)
            } else {
                null
            }
        }
    }

    // Long-press an app row opens the confirm menu ("Hide app" / "Cancel")
    // for that entry; the menu sits on top of the picker overlay. ROM rows
    // have no hide menu (hiding stays apps-only).
    private fun openHideMenu(entry: AppEntry) {
        if (hideMenu != null) return
        val overlay = overlayView ?: return
        val menu = HideMenu(entry)
        hideMenu = menu
        overlay.addView(menu.view, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    private fun closeHideMenu() {
        hideMenu?.let { overlayView?.removeView(it.view) }
        hideMenu = null
    }

    // Confirm-style modal for a picker row: "Hide app" adds the package to
    // settings.hiddenPackages (via onHide) so it leaves the picker and the
    // all-apps lists; a grid slot that already holds it stays launchable.
    private inner class HideMenu(private val entry: AppEntry) {
        private val choices = listOf("Hide app", "Cancel")
        private var selection = 0
        private val rows = mutableListOf<TextView>()

        val view: View by lazy {
            val density = activity.resources.displayMetrics.density
            fun dp(value: Int) = (value * density).toInt()

            val menuOverlay = FrameLayout(activity).apply {
                setBackgroundColor(0x99000000.toInt())
                isClickable = true
                setOnClickListener { closeHideMenu() }
            }
            val card = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                background = TileBackgrounds.card(activity)
                setPadding(dp(16), dp(12), dp(16), dp(12))
            }
            card.addView(TextView(activity).apply {
                text = entry.label
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                setTextColor(0x99FFFFFF.toInt())
                gravity = Gravity.CENTER
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                setPadding(dp(24), 0, dp(24), dp(4))
            }, LinearLayout.LayoutParams(
                dp(220), ViewGroup.LayoutParams.WRAP_CONTENT))
            choices.forEachIndexed { index, label ->
                val row = TextView(activity).apply {
                    text = label
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
                    setTextColor(0xFFFFFFFF.toInt())
                    gravity = Gravity.CENTER
                    setPadding(dp(24), dp(12), dp(24), dp(12))
                    setOnClickListener { choose(index) }
                }
                rows.add(row)
                card.addView(row, LinearLayout.LayoutParams(
                    dp(220), ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = dp(4); bottomMargin = dp(4)
                })
            }
            paintRows()
            menuOverlay.addView(card, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER))
            menuOverlay
        }

        private fun choose(index: Int) {
            closeHideMenu()
            if (index == 0) onHide(entry.packageName)
        }

        private fun paintRows() {
            rows.forEachIndexed { index, row ->
                row.background = if (index == selection) {
                    TileBackgrounds.selected(activity, accentColor)
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
                Action.CONFIRM -> choose(selection)
                Action.BACK -> closeHideMenu()
                else -> {}
            }
            return true
        }
    }

    // Row views are recycled (the ROM section can hold thousands of rows):
    // a shared row layout whose thumb container swaps app icon <-> platform
    // tile and whose text views are simply re-bound.
    private class RowHolder(
        val thumb: FrameLayout,
        val name: TextView,
        val tag: TextView,
    )

    private inner class PickerAdapter(
        private val context: Context,
        private val iconSize: Int,
        private val rowPadH: Int,
        private val rowPadV: Int,
    ) : BaseAdapter() {
        override fun getCount() = items.size
        override fun getItem(position: Int) = items[position]
        override fun getItemId(position: Int) = position.toLong()
        override fun getViewTypeCount() = 2
        override fun getItemViewType(position: Int) =
            if (items[position] is PickerItem.Header) 0 else 1
        override fun isEnabled(position: Int) = items[position] !is PickerItem.Header

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val density = context.resources.displayMetrics.density
            fun dp(value: Int) = (value * density).toInt()

            val item = items[position]
            if (item is PickerItem.Header) {
                val header = (convertView as? TextView) ?: TextView(context).apply {
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                    setTextColor(0x66FFFFFF)
                    setPadding(rowPadH, dp(10), rowPadH, dp(2))
                    layoutParams = AbsListView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT)
                }
                header.text = item.title
                header.background = null
                return header
            }

            val row: LinearLayout
            val holder: RowHolder
            val recycled = convertView?.tag as? RowHolder
            if (recycled != null) {
                row = convertView as LinearLayout
                holder = recycled
            } else {
                row = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(rowPadH, rowPadV, rowPadH, rowPadV)
                }
                val thumb = FrameLayout(context)
                row.addView(thumb, LinearLayout.LayoutParams(iconSize, iconSize))
                val name = TextView(context).apply {
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                    setTextColor(0xFFFFFFFF.toInt())
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    setPadding(dp(12), 0, dp(8), 0)
                }
                row.addView(name, LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                val tag = TextView(context).apply {
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                    setTextColor(0x66FFFFFF)
                    maxLines = 1
                }
                row.addView(tag, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT))
                holder = RowHolder(thumb, name, tag)
                row.tag = holder
                row.layoutParams = AbsListView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT)
            }

            holder.thumb.removeAllViews()
            when (item) {
                is PickerItem.App -> {
                    holder.thumb.addView(ImageView(context).apply {
                        CustomIcon.bind(
                            this, iconLoader, app.artCache, app.settings,
                            item.entry.packageName, iconSize)
                    }, FrameLayout.LayoutParams(iconSize, iconSize))
                    holder.name.text = item.entry.label
                    holder.tag.text = ""
                    row.setOnClickListener {
                        // VIRTUAL_KEY for touch picks; the gamepad A-pick
                        // path already taps via the central CONFIRM hook.
                        if (app.settings.haptics) {
                            row.performHapticFeedback(
                                HapticFeedbackConstants.VIRTUAL_KEY,
                                HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)
                        }
                        onPick(item.entry.packageName)
                    }
                    row.setOnLongClickListener {
                        openHideMenu(item.entry)
                        true
                    }
                }
                is PickerItem.Rom -> {
                    holder.thumb.addView(
                        PlatformTile.view(context, item.entry.platformId,
                            cornerRadiusDp = 12),
                        FrameLayout.LayoutParams(iconSize, iconSize))
                    holder.name.text = item.entry.name
                    holder.tag.text = Platforms.byId(item.entry.platformId)
                        ?.displayName ?: item.entry.platformId
                    row.setOnClickListener { onPick(SlotKey.rom(item.entry.id)) }
                    row.setOnLongClickListener(null)
                }
                is PickerItem.Header -> {}
            }
            row.isLongClickable = item is PickerItem.App
            row.background = if (position == highlight) {
                TileBackgrounds.selected(context, accentColor)
            } else {
                null
            }
            return row
        }
    }
}
