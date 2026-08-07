package com.visorcraft.ghostgalleon.art

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.visorcraft.ghostgalleon.rom.RomEntry
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * SteamGridDB gap-filler (Stage 3 Task 3). For every library entry with no
 * local artUri and no cached art: search SGDB by the cleaned ROM name,
 * take the first result, download its first grid and hero image into
 * ArtCache (grid = tile art, hero keyed separately for the hero panel).
 * Entries whose grid slot is already cached but whose HERO slot is empty
 * are still scraped — a hero-only backfill that skips the grid download.
 *
 * Downloaded CDN bytes are downscaled and re-encoded as PNG at the cache
 * target ([ArtCache.downscaledPngBytes]: grid ~512px max dimension, hero
 * ~1600px wide) BEFORE the disk write, so the disk cache and the memory
 * LRU only ever hold small images. Undecodable bytes count as a failure
 * and are never cached.
 *
 * The job runs on its OWN single-thread executor (never RomLibrary's
 * SCAN_EXECUTOR), is cancelable between ROMs and between requests, and is
 * polite: ~200ms between HTTP requests. Every failure is logged and
 * counted; the job never crashes. Pure URL/JSON/accounting logic is
 * host-tested through the [SgdbTransport] and `shrink` seams.
 */
object Sgdb {
    const val BASE = "https://www.steamgriddb.com/api/v2"

    private val BRACKET = Regex("""\[[^\]]*\]""")
    private val PAREN = Regex("""\([^)]*\)""")

    /**
     * Search-name normalization: strip region/tag parentheticals and
     * square-bracket tags ("(USA)", version/title-id tags), collapse
     * whitespace. "Super Mario World (USA) (!)" → "Super Mario World".
     */
    fun normalizeName(stem: String): String {
        var s = BRACKET.replace(stem, " ")
        s = PAREN.replace(s, " ")
        return s.trim().split(Regex("""\s+""")).joinToString(" ")
    }

    fun searchUrl(name: String): String =
        "$BASE/search/autocomplete/" +
            URLEncoder.encode(name, "UTF-8").replace("+", "%20")

    fun gridsUrl(gameId: Long): String = "$BASE/grids/game/$gameId"

    fun heroesUrl(gameId: Long): String = "$BASE/heroes/game/$gameId"

    /** First game id of a search/autocomplete response, else null. */
    fun parseSearchFirstId(json: String): Long? = runCatching {
        val data = JSONObject(json).optJSONArray("data")
        if (data == null || data.length() == 0) null else data.getJSONObject(0).getLong("id")
    }.getOrNull()

    /** First image URL of a grids/heroes response, else null. */
    fun parseFirstImageUrl(json: String): String? = runCatching {
        val data = JSONObject(json).optJSONArray("data")
        if (data == null || data.length() == 0) null else data.getJSONObject(0).getString("url")
    }.getOrNull()
}

/** HTTP seam so host tests can drive the scraper with canned responses. */
interface SgdbTransport {
    /** Authenticated API GET; body on HTTP 200, null on any failure. */
    fun get(url: String, apiKey: String): String?

    /** Plain GET for image bytes (SGDB CDN needs no auth). */
    fun download(url: String): ByteArray?
}

/**
 * Run/cancel surface of one batch scrape, so the app-scoped [ScrapeJob]
 * can be host-tested with a fake runner. Implemented by [SgdbScraper].
 */
interface ScrapeRunner {
    val isRunning: Boolean

    /** Cooperative cancel: takes effect between requests/ROMs. */
    fun cancel()

    /**
     * Start the batch job; false when a job is already running. Progress
     * (done, total) and the final [SgdbScraper.Summary] are delivered on
     * the main thread.
     */
    fun start(
        apiKey: String,
        entries: List<RomEntry>,
        onProgress: (done: Int, total: Int) -> Unit,
        onDone: (SgdbScraper.Summary) -> Unit,
    ): Boolean
}

/** HttpURLConnection transport; logs and swallows every failure. */
class HttpSgdbTransport : SgdbTransport {

    override fun get(url: String, apiKey: String): String? = runCatching {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.setRequestProperty("Authorization", "Bearer $apiKey")
        conn.connectTimeout = CONNECT_TIMEOUT_MS
        conn.readTimeout = READ_TIMEOUT_MS
        val code = conn.responseCode
        if (code != HttpURLConnection.HTTP_OK) {
            Log.w(TAG, "GET $url -> HTTP $code")
            return null
        }
        conn.inputStream.bufferedReader().use { it.readText() }
    }.onFailure { Log.w(TAG, "GET $url failed", it) }.getOrNull()

    override fun download(url: String): ByteArray? = runCatching {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = CONNECT_TIMEOUT_MS
        conn.readTimeout = READ_TIMEOUT_MS
        val code = conn.responseCode
        if (code != HttpURLConnection.HTTP_OK) {
            Log.w(TAG, "GET $url -> HTTP $code")
            return null
        }
        conn.inputStream.use { it.readBytes() }
    }.onFailure { Log.w(TAG, "GET $url failed", it) }.getOrNull()

    private companion object {
        const val TAG = "SgdbScraper"
        const val CONNECT_TIMEOUT_MS = 10_000
        const val READ_TIMEOUT_MS = 15_000
    }
}

