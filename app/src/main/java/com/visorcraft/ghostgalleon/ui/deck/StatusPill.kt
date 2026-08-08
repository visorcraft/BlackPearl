package com.visorcraft.ghostgalleon.ui.deck

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.BatteryManager
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextClock
import android.widget.TextView

/**
 * Compact battery + clock chrome for immersive decks (system status bar is
 * hidden). Companion already shows a larger pill; Grid/Game Mode overlay a
 * compact one so single-display and interactive panels always show time.
 *
 * [formatBatteryLabel] is pure and host-tested; [build] is the Android view.
 */
object StatusPill {

    const val TAG = "status_pill"
    const val TAG_BATTERY = "status_battery"
    const val TAG_CLOCK = "status_clock"

    /**
     * Battery label for a capacity percent. Null when [pct] is outside 0..100.
     * When [charging] is true, appends a lightning mark after the percent.
     */
    fun formatBatteryLabel(pct: Int, charging: Boolean = false): String? {
        if (pct !in 0..100) return null
        return if (charging) "$pct%⚡" else "$pct%"
    }

    /**
     * Build a horizontal pill (battery + live [TextClock]). [compact] uses
     * smaller type for overlay on the interactive deck.
     */
    fun build(context: Context, compact: Boolean = true): View {
        val density = context.resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()
        val textSp = if (compact) 13f else 20f
        val padH = if (compact) 12 else 20
        val padV = if (compact) 4 else 8
        val gap = if (compact) 8 else 12

        val pill = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = TileBackgrounds.pill(context)
            setPadding(dp(padH), dp(padV), dp(padH), dp(padV))
            tag = TAG
            contentDescription = "Status"
        }

        val battery = readBattery(context)
        formatBatteryLabel(battery.percent, battery.charging)?.let { label ->
            pill.addView(TextView(context).apply {
                text = label
                tag = TAG_BATTERY
                contentDescription = "Battery $label"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, textSp)
                setTextColor(Color.WHITE)
                setPadding(0, 0, dp(gap), 0)
            })
        }
        pill.addView(TextClock(context).apply {
            format12Hour = "h:mm a"
            format24Hour = "H:mm"
            tag = TAG_CLOCK
            contentDescription = "Clock"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, textSp)
            setTextColor(Color.WHITE)
        })
        return pill
    }

    /** Overlay params: top-end with small margin (interactive decks). */
    fun overlayLayoutParams(context: Context): android.widget.FrameLayout.LayoutParams {
        val density = context.resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()
        return android.widget.FrameLayout.LayoutParams(
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.END,
        ).apply {
            topMargin = dp(8)
            marginEnd = dp(12)
        }
    }

    data class BatterySnapshot(val percent: Int, val charging: Boolean)

    fun readBattery(context: Context): BatterySnapshot {
        val bm = context.getSystemService(BatteryManager::class.java)
        val pct = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
        val intent = context.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
        )
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        return BatterySnapshot(pct, charging)
    }
}
