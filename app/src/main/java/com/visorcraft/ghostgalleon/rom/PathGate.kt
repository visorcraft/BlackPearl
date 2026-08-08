package com.visorcraft.ghostgalleon.rom

import java.io.File

/**
 * Pure PATH-launch readiness: RetroArch-style players need a reconstructed
 * filesystem path that still exists and is readable. Host-tested with fakes
 * via [pathExists] / [pathReadable] seams.
 */
object PathGate {

    sealed class Decision {
        data object Ok : Decision()
        data class Blocked(val reason: String) : Decision()
    }

    /**
     * Decide whether a PATH-style launch may proceed.
     * - URI-style templates always Ok (SAF grant is separate).
     * - PATH with null/blank path → blocked.
     * - PATH with path that fails [pathExists] or [pathReadable] → blocked.
     */
    fun decide(
        uriStyle: UriStyle,
        path: String?,
        pathExists: (String) -> Boolean = { File(it).exists() },
        pathReadable: (String) -> Boolean = { File(it).canRead() },
    ): Decision {
        if (uriStyle != UriStyle.PATH) return Decision.Ok
        val p = path?.trim().orEmpty()
        if (p.isEmpty()) {
            return Decision.Blocked("ROM path unavailable")
        }
        if (!pathExists(p)) {
            return Decision.Blocked("Storage unmounted or file missing")
        }
        if (!pathReadable(p)) {
            return Decision.Blocked("ROM file not readable")
        }
        return Decision.Ok
    }

    fun isReady(
        uriStyle: UriStyle,
        path: String?,
        pathExists: (String) -> Boolean = { File(it).exists() },
        pathReadable: (String) -> Boolean = { File(it).canRead() },
    ): Boolean = decide(uriStyle, path, pathExists, pathReadable) is Decision.Ok
}
