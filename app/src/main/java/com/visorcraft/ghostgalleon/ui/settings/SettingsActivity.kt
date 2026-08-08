package com.visorcraft.ghostgalleon.ui.settings

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.visorcraft.ghostgalleon.GhostGalleonApp
import com.visorcraft.ghostgalleon.art.ScrapeJob
import com.visorcraft.ghostgalleon.art.SgdbScraper
import com.visorcraft.ghostgalleon.library.AppLibrary
import com.visorcraft.ghostgalleon.library.PackageManagerAppsSource
import com.visorcraft.ghostgalleon.library.RetroAchievements
import com.visorcraft.ghostgalleon.rom.Platforms
import com.visorcraft.ghostgalleon.rom.RomLibrary
import com.visorcraft.ghostgalleon.rom.TreeLabels
import com.visorcraft.ghostgalleon.display.DeviceProfileCatalog
import com.visorcraft.ghostgalleon.settings.Action
import com.visorcraft.ghostgalleon.settings.CompanionRole
import com.visorcraft.ghostgalleon.settings.SettingsBundle
import com.visorcraft.ghostgalleon.settings.SettingsStore
import com.visorcraft.ghostgalleon.settings.SlotKey
import com.visorcraft.ghostgalleon.settings.ThemePack
import com.visorcraft.ghostgalleon.settings.label
import com.visorcraft.ghostgalleon.state.UIMode
import com.visorcraft.ghostgalleon.system.SystemInfoCollector
import com.visorcraft.ghostgalleon.system.SystemInfoFormat
import com.visorcraft.ghostgalleon.ui.ControllerLabActivity
import com.visorcraft.ghostgalleon.ui.deck.TileBackgrounds
import com.visorcraft.ghostgalleon.ui.hideStatusBar

class SettingsActivity : AppCompatActivity() {

    private val app get() = application as GhostGalleonApp

    /** Settings pages: Display & Grid stay together; others are their own. */
    private enum class SettingsPage(val title: String) {
        DISPLAY_GRID("Display & Grid"),
        APPS("Apps"),
        CONTROLS("Controls"),
        LIBRARY("Library"),
        STATS("Stats"),
        SYSTEM("System"),
        ABOUT("About"),
    }

    private var currentPage: SettingsPage = SettingsPage.DISPLAY_GRID
    private var pageHost: LinearLayout? = null
    private val pageBodies = mutableMapOf<SettingsPage, LinearLayout>()
    private val navItems = mutableMapOf<SettingsPage, TextView>()
    private var pageDropdownLabel: TextView? = null

    private val remappable = listOf(
        Action.CONFIRM, Action.BACK, Action.SWAP_SCREENS,
        Action.TOGGLE_MODE, Action.OPEN_SETTINGS, Action.PAGE_PREV, Action.PAGE_NEXT,
        Action.OPEN_QUICK_PANEL,
    )

    private var captureTarget: Action? = null
    private var captureLabel: TextView? = null
    private var capturePulse: ObjectAnimator? = null

    // True while a ROM library rescan runs; the settings row ignores taps.
    private var scanning = false

    // Label source for the Apps section modals (hidden apps / dock). Goes
    // through AppLibrary so custom names show here too.
    private val appLibrary by lazy {
        AppLibrary(PackageManagerAppsSource(packageManager, packageName))
    }

    private var hiddenValue: TextView? = null
    private var hiddenRomsValue: TextView? = null
    private var dockValue: TextView? = null

    private fun appLabel(packageName: String): String =
        appLibrary.all(app.settings).firstOrNull { it.packageName == packageName }
            ?.label ?: packageName

    // Dock keys are app packages or "rom:<id>" values.
    private fun dockEntryLabel(key: String): String {
        val romId = SlotKey.romId(key)
        return if (romId != null) {
            app.romEntries.firstOrNull { it.id == romId }?.name ?: key
        } else {
            appLabel(key)
        }
    }

    private fun labelForStatsKey(key: String): String = dockEntryLabel(key)

