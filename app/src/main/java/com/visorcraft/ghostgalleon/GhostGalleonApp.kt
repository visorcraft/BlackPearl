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
import com.visorcraft.ghostgalleon.library.SessionMath
import com.visorcraft.ghostgalleon.library.SessionTracker
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
import java.io.File

class GhostGalleonApp : Application() {

    lateinit var deckState: DeckState
        private set

    lateinit var settings: Settings
        private set

    val settingsStore: SettingsStore by lazy {
        SettingsStore(File(filesDir, "settings.json"))
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
        deckState = DeckState()
        deckState.setMode(settings.defaultMode)
        deckState.setPrimaryDisplayId(settings.primaryDisplay)
        // Cold-start hero seed: select slot 0's content so the hero panel
        // shows it at boot instead of the "Ghost Galleon" fallback.
        settings.gridSlots.getOrNull(0)?.let { deckState.selectSlot(0, it) }
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
        settings = s
        settingsStore.save(s)
        contentEpoch++
        invalidateDrawerListCache()
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
