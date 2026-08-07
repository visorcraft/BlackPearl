package com.visorcraft.ghostgalleon.settings

import android.content.Context
import java.io.File

/**
 * One-shot package-rename migration helpers: copy private app data
 * (settings.json, rom_library.json, art/) between an export directory and
 * the app's filesDir.
 *
 * Used to carry BlackPearl → Ghost Galleon data across applicationId change
 * when Android Backup is disabled (allowBackup=false) and the old package
 * is not debuggable. Pure file I/O so host unit tests cover the core path.
 *
 * Note: SAF persistable URI grants (ROM folder trees, wallpaper, art
 * override content URIs) are package-bound and cannot transfer; the user
 * must re-grant the ROM folder after import. Cached art files do transfer.
 */
object DataMigrator {

    const val EXPORT_DIR_NAME = "migrate-export"
    const val IMPORT_DIR_NAME = "migrate-import"
    const val DONE_MARKER = "migrated.ok"
    const val READY_MARKER = "ready.ok"

    private val ROOT_FILES = listOf("settings.json", "rom_library.json")
    private const val ART_DIR = "art"

    /**
     * Copy private app data from [fromFilesDir] into [toDir].
     * Creates [toDir] and writes [READY_MARKER] on success.
     * Returns number of top-level items copied (files + art dir if present).
     */
    fun exportFrom(fromFilesDir: File, toDir: File): Int {
        toDir.mkdirs()
        var n = 0
        for (name in ROOT_FILES) {
            val src = File(fromFilesDir, name)
            if (src.isFile) {
                src.copyTo(File(toDir, name), overwrite = true)
                n++
            }
        }
        val artSrc = File(fromFilesDir, ART_DIR)
        if (artSrc.isDirectory) {
            val artDst = File(toDir, ART_DIR)
            if (artDst.exists()) artDst.deleteRecursively()
            artSrc.copyRecursively(artDst, overwrite = true)
            n++
        }
        File(toDir, READY_MARKER).writeText("ok\n")
        return n
    }

    /**
     * Import from [fromDir] into [toFilesDir] when [READY_MARKER] is present
     * and [DONE_MARKER] is not. Overwrites destination files. Writes
     * [DONE_MARKER] and removes [READY_MARKER] on success.
     * Returns items imported, or 0 if nothing to do.
     */
    fun importInto(fromDir: File, toFilesDir: File): Int {
        if (!fromDir.isDirectory) return 0
        if (!File(fromDir, READY_MARKER).isFile) return 0
        if (File(fromDir, DONE_MARKER).isFile) return 0

        toFilesDir.mkdirs()
        var n = 0
        for (name in ROOT_FILES) {
            val src = File(fromDir, name)
            if (src.isFile) {
                src.copyTo(File(toFilesDir, name), overwrite = true)
                n++
            }
        }
        val artSrc = File(fromDir, ART_DIR)
        if (artSrc.isDirectory) {
            val artDst = File(toFilesDir, ART_DIR)
            artDst.mkdirs()
            artSrc.walkTopDown().forEach { f ->
                if (f.isFile) {
                    val rel = f.relativeTo(artSrc)
                    val dest = File(artDst, rel.path)
                    dest.parentFile?.mkdirs()
                    f.copyTo(dest, overwrite = true)
                }
            }
            n++
        }
        File(fromDir, READY_MARKER).delete()
        File(fromDir, DONE_MARKER).writeText("ok\n")
        return n
    }

    /** Export private files to app-external-files/[EXPORT_DIR_NAME]. */
    fun exportToExternal(context: Context): Int {
        val dest = File(context.getExternalFilesDir(null), EXPORT_DIR_NAME)
        return exportFrom(context.filesDir, dest)
    }

    /**
     * Import from app-external-files/[IMPORT_DIR_NAME] into private filesDir.
     * Call before loading settings on cold start.
     */
    fun tryImportFromExternal(context: Context): Int {
        val src = File(context.getExternalFilesDir(null), IMPORT_DIR_NAME)
        return importInto(src, context.filesDir)
    }
}
