package com.visorcraft.ghostgalleon

import android.app.Activity
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.visorcraft.ghostgalleon.art.HttpSgdbTransport
import com.visorcraft.ghostgalleon.art.ScrapeJob
import com.visorcraft.ghostgalleon.art.SgdbScraper
import com.visorcraft.ghostgalleon.library.DrawerListCache
import com.visorcraft.ghostgalleon.library.DrawerListKey
import com.visorcraft.ghostgalleon.library.OpenSession
import com.visorcraft.ghostgalleon.library.PlayStats
import com.visorcraft.ghostgalleon.library.RaProgress
import com.visorcraft.ghostgalleon.library.RetroAchievements
import com.visorcraft.ghostgalleon.library.SessionMath
import com.visorcraft.ghostgalleon.library.SessionTracker
import com.visorcraft.ghostgalleon.display.AndroidDisplayProbe
import com.visorcraft.ghostgalleon.display.DeviceProfileCatalog
import com.visorcraft.ghostgalleon.display.DisplayTopology
import com.visorcraft.ghostgalleon.display.ResolvedTopology
import com.visorcraft.ghostgalleon.display.SurfaceMode
import com.visorcraft.ghostgalleon.rom.PlatformPackStore
import com.visorcraft.ghostgalleon.rom.RomEntry
import com.visorcraft.ghostgalleon.rom.RomLibrary
import com.visorcraft.ghostgalleon.settings.DataMigrator
import com.visorcraft.ghostgalleon.settings.Settings
import com.visorcraft.ghostgalleon.settings.SettingsStore
import com.visorcraft.ghostgalleon.state.DeckState
import com.visorcraft.ghostgalleon.ui.BaseDeckActivity
import com.visorcraft.ghostgalleon.ui.CompanionActivity
import com.visorcraft.ghostgalleon.ui.DisplayRole
import com.visorcraft.ghostgalleon.ui.deck.PickerItem
import com.visorcraft.ghostgalleon.ui.deck.PickerItems
import com.visorcraft.ghostgalleon.library.AppEntry
import android.hardware.display.DisplayManager
import org.json.JSONObject
import java.io.File

class GhostGalleonApp : Application() {

    lateinit var deckState: DeckState
        private set

    lateinit var settings: Settings
        private set

    /**
     * Last resolved display topology (SINGLE/DUAL roles + launch id).
     * Refreshed via [refreshDisplayConfig]; safe default until first probe.
     */
    @Volatile
    var displayConfig: ResolvedTopology = ResolvedTopology(
        mode = SurfaceMode.SINGLE,
        primaryDisplayId = 0,
        companionDisplayId = null,
        launchDisplayId = 0,
        allIds = listOf(0),
        reason = "uninitialized",
    )
        private set

    private var lastDisplayRefreshUptimeMs: Long = 0L
    private var displayListenerRegistered = false

    val settingsStore: SettingsStore by lazy {
        SettingsStore(File(filesDir, "settings.json"))
    }

    /**
     * Probe displays, match profile, resolve topology, align DeckState.
     * Debounced when [debounce] is true (resume path).
     */
    fun refreshDisplayConfig(debounce: Boolean = false): ResolvedTopology {
        val now = android.os.SystemClock.uptimeMillis()
        if (debounce && now - lastDisplayRefreshUptimeMs < 500L) {
            return displayConfig
        }
        lastDisplayRefreshUptimeMs = now
        val readings = AndroidDisplayProbe.read(this)
        val profile = DeviceProfileCatalog.effective(settings.deviceProfileId, readings)
        val topo = DisplayTopology.resolve(
            readings = readings,
            profile = profile,
            interactiveDisplayMode = settings.interactiveDisplayMode,
            userPinnedPrimaryId = settings.userPinnedPrimaryId,
        )
        displayConfig = topo
        if (::deckState.isInitialized) {
            // Prefer pin/topology primary; only rewrite if invalid.
            if (settings.userPinnedPrimaryId != null &&
                settings.userPinnedPrimaryId in topo.allIds
            ) {
                deckState.setPrimaryDisplayId(settings.userPinnedPrimaryId!!)
            } else {
                deckState.ensurePrimaryIn(topo.allIds, topo.primaryDisplayId)
                if (deckState.primaryDisplayId != topo.primaryDisplayId &&
                    settings.userPinnedPrimaryId == null
                ) {
                    deckState.setPrimaryDisplayId(topo.primaryDisplayId)
                }
            }
        }
        return topo
    }

