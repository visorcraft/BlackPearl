package com.visorcraft.ghostgalleon.ui.deck

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.visorcraft.ghostgalleon.GhostGalleonApp
import com.visorcraft.ghostgalleon.library.AppLibrary
import com.visorcraft.ghostgalleon.rom.PlatformTile
import com.visorcraft.ghostgalleon.rom.RomEntry
import com.visorcraft.ghostgalleon.settings.DockSlots
import com.visorcraft.ghostgalleon.settings.Settings
import com.visorcraft.ghostgalleon.settings.SlotKey

// The dock bar pinned at the bottom edge of BOTH launcher decks: a
// wrap-content card bar (just wide enough for its visible slots, centered)
// holding the auto-growing dock row. The bar renders
// DockSlots.visibleCount slots — one "+" placeholder past the filled
// count, at least 4, at most 9. Filled slots show the app icon / ROM
// platform tile (a missing ROM dims to 40% alpha, same as the grid);
// empty slots render as "+" tiles, visually consistent with the grid's
// blank slots. Taps and long-presses are delegated to the owning deck
// (launch / picker / slot menu); the bar itself only renders and repaints
// focus.
//
// The bar also hosts the grid's page dots (grid mode only) and the
// transient "Moving - A drop · B cancel" hint, which replaces the slots
// and dots while a grid or dock tile is lifted.
class DockBar(
    private val activity: AppCompatActivity,
    private val settings: Settings,
    private val library: AppLibrary,
    private val iconLoader: AppIconLoader,
    private val roms: List<RomEntry>,
    // The slots to render; the owning deck substitutes its working copy
    // while a dock move is in flight.
    private val slots: () -> List<String?>,
    private val onTap: (Int) -> Unit,
    private val onLongPress: (Int) -> Unit,
) {
    private val slotFrames = mutableListOf<FrameLayout>()
    private var moveHintView: TextView? = null
    private var contentView: LinearLayout? = null
    private var pulseAnimator: ObjectAnimator? = null
    private var slotSize: Int = 0

    // Dock tiles resolve like grid slots: apps through the FULL cache (a
    // hidden app stays docked and launchable), ROMs through the library
    // snapshot. An app key that no longer resolves (uninstalled) renders
    // as a blank "+" slot — tapping it opens the picker and overwrites.
    private val appByPkg by lazy {
        library.all(settings).associateBy { it.packageName }
    }
    private val romByKey by lazy { roms.associateBy { SlotKey.rom(it.id) } }

    fun keyAt(index: Int): String? = slots().getOrNull(index)

    // Blank = renders as a "+" placeholder: no key, or an app key whose
    // package is gone. Missing ROMs stay filled (they keep launch-toast
    // and menu behavior, same as the grid's "Missing" tiles).
    fun isBlank(index: Int): Boolean {
        val key = keyAt(index) ?: return true
        if (SlotKey.isRom(key)) return false
        return !appByPkg.containsKey(key)
    }

    // [pageDots] is the grid deck's dot strip (null in game mode).
    fun build(context: Context, pageDots: LinearLayout?): View {
        val density = context.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()

        // Slot frames sized from window metrics (LayoutMetrics clamps) so
        // a full dock always fits; not a fixed 2160×1080 table.
        val metrics = context.resources.displayMetrics
        val layout = com.visorcraft.ghostgalleon.display.LayoutMetricsResolver.fromWindow(
            windowWidthPx = metrics.widthPixels,
            windowHeightPx = metrics.heightPixels,
            densityDpi = metrics.densityDpi,
            topologyMode = (context.applicationContext as? com.visorcraft.ghostgalleon.GhostGalleonApp)
                ?.displayConfig?.mode
                ?: com.visorcraft.ghostgalleon.display.SurfaceMode.SINGLE,
            isCompanionRole = false,
        )
        val screenW = metrics.widthPixels
        slotSize = minOf(
            dp(layout.suggestedDockSlotDp),
            (screenW - dp(8) * 2) / DockSlots.CAPACITY - dp(12),
        ).coerceAtLeast(dp(40))

        val bar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = TileBackgrounds.card(context)
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        // Transient move-mode hint (child 0, hidden by default); shown in
        // place of the slots/page dots while a tile is lifted.
        val moveHint = TextView(context).apply {
            text = HintBar.MOVE_TEXT
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(settings.accentColor)
            gravity = Gravity.CENTER
            visibility = View.GONE
        }
        moveHintView = moveHint
        bar.addView(moveHint, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT))
        slotFrames.clear()
        repeat(DockSlots.visibleCount(slots())) { index ->
            val frame = FrameLayout(context)
            frame.setOnClickListener { onTap(index) }
            frame.setOnLongClickListener {
                onLongPress(index)
                true
            }
            populate(frame, index)
            slotFrames.add(frame)
            bar.addView(frame, LinearLayout.LayoutParams(slotSize, slotSize).apply {
                marginStart = dp(6); marginEnd = dp(6)
            })
        }
        if (pageDots != null) {
            pageDots.setPadding(dp(10), 0, 0, 0)
            bar.addView(pageDots, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        contentView = bar
        // Wrap-content width (the slots + dots exactly), centered in the
        // vertical content stack.
        return bar.apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.CENTER_HORIZONTAL }
        }
    }

    // One slot's content: app icon / ROM platform tile / dimmed missing
    // tile / "+" placeholder, centered in the frame. The icon keeps the
    // old 40/48 ratio of the (screen-derived) slot size.
    private fun populate(frame: FrameLayout, index: Int) {
        val context = frame.context
        val density = context.resources.displayMetrics.density
        val iconSize = slotSize * 40 / 48

        frame.removeAllViews()
        val key = keyAt(index)
        val romEntry = key?.takeIf(SlotKey::isRom)?.let { romByKey[it] }
        val appEntry = key?.takeUnless(SlotKey::isRom)?.let { appByPkg[it] }
        val content: View = when {
            romEntry != null -> PlatformTile.view(
                context, romEntry.platformId, cornerRadiusDp = 12)
            appEntry != null -> ImageView(context).apply {
                CustomIcon.bind(
                    this, iconLoader,
                    (activity.application as GhostGalleonApp).artCache,
                    settings, key, iconSize)
            }
            SlotKey.isRom(key) -> PlatformTile.view(
                context, SlotKey.platformIdOf(key) ?: "rom",
                cornerRadiusDp = 12).apply { alpha = 0.4f }
            else -> TextView(context).apply {
                text = "+"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
                setTextColor(0x44FFFFFF)
                gravity = Gravity.CENTER
                // Darker inset tile so the placeholder reads against the
                // bar's own card fill (same shade as the picker field).
                background = GradientDrawable().apply {
                    setColor(0xFF101014.toInt())
                    cornerRadius = 12 * density
                }
            }
        }
        frame.addView(content, FrameLayout.LayoutParams(
            iconSize, iconSize, Gravity.CENTER))
    }

    // Repopulates every slot's contents (a dock move swapped keys in the
    // working copy) without rebuilding the bar.
    fun rebind() {
        slotFrames.forEachIndexed { index, frame -> populate(frame, index) }
    }

    // Moves the accent ring to [focused] (null = no dock focus, ring
    // returns to the deck content) and pulses the [moving] slot's alpha
    // while a dock move has it lifted.
    fun updateFocus(focused: Int?, moving: Int? = null) {
        pulseAnimator?.cancel()
        pulseAnimator = null
        slotFrames.forEachIndexed { index, frame ->
            frame.alpha = 1f
            frame.background = if (index == focused) {
                TileBackgrounds.selected(frame.context, settings.accentColor)
            } else {
                null
            }
            if (index == moving) startPulse(frame)
        }
    }

    private fun startPulse(frame: FrameLayout) {
        pulseAnimator = ObjectAnimator.ofFloat(frame, View.ALPHA, 1f, 0.55f).apply {
            duration = 500
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            start()
        }
    }

    // Swaps the move hint in for the slots/dots while a tile is lifted,
    // and back when the move ends.
    fun showMoveHint(show: Boolean) {
        val bar = contentView ?: return
        val hint = moveHintView ?: return
        hint.visibility = if (show) View.VISIBLE else View.GONE
        for (i in 0 until bar.childCount) {
            val child = bar.getChildAt(i)
            if (child !== hint) child.visibility = if (show) View.GONE else View.VISIBLE
        }
    }
}
