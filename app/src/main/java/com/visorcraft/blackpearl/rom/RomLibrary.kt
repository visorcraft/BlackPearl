package com.visorcraft.blackpearl.rom

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.documentfile.provider.DocumentFile
import com.visorcraft.blackpearl.settings.Settings
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors

/**
 * Persistent ROM index: a JSON array in filesDir/rom_library.json, written
 * atomically (tmp + rename) like SettingsStore.
 */
class RomLibrary(private val file: File) {

    /** Outcome of a rescan, so the settings row can toast honestly. */
    sealed class RescanResult {
        /** Fresh index; entries of unreadable trees were retained from the
         *  previous library and [entries] has already been persisted. */
        data class Success(val entries: List<RomEntry>) : RescanResult()

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

    /**
     * SAF-walk every granted tree off the main thread, persist the result,
     * and invoke [onDone] on the main thread with the outcome. Unreadable
     * trees are skipped with their prior entries retained; when every
     * granted tree is unreadable the stored library is left untouched and
     * [RescanResult.Unreadable] is reported.
     */
    fun rescan(context: Context, settings: Settings, onDone: (RescanResult) -> Unit) {
        val appContext = context.applicationContext
        SCAN_EXECUTOR.execute {
            val result = rescanBlocking(
                treeUris = settings.romTreeUris,
                prior = load(),
                isReadable = { isTreeReadable(appContext, it) },
                treeFor = { uriString ->
                    SafDocumentTree(appContext, Uri.parse(uriString)) to
                        (StoragePaths.treeRootName(uriString) ?: "")
                },
            )
            if (result is RescanResult.Success) save(result.entries)
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
                        .put("visibleInUi", e.visibleInUi),
                )
            }
            return arr
        }

        internal fun parseEntries(arr: JSONArray): List<RomEntry> =
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                RomEntry(
                    id = o.getString("id"),
                    name = o.getString("name"),
                    platformId = o.getString("platformId"),
                    uri = o.getString("uri"),
                    path = if (o.isNull("path")) null else o.getString("path"),
                    // Added in Stage 3: absent in old library files.
                    artUri = if (!o.has("artUri") || o.isNull("artUri")) {
                        null
                    } else {
                        o.getString("artUri")
                    },
                    // Added in Stage 3 Task 4: absent in old library files.
                    visibleInUi = o.optBoolean("visibleInUi", true),
                )
            }

        private fun isTreeReadable(context: Context, treeUri: String): Boolean {
            val doc = DocumentFile.fromTreeUri(context, Uri.parse(treeUri))
            return doc != null && doc.exists() && doc.canRead()
        }

        /**
         * Guard + merge, pure over injected seams so host tests can drive it
         * with fake trees and a fake readability check. Trees that fail
         * [isReadable] are skipped and their previously stored entries are
         * retained (matched by tree-URI prefix — SAF child document URIs
         * embed the tree URI). When every granted tree is unreadable the
         * scan aborts so an ejected card can never wipe the library.
         */
        internal fun rescanBlocking(
            treeUris: List<String>,
            prior: List<RomEntry>,
            isReadable: (String) -> Boolean,
            treeFor: (String) -> Pair<DocumentTree, String>,
        ): RescanResult {
            if (treeUris.isNotEmpty() && treeUris.none(isReadable)) {
                return RescanResult.Unreadable
            }
            val skipped = treeUris.filterNot(isReadable)
            val fresh = RomScanner.scan(treeUris.filter(isReadable).map(treeFor))
            val retained = prior.filter { entry ->
                skipped.any { tree -> entry.uri.startsWith(tree) }
            }
            val merged = (fresh + retained).distinctBy { it.id }.sortedWith(
                compareBy({ it.platformId }, { it.name.lowercase() }, { it.id }),
            )
            return RescanResult.Success(SwitchDedupe.apply(merged))
        }
    }
}
