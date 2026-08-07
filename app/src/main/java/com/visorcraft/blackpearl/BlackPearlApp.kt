package com.visorcraft.blackpearl

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.visorcraft.blackpearl.art.HttpSgdbTransport
import com.visorcraft.blackpearl.art.ScrapeJob
import com.visorcraft.blackpearl.art.SgdbScraper
import com.visorcraft.blackpearl.library.OpenSession
import com.visorcraft.blackpearl.library.PlayStats
import com.visorcraft.blackpearl.library.SessionMath
import com.visorcraft.blackpearl.library.SessionTracker
import com.visorcraft.blackpearl.rom.RomEntry
import com.visorcraft.blackpearl.rom.RomLibrary
import com.visorcraft.blackpearl.settings.Settings
import com.visorcraft.blackpearl.settings.SettingsStore
import com.visorcraft.blackpearl.state.DeckState
import com.visorcraft.blackpearl.ui.BaseDeckActivity
import com.visorcraft.blackpearl.ui.CompanionActivity
import java.io.File

class BlackPearlApp : Application() {

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

    val artCache: com.visorcraft.blackpearl.art.ArtCache by lazy {
        com.visorcraft.blackpearl.art.ArtCache(File(filesDir, "art"))
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

    // A fresh scan result: swap the snapshot and rebuild the decks so the
    // picker/carousel/grid see the new entries immediately.
    fun publishRomEntries(entries: List<RomEntry>) {
        romEntries = entries
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
        settings = settingsStore.load()
        deckState = DeckState()
        deckState.setMode(settings.defaultMode)
        deckState.setPrimaryDisplayId(settings.primaryDisplay)
        // Cold-start hero seed: select slot 0's content so the hero panel
        // shows it at boot instead of the "BlackPearl" fallback.
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
        // Pause accrual when the screen turns off.
        registerComponentCallbacks(object : android.content.ComponentCallbacks2 {
            override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {}
            override fun onLowMemory() {}
            override fun onTrimMemory(level: Int) {
                if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
                    onSessionDeviceSleep()
                }
            }
        })
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
        if (notify) {
            deckState.setMode(s.defaultMode)
            deckState.notifyChanged()
        }
    }

    private companion object {
        val ROM_IO = java.util.concurrent.Executors.newSingleThreadExecutor()
    }
}
