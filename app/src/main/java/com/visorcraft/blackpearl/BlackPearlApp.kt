package com.visorcraft.blackpearl

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.visorcraft.blackpearl.art.HttpSgdbTransport
import com.visorcraft.blackpearl.art.ScrapeJob
import com.visorcraft.blackpearl.art.SgdbScraper
import com.visorcraft.blackpearl.library.SessionMath
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

    // Open play session: key + wall-clock start. Accrues when a deck
    // activity resumes after having been stopped (left for a game), or
    // when a new launch supersedes the previous session.
    private var openSessionKey: String? = null
    private var openSessionStartMs: Long = 0L
    private var sessionAwaitingReturn: Boolean = false

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
        openSessionKey = key
        openSessionStartMs = nowMs
        val stamped = SessionMath.recordLaunch(
            com.visorcraft.blackpearl.library.PlayStats(
                lastLaunchedMs = settings.lastLaunchedMs,
                totalPlaytimeMs = settings.playtimeMs,
            ),
            key,
            nowMs,
        )
        // Persist without full notify storm: updateSettings still rebuilds,
        // which is fine after a launch (deck is leaving focus).
        updateSettings(
            settings.copy(
                lastLaunchedMs = stamped.lastLaunchedMs,
                playtimeMs = stamped.totalPlaytimeMs,
            ),
            notify = false,
        )
    }

    /** Accrue playtime when returning to a deck activity. */
    fun noteReturnToLauncher(nowMs: Long = System.currentTimeMillis()) {
        endOpenSession(nowMs)
    }

    private fun endOpenSession(nowMs: Long) {
        val key = openSessionKey ?: return
        val start = openSessionStartMs
        openSessionKey = null
        openSessionStartMs = 0L
        val stamped = SessionMath.recordReturn(
            com.visorcraft.blackpearl.library.PlayStats(
                lastLaunchedMs = settings.lastLaunchedMs,
                totalPlaytimeMs = settings.playtimeMs,
            ),
            key,
            start,
            nowMs,
        )
        updateSettings(
            settings.copy(
                lastLaunchedMs = stamped.lastLaunchedMs,
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

            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityResumed(activity: Activity) {
                if (activity is BaseDeckActivity && sessionAwaitingReturn) {
                    noteReturnToLauncher()
                    sessionAwaitingReturn = false
                }
            }
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {
                if (activity is BaseDeckActivity && openSessionKey != null) {
                    sessionAwaitingReturn = true
                }
            }
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
        })
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
