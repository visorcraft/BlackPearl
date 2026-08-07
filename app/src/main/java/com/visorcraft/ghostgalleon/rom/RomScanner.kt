package com.visorcraft.ghostgalleon.rom

/**
 * Tree walk → RomEntry matching. Pure logic over the DocumentTree
 * abstraction; host-tested with fake trees.
 */
object RomScanner {

    /**
     * Walk every tree and match files against the platform registry.
     *
     * @param trees pairs of a tree and its root folder name (e.g. "roms" for
     *  a container grant, or "snes" when the grant points straight at a
     *  platform folder). Platform is decided by the tree root name first,
     *  then by the first relative-path segment.
     * @param readText optional reader for document URIs (SAF contentResolver
     *  in production). When present, every `gamelist.xml` found during the
     *  walk is parsed and [GamelistMeta.enrichRoms] applies offline titles
     *  and descriptions before the list is returned.
     */
    fun scan(
        trees: List<Pair<DocumentTree, String>>,
        readText: ((uri: String) -> String?)? = null,
    ): List<RomEntry> {
        val entries = mutableListOf<RomEntry>()
        val gamelistDocs = mutableListOf<DocFile>()
        trees.forEach { (tree, rootName) ->
            val rootPlatform = Platforms.platformForFolder(rootName)
            val docs = tree.walk()
            val media = LocalMedia.indexImages(docs, rootPlatform != null)
            val videos = LocalMedia.indexVideos(docs, rootPlatform != null)
            docs.forEach docs@{ doc ->
                // Dotfiles/junk anywhere in the path: .DS_Store, ._ AppleDouble
                // files, hidden directories.
                if (doc.relativePath.split('/').any { it.startsWith('.') }) return@docs
                if (doc.name.equals("gamelist.xml", ignoreCase = true)) {
                    gamelistDocs.add(doc)
                    return@docs
                }
                val dot = doc.name.lastIndexOf('.')
                if (dot <= 0 || dot == doc.name.length - 1) return@docs
                val ext = doc.name.substring(dot + 1).lowercase()
                val platform = rootPlatform
                    ?: Platforms.platformForFolder(doc.relativePath.substringBefore('/'))
                    ?: return@docs
                if (!platform.acceptsExtension(ext)) return@docs
                // The platform folder path prefix that artwork hangs off:
                // "" for a platform-rooted tree, else the first segment.
                val prefix =
                    if (rootPlatform != null) "" else doc.relativePath.substringBefore('/')
                val stem = doc.name.substring(0, dot)
                val artUri = LocalMedia.lookupArt(media, prefix, stem)
                entries.add(
                    RomEntry(
                        id = "${platform.id}:${doc.relativePath}",
                        name = stem,
                        platformId = platform.id,
                        uri = doc.uri,
                        path = StoragePaths.filesystemPath(doc.uri),
                        artUri = artUri,
                        screenshotUri = LocalMedia.screenshotUri(media, prefix, stem, artUri),
                        logoUri = LocalMedia.lookupLogo(media, prefix, stem),
                        videoUri = LocalMedia.lookupVideo(videos, prefix, stem),
                    ),
                )
            }
        }
        val sorted = entries.sortedWith(
            compareBy({ it.platformId }, { it.name.lowercase() }, { it.id }),
        )
        if (readText == null || gamelistDocs.isEmpty()) return sorted
        val meta = gamelistDocs.flatMap { doc ->
            val xml = readText(doc.uri) ?: return@flatMap emptyList()
            GamelistMeta.parse(xml)
        }
        if (meta.isEmpty()) return sorted
        return GamelistMeta.enrichRoms(sorted, meta)
    }

    /**
     * Scan pre-walked file lists (incremental rescan path: fingerprint walk
     * already produced [docs]). Same matching as [scan].
     */
    fun scanDocs(
        trees: List<Triple<List<DocFile>, String, Boolean>>,
        readText: ((uri: String) -> String?)? = null,
    ): List<RomEntry> {
        val fake = trees.map { (docs, rootName, _) ->
            object : DocumentTree {
                override fun walk(): List<DocFile> = docs
            } to rootName
        }
        return scan(fake, readText)
    }
}
