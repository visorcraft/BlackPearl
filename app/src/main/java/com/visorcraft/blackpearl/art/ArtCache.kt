package com.visorcraft.blackpearl.art

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import com.visorcraft.blackpearl.rom.RomEntry
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.Executors

/**
 * ROM artwork cache: a disk cache under `filesDir/art/` keyed by RomEntry
 * id (SHA-256 hashed filename, since ids contain ':'/'/'), backed by an
 * in-memory LRU. Disk usage is capped at [DISK_CACHE_CAP_BYTES]: writes
 * evict least-recently-used files (lastModified, bumped on read hits).
 * All I/O and bitmap decode/encode runs on a single background executor;
 * results are delivered on the main thread.
 *
 * The disk path is pure JVM (File + bytes) so host tests can drive it with
 * a temp dir; android.util.LruCache is an Android stub in host tests, so
 * the memory cache is created lazily and only touched on the async path.
 */
class ArtCache(private val dir: File) {

    /** Cache slot: GRID = tile art (also what [load] serves), HERO = wide
     *  hero-panel art keyed separately (scraped alongside grids). */
    enum class ArtKind { GRID, HERO }

    private val memory by lazy {
        object : LruCache<String, Bitmap>(MEMORY_BYTES) {
            override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
        }
    }

    private fun fileFor(romId: String, kind: ArtKind = ArtKind.GRID): File {
        val suffix = if (kind == ArtKind.HERO) ".hero.png" else ".png"
        return File(dir, keyFor(romId) + suffix)
    }

    /** Sidecar recording which source URI produced the GRID disk bytes.
     *  When [load] supplies a different effective URI (user override vs
     *  scanner art), a stamp mismatch forces a re-decode so stale cache
     *  never wins over [ArtOverride]. */
    private fun sourceStampFile(romId: String, kind: ArtKind = ArtKind.GRID): File {
        val suffix = if (kind == ArtKind.HERO) ".hero.src" else ".src"
        return File(dir, keyFor(romId) + suffix)
    }

    /** True when a downscaled copy is already on disk. */
    fun diskHas(romId: String, kind: ArtKind = ArtKind.GRID): Boolean =
        fileFor(romId, kind).isFile

    /** Raw cached bytes for [romId], or null on a miss. A hit bumps the
     *  file's lastModified so disk eviction stays least-recently-USED. */
    fun readDiskBytes(romId: String, kind: ArtKind = ArtKind.GRID): ByteArray? =
        fileFor(romId, kind).takeIf { it.isFile }?.let { file ->
            file.setLastModified(System.currentTimeMillis())
            file.readBytes()
        }

    /**
     * Drop memory + disk GRID/HERO slots for [romId] (and their source
     * stamps). Call after SET_ART / clear override so the next load
     * re-decodes the new effective URI instead of serving stale bytes.
     */
    fun invalidate(romId: String) {
        runCatching {
            memory.remove(romId)
            memory.remove(romId + ".hero")
        }
        fileFor(romId, ArtKind.GRID).delete()
        fileFor(romId, ArtKind.HERO).delete()
        sourceStampFile(romId, ArtKind.GRID).delete()
        sourceStampFile(romId, ArtKind.HERO).delete()
    }

    /** Atomic write (tmp + rename), like RomLibrary/SettingsStore. Triggers
     *  disk-cap eviction afterwards so scrapes cannot grow the cache
     *  without bound. Clears any prior source stamp so a later URI-backed
     *  load cannot treat scrape bytes as override art. */
    fun writeDiskBytes(romId: String, bytes: ByteArray, kind: ArtKind = ArtKind.GRID) {
        dir.mkdirs()
        val tmp = File(dir, keyFor(romId) + ".tmp")
        tmp.writeBytes(bytes)
        val target = fileFor(romId, kind)
        if (!tmp.renameTo(target)) {
            tmp.copyTo(target, overwrite = true)
            tmp.delete()
        }
        sourceStampFile(romId, kind).delete()
        // Scrape / non-URI writers own the bytes without a URI stamp.
        runCatching { memory.remove(romId) }
        if (kind == ArtKind.HERO) runCatching { memory.remove(romId + ".hero") }
        evictIfOverCap()
    }

    // Deletes the least-recently-used cache files (lastModified order) until
    // the directory is back under DISK_CACHE_CAP_BYTES. The just-written
    // file has the newest timestamp, so a single oversized write survives.
    private fun evictIfOverCap(capBytes: Long = DISK_CACHE_CAP_BYTES) {
        val files = dir.listFiles()?.toList() ?: return
        evictionCandidates(files, capBytes).forEach { it.delete() }
    }