    /** Topology-aware swap + sticky pin so Auto refresh does not undo it. */
    fun swapInteractiveDisplay() {
        val topo = refreshDisplayConfig()
        if (topo.mode != SurfaceMode.DUAL) return
        val companion = topo.allIds.firstOrNull { it != deckState.primaryDisplayId }
            ?: return
        val current = ResolvedTopology(
            mode = SurfaceMode.DUAL,
            primaryDisplayId = deckState.primaryDisplayId,
            companionDisplayId = companion,
            launchDisplayId = companion,
            secondaryHomeDisplayId = topo.secondaryHomeDisplayId,
            allIds = topo.allIds,
            reason = topo.reason,
        )
        val swapped = DisplayTopology.swap(current)
        val pin = DisplayTopology.pinAfterSwap(swapped)
        deckState.setPrimaryDisplayId(pin)
        settings = settings.copy(userPinnedPrimaryId = pin)
        settingsStore.save(settings)
        displayConfig = swapped
    }

    private fun registerDisplayListener() {
        if (displayListenerRegistered) return
        displayListenerRegistered = true
        val dm = getSystemService(DisplayManager::class.java) ?: return
        dm.registerDisplayListener(object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) {
                Handler(Looper.getMainLooper()).post { refreshDisplayConfig() }
            }
            override fun onDisplayRemoved(displayId: Int) {
                Handler(Looper.getMainLooper()).post { refreshDisplayConfig() }
            }
            override fun onDisplayChanged(displayId: Int) {
                Handler(Looper.getMainLooper()).post { refreshDisplayConfig(debounce = true) }
            }
        }, Handler(Looper.getMainLooper()))
    }

    val romLibrary: RomLibrary by lazy {
        RomLibrary(File(filesDir, "rom_library.json"))
    }

    val platformPackStore: PlatformPackStore by lazy {
        PlatformPackStore(File(filesDir, "platform_pack.json"))
    }

    val artCache: com.visorcraft.ghostgalleon.art.ArtCache by lazy {
        com.visorcraft.ghostgalleon.art.ArtCache(File(filesDir, "art"))
    }

    // App-scoped owner of the SteamGridDB batch scrape: a multi-thousand-ROM
    // job must survive the settings screen that started it. The executor and
    // cooperative cancel semantics stay in SgdbScraper; only the lifecycle
    // moved here. If the process dies the job dies with it - a re-run
    // resumes where cached art left off.
    val scrapeJob: ScrapeJob by lazy {
        ScrapeJob { SgdbScraper(artCache, HttpSgdbTransport()) }
    }

    // In-memory snapshot of the persisted ROM index, read by every deck.
    // Loaded once off the UI thread at boot (a full card index is thousands
    // of entries - JSON parse must not block first render); rescans publish
    // fresh snapshots via publishRomEntries().
    @Volatile
    var romEntries: List<RomEntry> = emptyList()
        private set

    // Honest open session (pause while launcher focused / device asleep).
    // Exposed for Now Playing companion UI.
    @Volatile
    var openSession: OpenSession? = null
        private set

    // Optional RetroAchievements progress by ROM id (filled by network fetch).
    @Volatile
    private var raProgressByRomId: Map<String, com.visorcraft.ghostgalleon.library.RaProgress> =
        emptyMap()

    /** Cached RA progress for a ROM, or null when unknown / not fetched. */
    fun raProgressFor(romId: String): com.visorcraft.ghostgalleon.library.RaProgress? =
        raProgressByRomId[romId]

    fun putRaProgress(romId: String, progress: RaProgress) {
        raProgressByRomId = raProgressByRomId + (romId to progress)
        Handler(Looper.getMainLooper()).post { deckState.notifyChanged() }
    }

    /** Parse and store RA progress JSON for [romId]; empty/malformed clears. */
    fun setRaProgress(romId: String, json: String?) {
        val id = romId.trim()
        if (id.isEmpty()) return
        if (json.isNullOrBlank()) {
            raProgressByRomId = raProgressByRomId - id
        } else {
            val parsed = RetroAchievements.parseProgress(json)
            raProgressByRomId = if (parsed.isEmpty) raProgressByRomId - id
            else raProgressByRomId + (id to parsed)
        }
        Handler(Looper.getMainLooper()).post { deckState.notifyChanged() }
    }

    /** Load optional filesDir/ra_cache.json: `{ "romId": {…progress…}, … }`. */
    private fun loadRaCacheFile() {
        val file = File(filesDir, "ra_cache.json")
        if (!file.isFile) return
        runCatching {
            val root = JSONObject(file.readText())
            val next = raProgressByRomId.toMutableMap()
            val keys = root.keys()
            while (keys.hasNext()) {
                val romId = keys.next()
                val value = root.opt(romId) ?: continue
                val json = when (value) {
                    is JSONObject -> value.toString()
                    is String -> value
                    else -> continue
                }
                val progress = RetroAchievements.parseProgress(json)
                if (!progress.isEmpty) next[romId] = progress
            }
            raProgressByRomId = next
        }
    }

    private var sessionAwaitingReturn: Boolean = false
    private var liveDeckCount: Int = 0

    private fun reloadRomEntries() {
        ROM_IO.execute {
            val loaded = romLibrary.load()
            romEntries = loaded
            Handler(Looper.getMainLooper()).post { deckState.notifyChanged() }
        }
    }

    // Bumped when settings or the ROM index change so decks can skip a full
    // rebuild on resume when nothing actually changed (HOME / SECONDARY_HOME
    // redelivery used to flash-rebuild every swipe).
    var contentEpoch: Int = 0
        private set

    // Shared across Main + Companion: one swipe delivers intents to both.
    @Volatile
    var lastDrawerRequestUptimeMs: Long = 0L

    // First-run setup overlay is primary-hosted; block deck input globally
    // while it is showing (keys may land on the companion activity).
    @Volatile
    var setupBlockingInput: Boolean = false

    // All-apps drawer list reuse: avoid rebuilding thousands of PickerItems
    // on every swipe when contentEpoch + apps/hidden sets are unchanged.
    @Volatile
    private var drawerListKey: DrawerListKey? = null
    @Volatile
    private var drawerListItems: List<PickerItem>? = null

    /**
     * Cached empty-query drawer rows for [apps] + current [romEntries].
     * Rebuilds only when [DrawerListCache.key] changes.
     */
    fun drawerPickerItems(apps: List<AppEntry>): List<PickerItem> {
        val current = DrawerListCache.key(
            contentEpoch = contentEpoch,
            romCount = romEntries.size,
            hiddenPackages = settings.hiddenPackages,
            appPackageNames = apps.map { it.packageName },
        )
        val cachedKey = drawerListKey
        val cachedItems = drawerListItems
        if (DrawerListCache.matches(cachedKey, current) && cachedItems != null) {
            return cachedItems
        }
        val built = PickerItems.build(apps, romEntries, "")
        drawerListKey = current
        drawerListItems = built
        return built
    }

    fun invalidateDrawerListCache() {
        drawerListKey = null
        drawerListItems = null
    }

    // A fresh scan result: swap the snapshot and rebuild the decks so the
    // picker/carousel/grid see the new entries immediately.
    fun publishRomEntries(entries: List<RomEntry>) {
        romEntries = entries
        contentEpoch++
        invalidateDrawerListCache()
        deckState.notifyChanged()
    }

    /** Stamp last-launched and open a play session for [key]. */
    fun noteLaunch(key: String, nowMs: Long = System.currentTimeMillis()) {
        endOpenSession(nowMs)
        openSession = SessionTracker.onLaunch(key, nowMs)
        val stamped = SessionMath.recordLaunch(
            PlayStats(
                lastLaunchedMs = settings.lastLaunchedMs,
                totalPlaytimeMs = settings.playtimeMs,
            ),
            key,
            nowMs,
        )
        updateSettings(
            settings.copy(
                lastLaunchedMs = stamped.lastLaunchedMs,
                playtimeMs = stamped.totalPlaytimeMs,
            ),
            notify = false,
        )
        // Now Playing: companion should rebuild when session opens.
        Handler(Looper.getMainLooper()).post { deckState.notifyChanged() }
    }

    /** Accrue honest active playtime when returning to a deck activity. */
    fun noteReturnToLauncher(nowMs: Long = System.currentTimeMillis()) {
        endOpenSession(nowMs)
        Handler(Looper.getMainLooper()).post { deckState.notifyChanged() }
    }

    fun onSessionLauncherFocused(nowMs: Long = System.currentTimeMillis()) {
        val s = openSession ?: return
        openSession = SessionTracker.onLauncherFocused(s, nowMs)
    }

    fun onSessionLauncherUnfocused(nowMs: Long = System.currentTimeMillis()) {
        val s = openSession ?: return
        openSession = SessionTracker.onLauncherUnfocused(s, nowMs)
    }

    fun onSessionDeviceSleep(nowMs: Long = System.currentTimeMillis()) {
        val s = openSession ?: return
        openSession = SessionTracker.onDeviceSleep(s, nowMs)
    }

    fun onSessionDeviceWake(nowMs: Long = System.currentTimeMillis()) {
        val s = openSession ?: return
        openSession = SessionTracker.onDeviceWake(s, nowMs)
    }

    private fun endOpenSession(nowMs: Long) {
        val s = openSession ?: return
        openSession = null
        sessionAwaitingReturn = false
        val activeMs = SessionTracker.onReturn(s, nowMs)
        val stamped = SessionTracker.commitPlaytime(
            PlayStats(
                lastLaunchedMs = settings.lastLaunchedMs,
                totalPlaytimeMs = settings.playtimeMs,
            ),
            s.key,
            activeMs,
        )
        // Keep last-launched from noteLaunch; only merge playtime.
        updateSettings(
            settings.copy(
                lastLaunchedMs = settings.lastLaunchedMs,
                playtimeMs = stamped.totalPlaytimeMs,
            ),
            notify = false,
        )
    }

    // Live BaseDeckActivity instances, one per display task. The set is the
    // authority: an entry is removed in onActivityDestroyed before any
    // requestExitAll cascade runs, so reentrant finish() calls are no-ops.
    private val liveDeckActivities = mutableSetOf<BaseDeckActivity>()

    override fun onCreate() {
        super.onCreate()
        // Package-rename bridge: BlackPearl update with EXPORT_MIGRATE_ON_BOOT
        // dumps private data to external files; Ghost Galleon then imports
        // from migrate-import/ before the first settings load.
        if (BuildConfig.EXPORT_MIGRATE_ON_BOOT) {
            runCatching { DataMigrator.exportToExternal(this) }
        } else {
            runCatching { DataMigrator.tryImportFromExternal(this) }
        }
        settings = settingsStore.load()
        // Install any persisted platform pack before ROM scans / launches.
        runCatching { platformPackStore.loadIntoRegistry() }
        loadRaCacheFile()
        deckState = DeckState()
        deckState.setMode(settings.defaultMode)
        // Topology-driven primary (secondary prefer on Sugar Auto); not raw primaryDisplay.
        refreshDisplayConfig()
        registerDisplayListener()
        // Cold-start hero seed: prefer Continue key when known, else slot 0.
        // Do not auto-launch — selection only so the companion shows the game.
        seedColdStartSelection()
        reloadRomEntries()
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                if (activity is BaseDeckActivity) liveDeckActivities.add(activity)
            }

            override fun onActivityDestroyed(activity: Activity) {
                if (activity is BaseDeckActivity) liveDeckActivities.remove(activity)
            }

            override fun onActivityStarted(activity: Activity) {
                if (activity is BaseDeckActivity) {
                    liveDeckCount++
                    if (liveDeckCount == 1 && openSession != null) {
                        // Returning to launcher after all decks were stopped.
                        sessionAwaitingReturn = true
                    }
                }
            }
            override fun onActivityResumed(activity: Activity) {
                if (activity is BaseDeckActivity) {
                    onSessionLauncherFocused()
                    if (sessionAwaitingReturn && openSession != null) {
                        // Still in session but launcher is focused again:
                        // keep session open for Now Playing; only end when
                        // the user starts a new launch or we explicitly clear.
                        // Honest pause already applied via onSessionLauncherFocused.
                        sessionAwaitingReturn = false
                    }
                }
            }
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {
                if (activity is BaseDeckActivity) {
                    liveDeckCount = (liveDeckCount - 1).coerceAtLeast(0)
                    if (liveDeckCount == 0 && openSession != null) {
                        onSessionLauncherUnfocused()
                        sessionAwaitingReturn = true
                    }
                }
            }
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
        })
        // Honest sleep/wake: pair SCREEN_OFF with SCREEN_ON so pausedForSleep
        // cannot stick forever (TRIM_MEMORY_UI_HIDDEN is NOT screen-off).
        val screenFilter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        val screenReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_SCREEN_OFF -> onSessionDeviceSleep()
                    Intent.ACTION_SCREEN_ON -> onSessionDeviceWake()
                }
            }
        }
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(screenReceiver, screenFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(screenReceiver, screenFilter)
        }
    }

    /** End the open session and commit playtime (e.g. user dismissed Now Playing). */
    fun clearOpenSession(nowMs: Long = System.currentTimeMillis()) {
        endOpenSession(nowMs)
        deckState.notifyChanged()
    }

    /**
     * Prefer Continue (most recent launch still known) for the first hero
     * selection; fall back to grid slot 0. Never launches.
     */
    private fun seedColdStartSelection() {
        val available = buildList {
            addAll(settings.gridSlots.filterNotNull())
            addAll(settings.dockSlots.filterNotNull())
            addAll(settings.lastLaunchedMs.keys)
        }
        val cont = com.visorcraft.ghostgalleon.library.LibraryBrowse.continueKey(
            available, settings.lastLaunchedMs,
        )
        if (cont != null) {
            val idx = settings.gridSlots.indexOf(cont)
            if (idx >= 0) deckState.selectSlot(idx, cont)
            else deckState.select(cont)
            return
        }
        settings.gridSlots.getOrNull(0)?.let { deckState.selectSlot(0, it) }
    }

    /** The currently live CompanionActivity, if any. */
    fun liveCompanion(): CompanionActivity? = liveCompanions().firstOrNull()

    /** All live CompanionActivity instances, oldest first. The ROM's
     *  SECONDARY_HOME starts can spawn duplicates despite singleInstance. */
    fun liveCompanions(): List<CompanionActivity> =
        liveDeckActivities.filterIsInstance<CompanionActivity>()

    /** Finish every other live deck activity; called when one deck exits. */
    fun requestExitAll(except: BaseDeckActivity) {
        liveDeckActivities.filter { it !== except && !it.isFinishing }
            .forEach { it.finish() }
    }

    fun updateSettings(s: Settings, notify: Boolean = true) {
        val displayPolicyChanged =
            s.deviceProfileId != settings.deviceProfileId ||
                s.interactiveDisplayMode != settings.interactiveDisplayMode ||
                s.orientationMode != settings.orientationMode ||
                s.userPinnedPrimaryId != settings.userPinnedPrimaryId
        settings = s
        settingsStore.save(s)
        contentEpoch++
        invalidateDrawerListCache()
        if (displayPolicyChanged) refreshDisplayConfig()
        if (notify) {
            deckState.setMode(s.defaultMode)
            deckState.notifyChanged()
        }
    }

    /** Interactive (PRIMARY-role) deck activity, if any is live. */
    fun primaryDeckActivity(): BaseDeckActivity? =
        liveDeckActivities.firstOrNull { activity ->
            !activity.isFinishing &&
                DisplayRole.roleFor(
                    activity.display?.displayId ?: -1,
                    deckState,
                ) == DisplayRole.PRIMARY
        }

    /** All live deck activities (Main + Companion). */
    fun liveDeckActivities(): List<BaseDeckActivity> =
        liveDeckActivities.filter { !it.isFinishing }

    private companion object {
        val ROM_IO = java.util.concurrent.Executors.newSingleThreadExecutor()
    }
}
