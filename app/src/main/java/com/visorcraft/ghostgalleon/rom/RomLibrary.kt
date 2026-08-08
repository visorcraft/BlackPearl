package com.visorcraft.ghostgalleon.rom

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.documentfile.provider.DocumentFile
import com.visorcraft.ghostgalleon.settings.Settings
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors

/**
 * Persistent ROM index: a JSON array in filesDir/rom_library.json, written
 * atomically (tmp + rename) like SettingsStore. Tree fingerprints for
 * incremental rescan live beside the library as rom_tree_fingerprints.json.
 */
class RomLibrary(private val file: File) {

    private val fingerprintFile: File
        get() = File(file.parentFile, "rom_tree_fingerprints.json")

    /** Outcome of a rescan, so the settings row can toast honestly. */
    sealed class RescanResult {
        /** Fresh index; entries of unreadable/clean trees were retained from
         *  the previous library and [entries] has already been persisted. */
        data class Success(
            val entries: List<RomEntry>,
            /** Trees skipped because their fingerprint matched (incremental). */
            val skippedCleanTrees: Int = 0,
            /** Granted trees that were unreadable this pass (prior entries retained). */
            val retainedUnreadableTrees: Int = 0,
        ) : RescanResult()

        /** Every granted tree was unreadable (card ejected, provider
         *  failure); the stored library was left untouched. */
        data object Unreadable : RescanResult()
    }

