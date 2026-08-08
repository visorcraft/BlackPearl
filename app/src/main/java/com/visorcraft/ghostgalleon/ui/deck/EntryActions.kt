package com.visorcraft.ghostgalleon.ui.deck

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import com.visorcraft.ghostgalleon.GhostGalleonApp
import com.visorcraft.ghostgalleon.library.CollectionsOps
import com.visorcraft.ghostgalleon.library.PlayStats
import com.visorcraft.ghostgalleon.library.SessionMath
import com.visorcraft.ghostgalleon.rom.Platforms
import com.visorcraft.ghostgalleon.rom.PlayerResolver
import com.visorcraft.ghostgalleon.rom.RomEntry
import com.visorcraft.ghostgalleon.rom.RomProfiles
import com.visorcraft.ghostgalleon.rom.isInstalled
import com.visorcraft.ghostgalleon.ui.toast

/**
 * Shared library entry actions for Grid, Game, Companion, and drawer hosts.
 */
object EntryActions {

    fun markAsPlayed(activity: AppCompatActivity, key: String) {
        val app = activity.application as GhostGalleonApp
        val live = app.settings
        val next = SessionMath.stampLastPlayed(
            PlayStats(live.lastLaunchedMs, live.playtimeMs),
            key,
            System.currentTimeMillis(),
        )
        app.updateSettings(live.copy(lastLaunchedMs = next.lastLaunchedMs))
        activity.toast("Marked as played")
    }

    fun clearPlayStats(activity: AppCompatActivity, key: String, label: String) {
        val app = activity.application as GhostGalleonApp
        val live = app.settings
        val stats = PlayStats(live.lastLaunchedMs, live.playtimeMs)
        if (!SessionMath.hasStats(stats, key)) {
            activity.toast("No play stats")
            return
        }
        AlertDialog.Builder(activity)
            .setTitle("Clear play stats")
            .setMessage("Remove last played and playtime for $label?")
            .setPositiveButton("Clear") { _, _ ->
                val next = SessionMath.clearStats(
                    PlayStats(live.lastLaunchedMs, live.playtimeMs),
                    key,
                )
                app.updateSettings(
                    live.copy(
                        lastLaunchedMs = next.lastLaunchedMs,
                        playtimeMs = next.totalPlaytimeMs,
                    ),
                )
                activity.toast("Cleared stats")
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    fun toggleFavorite(activity: AppCompatActivity, key: String) {
        val app = activity.application as GhostGalleonApp
        val live = app.settings
        val result = CollectionsOps.toggleFavoriteWithRail(
            live.favorites,
            live.collections,
            key,
        )
        app.updateSettings(
            live.copy(favorites = result.favorites, collections = result.collections),
        )
        activity.toast(
            if (result.added) "Added to favorites" else "Removed from favorites",
        )
    }

    /**
     * Open-with for a ROM: installed players only. [onLaunch] receives the
     * chosen player id after it is saved as the platform default.
     */
    fun openWith(
        activity: AppCompatActivity,
        rom: RomEntry,
        onLaunch: (playerId: String) -> Unit,
    ) {
        val platform = Platforms.byId(rom.platformId) ?: return
        val pm = activity.packageManager
        val installed = PlayerResolver.installedPlayers(platform) { pm.isInstalled(it) }
        if (installed.isEmpty()) {
            activity.toast("No players installed")
            return
        }
        val labels = installed.map { it.displayName }.toTypedArray()
        AlertDialog.Builder(activity)
            .setTitle("Open with")
            .setItems(labels) { _, which ->
                val player = installed[which]
                val app = activity.application as GhostGalleonApp
                app.updateSettings(
                    app.settings.copy(
                        defaultPlayers = app.settings.defaultPlayers +
                            (rom.platformId to player.id),
                    ),
                    notify = false,
                )
                onLaunch(player.id)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    fun playerProfile(activity: AppCompatActivity, rom: RomEntry) {
        val platform = Platforms.byId(rom.platformId) ?: return
        val players = platform.players
        if (players.isEmpty()) {
            activity.toast("No players for platform")
            return
        }
        val app = activity.application as GhostGalleonApp
        val current = app.settings.romProfiles[rom.id]
        val labels = players.map { p ->
            val mark = if (p.id == current) " ✓" else ""
            p.displayName + mark
        } + listOf(
            if (current == null) "Platform default ✓" else "Platform default",
        )
        AlertDialog.Builder(activity)
            .setTitle("Player profile")
            .setItems(labels.toTypedArray()) { _, which ->
                val live = app.settings
                val nextProfiles = if (which >= players.size) {
                    RomProfiles.clearProfile(live.romProfiles, rom.id)
                } else {
                    RomProfiles.setProfile(live.romProfiles, rom.id, players[which].id)
                }
                app.updateSettings(live.copy(romProfiles = nextProfiles))
                activity.toast("Player saved")
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    fun openAppInfo(activity: AppCompatActivity, packageName: String) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { activity.startActivity(intent) }
            .onFailure { activity.toast("Cannot open app info") }
    }

    fun copyTitle(activity: AppCompatActivity, title: String) {
        val text = title.trim().ifEmpty { return }
        val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE)
            as? ClipboardManager
        if (clipboard == null) {
            activity.toast("Clipboard unavailable")
            return
        }
        clipboard.setPrimaryClip(ClipData.newPlainText("title", text))
        activity.toast("Copied title")
    }
}
