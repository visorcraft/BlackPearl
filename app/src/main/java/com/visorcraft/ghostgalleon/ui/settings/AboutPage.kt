package com.visorcraft.ghostgalleon.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.visorcraft.ghostgalleon.BuildConfig
import com.visorcraft.ghostgalleon.R
import com.visorcraft.ghostgalleon.ui.deck.TileBackgrounds

/**
 * Grexa-style About page: hero card with the dynamic version, feature
 * cards, project link, and the Licenses / Credits dialogs backed by the
 * bundled raw texts (license_ghost_galleon, licenses_third_party,
 * acknowledgments, runtime_components). Self-contained - SettingsActivity
 * just adds [build]'s view to its ABOUT page body.
 */
object AboutPage {

    private const val PROJECT_URL = "https://github.com/visorcraft/GhostGalleon"

    private data class Credit(val name: String, val version: String, val license: String)

    // Direct bundled dependencies (transitives are all AndroidX Apache-2.0;
    // the full resolved list lives in raw/licenses_third_party.txt).
    private val bundledLibraries = listOf(
        Credit("Kotlin Standard Library", "1.9.24", "Apache-2.0"),
        Credit("AndroidX AppCompat", "1.7.0", "Apache-2.0"),
        Credit("AndroidX RecyclerView", "1.3.2", "Apache-2.0"),
        Credit("AndroidX DocumentFile", "1.0.1", "Apache-2.0"),
        Credit("kotlinx-coroutines (transitive)", "1.6.4", "Apache-2.0"),
        Credit("JetBrains Java Annotations (transitive)", "13.0", "Apache-2.0"),
        Credit("Guava ListenableFuture (transitive)", "1.0", "Apache-2.0"),
    )

    // System components / external services relied on at execution; none
    // are bundled (Grexa's "Runtime components" model).
    private val runtimeComponents = listOf(
        Credit("Android 14 platform framework", "API 34", "Apache-2.0"),
        Credit("Android Runtime (ART)", "", "Apache-2.0"),
        Credit("Storage Access Framework (DocumentsUI)", "", "Apache-2.0"),
        Credit("System media codecs (animated WebP / GIF)", "", "Apache-2.0"),
        Credit("SteamGridDB Web API (optional)", "", "service terms"),
        Credit("Emulator apps (launched, never bundled)", "", "various"),
    )

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    private fun dpF(context: Context, value: Int): Float =
        value * context.resources.displayMetrics.density

    private fun withAlpha(color: Int, alpha: Int): Int =
        (color and 0x00FFFFFF) or (alpha shl 24)

    private fun pill(context: Context, fill: Int, radiusDp: Int, stroke: Int = 0) =
        GradientDrawable().apply {
            setColor(fill)
            cornerRadius = dpF(context, radiusDp)
            if (stroke != 0) setStroke(dp(context, 1), stroke)
        }

    private fun text(
        context: Context, value: String, sp: Float, color: Int, bold: Boolean = false,
    ): TextView = TextView(context).apply {
        text = value
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    private fun header(context: Context, accent: Int, value: String): TextView =
        text(context, value.uppercase(), 13f, withAlpha(accent, 0xCC)).apply {
            letterSpacing = 0.15f
        }

    private fun card(context: Context): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        background = TileBackgrounds.card(context)
        val pad = dp(context, 20)
        setPadding(pad, pad, pad, pad)
    }

    private fun chip(
        context: Context, value: String, sp: Float = 13f,
        textColor: Int, fill: Int, stroke: Int = 0,
    ): TextView = text(context, value, sp, textColor, bold = true).apply {
        background = pill(context, fill, 14, stroke)
        setPadding(dp(context, 12), dp(context, 6), dp(context, 12), dp(context, 6))
    }

    /** Full About page body (sections stacked vertically). */
    fun build(context: Context, accent: Int): View {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        fun addSection(title: String?, sectionCard: View, topMarginDp: Int = 0) {
            if (title != null) {
                root.addView(header(context, accent, title), LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply {
                    topMargin = dp(context, 24)
                    bottomMargin = dp(context, 10)
                })
            }
            root.addView(sectionCard, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                if (topMarginDp > 0) topMargin = dp(context, topMarginDp)
            })
        }

