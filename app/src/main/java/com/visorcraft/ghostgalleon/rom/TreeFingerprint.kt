package com.visorcraft.ghostgalleon.rom

/**
 * Pure fingerprint + dirty decision for incremental ROM tree rescans.
 * Host-tested; no Android types. Fingerprints are opaque strings produced
 * from tree file listings (or any injected metadata the caller prefers).
 *
 * Cheap probe form: [ofMeta] produces `m{count}:{nameHash}` from a file
 * count + sorted basenames only. When a prior fingerprint is the same
 * meta form and [isDirty] is false, callers may skip a full SAF re-match
 * (and optionally skip re-walking if the probe was computed without a
 * full recursive listing — honest best-effort; false "clean" is avoided
 * by never inventing a meta match without real listing data).
 */
object TreeFingerprint {

    /**
     * Stable fingerprint of a tree listing: sorted relative paths joined
     * with newlines, then a simple non-crypto hash (host-stable, fast).
     * Empty trees get a non-empty sentinel so "missing prior" still dirties.
     */
    fun of(files: List<DocFile>): String {
        if (files.isEmpty()) return "empty"
        val body = files.map { it.relativePath }.sorted().joinToString("\n")
        return "h${fnv1a64(body)}"
    }

    /**
     * Cheap meta fingerprint: file count + hash of sorted basenames.
     * Cheaper to compare when a probe already listed names; still dirties
     * when a file is renamed or added (count/name set changes). Content
     * rewrite at the same path is not detected (same as path-list-only
     * fingerprints) — force full rescan covers that case.
     */
    fun ofMeta(fileCount: Int, basenames: List<String>): String {
        if (fileCount <= 0 && basenames.isEmpty()) return "m0:empty"
        val body = basenames.sorted().joinToString("\n")
        return "m$fileCount:${fnv1a64(body)}"
    }

    /** Meta fingerprint derived from a full [DocFile] listing. */
    fun ofFilesMeta(files: List<DocFile>): String =
        ofMeta(files.size, files.map { it.name })

    /**
     * Combined fingerprint: full path hash + meta prefix so either signal
     * can dirty a tree. Format `c{meta}|{pathHash}`.
     */
    fun ofCombined(files: List<DocFile>): String {
        if (files.isEmpty()) return "cempty|empty"
        return "c${ofFilesMeta(files)}|${of(files)}"
    }

    /**
     * True when the tree must be rescanned: [force], no prior fingerprint,
     * or the current fingerprint differs from the stored one.
     */
    fun isDirty(
        treeUri: String,
        currentFingerprint: String,
        priorFingerprints: Map<String, String>,
        force: Boolean,
    ): Boolean {
        if (force) return true
        val prior = priorFingerprints[treeUri] ?: return true
        return prior != currentFingerprint
    }

    /**
     * When [prior] is a combined fingerprint and [metaProbe] matches its
     * meta half, the path half may still be checked later — but if the
     * caller only has a meta probe and it mismatches, treat as dirty.
     * Returns true if dirty/unknown, false if meta alone proves clean
     * (only when prior is pure meta form starting with `m`).
     */
    fun isDirtyFromMetaProbe(
        treeUri: String,
        metaProbe: String,
        priorFingerprints: Map<String, String>,
        force: Boolean,
    ): Boolean {
        if (force) return true
        val prior = priorFingerprints[treeUri] ?: return true
        // Pure meta prior: direct compare.
        if (prior.startsWith("m") && !prior.startsWith("c")) {
            return prior != metaProbe
        }
        // Combined prior `c{meta}|{path}`: meta mismatch ⇒ dirty; meta match
        // is not enough to skip path walk (return true = still need walk).
        if (prior.startsWith("c") && prior.contains('|')) {
            val priorMeta = prior.removePrefix("c").substringBefore('|')
            if (priorMeta != metaProbe) return true
            // Meta matches but path half may differ — still dirty for skip-walk
            // purposes unless caller has full fingerprint.
            return true
        }
        // Unknown prior form: dirty.
        return true
    }

    /** FNV-1a 64-bit over UTF-16 code units (Kotlin String); hex output. */
    internal fun fnv1a64(s: String): String {
        var hash = -0x340d631b7bdddcdbL // FNV offset basis
        val prime = 0x100000001b3L
        for (i in s.indices) {
            hash = hash xor s[i].code.toLong()
            hash *= prime
        }
        return java.lang.Long.toUnsignedString(hash, 16)
    }
}
