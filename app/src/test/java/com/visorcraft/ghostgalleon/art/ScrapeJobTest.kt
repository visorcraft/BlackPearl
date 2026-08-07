package com.visorcraft.ghostgalleon.art

import com.visorcraft.ghostgalleon.rom.RomEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Lifecycle of the app-scoped scrape job: listeners bind/unbind while the
 * job runs, progress survives rebinds, and leaving the settings screen
 * (removing the listener) never cancels the job.
 */
class ScrapeJobTest {

    private fun rom(id: String) = RomEntry(
        id = id,
        name = id,
        platformId = "snes",
        uri = "content://$id",
        path = null,
        artUri = null,
    )

    /** Synchronous fake runner: start() drives canned progress/finish. */
    private class FakeRunner(
        private val progress: List<Pair<Int, Int>> = listOf(1 to 2, 2 to 2),
        private val summary: SgdbScraper.Summary =
            SgdbScraper.Summary(downloaded = 2, skipped = 0, failed = 0, cancelled = false),
    ) : ScrapeRunner {
        var running = false
        var cancelCalls = 0
        var startCalls = 0

        override val isRunning: Boolean get() = running

        override fun cancel() {
            cancelCalls++
            running = false
        }

        override fun start(
            apiKey: String,
            entries: List<RomEntry>,
            onProgress: (Int, Int) -> Unit,
            onDone: (SgdbScraper.Summary) -> Unit,
        ): Boolean {
            if (running) return false
            running = true
            startCalls++
            progress.forEach { (done, total) -> onProgress(done, total) }
            running = false
            onDone(summary)
            return true
        }
    }

    /** Runner whose start() stays running until told to finish — lets a
     *  test remove the listener mid-run. */
    private class ManualRunner : ScrapeRunner {
        var running = false
        var cancelCalls = 0
        private lateinit var progress: (Int, Int) -> Unit
        private lateinit var done: (SgdbScraper.Summary) -> Unit

        override val isRunning: Boolean get() = running

        override fun cancel() {
            cancelCalls++
            running = false
        }

        override fun start(
            apiKey: String,
            entries: List<RomEntry>,
            onProgress: (Int, Int) -> Unit,
            onDone: (SgdbScraper.Summary) -> Unit,
        ): Boolean {
            if (running) return false
            running = true
            progress = onProgress
            done = onDone
            return true
        }

        fun emit(done: Int, total: Int) = progress(done, total)

        fun finish(summary: SgdbScraper.Summary) {
            running = false
            done(summary)
        }
    }

    private class RecordingListener : ScrapeJob.Listener {
        val progress = mutableListOf<Pair<Int, Int>>()
        val summaries = mutableListOf<SgdbScraper.Summary>()

        override fun onProgress(done: Int, total: Int) {
            progress += done to total
        }

        override fun onFinished(summary: SgdbScraper.Summary) {
            summaries += summary
        }
    }

    @Test
    fun `progress and finish are relayed and state is retained`() {
        val runner = FakeRunner()
        val job = ScrapeJob { runner }
        val listener = RecordingListener()
        job.addListener(listener)
        assertTrue(job.start("KEY", listOf(rom("a"), rom("b"))))
        assertEquals(listOf(1 to 2, 2 to 2), listener.progress)
        assertEquals(1, listener.summaries.size)
        // Job state outlives the listener: progress + last summary stick.
        assertEquals(2, job.progressDone)
        assertEquals(2, job.progressTotal)
        assertEquals(2, job.lastSummary?.downloaded)
        assertFalse(job.isRunning)
    }

    @Test
    fun `second start is refused while a job is running`() {
        val runner = ManualRunner()
        val job = ScrapeJob { runner }
        assertTrue(job.start("KEY", listOf(rom("a"))))
        assertTrue(job.isRunning)
        assertFalse(job.start("KEY", listOf(rom("a"))))
        assertTrue(job.isRunning)
    }

    @Test
    fun `removing the listener mid-run keeps the job alive`() {
        val runner = ManualRunner()
        val job = ScrapeJob { runner }
        val listener = RecordingListener()
        job.addListener(listener)
        job.start("KEY", listOf(rom("a"), rom("b")))
        runner.emit(310, 2412)
        // The settings screen leaves: listener unregisters, job continues.
        job.removeListener(listener)
        runner.emit(311, 2412)
        val summary = SgdbScraper.Summary(311, 0, 0, cancelled = false)
        runner.finish(summary)
        assertEquals(listOf(310 to 2412), listener.progress)
        assertTrue(listener.summaries.isEmpty())
        // Job state still advanced after the listener left.
        assertEquals(311, job.progressDone)
        assertEquals(2412, job.progressTotal)
        assertEquals(summary, job.lastSummary)
        assertEquals(0, runner.cancelCalls)
        assertFalse(job.isRunning)
    }

    @Test
    fun `listener registered mid-run gets current progress immediately`() {
        val runner = ManualRunner()
        val job = ScrapeJob { runner }
        job.start("KEY", listOf(rom("a"), rom("b")))
        runner.emit(310, 2412)
        val late = RecordingListener()
        job.addListener(late)
        assertEquals(listOf(310 to 2412), late.progress)
    }

    @Test
    fun `cancel delegates to the running runner`() {
        val runner = ManualRunner()
        val job = ScrapeJob { runner }
        job.cancel() // no job: no-op
        assertEquals(0, runner.cancelCalls)
        job.start("KEY", listOf(rom("a")))
        job.cancel()
        assertEquals(1, runner.cancelCalls)
        assertFalse(job.isRunning)
    }

    @Test
    fun `last summary is null before the first run`() {
        assertNull(ScrapeJob { FakeRunner() }.lastSummary)
    }
}
