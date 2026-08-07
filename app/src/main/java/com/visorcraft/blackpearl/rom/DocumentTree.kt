package com.visorcraft.blackpearl.rom

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile

/**
 * One file under a scanned tree. `relativePath` is '/'-separated, relative
 * to the tree root ("snes/2020 Super Baseball (U).smc").
 */
data class DocFile(val name: String, val uri: String, val relativePath: String)

/** A walkable SAF tree (or a host-test fake). */
interface DocumentTree {
    fun walk(): List<DocFile>
}

/** SAF-backed tree walker (androidx.documentfile). Runs off the UI thread. */
class SafDocumentTree(context: Context, treeUri: Uri) : DocumentTree {

    private val root: DocumentFile? = DocumentFile.fromTreeUri(context, treeUri)

    override fun walk(): List<DocFile> {
        val out = mutableListOf<DocFile>()
        fun recurse(dir: DocumentFile, prefix: String) {
            dir.listFiles().forEach { child ->
                val name = child.name ?: return@forEach
                when {
                    child.isDirectory -> recurse(child, "$prefix$name/")
                    child.isFile -> out.add(DocFile(name, child.uri.toString(), prefix + name))
                }
            }
        }
        root?.let { recurse(it, "") }
        return out
    }
}