    private fun statRow(label: String, value: String): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(6), 0, dp(6))
            addView(rowLabel(label), LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(TextView(this@SettingsActivity).apply {
                text = value
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setTextColor(0xCCFFFFFF.toInt())
                gravity = Gravity.END
            })
        }

    /** Bundled pack asset basenames under assets/platform_packs/. */
    private fun listBundledPackAssets(): List<String> =
        runCatching {
            assets.list("platform_packs")
                ?.filter { it.endsWith(".json", ignoreCase = true) }
                ?.sorted()
                .orEmpty()
        }.getOrDefault(emptyList())

    private fun loadBundledPackAsset(assetName: String) {
        val text = runCatching {
            assets.open("platform_packs/$assetName").bufferedReader().use { it.readText() }
        }.getOrNull()
        if (text == null) {
            Toast.makeText(this, "Pack missing from APK: $assetName", Toast.LENGTH_SHORT).show()
            return
        }
        val parsed = app.platformPackStore.importJson(text)
        if (parsed == null) {
            Toast.makeText(this, "Invalid pack: $assetName", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(
                this,
                "Loaded $assetName: ${parsed.platforms.joinToString { it.id }}",
                Toast.LENGTH_LONG,
            ).show()
            recreate()
        }
    }

    /** Merge all bundled packs into one overlay (later packs win on id clash). */
    private fun loadAllBundledPacks() {
        val names = listBundledPackAssets()
        if (names.isEmpty()) {
            Toast.makeText(this, "No bundled packs in APK", Toast.LENGTH_SHORT).show()
            return
        }
        var merged = emptyList<com.visorcraft.ghostgalleon.rom.Platform>()
        var loaded = 0
        for (name in names) {
            val text = runCatching {
                assets.open("platform_packs/$name").bufferedReader().use { it.readText() }
            }.getOrNull() ?: continue
            val parsed = com.visorcraft.ghostgalleon.rom.PlatformPack.parse(text) ?: continue
            merged = com.visorcraft.ghostgalleon.rom.PlatformPack.merge(merged, parsed.platforms)
            loaded++
        }
        if (merged.isEmpty()) {
            Toast.makeText(this, "No valid packs found", Toast.LENGTH_LONG).show()
            return
        }
        // Serialize merged pack and install via the real store path.
        val root = org.json.JSONObject()
            .put("schemaVersion", 1)
            .put("platforms", org.json.JSONArray().apply {
                merged.forEach { p ->
                    put(org.json.JSONObject()
                        .put("id", p.id)
                        .put("displayName", p.displayName)
                        .put("shortName", p.shortName)
                        .put("folderNames", org.json.JSONArray(p.folderNames))
                        .put("extensions", org.json.JSONArray(p.extensions))
                        .put("players", org.json.JSONArray().apply {
                            p.players.forEach { pl ->
                                put(org.json.JSONObject()
                                    .put("id", pl.id)
                                    .put("displayName", pl.displayName)
                                    .put("component", pl.component)
                                    .put("action", pl.action ?: "")
                                    .put("uriStyle", pl.uriStyle.name)
                                    .put("grantRead", pl.grantRead)
                                    .put("flags", pl.flags)
                                    .put("extras", org.json.JSONObject().apply {
                                        pl.extras.forEach { (k, v) -> put(k, v) }
                                    }))
                            }
                        }))
                }
            })
        val result = app.platformPackStore.importJson(root.toString())
        if (result == null) {
            Toast.makeText(this, "Failed to install merged packs", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(
                this,
                "Loaded $loaded packs (${result.platforms.size} platforms)",
                Toast.LENGTH_LONG,
            ).show()
            recreate()
        }
    }

    private fun showBundledPackCatalog() {
        val names = listBundledPackAssets()
        if (names.isEmpty()) {
            Toast.makeText(this, "No bundled packs in APK", Toast.LENGTH_SHORT).show()
            return
        }
        val labels = names.map { it.removeSuffix(".json") }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Bundled platform packs")
            .setItems(labels) { _, which ->
                loadBundledPackAsset(names[which])
            }
            .setNeutralButton("Load all") { _, _ -> loadAllBundledPacks() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun loadBundledExamplePack() {
        // Backward-compatible entry: open the multi-pack catalog.
        showBundledPackCatalog()
    }

    private fun refreshAppsRows() {
        hiddenValue?.text = app.settings.hiddenPackages.size.toString()
        hiddenRomsValue?.text = app.settings.hiddenRomIds.size.toString()
        val dock = app.settings.dockSlots.filterNotNull()
        dockValue?.text =
            if (dock.isEmpty()) "Empty"
            else dock.joinToString(" · ", transform = ::dockEntryLabel)
    }

    // One label + chip row inside a management modal.
    private fun modalRow(label: String, chip: String, onChip: () -> Unit): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(rowLabel(label).apply {
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(TextView(this@SettingsActivity).apply {
                text = chip
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setTextColor(accent)
                background = pillDrawable(0xFF1C1C22.toInt(), 14, 0x26FFFFFF)
                val hp = dp(12); val vp = dp(6)
                setPadding(hp, vp, hp, vp)
                setOnClickListener { onChip() }
            })
        }

    private fun modalEmpty(text: String): View = TextView(this).apply {
        this.text = text
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        setTextColor(0x66FFFFFF)
        gravity = Gravity.CENTER
        val v = dp(16)
        setPadding(0, v, 0, v)
    }

    // Hidden-apps management: every hidden package with an Unhide chip;
    // unhiding removes it from settings.hiddenPackages (picker-visible
    // again). Grid slots holding a hidden app are unaffected either way.
    private fun showHiddenAppsDialog() {
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = dp(20)
            setPadding(pad, dp(8), pad, dp(8))
        }
        fun rebuild() {
            list.removeAllViews()
            val hidden = app.settings.hiddenPackages
                .sortedBy { appLabel(it).lowercase() }
            if (hidden.isEmpty()) {
                list.addView(modalEmpty("No hidden apps"))
            } else {
                hidden.forEach { pkg ->
                    list.addView(modalRow(appLabel(pkg), "Unhide") {
                        app.updateSettings(app.settings.copy(
                            hiddenPackages = app.settings.hiddenPackages - pkg))
                        refreshAppsRows()
                        rebuild()
                    }, LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(56)))
                }
            }
        }
        rebuild()
        AlertDialog.Builder(this)
            .setTitle("Hidden apps")
            .setView(ScrollView(this).apply { addView(list) })
            .setNegativeButton("Close", null)
            .show()
    }

    // User-hidden ROMs: unhide restores carousel/picker visibility; grid
    // slots that still hold the key remain launchable either way.
    private fun showHiddenRomsDialog() {
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = dp(20)
            setPadding(pad, dp(8), pad, dp(8))
        }
        fun romLabel(id: String): String {
            val rom = app.romEntries.firstOrNull { it.id == id }
            return rom?.name ?: id.substringAfterLast(':').ifBlank { id }
        }
        fun rebuild() {
            list.removeAllViews()
            val hidden = app.settings.hiddenRomIds
                .sortedBy { romLabel(it).lowercase() }
            if (hidden.isEmpty()) {
                list.addView(modalEmpty("No hidden ROMs"))
            } else {
                hidden.forEach { id ->
                    list.addView(modalRow(romLabel(id), "Unhide") {
                        val next = com.visorcraft.ghostgalleon.library.HiddenRoms
                            .unhide(app.settings.hiddenRomIds, id)
                        app.updateSettings(app.settings.copy(hiddenRomIds = next))
                        refreshAppsRows()
                        rebuild()
                    }, LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(56)))
                }
            }
        }
        rebuild()
        AlertDialog.Builder(this)
            .setTitle("Hidden ROMs")
            .setView(ScrollView(this).apply { addView(list) })
            .setNegativeButton("Close", null)
            .show()
    }

    /** Pick an installed launcher app as the companion pin. */
    private fun showPinnedCompanionPicker() {
        val apps = appLibrary.visible(app.settings)
            .sortedBy { it.label.lowercase() }
        if (apps.isEmpty()) {
            Toast.makeText(this, "No apps available", Toast.LENGTH_SHORT).show()
            return
        }
        val labels = apps.map { it.label }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Pinned companion app")
            .setItems(labels) { _, which ->
                val pkg = apps[which].packageName
                app.updateSettings(app.settings.copy(companionPinnedPackage = pkg))
                // Rebuild settings so the pin row label refreshes.
                recreate()
            }
            .setNeutralButton("Clear") { _, _ ->
                app.updateSettings(app.settings.copy(companionPinnedPackage = null))
                recreate()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // Dock management: current dock entries in order with a Remove chip.
    // Adding stays grid-side (slot menu "Pin to dock").
    private fun showDockDialog() {
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = dp(20)
            setPadding(pad, dp(8), pad, dp(8))
        }
        fun rebuild() {
            list.removeAllViews()
            val dock = app.settings.dockSlots.filterNotNull()
            if (dock.isEmpty()) {
                list.addView(modalEmpty("Dock is empty"))
            } else {
                dock.forEach { key ->
                    list.addView(modalRow(dockEntryLabel(key), "Remove") {
                        app.updateSettings(app.settings.copy(
                            dockSlots = app.settings.dockSlots.map {
                                if (it == key) null else it
                            }))
                        refreshAppsRows()
                        rebuild()
                    }, LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(56)))
                }
            }
        }
        rebuild()
        AlertDialog.Builder(this)
            .setTitle("Dock")
            .setView(ScrollView(this).apply { addView(list) })
            .setNegativeButton("Close", null)
            .show()
    }

    // Platform pack import (Library): JSON platforms/players overlay.
    private val platformPackLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri == null) return@registerForActivityResult
            val text = runCatching {
                contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.getOrNull()
            if (text == null) {
                Toast.makeText(this, "Could not read pack file", Toast.LENGTH_SHORT).show()
                return@registerForActivityResult
            }
            val parsed = app.platformPackStore.importJson(text)
            if (parsed == null) {
                Toast.makeText(this, "Invalid platform pack", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(
                    this,
                    "Imported ${parsed.platforms.size} platform(s)",
                    Toast.LENGTH_LONG,
                ).show()
                recreate()
            }
        }

    // Settings export/import (Library section): one JSON bundle holding the
    // settings object and the ROM library array (SettingsBundle). Import
    // validates by decoding through the real codecs, then swaps both stores
    // and reloads all state; anything malformed is rejected with a Toast.
    private val exportLauncher =
        registerForActivityResult(
            ActivityResultContracts.CreateDocument("application/json")
        ) { uri: Uri? ->
            if (uri == null) return@registerForActivityResult
            runCatching {
                val text = SettingsBundle.pack(
                    SettingsStore.toJson(app.settings),
                    RomLibrary.entriesToJson(app.romLibrary.load()),
                )
                contentResolver.openOutputStream(uri)?.use {
                    it.write(text.toByteArray(Charsets.UTF_8))
                } ?: error("could not open $uri")
            }.onSuccess {
                Toast.makeText(this, "Settings exported", Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(this, "Export failed", Toast.LENGTH_SHORT).show()
            }
        }

    private val importLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri == null) return@registerForActivityResult
            runCatching {
                val text = contentResolver.openInputStream(uri)?.use {
                    it.readBytes().toString(Charsets.UTF_8)
                } ?: error("could not open $uri")
                val (settingsJson, romJson) = SettingsBundle.unpack(text)
                // Codec-level validation: bad enums/types throw here, before
                // anything is persisted.
                val newSettings = SettingsStore.parse(settingsJson)
                val entries = RomLibrary.parseEntries(romJson)
                app.romLibrary.save(entries)
                // load() re-applies SwitchDedupe, matching a fresh scan.
                app.publishRomEntries(app.romLibrary.load())
                app.updateSettings(newSettings)
            }.onSuccess {
                Toast.makeText(this, "Settings imported", Toast.LENGTH_SHORT).show()
                recreate()
            }.onFailure {
                Toast.makeText(this, "Invalid settings file", Toast.LENGTH_SHORT).show()
            }
        }

    // SAF image picker for the grid wallpaper. The read grant is persisted
    // so the URI keeps working across reboots; settings hot-reload rebuilds
    // the grid with the new image.
    private val wallpaperPicker =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri != null) {
                runCatching {
                    contentResolver.takePersistableUriPermission(
                        uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                app.updateSettings(app.settings.copy(wallpaperUri = uri.toString()))
                refreshWallpaperRow()
            }
        }

    private val themeJsonPicker =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri == null) return@registerForActivityResult
            val text = runCatching {
                contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.getOrNull()
            if (text == null) {
                Toast.makeText(this, "Could not read theme file", Toast.LENGTH_SHORT).show()
                return@registerForActivityResult
            }
            val tokens = ThemePack.parseJson(text)
            if (tokens == null) {
                Toast.makeText(this, "Invalid theme JSON", Toast.LENGTH_LONG).show()
            } else {
                app.updateSettings(ThemePack.applyCustom(app.settings, tokens, text))
                Toast.makeText(this, "Theme: ${tokens.displayName}", Toast.LENGTH_SHORT).show()
                recreate()
            }
        }

    private var wallpaperValue: TextView? = null
    private var wallpaperClear: View? = null

    // SteamGridDB gap-filler (Stage 3 Task 3): the API-key row shows
    // Set/Not set (long-press clears); the download row binds to the
    // APP-SCOPED scrape job (GhostGalleonApp.scrapeJob) — the job survives
    // this screen. The row shows live "N/M" progress and turns into Cancel
    // while running; the listener is registered in onResume and removed in
    // onPause, so leaving Settings never cancels the job.
    private var sgdbKeyValue: TextView? = null
    private var scrapeLabel: TextView? = null
    private var scrapeValue: TextView? = null
    private var scrapeRow: View? = null

    private val scrapeListener = object : ScrapeJob.Listener {
        override fun onProgress(done: Int, total: Int) {
            scrapeLabel?.text = "Cancel"
            scrapeValue?.text = "$done/$total"
        }

        override fun onFinished(summary: SgdbScraper.Summary) {
            refreshSgdbRows()
            // The listener only fires while resumed, but a finish can race
            // onPause; never toast into a destroyed activity.
            if (!isFinishing && !isDestroyed) {
                val text = "Artwork: ${summary.downloaded} downloaded, " +
                    "${summary.skipped} skipped, ${summary.failed} failed" +
                    if (summary.cancelled) " (cancelled)" else ""
                Toast.makeText(this@SettingsActivity, text, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun refreshSgdbRows() {
        val hasKey = !app.settings.sgdbApiKey.isNullOrEmpty()
        val job = app.scrapeJob
        val running = job.isRunning
        sgdbKeyValue?.text = if (hasKey) "Set" else "Not set"
        if (running) {
            // Rebind to live job state (progress survives re-renders because
            // it lives in the app, not the views).
            scrapeLabel?.text = "Cancel"
            scrapeValue?.text = if (job.progressTotal > 0)
                "${job.progressDone}/${job.progressTotal}" else "…"
        } else {
            scrapeLabel?.text = "Download missing artwork"
            scrapeValue?.text = if (hasKey) "" else "Add API key first"
        }
        val usable = hasKey || running
        scrapeRow?.isEnabled = usable
        scrapeRow?.alpha = if (usable) 1f else 0.5f
    }

    private fun showCollectionsDialog() {
        val names = app.settings.collections.keys.sortedBy { it.lowercase() }
            .toMutableList()
        names.add(0, "+ New collection")
        AlertDialog.Builder(this)
            .setTitle("Collections")
            .setItems(names.toTypedArray()) { _, which ->
                if (which == 0) {
                    val input = EditText(this).apply {
                        hint = "Name"
                        setTextColor(Color.WHITE)
                        setHintTextColor(0x66FFFFFF)
                    }
                    AlertDialog.Builder(this)
                        .setTitle("New collection")
                        .setView(input)
                        .setPositiveButton("Create") { _, _ ->
                            val next = com.visorcraft.ghostgalleon.library.CollectionsOps
                                .createCollection(
                                    app.settings.collections,
                                    input.text?.toString().orEmpty(),
                                )
                            app.updateSettings(app.settings.copy(collections = next))
                            recreate()
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                } else {
                    val name = names[which]
                    val count = app.settings.collections[name]?.size ?: 0
                    AlertDialog.Builder(this)
                        .setTitle(name)
                        .setMessage("$count items")
                        .setPositiveButton("Rename") { _, _ ->
                            val input = EditText(this).apply {
                                setText(name)
                                setTextColor(Color.WHITE)
                            }
                            AlertDialog.Builder(this)
                                .setTitle("Rename")
                                .setView(input)
                                .setPositiveButton("Save") { _, _ ->
                                    val next = com.visorcraft.ghostgalleon.library.CollectionsOps
                                        .renameCollection(
                                            app.settings.collections,
                                            name,
                                            input.text?.toString().orEmpty(),
                                        )
                                    app.updateSettings(app.settings.copy(collections = next))
                                    recreate()
                                }
                                .setNegativeButton("Cancel", null)
                                .show()
                        }
                        .setNeutralButton("Delete") { _, _ ->
                            val next = com.visorcraft.ghostgalleon.library.CollectionsOps
                                .deleteCollection(app.settings.collections, name)
                            app.updateSettings(app.settings.copy(collections = next))
                            recreate()
                        }
                        .setNegativeButton("Close", null)
                        .show()
                }
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showDefaultPlayersDialog() {
        val platforms = com.visorcraft.ghostgalleon.rom.Platforms.ALL
        val labels = platforms.map { p ->
            val defId = app.settings.defaultPlayers[p.id]
            val def = defId?.let { id -> p.players.firstOrNull { it.id == id } }
                ?: p.player
            "${p.displayName}: ${def.displayName}"
        }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Default players")
            .setItems(labels) { _, which ->
                val platform = platforms[which]
                val playerLabels = platform.players.map { it.displayName }.toTypedArray()
                AlertDialog.Builder(this)
                    .setTitle(platform.displayName)
                    .setItems(playerLabels) { _, pWhich ->
                        val player = platform.players[pWhich]
                        app.updateSettings(
                            app.settings.copy(
                                defaultPlayers = app.settings.defaultPlayers +
                                    (platform.id to player.id),
                            ),
                        )
                        recreate()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showSgdbKeyDialog() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_VARIATION_PASSWORD
            setText(app.settings.sgdbApiKey ?: "")
            setSelectAllOnFocus(true)
            setTextColor(Color.WHITE)
            setHintTextColor(offTint)
            hint = "Paste API key"
        }
        val container = FrameLayout(this).apply {
            val margin = dp(20)
            setPadding(margin, dp(12), margin, 0)
            addView(input)
        }
        AlertDialog.Builder(this)
            .setTitle("SteamGridDB API key")
            .setView(container)
            .setPositiveButton("Save") { _, _ ->
                val key = input.text.toString().trim().ifEmpty { null }
                app.updateSettings(app.settings.copy(sgdbApiKey = key))
                refreshSgdbRows()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showRaUsernameDialog() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            setText(app.settings.raUsername ?: "")
            setSelectAllOnFocus(true)
            setTextColor(Color.WHITE)
            setHintTextColor(offTint)
            hint = "RA username"
        }
        val container = FrameLayout(this).apply {
            val margin = dp(20)
            setPadding(margin, dp(12), margin, 0)
            addView(input)
        }
        AlertDialog.Builder(this)
            .setTitle("RetroAchievements username")
            .setView(container)
            .setPositiveButton("Save") { _, _ ->
                val name = input.text.toString().trim().ifEmpty { null }
                app.updateSettings(app.settings.copy(raUsername = name))
                recreate()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showRaApiKeyDialog() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_VARIATION_PASSWORD
            setText(app.settings.raApiKey ?: "")
            setSelectAllOnFocus(true)
            setTextColor(Color.WHITE)
            setHintTextColor(offTint)
            hint = "Paste API key"
        }
        val container = FrameLayout(this).apply {
            val margin = dp(20)
            setPadding(margin, dp(12), margin, 0)
            addView(input)
        }
        AlertDialog.Builder(this)
            .setTitle("RetroAchievements API key")
            .setView(container)
            .setPositiveButton("Save") { _, _ ->
                val key = input.text.toString().trim().ifEmpty { null }
                app.updateSettings(app.settings.copy(raApiKey = key))
                recreate()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /**
     * Demo path: parse a tiny embedded RA progress payload for the currently
     * selected ROM (or first library ROM) so the hero RA line can be verified.
     */
    private fun loadRaSampleForSelection() {
        val romId = SlotKey.romId(app.deckState.selectedKey)
            ?: app.romEntries.firstOrNull { it.visibleInUi }?.id
        if (romId == null) {
            Toast.makeText(this, "No ROM selected", Toast.LENGTH_SHORT).show()
            return
        }
        val sample = """
            {"ID":1,"Title":"Sample","NumAwardedToUser":3,"NumAchievements":10,"HardcoreMode":0}
        """.trimIndent()
        // Ensure credentials flag so heroLine is eligible to show.
        if (app.settings.raApiKey.isNullOrBlank()) {
            app.updateSettings(app.settings.copy(raApiKey = "sample"), notify = false)
        }
        app.setRaProgress(romId, sample)
        val line = RetroAchievements.heroLine(
            app.raProgressFor(romId),
            !app.settings.raApiKey.isNullOrBlank(),
        )
        Toast.makeText(
            this,
            line ?: "RA sample loaded",
            Toast.LENGTH_SHORT,
        ).show()
    }

    private fun onScrapeRowClicked() {
        val job = app.scrapeJob
        if (job.isRunning) {
            job.cancel()
            scrapeValue?.text = "Cancelling…"
            return
        }
        val key = app.settings.sgdbApiKey ?: return
        if (job.start(key, app.romEntries)) {
            scrapeLabel?.text = "Cancel"
            scrapeValue?.text = "…"
        }
    }

    // SAF tree picker for ROM folders. The read grant is persisted so the
    // tree stays readable across reboots; the URI lands in romTreeUris.
    private val romFolderPicker =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
            if (uri != null) {
                runCatching {
                    contentResolver.takePersistableUriPermission(
                        uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                val trees = app.settings.romTreeUris
                if (uri.toString() !in trees) {
                    app.updateSettings(app.settings.copy(romTreeUris = trees + uri.toString()))
                }
                refreshFolderRows()
            }
        }

    private var folderRows: LinearLayout? = null

    private fun removeRomFolder(uriString: String) {
        runCatching {
            contentResolver.releasePersistableUriPermission(
                Uri.parse(uriString), Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        app.updateSettings(app.settings.copy(
            romTreeUris = app.settings.romTreeUris - uriString))
        refreshFolderRows()
    }

    private fun refreshFolderRows() {
        val rows = folderRows ?: return
        rows.removeAllViews()
        app.settings.romTreeUris.forEach { uriString ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            row.addView(rowLabel(TreeLabels.label(uriString)), LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(TextView(this).apply {
                text = "Remove"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setTextColor(accent)
                background = pillDrawable(0xFF1C1C22.toInt(), 14, 0x26FFFFFF)
                val hp = dp(12); val vp = dp(6)
                setPadding(hp, vp, hp, vp)
                setOnClickListener { removeRomFolder(uriString) }
            })
            rows.addView(row, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(64)))
        }
    }

    private fun wallpaperDisplayName(uriString: String): String = runCatching {
        val uri = Uri.parse(uriString)
        contentResolver.query(uri, null, null, null, null)?.use { c ->
            val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (i >= 0 && c.moveToFirst()) c.getString(i) else null
        } ?: uri.lastPathSegment ?: "Set"
    }.getOrDefault("Set")

    private fun refreshWallpaperRow() {
        val uri = app.settings.wallpaperUri
        wallpaperValue?.text = if (uri != null) wallpaperDisplayName(uri) else "None"
        wallpaperClear?.visibility = if (uri != null) View.VISIBLE else View.GONE
    }

    private val accent get() = app.settings.accentColor

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Ghost Galleon Settings"
        setContentView(buildContent())
    }

    override fun onResume() {
        super.onResume()
        // Same immersive status-bar hiding as the deck activities.
        hideStatusBar(window)
        // Bind to the app-scoped scrape job while visible; the job itself
        // is owned by GhostGalleonApp and keeps running when this screen
        // pauses or is destroyed.
        app.scrapeJob.addListener(scrapeListener)
        refreshSgdbRows()
    }

    override fun onPause() {
        app.scrapeJob.removeListener(scrapeListener)
        super.onPause()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun dpF(value: Int): Float =
        value * resources.displayMetrics.density

    /** Accent with an explicit alpha channel. */
    private fun withAlpha(color: Int, alpha: Int): Int =
        (color and 0x00FFFFFF) or (alpha shl 24)

    private val offTint = 0x4DFFFFFF.toInt() // 30% white

    // Small uppercase section header, letter-spaced, tinted with the accent.
    private fun sectionHeader(text: String): TextView = TextView(this).apply {
        this.text = text.uppercase()
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        setTextColor(withAlpha(accent, 0xCC))
        letterSpacing = 0.15f
    }

    // Card container grouping one section's controls.
    private fun sectionCard(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = TileBackgrounds.card(this@SettingsActivity)
        val pad = dp(20)
        setPadding(pad, pad, pad, pad)
    }

    private fun rowLabel(text: String): TextView = TextView(this).apply {
        this.text = text
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
        setTextColor(Color.WHITE)
    }

    /** A 64dp control row: label left, control right. */
    private fun controlRow(label: String, control: View): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(rowLabel(label), LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(control)
        }

    private fun accentSwitch(checked: Boolean, onChange: (Boolean) -> Unit): Switch =
        Switch(this).apply {
            val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
            thumbTintList = ColorStateList(states, intArrayOf(accent, offTint))
            trackTintList = ColorStateList(
                states, intArrayOf(withAlpha(accent, 0x66), offTint))
            isChecked = checked
            setOnCheckedChangeListener { _, isOn -> onChange(isOn) }
        }

    private fun pillDrawable(fill: Int, radiusDp: Int, stroke: Int = 0): GradientDrawable =
        GradientDrawable().apply {
            setColor(fill)
            cornerRadius = dpF(radiusDp)
            if (stroke != 0) setStroke(dp(1), stroke)
        }

    /** Segmented pill control (Default mode / Grid scrolling style). */
    private fun segmented(
        options: List<Pair<String, String>>, // value -> pill text
        current: String,
        onSelect: (String) -> Unit,
    ): View {
        val track = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = pillDrawable(0xFF1C1C22.toInt(), 20, 0x26FFFFFF)
            setPadding(dp(3), dp(3), dp(3), dp(3))
        }
        val pills = mutableMapOf<String, TextView>()
        var selected = current

        fun restyle() {
            pills.forEach { (value, pill) ->
                if (value == selected) {
                    pill.background = pillDrawable(accent, 17)
                    pill.setTextColor(Color.BLACK)
                } else {
                    pill.background = null
                    pill.setTextColor(Color.WHITE)
                }
            }
        }

        options.forEach { (value, text) ->
            val pill = TextView(this).apply {
                this.text = text
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                letterSpacing = 0.1f
                gravity = Gravity.CENTER
                isFocusable = true
                setPadding(dp(14), 0, dp(14), 0)
                setOnClickListener {
                    if (selected != value) {
                        selected = value
                        onSelect(value)
                    }
                    restyle()
                }
            }
            pills[value] = pill
            track.addView(pill, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(34)))
        }
        restyle()
        return track
    }

    /** Two-pill segmented control for the default mode. */
    private fun modeSegmented(current: UIMode): View = segmented(
        listOf(UIMode.GRID.name to "GRID", UIMode.GAME.name to "GAME"),
        current.name,
    ) { app.updateSettings(app.settings.copy(defaultMode = UIMode.valueOf(it))) }

    /** Bound-key chip: card-surface pill, accent text. */
    private fun keyChip(bound: String): TextView = TextView(this).apply {
        text = bound
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        setTextColor(accent)
        background = pillDrawable(0xFF1C1C22.toInt(), 14, 0x26FFFFFF)
        setPadding(dp(12), dp(6), dp(12), dp(6))
    }

    /** Wide layout (top Sugar panel / docked): left nav blade. Narrow: dropdown. */
    private fun isWideSettings(): Boolean {
        val dm = resources.displayMetrics
        return dm.widthPixels >= (700f * dm.density).toInt()
    }

    private fun selectPage(page: SettingsPage) {
        currentPage = page
        val host = pageHost ?: return
        host.removeAllViews()
        val body = pageBodies[page] ?: return
        // Re-parent safely if the body was already attached.
        (body.parent as? ViewGroup)?.removeView(body)
        host.addView(body, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ))
        paintNav()
        pageDropdownLabel?.text = page.title
    }

    private fun paintNav() {
        navItems.forEach { (page, view) ->
            val selected = page == currentPage
            view.setTextColor(if (selected) Color.BLACK else Color.WHITE)
            view.background = if (selected) {
                pillDrawable(accent, 14)
            } else {
                null
            }
            view.setTypeface(
                null,
                if (selected) android.graphics.Typeface.BOLD
                else android.graphics.Typeface.NORMAL,
            )
        }
    }

    private fun buildNavBlade(): View {
        val blade = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = GradientDrawable().apply {
                setColor(0xFF121218.toInt())
                cornerRadius = dpF(20)
            }
        }
        SettingsPage.entries.forEach { page ->
            val item = TextView(this).apply {
                text = page.title
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(16), dp(14), dp(16), dp(14))
                isFocusable = true
                setOnClickListener { selectPage(page) }
            }
            navItems[page] = item
            blade.addView(item, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { bottomMargin = dp(6) })
        }
        paintNav()
        return blade
    }

    private fun buildPageDropdown(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                setColor(0xFF1C1C22.toInt())
                cornerRadius = dpF(18)
                setStroke(dp(1), 0x33FFFFFF)
            }
            setPadding(dp(16), dp(12), dp(16), dp(12))
            isFocusable = true
            setOnClickListener { showPagePicker() }
        }
        val label = TextView(this).apply {
            text = currentPage.title
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(Color.WHITE)
        }
        pageDropdownLabel = label
        row.addView(label, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(TextView(this).apply {
            text = "▾"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setTextColor(accent)
        })
        return row
    }

    private fun showPagePicker() {
        val labels = SettingsPage.entries.map { it.title }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Settings page")
            .setItems(labels) { _, which ->
                selectPage(SettingsPage.entries[which])
            }
            .show()
    }

    private fun buildContent(): View {
        val s = app.settings
        val wide = isWideSettings()

        // Per-page vertical stacks of section headers + cards.
        SettingsPage.entries.forEach { page ->
            pageBodies[page] = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 0, 0, dp(24))
            }
        }

        fun addSection(page: SettingsPage, title: String, card: LinearLayout) {
            val root = pageBodies.getValue(page)
            root.addView(sectionHeader(title), LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = dp(24)
                bottomMargin = dp(10)
            })
            root.addView(card, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ))
        }

        val shell = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
            setPadding(dp(24), dp(20), dp(24), dp(16))
        }

        // Header: back button + title with a thin accent underline.
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(TextView(this).apply {
            text = "←"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            contentDescription = "Back"
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(dp(48), dp(48)))
        header.addView(TextView(this).apply {
            text = "Ghost Galleon Settings"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f)
            setTextColor(Color.WHITE)
            setPadding(dp(16), 0, 0, 0)
        })
        shell.addView(header, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ))
        // Thin accent underline beneath the title start.
        shell.addView(View(this).apply {
            setBackgroundColor(accent)
        }, LinearLayout.LayoutParams(dp(40), dp(2)).apply {
            marginStart = dp(64)
            topMargin = dp(6)
            bottomMargin = dp(12)
        })

        if (!wide) {
            shell.addView(buildPageDropdown(), LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { bottomMargin = dp(8) })
        }

        fun toggle(card: LinearLayout, label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
            card.addView(controlRow(label, accentSwitch(checked, onChange)),
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(64)))
        }

        fun seek(card: LinearLayout, label: String, value: Int, min: Int, max: Int, onChange: (Int) -> Unit) {
            val labelRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            labelRow.addView(rowLabel(label), LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            val valueView = TextView(this).apply {
                text = value.toString()
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
                setTextColor(accent)
            }
            labelRow.addView(valueView)
            card.addView(labelRow, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48)))
            card.addView(SeekBar(this).apply {
                progressTintList = ColorStateList.valueOf(accent)
                thumbTintList = ColorStateList.valueOf(accent)
                progressBackgroundTintList = ColorStateList.valueOf(offTint)
                minimumHeight = dp(32)
                this.max = max - min
                progress = value - min
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {
                        if (fromUser) valueView.text = (p + min).toString()
                    }
                    override fun onStartTrackingTouch(sb: SeekBar) = Unit
                    override fun onStopTrackingTouch(sb: SeekBar) = onChange(sb.progress + min)
                })
            }, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ))
        }

        // Display section.
        val displayCard = sectionCard()
        toggle(displayCard, "Gyroscope orientation", s.gyroEnabled) {
            app.updateSettings(app.settings.copy(gyroEnabled = it))
        }
        toggle(displayCard, "Angle lock (landscape only)", s.angleLock) {
            app.updateSettings(app.settings.copy(angleLock = it))
        }
        toggle(displayCard, "Show control hints", s.showHints) {
            app.updateSettings(app.settings.copy(showHints = it))
        }
        displayCard.addView(controlRow("Default mode", modeSegmented(s.defaultMode)),
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(64)))

        // Theme packs: built-in chips + optional import JSON.
        val themeOptions = ThemePack.BUILTINS.map { it.id to it.displayName.uppercase() }
        val themeCurrent = ThemePack.resolve(s).id.let { id ->
            if (ThemePack.BUILTINS.any { it.id == id }) id else ThemePack.GHOST.id
        }
        displayCard.addView(controlRow(
            "Theme",
            segmented(themeOptions, themeCurrent) { packId ->
                val tokens = ThemePack.byId(packId)
                app.updateSettings(ThemePack.applyToSettings(app.settings, tokens))
                recreate()
            },
        ), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(64)))
        val themeImportRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isFocusable = true
            setOnClickListener { themeJsonPicker.launch(arrayOf("application/json", "text/*", "*/*")) }
            setOnLongClickListener {
                if (app.settings.themeCustomJson != null) {
                    app.updateSettings(
                        ThemePack.applyToSettings(app.settings, ThemePack.GHOST),
                    )
                    Toast.makeText(this@SettingsActivity,
                        "Custom theme cleared", Toast.LENGTH_SHORT).show()
                    recreate()
                }
                true
            }
        }
        themeImportRow.addView(rowLabel("Import theme JSON"), LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        themeImportRow.addView(TextView(this).apply {
            text = if (s.themeCustomJson != null) "Custom" else "SAF"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(if (s.themeCustomJson != null) accent else 0x66FFFFFF.toInt())
        })
        displayCard.addView(themeImportRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(64)))
        // Portable display topology (Auto / Sugar / dual / single).
        val profileOptions = listOf(
            "auto" to "AUTO",
            "onex-sugar" to "SUGAR",
            "generic-dual" to "DUAL",
            "single" to "SINGLE",
        )
        displayCard.addView(controlRow(
            "Device profile",
            segmented(profileOptions, s.deviceProfileId) { id ->
                app.updateSettings(app.settings.copy(
                    deviceProfileId = id,
                    userPinnedPrimaryId = null,
                ))
                app.refreshDisplayConfig()
                recreate()
            },
        ), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(64)))
        val interactiveOptions = listOf(
            "auto" to "AUTO",
            "default" to "DEFAULT",
            "secondary" to "SECONDARY",
        )
        val interactiveCurrent = when {
            s.interactiveDisplayMode.startsWith("id:") -> "auto"
            else -> s.interactiveDisplayMode
        }
        displayCard.addView(controlRow(
            "Interactive display",
            segmented(interactiveOptions, interactiveCurrent) { mode ->
                app.updateSettings(app.settings.copy(
                    interactiveDisplayMode = mode,
                    userPinnedPrimaryId = null,
                ))
                app.refreshDisplayConfig()
                recreate()
            },
        ), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(64)))
        val orientOptions = listOf(
            "auto" to "AUTO",
            "sensor_landscape" to "SENSOR",
            "lock_landscape" to "LOCK",
        )
        displayCard.addView(controlRow(
            "Orientation",
            segmented(orientOptions, s.orientationMode) { mode ->
                app.updateSettings(app.settings.copy(orientationMode = mode))
                recreate()
            },
        ), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(64)))
        val resetDisplayRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isFocusable = true
            setOnClickListener {
                app.updateSettings(app.settings.copy(
                    userPinnedPrimaryId = null,
                    interactiveDisplayMode = "auto",
                    deviceProfileId = "auto",
                ))
                app.refreshDisplayConfig()
                Toast.makeText(this@SettingsActivity, "Display roles reset", Toast.LENGTH_SHORT).show()
                recreate()
            }
        }
        resetDisplayRow.addView(rowLabel("Reset display roles"), LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        displayCard.addView(resetDisplayRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(64)))
        addSection(SettingsPage.DISPLAY_GRID, "Display", displayCard)

        // Browse chrome: minimal default; power users opt into extra rails.
        val chrome = s.browseChrome
        val chromeCard = sectionCard()
        val presetOptions = listOf("minimal" to "MINIMAL", "full" to "FULL")
        val presetId = when {
            chrome.isFull() -> "full"
            chrome.isMinimal() -> "minimal"
            else -> "minimal" // custom: applying Minimal/Full resets
        }
        chromeCard.addView(controlRow(
            "Chrome preset",
            segmented(presetOptions, presetId) { id ->
                val next = if (id == "full") {
                    com.visorcraft.ghostgalleon.settings.BrowseChrome.FULL
                } else {
                    com.visorcraft.ghostgalleon.settings.BrowseChrome.MINIMAL
                }
                app.updateSettings(app.settings.copy(browseChrome = next))
                recreate()
            },
        ), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(64)))
        fun chromeFlag(
            label: String,
            checked: Boolean,
            set: (com.visorcraft.ghostgalleon.settings.BrowseChrome, Boolean) ->
                com.visorcraft.ghostgalleon.settings.BrowseChrome,
        ) {
            toggle(chromeCard, label, checked) { on ->
                app.updateSettings(
                    app.settings.copy(browseChrome = set(app.settings.browseChrome, on)),
                )
            }
        }
        chromeFlag("Installed rail", chrome.installedRail) { c, v -> c.copy(installedRail = v) }
        chromeFlag("Games rail", chrome.gamesRail) { c, v -> c.copy(gamesRail = v) }
        chromeFlag("Top (most played) rail", chrome.topRail) { c, v -> c.copy(topRail = v) }
        chromeFlag("Today (last 24 hours) rail", chrome.todayRail) { c, v -> c.copy(todayRail = v) }
        chromeFlag("Week (last 7 days) rail", chrome.weekRail) { c, v -> c.copy(weekRail = v) }
        chromeFlag("Month (last 30 days) rail", chrome.monthRail) { c, v -> c.copy(monthRail = v) }
        chromeFlag("A–Z rail + letter jump", chrome.alphaRail) { c, v -> c.copy(alphaRail = v) }
        chromeFlag("New (unplayed) rail", chrome.unplayedRail) { c, v -> c.copy(unplayedRail = v) }
        chromeFlag("Random chip", chrome.randomChip) { c, v -> c.copy(randomChip = v) }
        chromeFlag("Genre chips", chrome.genreChips) { c, v -> c.copy(genreChips = v) }
        chromeFlag("Developer chips", chrome.developerChips) { c, v -> c.copy(developerChips = v) }
        chromeFlag("Year decade chips", chrome.yearChips) { c, v -> c.copy(yearChips = v) }
        chromeFlag(
            "Only launchable ROMs (player installed)",
            chrome.launchableOnly,
        ) { c, v -> c.copy(launchableOnly = v) }
        chromeFlag("Platform chips", chrome.platformChips) { c, v -> c.copy(platformChips = v) }
        chromeFlag("Collection rails", chrome.collectionRails) { c, v -> c.copy(collectionRails = v) }
        chromeFlag(
            "Clock / battery (time & charge)",
            chrome.deckStatusPill,
        ) { c, v -> c.copy(deckStatusPill = v) }
        chromeFlag(
            "Resume chip (last played on companion)",
            chrome.resumeChip,
        ) { c, v -> c.copy(resumeChip = v) }
        chromeFlag(
            "Quick Panel browse (Fav/rails)",
            chrome.quickPanelBrowse,
        ) { c, v -> c.copy(quickPanelBrowse = v) }
        addSection(
            SettingsPage.DISPLAY_GRID,
            "Browse chrome (minimal default)",
            chromeCard,
        )

        // Companion panel role + pinned package (same page as Display).
        val companionCard = sectionCard()
        val roleOptions = listOf(
            CompanionRole.HERO.name to "HERO",
            CompanionRole.NOW_PLAYING.name to "NOW",
            CompanionRole.PERF_HUD.name to "PERF",
            CompanionRole.PINNED_APP.name to "PIN",
        )
        companionCard.addView(
            controlRow(
                "Companion role",
                segmented(roleOptions, s.companionRole) { next ->
                    app.updateSettings(app.settings.copy(companionRole = next))
                },
            ),
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(64)),
        )
        val pinRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isFocusable = true
            setOnClickListener { showPinnedCompanionPicker() }
            setOnLongClickListener {
                app.updateSettings(app.settings.copy(companionPinnedPackage = null))
                Toast.makeText(this@SettingsActivity, "Pin cleared", Toast.LENGTH_SHORT).show()
                true
            }
        }
        pinRow.addView(rowLabel("Pinned companion app"), LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        pinRow.addView(TextView(this).apply {
            text = s.companionPinnedPackage?.let { pkg ->
                appLabel(pkg)
            } ?: "None (long-press to clear)"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(accent)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            gravity = Gravity.END
        }, LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.2f))
        companionCard.addView(pinRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(64)))
        companionCard.addView(TextView(this).apply {
            text = "Long-press the pin row to clear. Dual-screen games (NDS/3DS) degrade Pin automatically."
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(0x66FFFFFF.toInt())
            setPadding(0, 0, 0, dp(4))
        })
        addSection(SettingsPage.DISPLAY_GRID, "Companion", companionCard)

        // Grid section (same page as Display).
        val gridCard = sectionCard()
        gridCard.addView(controlRow("Grid scrolling", segmented(
            listOf("vertical" to "VERTICAL", "horizontal" to "HORIZONTAL"),
            s.gridDirection,
        ) { app.updateSettings(app.settings.copy(gridDirection = it)) }),
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(64)))
        seek(gridCard, "Grid columns", s.gridColumns, 3, 8) {
            app.updateSettings(app.settings.copy(gridColumns = it))
        }
        seek(gridCard, "Icon size (dp)", s.iconSizeDp, 48, 128) {
            app.updateSettings(app.settings.copy(iconSizeDp = it))
        }
        toggle(gridCard, "Show app names", s.showLabels) {
            app.updateSettings(app.settings.copy(showLabels = it))
        }
        // Grid wallpaper: tap the row to pick an image via SAF, Clear to
        // go back to plain black.
        val wallpaperRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isFocusable = true
            setOnClickListener { wallpaperPicker.launch(arrayOf("image/*")) }
        }
        wallpaperRow.addView(rowLabel("Grid wallpaper"), LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val valueView = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(accent)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        wallpaperValue = valueView
        wallpaperRow.addView(valueView, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginEnd = dp(12) })
        val clearView = TextView(this).apply {
            text = "Clear"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(Color.WHITE)
            background = pillDrawable(0xFF1C1C22.toInt(), 14, 0x26FFFFFF)
            val hp = dp(12); val vp = dp(6)
            setPadding(hp, vp, hp, vp)
            setOnClickListener {
                app.updateSettings(app.settings.copy(wallpaperUri = null))
                refreshWallpaperRow()
            }
        }
        wallpaperClear = clearView
        wallpaperRow.addView(clearView)
        refreshWallpaperRow()
        gridCard.addView(wallpaperRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(64)))
        addSection(SettingsPage.DISPLAY_GRID, "Grid", gridCard)

        // Apps section: hidden-apps and dock management. Adding stays
        // grid-side (picker hide menu / slot "Pin to dock"); these rows are
        // the management surface.
        val appsCard = sectionCard()
        val hiddenRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isFocusable = true
            setOnClickListener { showHiddenAppsDialog() }
        }
        hiddenRow.addView(rowLabel("Hidden apps"), LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        hiddenValue = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(accent)
        }
        hiddenRow.addView(hiddenValue)
        appsCard.addView(hiddenRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(64)))
        val dockRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isFocusable = true
            setOnClickListener { showDockDialog() }
        }
        dockRow.addView(rowLabel("Dock"), LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        dockValue = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(accent)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            gravity = Gravity.END
        }
        dockRow.addView(dockValue, LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 2f))
        appsCard.addView(dockRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(64)))
        refreshAppsRows()
        addSection(SettingsPage.APPS, "Apps", appsCard)

        // Controls section: haptics + remappable button rows.
        val controlsCard = sectionCard()
        toggle(controlsCard, "Haptics", s.haptics) {
            app.updateSettings(app.settings.copy(haptics = it))
        }
        val labRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isFocusable = true
            setOnClickListener {
                startActivity(Intent(this@SettingsActivity, ControllerLabActivity::class.java))
            }
        }
        labRow.addView(rowLabel("Controller lab"), LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        labRow.addView(TextView(this).apply {
            text = "Open"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(accent)
        })
        controlsCard.addView(labRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(64)))
        remappable.forEach { action ->
            val bound = app.settings.keyMap.entries
                .firstOrNull { it.value == action }?.key?.let { "keycode $it" } ?: "unbound"
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                isFocusable = true
            }
            row.addView(rowLabel(action.label()), LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            val chip = keyChip(bound)
            row.addView(chip)
            row.onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
                if (captureTarget != action) {
                    row.background = if (hasFocus) {
                        GradientDrawable().apply {
                            cornerRadius = dpF(16)
                            setStroke(dp(2), accent)
                        }
                    } else {
                        null
                    }
                }
            }
            row.setOnClickListener {
                captureTarget = action
                captureLabel = chip
                chip.text = "Press a button…"
                chip.setTextColor(Color.BLACK)
                chip.background = pillDrawable(accent, 14)
                capturePulse?.cancel()
                capturePulse = ObjectAnimator.ofFloat(chip, View.ALPHA, 1f, 0.35f).apply {
                    duration = 500
                    repeatMode = ValueAnimator.REVERSE
                    repeatCount = ValueAnimator.INFINITE
                    start()
                }
            }
            controlsCard.addView(row, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(64)))
        }
        addSection(SettingsPage.CONTROLS, "Controls", controlsCard)

        // Library section: granted SAF ROM folder trees + add/rescan rows.
        // Rescan SAF-walks the granted trees off the main thread via
        // RomLibrary and toasts the resulting entry count, or "library
        // unchanged" when every granted tree is unreadable (card ejected).
        val libraryCard = sectionCard()
        val rows = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        folderRows = rows
        libraryCard.addView(rows, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ))
        refreshFolderRows()
        val addRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isFocusable = true
            setOnClickListener { romFolderPicker.launch(null) }
        }
        addRow.addView(rowLabel("Add ROM folder"), LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addRow.addView(TextView(this).apply {
            text = "+"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setTextColor(accent)
            gravity = Gravity.CENTER
            background = pillDrawable(0xFF1C1C22.toInt(), 14, 0x26FFFFFF)
        }, LinearLayout.LayoutParams(dp(40), dp(40)))
        libraryCard.addView(addRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(64)))
        val hiddenRomsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isFocusable = true
            setOnClickListener { showHiddenRomsDialog() }
        }
        hiddenRomsRow.addView(rowLabel("Hidden ROMs"), LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        hiddenRomsValue = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(accent)
            text = app.settings.hiddenRomIds.size.toString()
        }
        hiddenRomsRow.addView(hiddenRomsValue)
        libraryCard.addView(hiddenRomsRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(64)))
        // Rescan library: guarded against re-entry — a second tap while the
        // ~50 s SAF walk runs would queue a duplicate scan on RomLibrary's
        // executor. While scanning, the label reads "Scanning…" and taps
        // are ignored; completion toasts are dropped once the activity is
        // gone (the scan can outlive the settings screen).
        val rescanLabel = rowLabel("Rescan library")
        fun startRescan(force: Boolean) {
            if (scanning) return
            scanning = true
            rescanLabel.text = if (force) "Full scan…" else "Scanning…"
            app.romLibrary.rescan(
                this@SettingsActivity,
                app.settings,
                force = force,
            ) { result ->
                scanning = false
                rescanLabel.text = "Rescan library"
                app.noteRescanOutcome(result)
                if (result is RomLibrary.RescanResult.Success) {
                    app.publishRomEntries(result.entries)
                }
                if (isFinishing || isDestroyed) return@rescan
                when (result) {
                    is RomLibrary.RescanResult.Success -> {
                        val skip = result.skippedCleanTrees
                        val msg = if (skip > 0) {
                            "${result.entries.size} ROMs ($skip tree(s) unchanged)"
                        } else {
                            "${result.entries.size} ROMs found"
                        }
                        Toast.makeText(this@SettingsActivity, msg, Toast.LENGTH_LONG).show()
                    }
                    RomLibrary.RescanResult.Unreadable ->
                        Toast.makeText(this@SettingsActivity,
                            "Card unreadable - library unchanged",
                            Toast.LENGTH_LONG).show()
                }
            }
        }
        val rescanRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isFocusable = true
            // Tap: incremental (skip clean trees). Long-press: force full.
            setOnClickListener { startRescan(force = false) }
            setOnLongClickListener {
                startRescan(force = true)
                true
            }
        }
        rescanRow.addView(rescanLabel, LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        rescanRow.addView(TextView(this).apply {
            text = "hold=full"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(0x66FFFFFF.toInt())
        })
        libraryCard.addView(rescanRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(64)))
        // Bulk-pin favorites into empty curated-grid slots (E).
        val pinFavsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isFocusable = true
            setOnClickListener {
                val keys = app.settings.favorites.toList()
                if (keys.isEmpty()) {
                    Toast.makeText(this@SettingsActivity,
                        "No favorites yet", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val filled = com.visorcraft.ghostgalleon.library.CollectionsOps
                    .bulkFillSlots(app.settings.gridSlots, keys)
                app.updateSettings(app.settings.copy(gridSlots = filled))
                Toast.makeText(this@SettingsActivity,
                    "Pinned ${keys.size} favorite(s) into grid",
                    Toast.LENGTH_SHORT).show()
            }
        }
        pinFavsRow.addView(
            rowLabel("Pin favorites to empty grid slots"),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
        )
        libraryCard.addView(pinFavsRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(64)))
        // Named collections management (Phase 1).
        val collectionsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isFocusable = true
            setOnClickListener { showCollectionsDialog() }
        }
        collectionsRow.addView(rowLabel("Collections"), LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        collectionsRow.addView(TextView(this).apply {
            text = app.settings.collections.size.toString()
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(accent)
        })
        libraryCard.addView(collectionsRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(64)))
        // Default emulator per platform (Phase 2).
        val playersRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isFocusable = true
            setOnClickListener { showDefaultPlayersDialog() }
        }
        playersRow.addView(rowLabel("Default players"), LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        playersRow.addView(TextView(this).apply {
            text = if (app.settings.defaultPlayers.isEmpty()) "System defaults"
            else "${app.settings.defaultPlayers.size} set"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(accent)
        })
        libraryCard.addView(playersRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(64)))
        // RetroAchievements credentials (optional; hero shows cached progress).
        val raUserRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isFocusable = true
            setOnClickListener { showRaUsernameDialog() }
            setOnLongClickListener {
                app.updateSettings(app.settings.copy(raUsername = null))
                Toast.makeText(this@SettingsActivity,
                    "RA username cleared", Toast.LENGTH_SHORT).show()
                recreate()
                true
            }
        }
        raUserRow.addView(rowLabel("RetroAchievements username"), LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        raUserRow.addView(TextView(this).apply {
            text = app.settings.raUsername?.takeIf { it.isNotBlank() } ?: "Not set"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(accent)
        })
        libraryCard.addView(raUserRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(64)))
        val raKeyRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isFocusable = true
            setOnClickListener { showRaApiKeyDialog() }
            setOnLongClickListener {
                app.updateSettings(app.settings.copy(raApiKey = null))
                Toast.makeText(this@SettingsActivity,
                    "RA API key cleared", Toast.LENGTH_SHORT).show()
                recreate()
                true
            }
        }
        raKeyRow.addView(rowLabel("RetroAchievements API key"), LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        raKeyRow.addView(TextView(this).apply {
            text = if (!app.settings.raApiKey.isNullOrBlank()) "Set" else "Not set"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(accent)
        })
        libraryCard.addView(raKeyRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(64)))
        val raSampleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isFocusable = true
            setOnClickListener { loadRaSampleForSelection() }
        }
        raSampleRow.addView(rowLabel("Load RA sample (selected game)"), LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        raSampleRow.addView(TextView(this).apply {
            text = "Demo"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(0x66FFFFFF.toInt())
        })
        libraryCard.addView(raSampleRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(64)))
        // SteamGridDB gap-filler: key entry row (tap = paste dialog,
        // long-press = clear) and the batch download row.
        val keyRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isFocusable = true
            setOnClickListener { showSgdbKeyDialog() }
            setOnLongClickListener {
                app.updateSettings(app.settings.copy(sgdbApiKey = null))
                refreshSgdbRows()
                Toast.makeText(this@SettingsActivity,
                    "API key cleared", Toast.LENGTH_SHORT).show()
                true
            }
        }
        keyRow.addView(rowLabel("SteamGridDB API key"), LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val keyValue = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(accent)
        }
        sgdbKeyValue = keyValue
        keyRow.addView(keyValue)
        libraryCard.addView(keyRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(64)))
        val downloadRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isFocusable = true
            setOnClickListener { onScrapeRowClicked() }
        }
        val downloadLabel = rowLabel("Download missing artwork")
        scrapeLabel = downloadLabel
        downloadRow.addView(downloadLabel, LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val downloadValue = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(accent)
        }
        scrapeValue = downloadValue
        downloadRow.addView(downloadValue)
        scrapeRow = downloadRow
        libraryCard.addView(downloadRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(64)))
        refreshSgdbRows()

        // Export/import: one SAF JSON bundle carrying settings + ROM
        // library (SettingsBundle). Import validates before touching either
        // store and rejects malformed files with a Toast.
        val exportRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isFocusable = true
            setOnClickListener { exportLauncher.launch("ghost-galleon-settings.json") }
        }
        exportRow.addView(rowLabel("Export settings"), LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        libraryCard.addView(exportRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(64)))
        val importRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isFocusable = true
            setOnClickListener { importLauncher.launch(arrayOf("application/json")) }
        }
        importRow.addView(rowLabel("Import settings"), LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        libraryCard.addView(importRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(64)))
        val packRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isFocusable = true
            setOnClickListener {
                platformPackLauncher.launch(arrayOf("application/json", "text/*", "*/*"))
            }
            setOnLongClickListener {
                app.platformPackStore.clear()
                Toast.makeText(
                    this@SettingsActivity,
                    "Platform pack cleared",
                    Toast.LENGTH_SHORT,
                ).show()
                recreate()
                true
            }
        }
        packRow.addView(rowLabel("Import platform pack"), LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        packRow.addView(TextView(this).apply {
            text = if (app.platformPackStore.hasPack()) {
                "${Platforms.packOverlay().size} pack"
            } else {
                "None"
            }
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(accent)
        })
        libraryCard.addView(packRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(64)))
        // Bundled catalog: assets/platform_packs/*.json (pick one, or Load all).
        // Note: importing a single pack replaces the stored overlay; use
        // "Load all" to merge every bundled pack into one overlay.
        val bundledCount = listBundledPackAssets().size
        val examplePackRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isFocusable = true
            setOnClickListener { showBundledPackCatalog() }
        }
        examplePackRow.addView(rowLabel("Bundled platform packs"), LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        examplePackRow.addView(TextView(this).apply {
            text = if (bundledCount > 0) "$bundledCount packs" else "none"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(accent)
        })
        libraryCard.addView(examplePackRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(64)))
        // Re-show setup card on next empty boot.
        val setupRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isFocusable = true
            setOnClickListener {
                app.updateSettings(app.settings.copy(setupDismissed = false))
                Toast.makeText(this@SettingsActivity,
                    "Setup will show when library is empty", Toast.LENGTH_SHORT).show()
            }
        }
        setupRow.addView(rowLabel("Reset first-run setup card"), LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        libraryCard.addView(setupRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(64)))
        addSection(SettingsPage.LIBRARY, "Library", libraryCard)

        // Light stats: most played + recently played from stored maps.
        val statsCard = sectionCard()
        val most = com.visorcraft.ghostgalleon.library.LibraryStats.mostPlayed(
            app.settings.playtimeMs, limit = 12,
        )
        val recent = com.visorcraft.ghostgalleon.library.LibraryStats.recentlyPlayed(
            app.settings.lastLaunchedMs, limit = 12,
        )
        if (!com.visorcraft.ghostgalleon.library.LibraryStats.hasAnySessions(
                app.settings.playtimeMs, app.settings.lastLaunchedMs,
            )
        ) {
            statsCard.addView(TextView(this).apply {
                text = "No play sessions yet. Launch a game from the grid or carousel."
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setTextColor(0x99FFFFFF.toInt())
                setPadding(0, dp(8), 0, dp(8))
            })
        } else {
            statsCard.addView(sectionHeader("Most played"))
            most.forEach { row ->
                statsCard.addView(statRow(labelForStatsKey(row.key),
                    com.visorcraft.ghostgalleon.library.SessionMath.formatPlaytime(row.score)))
            }
            if (most.isEmpty()) {
                statsCard.addView(TextView(this).apply {
                    text = "No playtime recorded yet"
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                    setTextColor(0x66FFFFFF.toInt())
                })
            }
            statsCard.addView(sectionHeader("Recently played"))
            recent.forEach { row ->
                val whenLabel = com.visorcraft.ghostgalleon.library.SessionMath.formatLastPlayed(
                    row.score, System.currentTimeMillis(),
                ) ?: "—"
                statsCard.addView(statRow(labelForStatsKey(row.key), whenLabel))
            }
            if (recent.isEmpty()) {
                statsCard.addView(TextView(this).apply {
                    text = "No launches recorded yet"
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                    setTextColor(0x66FFFFFF.toInt())
                })
            }
        }
        addSection(SettingsPage.STATS, "Stats", statsCard)

        // System: live device hardware / RAM / CPU / storage / battery / power.
        val systemCard = sectionCard()
        val topo = app.refreshDisplayConfig()
        val systemCardExtra = systemCard
        systemCardExtra.addView(statRow(
            "Display mode",
            "${topo.mode} · profile=${DeviceProfileCatalog.effective(app.settings.deviceProfileId, com.visorcraft.ghostgalleon.display.AndroidDisplayProbe.read(this)).id}",
        ))
        systemCardExtra.addView(statRow(
            "Topology",
            "primary=${topo.primaryDisplayId} companion=${topo.companionDisplayId} launch=${topo.launchDisplayId}",
        ))
        systemCardExtra.addView(TextView(this).apply {
            text = topo.reason
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(0x66FFFFFF.toInt())
            setPadding(0, 0, 0, dp(8))
        })
        val readings = SystemInfoCollector.collect(this)
        SystemInfoFormat.rows(readings).forEach { (label, value) ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(4), 0, dp(4))
            }
            row.addView(rowLabel(label), LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(TextView(this).apply {
                text = value
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setTextColor(0xCCFFFFFF.toInt())
                gravity = Gravity.END
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
            }, LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.2f,
            ))
            systemCard.addView(row, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = dp(6)
                bottomMargin = dp(6)
            })
        }
        val refreshSys = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isFocusable = true
            setOnClickListener { recreate() }
        }
        refreshSys.addView(rowLabel("Refresh readings"), LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        systemCard.addView(refreshSys, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(56)))
        addSection(SettingsPage.SYSTEM, "System", systemCard)

        // About page (Grexa-style): hero card with the dynamic version,
        // feature cards, project link, Licenses/Credits dialogs. Built by
        // AboutPage; no live settings binding, so it's static per inflate.
        pageBodies.getValue(SettingsPage.ABOUT).addView(
            AboutPage.build(this, accent),
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )

        // Body: optional left nav blade (wide) + scrollable page content.
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        if (wide) {
            body.addView(buildNavBlade(), LinearLayout.LayoutParams(
                dp(220), ViewGroup.LayoutParams.MATCH_PARENT,
            ).apply { marginEnd = dp(20) })
        }
        val host = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        pageHost = host
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            addView(host, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ))
        }
        body.addView(scroll, LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.MATCH_PARENT, 1f,
        ))
        shell.addView(body, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f,
        ))
        selectPage(currentPage)
        return shell
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val target = captureTarget ?: return super.onKeyDown(keyCode, event)
        val withoutTarget = app.settings.keyMap.filterValues { it != target }
        val newMap = withoutTarget + (keyCode to target)
        app.updateSettings(app.settings.copy(keyMap = newMap))
        capturePulse?.cancel()
        capturePulse = null
        captureLabel?.apply {
            alpha = 1f
            text = "keycode $keyCode"
            setTextColor(accent)
            background = pillDrawable(0xFF1C1C22.toInt(), 14, 0x26FFFFFF)
        }
        captureTarget = null
        captureLabel = null
        return true
    }
}