    fun load(): List<RomEntry> {
        if (!file.exists()) return emptyList()
        return try {
            // Recompute dedupe on load so a library stored by an older
            // build gets its flags without needing a rescan.
            SwitchDedupe.apply(parseEntries(JSONArray(file.readText())))
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun save(entries: List<RomEntry>) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(entriesToJson(entries).toString(2))
        if (!tmp.renameTo(file)) {
            tmp.copyTo(file, overwrite = true)
            tmp.delete()
        }
    }

    fun loadFingerprints(): Map<String, String> {
        if (!fingerprintFile.exists()) return emptyMap()
        return try {
            val o = JSONObject(fingerprintFile.readText())
            o.keys().asSequence().associateWith { o.getString(it) }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    fun saveFingerprints(map: Map<String, String>) {
        fingerprintFile.parentFile?.mkdirs()
        val tmp = File(fingerprintFile.parentFile, fingerprintFile.name + ".tmp")
        val o = JSONObject()
        map.forEach { (k, v) -> o.put(k, v) }
        tmp.writeText(o.toString(2))
        if (!tmp.renameTo(fingerprintFile)) {
            tmp.copyTo(fingerprintFile, overwrite = true)
            tmp.delete()
        }
    }

    /**
     * SAF-walk every granted tree off the main thread, persist the result
     * and fingerprints, and invoke [onDone] on the main thread. Unreadable
     * trees are skipped with their prior entries retained; clean trees
     * (matching fingerprint) are skipped unless [force] is true. When every
     * granted tree is unreadable the stored library is left untouched.
     */
    fun rescan(
        context: Context,
        settings: Settings,
        force: Boolean = false,
        onDone: (RescanResult) -> Unit,
    ) {
        val appContext = context.applicationContext
        SCAN_EXECUTOR.execute {
            val result = try {
                val priorFp = loadFingerprints()
                val (scanResult, newFp) = rescanBlockingWithFingerprints(
                    treeUris = settings.romTreeUris,
                    prior = load(),
                    isReadable = { isTreeReadable(appContext, it) },
                    treeFor = { uriString ->
                        SafDocumentTree(appContext, Uri.parse(uriString)) to
                            (StoragePaths.treeRootName(uriString) ?: "")
                    },
                    priorFingerprints = priorFp,
                    force = force,
                    // Pure meta fingerprint (count+basenames): one SAF walk, then
                    // skip expensive RomScanner.scan when unchanged. quickMeta
                    // walk-skip is available for injectors that can probe without
                    // a full DocumentTree; production uses a single walk here.
                    fingerprintOf = { TreeFingerprint.ofFilesMeta(it) },
                    readText = { uriString ->
                        runCatching {
                            appContext.contentResolver.openInputStream(Uri.parse(uriString))
                                ?.bufferedReader()
                                ?.use { it.readText() }
                        }.getOrNull()
                    },
                )
                if (scanResult is RescanResult.Success) {
                    save(scanResult.entries)
                    saveFingerprints(newFp)
                }
                scanResult
            } catch (_: Exception) {
                // Fail soft: leave library untouched, report unreadable so
                // callers clear in-flight flags and can retry on next resume.
                RescanResult.Unreadable
            }
            Handler(Looper.getMainLooper()).post { onDone(result) }
        }
    }

    companion object {
        private val SCAN_EXECUTOR = Executors.newSingleThreadExecutor()

        // Internal (not private) so the settings export/import bundle uses
        // the exact same entry codec as the on-disk library file.
        internal fun entriesToJson(entries: List<RomEntry>): JSONArray {
            val arr = JSONArray()
            entries.forEach { e ->
                arr.put(
                    JSONObject()
                        .put("id", e.id)
                        .put("name", e.name)
                        .put("platformId", e.platformId)
                        .put("uri", e.uri)
                        .put("path", e.path ?: JSONObject.NULL)
                        .put("artUri", e.artUri ?: JSONObject.NULL)
                        .put("visibleInUi", e.visibleInUi)
                        .put("description", e.description ?: JSONObject.NULL)
                        .put("screenshotUri", e.screenshotUri ?: JSONObject.NULL)
                        .put("logoUri", e.logoUri ?: JSONObject.NULL)
                        .put("videoUri", e.videoUri ?: JSONObject.NULL)
                        .put("year", e.year ?: JSONObject.NULL)
                        .put("genre", e.genre ?: JSONObject.NULL)
                        .put("developer", e.developer ?: JSONObject.NULL)
                        .put("rating", e.rating ?: JSONObject.NULL),
                )
            }
            return arr
        }

        private fun JSONObject.optNullableString(key: String): String? =
            if (!has(key) || isNull(key)) null else getString(key)

        internal fun parseEntries(arr: JSONArray): List<RomEntry> =
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                RomEntry(
                    id = o.getString("id"),
                    name = o.getString("name"),
                    platformId = o.getString("platformId"),
                    uri = o.getString("uri"),
                    path = o.optNullableString("path"),
                    artUri = o.optNullableString("artUri"),
                    visibleInUi = o.optBoolean("visibleInUi", true),
                    description = o.optNullableString("description"),
                    screenshotUri = o.optNullableString("screenshotUri"),
                    logoUri = o.optNullableString("logoUri"),
                    videoUri = o.optNullableString("videoUri"),
                    year = o.optNullableString("year"),
                    genre = o.optNullableString("genre"),
                    developer = o.optNullableString("developer"),
                    rating = o.optNullableString("rating"),
                )
            }

        private fun isTreeReadable(context: Context, treeUri: String): Boolean {
            val doc = DocumentFile.fromTreeUri(context, Uri.parse(treeUri))
            return doc != null && doc.exists() && doc.canRead()
        }

        /**
         * Guard + merge + incremental fingerprint skip. Pure over injected
         * seams so host tests drive it with fakes. Trees that fail
         * [isReadable] are skipped and prior entries retained. Clean trees
         * (fingerprint match, [force] false) retain prior entries after a
         * fingerprint walk (treeFor still runs to list files; only
         * RomScanner.scan is skipped). When every granted tree is unreadable
         * the scan aborts.
         *
         * Prefer [rescanBlockingWithFingerprints] when the caller needs the
         * updated fingerprint map for persistence.
         */
        internal fun rescanBlocking(
            treeUris: List<String>,
            prior: List<RomEntry>,
            isReadable: (String) -> Boolean,
            treeFor: (String) -> Pair<DocumentTree, String>,
            priorFingerprints: Map<String, String> = emptyMap(),
            force: Boolean = true,
            readText: ((String) -> String?)? = null,
            fingerprintOf: (List<DocFile>) -> String = { TreeFingerprint.ofCombined(it) },
        ): RescanResult {
            return rescanBlockingWithFingerprints(
                treeUris, prior, isReadable, treeFor,
                priorFingerprints, force, readText, fingerprintOf,
            ).first
        }

        /**
         * @param quickMeta optional cheap probe per tree URI (e.g. meta
         *  fingerprint from a lightweight listing). When it matches a pure
         *  meta prior and [force] is false, the full [treeFor] walk is
         *  skipped for that tree. Never invents clean: unknown/mismatch
         *  always falls through to a full walk.
         */
        internal fun rescanBlockingWithFingerprints(
            treeUris: List<String>,
            prior: List<RomEntry>,
            isReadable: (String) -> Boolean,
            treeFor: (String) -> Pair<DocumentTree, String>,
            priorFingerprints: Map<String, String> = emptyMap(),
            force: Boolean = true,
            readText: ((String) -> String?)? = null,
            fingerprintOf: (List<DocFile>) -> String = { TreeFingerprint.ofCombined(it) },
            quickMeta: ((String) -> String?)? = null,
        ): Pair<RescanResult, Map<String, String>> {
            if (treeUris.isNotEmpty() && treeUris.none(isReadable)) {
                return RescanResult.Unreadable to priorFingerprints
            }
            val skippedUnreadable = treeUris.filterNot(isReadable)
            val readable = treeUris.filter(isReadable)
            val freshTrees = mutableListOf<Pair<DocumentTree, String>>()
            val cleanTrees = mutableListOf<String>()
            val newFingerprints = priorFingerprints.toMutableMap()
            // Drop fingerprints for trees no longer granted.
            val granted = treeUris.toSet()
            newFingerprints.keys.filter { it !in granted }.forEach { newFingerprints.remove(it) }
            skippedUnreadable.forEach { /* keep prior fp for when card returns */ }

            for (uri in readable) {
                // Cheap meta-only short-circuit: pure `m…` prior matches probe.
                if (!force && quickMeta != null) {
                    val probe = quickMeta(uri)
                    val priorFp = priorFingerprints[uri]
                    if (probe != null && priorFp != null &&
                        priorFp.startsWith("m") && !priorFp.startsWith("c") &&
                        priorFp == probe
                    ) {
                        cleanTrees.add(uri)
                        newFingerprints[uri] = probe
                        continue
                    }
                }
                val (tree, rootName) = treeFor(uri)
                val files = tree.walk()
                val fp = fingerprintOf(files)
                if (!TreeFingerprint.isDirty(uri, fp, priorFingerprints, force)) {
                    cleanTrees.add(uri)
                    newFingerprints[uri] = fp
                    continue
                }
                // Re-wrap the already-walked listing so scan does not walk again.
                val frozen = object : DocumentTree {
                    override fun walk(): List<DocFile> = files
                }
                freshTrees.add(frozen to rootName)
                newFingerprints[uri] = fp
            }

            val fresh = if (freshTrees.isEmpty()) {
                emptyList()
            } else {
                RomScanner.scan(freshTrees, readText = readText)
            }
            val retainedUris = skippedUnreadable + cleanTrees
            val retained = prior.filter { entry ->
                retainedUris.any { tree -> entry.uri.startsWith(tree) }
            }
            val merged = (fresh + retained).distinctBy { it.id }.sortedWith(
                compareBy({ it.platformId }, { it.name.lowercase() }, { it.id }),
            )
            val success = RescanResult.Success(
                entries = SwitchDedupe.apply(merged),
                skippedCleanTrees = cleanTrees.size,
                retainedUnreadableTrees = skippedUnreadable.size,
            )
            return success to newFingerprints
        }
    }
}