class SgdbScraper(
    private val cache: ArtCache,
    private val transport: SgdbTransport,
    private val delayMs: Long = DELAY_MS,
    // Downscale+re-encode seam: BitmapFactory is Android-only, so host
    // tests inject an identity/failing fake. Default downscales to the
    // ArtCache target for the kind before the bytes reach disk.
    private val shrink: (bytes: ByteArray, kind: ArtCache.ArtKind) -> ByteArray? =
        { bytes, kind -> ArtCache.downscaledPngBytes(bytes, kind) },
) : ScrapeRunner {

    data class Summary(
        val downloaded: Int,
        val skipped: Int,
        val failed: Int,
        val cancelled: Boolean,
    )

    @Volatile
    private var cancelled = false
    private val running = AtomicBoolean(false)

    override val isRunning: Boolean get() = running.get()

    /** Cooperative cancel: takes effect between requests/ROMs. */
    override fun cancel() {
        cancelled = true
    }

    /**
     * Start the batch job; false when a job is already running. Progress
     * (done, total) and the final [Summary] are delivered on the main
     * thread.
     */
    override fun start(
        apiKey: String,
        entries: List<RomEntry>,
        onProgress: (done: Int, total: Int) -> Unit,
        onDone: (Summary) -> Unit,
    ): Boolean {
        if (!running.compareAndSet(false, true)) return false
        cancelled = false
        val handler = Handler(Looper.getMainLooper())
        EXECUTOR.execute {
            val summary = runBlocking(apiKey, entries, { Thread.sleep(it) }) { done, total ->
                handler.post { onProgress(done, total) }
            }
            running.set(false)
            handler.post { onDone(summary) }
        }
        return true
    }

    /**
     * The whole job, synchronously, over injected seams — host-tested.
     * Entries that already have art (local artUri, or BOTH cache slots
     * filled) are skipped without any network traffic; an entry whose grid
     * slot is cached but whose HERO slot is empty stays in the job as a
     * hero-only backfill.
     */
    internal fun runBlocking(
        apiKey: String,
        entries: List<RomEntry>,
        sleep: (Long) -> Unit,
        onProgress: (done: Int, total: Int) -> Unit,
    ): Summary {
        val missing = entries.filter {
            it.artUri == null &&
                (!cache.diskHas(it.id) || !cache.diskHas(it.id, ArtCache.ArtKind.HERO))
        }
        val skipped = entries.size - missing.size
        var downloaded = 0
        var failed = 0
        missing.forEachIndexed { index, rom ->
            if (cancelled) {
                return Summary(downloaded, skipped, failed, cancelled = true)
            }
            if (scrapeOne(apiKey, rom, sleep)) downloaded++ else failed++
            onProgress(index + 1, missing.size)
        }
        return Summary(downloaded, skipped, failed, cancelled = false)
    }

    /** One ROM: search → first grid (unless already cached) + first hero
     *  (unless already cached). True when the grid slot ends up filled or
     *  a hero landed. Never throws. */
    private fun scrapeOne(apiKey: String, rom: RomEntry, sleep: (Long) -> Unit): Boolean {
        return runCatching {
            val query = Sgdb.normalizeName(rom.name)
            if (query.isEmpty()) return false
            val searchJson = request(sleep) { transport.get(Sgdb.searchUrl(query), apiKey) }
                ?: return false
            if (cancelled) return false
            val gameId = Sgdb.parseSearchFirstId(searchJson) ?: return false

            // Grid slot already cached → this run is a hero backfill: count
            // as success without re-downloading or overwriting the tile art.
            var stored = cache.diskHas(rom.id)
            if (!stored) {
                val gridUrl = request(sleep) { transport.get(Sgdb.gridsUrl(gameId), apiKey) }
                    ?.let(Sgdb::parseFirstImageUrl)
                if (cancelled) return false
                if (gridUrl != null) {
                    request(sleep) { transport.download(gridUrl) }?.let { raw ->
                        shrink(raw, ArtCache.ArtKind.GRID)?.let { png ->
                            cache.writeDiskBytes(rom.id, png)
                            stored = true
                        }
                    }
                }
            }
            if (cancelled) return false

            if (!cache.diskHas(rom.id, ArtCache.ArtKind.HERO)) {
                val heroUrl = request(sleep) { transport.get(Sgdb.heroesUrl(gameId), apiKey) }
                    ?.let(Sgdb::parseFirstImageUrl)
                if (cancelled) return false
                if (heroUrl != null) {
                    request(sleep) { transport.download(heroUrl) }?.let { raw ->
                        shrink(raw, ArtCache.ArtKind.HERO)?.let { png ->
                            cache.writeDiskBytes(rom.id, png, ArtCache.ArtKind.HERO)
                            stored = true
                        }
                    }
                }
            }
            stored
        }.getOrDefault(false)
    }

    /** One HTTP call followed by the polite delay. */
    private inline fun <T> request(sleep: (Long) -> Unit, call: () -> T): T {
        val result = call()
        sleep(delayMs)
        return result
    }

    companion object {
        const val DELAY_MS = 200L

        // The scraper's own single thread — never RomLibrary's
        // SCAN_EXECUTOR, so artwork never waits on a card scan.
        private val EXECUTOR = Executors.newSingleThreadExecutor()
    }
}