        // Hero card: icon, name, tagline, version/license/platform pills.
        val hero = card(context)
        val heroRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        heroRow.addView(ImageView(context).apply {
            setImageResource(R.mipmap.ic_launcher)
        }, LinearLayout.LayoutParams(dp(context, 96), dp(context, 96)))
        val heroText = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context, 20), 0, 0, 0)
        }
        heroText.addView(text(context, "Ghost Galleon", 26f, Color.WHITE, bold = true))
        heroText.addView(text(context,
            "Ghost Galleon Dual Screen Launcher",
            14f, accent).apply {
            setPadding(0, dp(context, 4), 0, 0)
        })
        heroText.addView(text(context,
            "A dual-screen Android launcher for the One X Sugar handheld.",
            13f, 0xB3FFFFFF.toInt()).apply {
            setPadding(0, dp(context, 4), 0, dp(context, 10))
        })
        val pillsRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        val versionChip = chip(context, "v${BuildConfig.VERSION_NAME}",
            textColor = accent, fill = 0xFF121218.toInt(), stroke = accent)
        pillsRow.addView(versionChip)
        pillsRow.addView(chip(context, "GPL v3",
            textColor = Color.WHITE, fill = 0xFF121218.toInt(),
            stroke = 0x26FFFFFF), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { marginStart = dp(context, 8) })
        pillsRow.addView(chip(context, "Android · API 34",
            textColor = Color.WHITE, fill = 0xFF121218.toInt(),
            stroke = 0x26FFFFFF), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { marginStart = dp(context, 8) })
        heroText.addView(pillsRow)
        heroText.addView(text(context, BuildConfig.GIT_SHA, 12f,
            0x66FFFFFF).apply {
            setPadding(0, dp(context, 8), 0, 0)
        })
        heroRow.addView(heroText, LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        hero.addView(heroRow)
        addSection(null, hero, topMarginDp = 24)

        // What's inside: feature cards in a 2-column arrangement.
        data class Feature(val title: String, val body: String)
        val features = listOf(
            Feature("Grid Mode",
                "Curated 3DS/Wii-style icon grid with blank \"+\" slots, dock, favorites, and folders."),
            Feature("Game Mode",
                "Card carousel with Recent / Continue / Favorites, platform filters, and playtime."),
            Feature("ROM library",
                "SAF-scanned folders launch into the right emulator; optional SteamGridDB & RA."),
            Feature("Dual screens",
                "Companion roles (Hero / Now Playing / Perf / Pin), themes, Quick Panel; X swaps live."),
        )
        val grid = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        features.chunked(2).forEachIndexed { rowIndex, pair ->
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
            }
            pair.forEachIndexed { colIndex, feature ->
                val cell = card(context)
                cell.addView(text(context, feature.title, 15f, Color.WHITE, bold = true))
                cell.addView(text(context, feature.body, 13f,
                    0x99FFFFFF.toInt()).apply {
                    setPadding(0, dp(context, 6), 0, 0)
                })
                row.addView(cell, LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f,
                ).apply {
                    if (colIndex > 0) marginStart = dp(context, 12)
                })
            }
            grid.addView(row, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                if (rowIndex > 0) topMargin = dp(context, 12)
            })
        }
        val featuresCard = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(grid)
        }
        addSection("What's inside", featuresCard)

        // Project card: summary + repo link + Visit pill.
        val project = card(context)
        val projectRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        projectRow.addView(ImageView(context).apply {
            setImageResource(R.drawable.ic_brand_ship)
        }, LinearLayout.LayoutParams(dp(context, 56), dp(context, 56)))
        val projectText = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context, 16), 0, dp(context, 16), 0)
        }
        projectText.addView(text(context,
            "Free and open-source software by VisorCraft.",
            15f, Color.WHITE, bold = true))
        projectText.addView(text(context, "github.com/visorcraft/GhostGalleon",
            13f, accent).apply {
            setPadding(0, dp(context, 4), 0, 0)
        })
        projectRow.addView(projectText, LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        projectRow.addView(chip(context, "Visit", textColor = Color.BLACK,
            fill = accent).apply {
            setOnClickListener {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(PROJECT_URL)))
            }
        })
        project.addView(projectRow)
        addSection(null, project, topMarginDp = 16)

        // Licenses & Credits card: entry points to the two dialogs.
        val legal = card(context)
        legal.addView(text(context, "Licenses & Credits", 17f,
            Color.WHITE, bold = true))
        legal.addView(text(context,
            "Every bundled library, runtime component, acknowledgment, and " +
                "full license text ships with the app - no browser needed.",
            13f, 0x99FFFFFF.toInt()).apply {
            setPadding(0, dp(context, 6), 0, dp(context, 12))
        })
        val buttons = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        buttons.addView(chip(context, "☰  Licenses", textColor = accent,
            fill = 0xFF121218.toInt(), stroke = 0x26FFFFFF).apply {
            setOnClickListener { showLicensesDialog(context, accent) }
        })
        buttons.addView(chip(context, "ⓘ  Credits", textColor = accent,
            fill = 0xFF121218.toInt(), stroke = 0x26FFFFFF).apply {
            setOnClickListener { showCreditsDialog(context, accent) }
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { marginStart = dp(context, 16) })
        legal.addView(buttons)
        addSection(null, legal, topMarginDp = 16)

        // Footer.
        root.addView(text(context,
            "Built by VisorCraft · Powered by Kotlin and the Android platform",
            12f, 0x66FFFFFF).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(context, 24), 0, dp(context, 8))
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ))

        return root
    }

    private fun loadRaw(context: Context, resId: Int): String = runCatching {
        context.resources.openRawResource(resId).bufferedReader().use { it.readText() }
    }.getOrDefault("Text unavailable.")

    /** Licenses dialog: four tabs over the bundled texts, plus Copy. */
    fun showLicensesDialog(context: Context, accent: Int) {
        val tabs = listOf(
            "Ghost Galleon License" to R.raw.license_ghost_galleon,
            "Third-party" to R.raw.licenses_third_party,
            "Acknowledgments" to R.raw.acknowledgments,
            "Runtime components" to R.raw.runtime_components,
        )
        var selected = 0

        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val pad = dp(context, 20)
            setPadding(pad, dp(context, 8), pad, 0)
        }

        val tabViews = mutableListOf<TextView>()
        val tabRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        val body = text(context, "", 12f, 0xE6FFFFFF.toInt()).apply {
            typeface = Typeface.MONOSPACE
            setTextIsSelectable(true)
            setPadding(0, dp(context, 16), 0, dp(context, 8))
        }

        fun restyle() {
            tabs.forEachIndexed { index, _ ->
                val pillView = tabViews[index]
                if (index == selected) {
                    pillView.background = pill(context, accent, 14)
                    pillView.setTextColor(Color.BLACK)
                } else {
                    pillView.background = pill(context, 0xFF1C1C22.toInt(), 14, 0x26FFFFFF)
                    pillView.setTextColor(Color.WHITE)
                }
            }
            body.text = loadRaw(context, tabs[selected].second)
        }

        tabs.forEachIndexed { index, (label, _) ->
            val pillView = text(context, label, 12f, Color.WHITE, bold = true).apply {
                setPadding(dp(context, 12), dp(context, 8), dp(context, 12), dp(context, 8))
                setOnClickListener {
                    if (selected != index) {
                        selected = index
                        restyle()
                    }
                }
            }
            tabViews.add(pillView)
            tabRow.addView(pillView, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { marginEnd = dp(context, 8) })
        }
        // Tabs can overflow the dialog width on narrow panels; scroll them.
        content.addView(android.widget.HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            addView(tabRow)
        })
        content.addView(ScrollView(context).apply {
            addView(body)
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 360)))
        restyle()

        AlertDialog.Builder(context)
            .setTitle("Licenses")
            .setView(content)
            .setPositiveButton("Copy") { _, _ ->
                val clipboard = context.getSystemService(
                    Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText(
                    tabs[selected].first, loadRaw(context, tabs[selected].second)))
                Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Close", null)
            .show()
    }

    /** Credits dialog: runtime components + bundled library table. */
    fun showCreditsDialog(context: Context, accent: Int) {
        val list = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val pad = dp(context, 20)
            setPadding(pad, dp(context, 8), pad, dp(context, 8))
        }

        fun creditRow(credit: Credit) {
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val left = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
            }
            left.addView(text(context, credit.name, 14f, Color.WHITE, bold = true))
            if (credit.version.isNotEmpty()) {
                left.addView(text(context, credit.version, 12f, 0x66FFFFFF))
            }
            row.addView(left, LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(text(context, credit.license, 12f, accent))
            list.addView(row, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { bottomMargin = dp(context, 14) })
        }

        list.addView(text(context, "Runtime components", 15f,
            Color.WHITE, bold = true))
        list.addView(text(context,
            "System components and services used at execution. None are bundled.",
            12f, 0x99FFFFFF.toInt()).apply {
            setPadding(0, dp(context, 4), 0, dp(context, 12))
        })
        runtimeComponents.forEach(::creditRow)

        list.addView(text(context, "Bundled libraries", 15f,
            Color.WHITE, bold = true).apply {
            setPadding(0, dp(context, 10), 0, 0)
        })
        list.addView(text(context,
            "Direct plus notable transitive dependencies shipped inside the APK. " +
                "The full resolved list is under Licenses → Third-party.",
            12f, 0x99FFFFFF.toInt()).apply {
            setPadding(0, dp(context, 4), 0, dp(context, 12))
        })
        bundledLibraries.forEach(::creditRow)

        AlertDialog.Builder(context)
            .setTitle("Credits")
            .setView(ScrollView(context).apply { addView(list) })
            .setNegativeButton("Close", null)
            .show()
    }
}
