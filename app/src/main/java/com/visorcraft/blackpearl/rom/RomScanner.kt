package com.visorcraft.blackpearl.rom

/**
 * Tree walk → RomEntry matching. Pure logic over the DocumentTree
 * abstraction; host-tested with fake trees.
 */
object RomScanner {

    // Local artwork discovery (romm-style): for a ROM in a platform folder,
    // art lives in a sibling images/ (preferred), media/, or art/ folder,
    // matched by filename stem, case-insensitive, with common suffixes
    // ("-image", "_thumb") tolerated when no exact stem matches.
    // romm: images/; ES-DE-ish: media/, art/, boxfront/, covers/
    private val ART_DIRS = listOf("images", "media", "art", "boxfront", "covers")
    private val ART_EXTENSIONS = setOf("png", "jpg", "jpeg", "webp")
    private val ART_SUFFIXES = listOf("", "-image", "_thumb", "-boxart", "-cover")

    /**
     * Walk every tree and match files against the platform registry.
     *
     * @param trees pairs of a tree and its root folder name (e.g. "roms" for
     *  a container grant, or "snes" when the grant points straight at a
     *  platform folder). Platform is decided by the tree root name first,
     *  then by the first relative-path segment.
     */
    fun scan(trees: List<Pair<DocumentTree, String>>): List<RomEntry> {
        val entries = mutableListOf<RomEntry>()
        trees.forEach { (tree, rootName) ->
            val rootPlatform = Platforms.platformForFolder(rootName)
            val docs = tree.walk()
            val art = artworkIndex(docs, rootPlatform != null)
            docs.forEach docs@{ doc ->
                // Dotfiles/junk anywhere in the path: .DS_Store, ._ AppleDouble
                // files, hidden directories.
                if (doc.relativePath.split('/').any { it.startsWith('.') }) return@docs
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
                entries.add(
                    RomEntry(
                        id = "${platform.id}:${doc.relativePath}",
                        name = doc.name.substring(0, dot),
                        platformId = platform.id,
                        uri = doc.uri,
                        path = StoragePaths.filesystemPath(doc.uri),
                        artUri = art.lookup(prefix, doc.name.substring(0, dot)),
                    ),
                )
            }
        }
        return entries.sortedWith(
            compareBy({ it.platformId }, { it.name.lowercase() }, { it.id }),
        )
    }

    // "prefix|lowercase-stem" -> art document uri.
    private class ArtIndex(private val map: Map<String, String>) {
        fun lookup(prefix: String, stem: String): String? {
            val norm = stem.lowercase()
            ART_SUFFIXES.forEach { suffix ->
                map["$prefix|$norm$suffix"]?.let { return it }
            }
            return null
        }
    }

    // Indexes artwork files: direct children of an images//media//art/
    // folder under the platform folder (or the root of a platform-rooted
    // tree). Insertion priority decides collisions: exact stems before
    // suffixed ones, images/ before media/ before art/ — first insert wins.
    private fun artworkIndex(docs: List<DocFile>, rootIsPlatform: Boolean): ArtIndex {
        class Candidate(val key: String, val uri: String, val dirRank: Int, val suffixed: Boolean)
        val candidates = mutableListOf<Candidate>()
        docs.forEach { doc ->
            if (doc.relativePath.split('/').any { it.startsWith('.') }) return@forEach
            val segments = doc.relativePath.split('/')
            val dir: String
            val fileName: String
            val prefix: String
            if (rootIsPlatform) {
                if (segments.size != 2) return@forEach
                prefix = ""; dir = segments[0]; fileName = segments[1]
            } else {
                if (segments.size != 3) return@forEach
                prefix = segments[0]; dir = segments[1]; fileName = segments[2]
            }
            val dirRank = ART_DIRS.indexOf(dir.lowercase())
            if (dirRank < 0) return@forEach
            val dot = fileName.lastIndexOf('.')
            if (dot <= 0 || dot == fileName.length - 1) return@forEach
            if (fileName.substring(dot + 1).lowercase() !in ART_EXTENSIONS) return@forEach
            val stem = fileName.substring(0, dot).lowercase()
            val suffixed = ART_SUFFIXES.drop(1).any { stem.endsWith(it) }
            candidates.add(Candidate("$prefix|$stem", doc.uri, dirRank, suffixed))
        }
        val map = LinkedHashMap<String, String>()
        candidates.sortedWith(compareBy({ it.suffixed }, { it.dirRank }))
            .forEach { map.putIfAbsent(it.key, it.uri) }
        return ArtIndex(map)
    }
}
