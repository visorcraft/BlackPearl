package com.visorcraft.ghostgalleon.ui.settings

import android.graphics.Color
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.visorcraft.ghostgalleon.GhostGalleonApp
import com.visorcraft.ghostgalleon.library.SetupNeeds
import com.visorcraft.ghostgalleon.rom.Platforms
import com.visorcraft.ghostgalleon.rom.PlayerResolver
import com.visorcraft.ghostgalleon.ui.deck.TileBackgrounds

/**
 * First-run / empty-library guided card. Hosted as a full-screen overlay
 * on the primary deck when [SetupNeeds.shouldShow] is true.
 */
object SetupCard {

    fun snapshot(app: GhostGalleonApp, installed: (String) -> Boolean): SetupNeeds.Snapshot {
        val players = Platforms.ALL.flatMap { it.players }
            .map { PlayerResolver.packageName(it) }
            .distinct()
            .count { installed(it) }
        return SetupNeeds.Snapshot(
            setupDismissed = app.settings.setupDismissed,
            romTreeCount = app.settings.romTreeUris.size,
            romEntryCount = app.romEntries.size,
            installedPlayerCount = players,
            hasSgdbKey = !app.settings.sgdbApiKey.isNullOrBlank(),
        )
    }

    fun build(
        activity: AppCompatActivity,
        accent: Int,
        snap: SetupNeeds.Snapshot,
        onAddRomFolder: () -> Unit,
        onOpenSettings: () -> Unit,
        onDismiss: () -> Unit,
    ): View {
        val density = activity.resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()
        val overlay = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(0xEE000000.toInt())
            setPadding(dp(24), dp(24), dp(24), dp(24))
            isClickable = true
        }
        val card = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = TileBackgrounds.card(activity)
            setPadding(dp(20), dp(18), dp(20), dp(18))
        }
        card.addView(TextView(activity).apply {
            text = "Welcome aboard"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        })
        card.addView(TextView(activity).apply {
            text = "A few steps to get your dual-screen library ready."
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(0x99FFFFFF.toInt())
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(16))
        })
        SetupNeeds.checklist(snap).forEach { (label, done) ->
            card.addView(TextView(activity).apply {
                text = (if (done) "✓ " else "○ ") + label
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                setTextColor(if (done) accent else Color.WHITE)
                setPadding(0, dp(6), 0, dp(6))
            })
        }
        fun actionBtn(label: String, filled: Boolean, onClick: () -> Unit) =
            TextView(activity).apply {
                text = label
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                setTextColor(if (filled) Color.BLACK else Color.WHITE)
                gravity = Gravity.CENTER
                setPadding(dp(16), dp(12), dp(16), dp(12))
                background = if (filled) {
                    TileBackgrounds.selected(activity, accent)
                } else {
                    TileBackgrounds.card(activity)
                }
                setOnClickListener { onClick() }
            }
        card.addView(actionBtn("Add ROM folder", true, onAddRomFolder), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = dp(16) })
        card.addView(actionBtn("Open Settings", false, onOpenSettings), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = dp(8) })
        card.addView(actionBtn("Skip for now", false, onDismiss), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = dp(8) })
        overlay.addView(card, LinearLayout.LayoutParams(
            minOf(dp(420), (activity.resources.displayMetrics.widthPixels * 0.9f).toInt()),
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ))
        return overlay
    }
}