    /**
     * Load art for [rom]: memory cache → disk cache → (GRID only) the
     * entry's local `artUri` (decoded downscaled to roughly [maxDimension]
     * px and written into the disk cache). HERO art has no local source, so
     * a HERO disk miss resolves to null. [onResult] always runs on the main
     * thread, synchronously on a memory hit, and receives null when no art
     * exists or the source cannot be read. Stale-result guarding is the
     * caller's job (ArtTile re-checks the overlay's tag before applying) —
     * and [isStillValid] re-runs on the decode thread BEFORE any I/O or
     * decode, so work queued behind a long carousel fling is dropped cheap
     * instead of decoding hundreds of stale cards first.
     */
    fun load(
        context: Context,
        rom: RomEntry,
        maxDimension: Int,
        kind: ArtKind = ArtKind.GRID,
        isStillValid: () -> Boolean = { true },
        // Optional artOverrides[rom.id] URI wins over rom.artUri for GRID.
        artOverrides: Map<String, String> = emptyMap(),
        onResult: (Bitmap?) -> Unit,
    ) {
        // HERO art has no local source, so a HERO disk miss resolves to null.
        // GRID: user override (Settings.artOverrides) then scanner artUri.
        val localUri = if (kind == ArtKind.GRID) {
            ArtOverride.effectiveArtUri(rom, artOverrides)
        } else {
            null
        }
        // Memory key includes the effective source so a SET_ART override
        // cannot hit a prior mem entry keyed only by rom.id.
        val memKey = memKeyFor(rom.id, kind, localUri)
        enqueue(
            context, memKey, rom.id, kind,
            uriString = localUri,
            maxDimension, isStillValid, onResult,
        )
    }

    /**
     * Same memory → disk → decode-downscaled pipeline as [load], but keyed by
     * an arbitrary string with an explicit source URI — used for per-app
     * custom icons ("app:<package>" keys), which are not ROM entries. The
     * decode reuses [decodeDownscaled] (bounds-then-sample via
     * [sampleSizeFor]) and the PNG disk cache.
     */
    fun loadUri(
        context: Context,
        key: String,
        uriString: String,
        maxDimension: Int,
        isStillValid: () -> Boolean = { true },
        onResult: (Bitmap?) -> Unit,
    ) = enqueue(context, key, key, ArtKind.GRID, uriString, maxDimension, isStillValid, onResult)

    private fun enqueue(
        context: Context,
        memKey: String,
        diskKey: String,
        kind: ArtKind,
        uriString: String?,
        maxDimension: Int,
        isStillValid: () -> Boolean,
        onResult: (Bitmap?) -> Unit,
    ) {
        memory.get(memKey)?.let { onResult(it); return }
        val appContext = context.applicationContext
        DECODE_EXECUTOR.execute {
            // The queue is single-thread FIFO: by the time this runs the
            // view may have been rebound many flings ago. Bail before the
            // expensive part; the post-decode tag guard stays as the final
            // correctness check.
            if (!isStillValid()) return@execute
            val bitmap = decodeCached(diskKey, kind, expectedSourceUri = uriString)
                ?: uriString?.let { uri ->
                    decodeDownscaled(appContext, uri, maxDimension)?.also { bmp ->
                        runCatching {
                            val out = ByteArrayOutputStream()
                            bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
                            // Write bytes without clearing via public writeDiskBytes
                            // stamp rules: stamp the URI after so override/source
                            // is bound to these bytes.
                            writeDiskBytesFromUri(diskKey, out.toByteArray(), kind, uri)
                        }
                    }
                }
            if (bitmap != null) memory.put(memKey, bitmap)
            Handler(Looper.getMainLooper()).post { onResult(bitmap) }
        }
    }

