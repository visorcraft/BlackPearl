package com.visorcraft.blackpearl.art

import com.visorcraft.blackpearl.rom.RomEntry

/**
 * Application-scoped owner of the SteamGridDB batch scrape. The job used to
 * be owned by SettingsActivity, so leaving the settings screen mid-run
 * cancelled a multi-thousand-ROM job. The lifecycle now lives in
 * BlackPearlApp: the settings row only binds a listener while resumed.
 * If the process dies the job dies with it — acceptable, a re-run resumes
 * where cached art left off.
 *
 * The runner seam ([ScrapeRunner]) keeps this class host-testable; the real
 * runner is an [SgdbScraper] over [HttpSgdbTransport]. Runner callbacks
 * already arrive on the main thread, so listener callbacks do too.
 */
class ScrapeJob(
    private val runnerFactory: () -> ScrapeRunner,
) {

    interface Listener {
        fun onProgress(done: Int, total: Int)
        fun onFinished(summary: SgdbScraper.Summary)
    }

    private var runner: ScrapeRunner? = null

    val isRunning: Boolean get() = runner?.isRunning == true

    /** Live progress of the current/last run; survives listener rebinds. */
    @Volatile
    var progressDone: Int = 0
        private set

    @Volatile
    var progressTotal: Int = 0
        private set

    /** Summary of the last finished run (any outcome), null before the first. */
    @Volatile
    var lastSummary: SgdbScraper.Summary? = null
        private set

    private val listeners = mutableListOf<Listener>()

    /** Register a listener; replays current progress when a job is running. */
    fun addListener(listener: Listener) {
        synchronized(listeners) { listeners += listener }
        if (isRunning) listener.onProgress(progressDone, progressTotal)
    }

    fun removeListener(listener: Listener) {
        synchronized(listeners) { listeners -= listener }
    }

    /** Start the batch job; false when one is already running. */
    fun start(apiKey: String, entries: List<RomEntry>): Boolean {
        if (isRunning) return false
        val job = runnerFactory()
        // Reset BEFORE start: a synchronous runner (host tests) reports
        // progress from inside start(), and the real one returns first.
        progressDone = 0
        progressTotal = 0
        val started = job.start(apiKey, entries, { done, total ->
            progressDone = done
            progressTotal = total
            listenerSnapshot().forEach { it.onProgress(done, total) }
        }) { summary ->
            lastSummary = summary
            listenerSnapshot().forEach { it.onFinished(summary) }
        }
        if (started) runner = job
        return started
    }

    /** Cooperative cancel; takes effect between requests/ROMs. */
    fun cancel() {
        runner?.cancel()
    }

    private fun listenerSnapshot(): List<Listener> =
        synchronized(listeners) { listeners.toList() }
}
