package com.visorcraft.ghostgalleon.rom

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import com.visorcraft.ghostgalleon.state.DeckState
import com.visorcraft.ghostgalleon.ui.deck.launchOnOtherDisplay

/**
 * Android-free description of a launch intent, built from a platform's
 * [PlayerTemplate] plus a [RomEntry]. Host-tested; [RomLauncher.launch] is a
 * thin wrapper that turns this into a real Intent.
 */
data class LaunchPlan(
    val action: String?,
    val packageName: String,
    val className: String,
    // Intent data (URI-style players); null for PATH-style (RetroArch).
    val dataString: String?,
    val extras: Map<String, String>,
    val grantRead: Boolean,
    // Activity flags from the template (NEW_TASK always; CLEAR_TASK/CLEAR_TOP
    // where the registry carries them - prevents repeat-launch ANRs).
    val flags: Int,
)

/** Pure template interpreter. Returns null when the entry cannot fill the
 *  template (PATH-style player with no reconstructed filesystem path). */
object LaunchPlanBuilder {

    fun build(template: PlayerTemplate, entry: RomEntry): LaunchPlan? {
        val path = entry.path
        if (template.uriStyle == UriStyle.PATH && path == null) return null
        val pkg = template.component.substringBefore('/')
        val rawClass = template.component.substringAfter('/')
        val cls = if (rawClass.startsWith(".")) pkg + rawClass else rawClass
        return LaunchPlan(
            action = template.action,
            packageName = pkg,
            className = cls,
            dataString = if (template.uriStyle == UriStyle.URI) entry.uri else null,
            extras = template.extras.mapValues { (_, v) -> substitute(v, entry) },
            grantRead = template.grantRead,
            flags = template.flags,
        )
    }

    private fun substitute(value: String, entry: RomEntry): String = value
        .replace("{file.uri}", entry.uri)
        .replace("{file.path}", entry.path ?: "")
}

/** Fires launch intents for ROM entries through the platform templates. */
object RomLauncher {

    /**
     * Launch [entry] on the non-interactive display. [playerId] forces a
     * specific registry player; otherwise [preferredPlayerId] (settings
     * default for the platform) is tried before the first installed player.
     * Returns false (with a Toast) when the platform is unknown, the path is
     * unavailable, or no suitable player is installed.
     */
    fun launch(
        activity: Activity,
        state: DeckState,
        entry: RomEntry,
        playerId: String? = null,
        preferredPlayerId: String? = null,
    ): Boolean {
        val platform = Platforms.byId(entry.platformId)
        if (platform == null) {
            toast(activity, "Unknown platform: ${entry.platformId}")
            return false
        }
        val installed = { pkg: String -> isInstalled(activity, pkg) }
        val template = if (playerId != null) {
            PlayerResolver.byId(platform, playerId)?.takeIf {
                installed(PlayerResolver.packageName(it))
            }
        } else {
            PlayerResolver.resolve(platform, preferredPlayerId, installed)
        }
        if (template == null) {
            toast(activity, "${platform.displayName} player not installed")
            return false
        }
        val plan = LaunchPlanBuilder.build(template, entry)
        if (plan == null) {
            toast(activity, "ROM path unavailable")
            return false
        }
        // PATH players (RetroArch): refuse launch when the reconstructed path
        // is missing or unreadable (card ejected / not mounted yet).
        when (val gate = PathGate.decide(template.uriStyle, entry.path)) {
            is PathGate.Decision.Blocked -> {
                toast(activity, gate.reason)
                return false
            }
            PathGate.Decision.Ok -> {}
        }
        val intent = Intent()
            .setClassName(plan.packageName, plan.className)
            .apply {
                plan.action?.let { action = it }
                plan.dataString?.let { data = Uri.parse(it) }
                plan.extras.forEach { (k, v) -> putExtra(k, v) }
                addFlags(plan.flags)
                if (plan.grantRead) addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        return try {
            launchOnOtherDisplay(activity, state, intent)
            true
        } catch (e: ActivityNotFoundException) {
            toast(activity, "${template.displayName} not installed")
            false
        }
    }

    private fun isInstalled(activity: Activity, packageName: String): Boolean =
        try {
            activity.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }

    private fun toast(activity: Activity, message: String) {
        Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
    }
}