    /** Disk hit only when bytes exist AND (no expected URI, or stamp matches). */
    private fun decodeCached(
        romId: String,
        kind: ArtKind = ArtKind.GRID,
        expectedSourceUri: String? = null,
    ): Bitmap? {
        val bytes = readDiskBytes(romId, kind) ?: return null
        val stampText = sourceStampFile(romId, kind).takeIf { it.isFile }?.readText()
        if (!sourceStampMatches(stampText, expectedSourceUri)) return null
        return runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }
            .getOrNull()
    }

    /** Persist URI-sourced art and stamp the source so a later different
     *  override/scraper cannot silently reuse these bytes. */
    private fun writeDiskBytesFromUri(
        romId: String,
        bytes: ByteArray,
        kind: ArtKind,
        sourceUri: String,
    ) {
        dir.mkdirs()
        val tmp = File(dir, keyFor(romId) + ".tmp")
        tmp.writeBytes(bytes)
        val target = fileFor(romId, kind)
        if (!tmp.renameTo(target)) {
            tmp.copyTo(target, overwrite = true)
            tmp.delete()
        }
        sourceStampFile(romId, kind).writeText(sourceUri)
        runCatching { memory.remove(romId) }
        if (kind == ArtKind.HERO) runCatching { memory.remove(romId + ".hero") }
        // Also drop any source-suffixed mem keys for this rom (best-effort).
        runCatching { memory.remove(memKeyFor(romId, kind, sourceUri)) }
        evictIfOverCap()
    }

    companion object {
        private const val MEMORY_BYTES = 16 * 1024 * 1024

        /** Disk cache cap; writes evict least-recently-used files
         *  (lastModified) once the directory exceeds this. */
        const val DISK_CACHE_CAP_BYTES = 256L * 1024 * 1024

        /**
         * Memory key for a load: plain rom id (or `.hero`) when there is no
         * local source URI; otherwise rom id + a short hash of the URI so
         * SET_ART cannot hit a prior entry for a different source.
         */
        internal fun memKeyFor(romId: String, kind: ArtKind, sourceUri: String?): String {
            val base = if (kind == ArtKind.HERO) "$romId.hero" else romId
            if (sourceUri.isNullOrBlank()) return base
            return "$base|src:${keyFor(sourceUri).take(16)}"
        }

        /**
         * Whether a disk entry with [stampText] may satisfy a load that wants
         * [expectedSourceUri]. Null expected = any stamp ok (scrape / no-URI
         * path). Non-null expected requires an exact stamp match so a prior
         * cache from a different override/scanner URI cannot win.
         */
        internal fun sourceStampMatches(stampText: String?, expectedSourceUri: String?): Boolean {
            if (expectedSourceUri == null) return true
            return stampText != null && stampText == expectedSourceUri
        }

        /**
         * Pure eviction selection: the oldest-lastModified [files] to delete
         * so the remaining total is at most [capBytes]. Deletion is the
         * caller's job, so host tests can drive this on a temp dir.
         */
        internal fun evictionCandidates(files: List<File>, capBytes: Long): List<File> {
            val sorted = files.filter { it.isFile }.sortedBy { it.lastModified() }
            var total = sorted.sumOf { it.length() }
            val evict = mutableListOf<File>()
            for (file in sorted) {
                if (total <= capBytes) break
                evict += file
                total -= file.length()
            }
            return evict
        }

        /** Scrape cache targets: grid tiles ~512px max dimension, heroes
         *  ~1600px wide. Scraped art is downscaled to these before it ever
         *  touches the disk cache, so [decodeCached] never sees multi-MB
         *  CDN originals. */
        const val GRID_SCRAPE_TARGET_PX = 512
        const val HERO_SCRAPE_TARGET_WIDTH_PX = 1600

        // Serializes decodes. Queued tasks re-check staleness BEFORE
        // decoding (the caller's isStillValid seam), so work obsoleted by a
        // fling costs one lambda call instead of a full decode; the
        // post-decode tag guard still drops anything that goes stale
        // mid-decode. Separate from RomLibrary's SCAN_EXECUTOR so art never
        // waits on a card scan.
        private val DECODE_EXECUTOR = Executors.newSingleThreadExecutor()

        /**
         * Stable cache key for a RomEntry id: SHA-256 hex (ids contain
         * ':'/'/', unusable in a filename). Pure — pinned by host tests.
         */
        fun keyFor(romId: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(romId.toByteArray(Charsets.UTF_8))
            return digest.joinToString("") { "%02x".format(it) }
        }

        /**
         * inSampleSize for a bounds-then-sample decode: the largest power of
         * two that keeps BOTH dimensions at or above [maxDimension] after
         * sampling (never upscales, never drops below target). Pure — pinned
         * by host tests.
         */
        internal fun sampleSizeFor(width: Int, height: Int, maxDimension: Int): Int {
            var sample = 1
            while (width / (sample * 2) >= maxDimension &&
                height / (sample * 2) >= maxDimension
            ) {
                sample *= 2
            }
            return sample
        }

        /**
         * Bounds-then-sample downscale of encoded image [bytes], re-encoded
         * as PNG at the cache target for [kind] (grid: ~[GRID_SCRAPE_TARGET_PX]
         * px max dimension; hero: ~[HERO_SCRAPE_TARGET_WIDTH_PX] px wide —
         * heroes are wide and short, so the sample tracks width alone).
         * Null when [bytes] is not a decodable image. Android-only
         * (BitmapFactory); host tests inject a fake through the caller's
         * seam ([SgdbScraper]'s `shrink`).
         */
        internal fun downscaledPngBytes(bytes: ByteArray, kind: ArtKind): ByteArray? =
            runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
                val sample = if (kind == ArtKind.HERO) {
                    // Width-only sampling: pass the width as both dimensions.
                    sampleSizeFor(
                        bounds.outWidth,
                        bounds.outWidth,
                        HERO_SCRAPE_TARGET_WIDTH_PX,
                    )
                } else {
                    sampleSizeFor(bounds.outWidth, bounds.outHeight, GRID_SCRAPE_TARGET_PX)
                }
                val opts = BitmapFactory.Options().apply { inSampleSize = sample }
                val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
                    ?: return null
                val out = ByteArrayOutputStream()
                bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
                out.toByteArray()
            }.getOrNull()

        /**
         * Bounds-then-sample decode down to roughly [maxDimension] px; null
         * on any failure (revoked grant, deleted file, non-image). Runs on
         * DECODE_EXECUTOR, never the UI thread.
         */
        internal fun decodeDownscaled(
            context: Context,
            uriString: String,
            maxDimension: Int,
        ): Bitmap? = runCatching {
            val uri = Uri.parse(uriString)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            val opts = BitmapFactory.Options().apply {
                inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, maxDimension)
            }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            }
        }.getOrNull()
    }
}
