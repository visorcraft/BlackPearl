package com.visorcraft.ghostgalleon.rom

/**
 * Pure local media matching for box art and screenshots beside ROM trees
 * (romm / ES-DE conventions). Host-tested; no Android types.
 *
 * Supports flat layouts (`images/stem.png`) and deeper ES-DE-style paths
 * (`media/screenshots/stem.png`, `media/images/stem-image.png`,
 * `boxfront/stem.png`).
 */
object LocalMedia {

    val ART_DIRS = listOf(
        "images", "media", "art", "boxfront", "covers", "screenshots",
        "titlescreens", "wheels", "marquee", "logos", "videos", "video",
    )
    val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "webp")
    val VIDEO_EXTENSIONS = setOf("mp4", "webm", "mkv")
    val VIDEO_DIRS = listOf("videos", "video", "snaps", "movies")

    /** Box-art stem suffixes tried after exact stem match. */
    val ART_SUFFIXES = listOf(
        "", "-image", "_thumb", "-boxart", "-cover", "-poster", "-logo", "-wheel",
    )

    /** Screenshot stem suffixes (exact first, then these). */
    val SCREENSHOT_SUFFIXES = listOf(
        "-screenshot",
        "_screenshot",
        "-ss",
        "_ss",
        "-snap",
        "_snap",
        "-screen",
        "_screen",
        "-title",
        "_title",
    )

    // Nested media subfolders (ES-DE): media/screenshots/x.png
    private val NESTED_MEDIA = setOf(
        "screenshots", "images", "covers", "boxfront", "titlescreens",
        "wheels", "marquee", "logos", "boxart",
    )

    /**
     * Index image files under art dirs. [rootIsPlatform] matches
     * [RomScanner] tree layout (platform-rooted vs container grant).
     * Keys are `"$prefix|$lowercaseStem"`.
     */
    fun indexImages(docs: List<DocFile>, rootIsPlatform: Boolean): Map<String, String> {
        data class Candidate(val key: String, val uri: String, val dirRank: Int, val suffixed: Boolean)
        val candidates = mutableListOf<Candidate>()
        docs.forEach { doc ->
            if (doc.relativePath.split('/').any { it.startsWith('.') }) return@forEach
            val parsed = parseMediaPath(doc.relativePath, rootIsPlatform) ?: return@forEach
            val (prefix, dir, fileName) = parsed
            val dirRank = ART_DIRS.indexOf(dir.lowercase()).let { if (it < 0) 50 else it }
            val dot = fileName.lastIndexOf('.')
            if (dot <= 0 || dot == fileName.length - 1) return@forEach
            if (fileName.substring(dot + 1).lowercase() !in IMAGE_EXTENSIONS) return@forEach
            val stem = fileName.substring(0, dot).lowercase()
            val artSuffixed = ART_SUFFIXES.drop(1).any { stem.endsWith(it) }
            val shotSuffixed = SCREENSHOT_SUFFIXES.any { stem.endsWith(it) }
            candidates.add(
                Candidate("$prefix|$stem", doc.uri, dirRank, artSuffixed || shotSuffixed),
            )
        }
        val map = LinkedHashMap<String, String>()
        candidates.sortedWith(compareBy({ it.suffixed }, { it.dirRank }))
            .forEach { map.putIfAbsent(it.key, it.uri) }
        return map
    }

    /**
     * Parse a relative path into (platformPrefix, mediaDir, fileName).
     * Accepts:
     * - root platform: `images/x.png`, `media/screenshots/x.png`
     * - container: `snes/images/x.png`, `snes/media/screenshots/x.png`
     */
    fun parseMediaPath(
        relativePath: String,
        rootIsPlatform: Boolean,
    ): Triple<String, String, String>? {
        val segments = relativePath.split('/').filter { it.isNotEmpty() }
        if (segments.size < 2) return null
        return if (rootIsPlatform) {
            when (segments.size) {
                2 -> {
                    val dir = segments[0]
                    if (dir.lowercase() !in ART_DIRS.map { it.lowercase() }.toSet() &&
                        dir.lowercase() !in NESTED_MEDIA
                    ) {
                        return null
                    }
                    Triple("", dir, segments[1])
                }
                3 -> {
                    // media/screenshots/file.png
                    val mid = segments[0].lowercase()
                    val sub = segments[1].lowercase()
                    if (mid == "media" && sub in NESTED_MEDIA) {
                        Triple("", sub, segments[2])
                    } else if (mid in ART_DIRS.map { it.lowercase() }.toSet()) {
                        // images/extra/file.png — treat mid as dir
                        Triple("", mid, segments[2])
                    } else {
                        null
                    }
                }
                else -> null
            }
        } else {
            when (segments.size) {
                3 -> {
                    val prefix = segments[0]
                    val dir = segments[1]
                    if (dir.lowercase() !in ART_DIRS.map { it.lowercase() }.toSet() &&
                        dir.lowercase() !in NESTED_MEDIA
                    ) {
                        return null
                    }
                    Triple(prefix, dir, segments[2])
                }
                4 -> {
                    // snes/media/screenshots/file.png
                    val prefix = segments[0]
                    val mid = segments[1].lowercase()
                    val sub = segments[2].lowercase()
                    if (mid == "media" && sub in NESTED_MEDIA) {
                        Triple(prefix, sub, segments[3])
                    } else {
                        null
                    }
                }
                else -> null
            }
        }
    }

    fun lookupArt(index: Map<String, String>, prefix: String, stem: String): String? {
        val norm = stem.lowercase()
        ART_SUFFIXES.forEach { suffix ->
            index["$prefix|$norm$suffix"]?.let { return it }
        }
        return null
    }

    fun lookupScreenshot(index: Map<String, String>, prefix: String, stem: String): String? {
        val norm = stem.lowercase()
        SCREENSHOT_SUFFIXES.forEach { suffix ->
            index["$prefix|$norm$suffix"]?.let { return it }
        }
        return index["$prefix|$norm"]
    }

    /**
     * Logo / wheel art (titlescreen-style), separate from box art.
     * Prefers `-logo` / `-wheel` suffixes then bare stem in logos/wheels dirs
     * (already merged into the flat index by stem).
     */
    fun lookupLogo(index: Map<String, String>, prefix: String, stem: String): String? {
        val norm = stem.lowercase()
        listOf("-logo", "_logo", "-wheel", "_wheel", "-marquee", "_marquee").forEach { suffix ->
            index["$prefix|$norm$suffix"]?.let { return it }
        }
        // Bare stem: ES-DE media/logos/Game.png indexes as prefix|game.
        return index["$prefix|$norm"]
    }

    /**
     * Screenshot URI for [stem], preferring screenshot suffixes. When the
     * only match is the same URI already used as box art, returns null so
     * the hero does not double-show the cover as a "screenshot".
     */
    fun screenshotUri(
        index: Map<String, String>,
        prefix: String,
        stem: String,
        artUri: String?,
    ): String? {
        val norm = stem.lowercase()
        SCREENSHOT_SUFFIXES.forEach { suffix ->
            index["$prefix|$norm$suffix"]?.let { return it }
        }
        val bare = index["$prefix|$norm"]
        return if (bare != null && bare != artUri) bare else null
    }

    /**
     * Index video snaps under conventional dirs. Keys `"$prefix|$lowercaseStem"`.
     * Accepts flat `videos/stem.mp4`, `media/videos/stem.mp4`, and platform-
     * prefixed `snes/videos/stem.mp4`.
     */
    fun indexVideos(docs: List<DocFile>, rootIsPlatform: Boolean): Map<String, String> {
        val map = LinkedHashMap<String, String>()
        docs.forEach { doc ->
            if (doc.relativePath.split('/').any { it.startsWith('.') }) return@forEach
            val parsed = parseVideoPath(doc.relativePath, rootIsPlatform) ?: return@forEach
            val (prefix, fileName) = parsed
            val dot = fileName.lastIndexOf('.')
            if (dot <= 0 || dot == fileName.length - 1) return@forEach
            if (fileName.substring(dot + 1).lowercase() !in VIDEO_EXTENSIONS) return@forEach
            val stem = fileName.substring(0, dot).lowercase()
            map.putIfAbsent("$prefix|$stem", doc.uri)
        }
        return map
    }

    fun lookupVideo(index: Map<String, String>, prefix: String, stem: String): String? =
        index["$prefix|${stem.lowercase()}"]

    /**
     * Parse video relative path → (platformPrefix, fileName).
     * - root platform: `videos/x.mp4`, `media/videos/x.mp4`
     * - container: `snes/videos/x.mp4`, `snes/media/videos/x.mp4`
     */
    fun parseVideoPath(
        relativePath: String,
        rootIsPlatform: Boolean,
    ): Pair<String, String>? {
        val segments = relativePath.split('/').filter { it.isNotEmpty() }
        if (segments.size < 2) return null
        val videoDirs = VIDEO_DIRS.map { it.lowercase() }.toSet()
        return if (rootIsPlatform) {
            when (segments.size) {
                2 -> {
                    if (segments[0].lowercase() !in videoDirs) return null
                    "" to segments[1]
                }
                3 -> {
                    // media/videos/file.mp4
                    if (segments[0].lowercase() == "media" &&
                        segments[1].lowercase() in videoDirs
                    ) {
                        "" to segments[2]
                    } else {
                        null
                    }
                }
                else -> null
            }
        } else {
            when (segments.size) {
                3 -> {
                    if (segments[1].lowercase() !in videoDirs) return null
                    segments[0] to segments[2]
                }
                4 -> {
                    if (segments[1].lowercase() == "media" &&
                        segments[2].lowercase() in videoDirs
                    ) {
                        segments[0] to segments[3]
                    } else {
                        null
                    }
                }
                else -> null
            }
        }
    }
}
