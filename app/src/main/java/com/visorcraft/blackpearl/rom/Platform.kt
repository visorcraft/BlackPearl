package com.visorcraft.blackpearl.rom

import android.content.Intent

/** How the ROM reference reaches the player: a content/file URI, or a raw
 *  filesystem path (RetroArch is path-only per the launch registry). */
enum class UriStyle { URI, PATH }

// The registry's `--activity-clear-task --activity-clear-top` pair.
// Without it, a repeat launch while the emulator's task is still alive
// stacks/queues the intent and hangs the emulator's main thread (the
// RetroArch "isn't responding" ANR). Intent flag constants are compile-time
// constants, so this stays host-testable.
private const val CLEAR_FLAGS =
    Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP

/**
 * Launch-intent template for a platform's player, taken from the verified
 * launch registry (.superpowers/sdd/stage2-launch-registry.md). `component`
 * is `am -n` notation ("pkg/class"); `extras` values may contain the
 * placeholders {file.uri} / {file.path} — Task 3's RomLauncher substitutes
 * them. The CocoonFE template's `APK` extra is omitted deliberately: its
 * `/data/app/...-1/base.apk` value is a guess and RetroArch launches fine
 * without it. PPSSPP's registry `--activity-no-history` is also omitted
 * deliberately: it would finish the game activity on every HOME press.
 */
data class PlayerTemplate(
    // Stable id within a platform (e.g. "melondualds", "retroarch-snes9x").
    val id: String,
    val displayName: String,
    val component: String,
    val action: String?,
    val uriStyle: UriStyle,
    val extras: Map<String, String> = emptyMap(),
    // Content URIs only work when the caller relays its SAF grant
    // (FLAG_GRANT_READ_URI_PERMISSION); Eden hard-requires it.
    val grantRead: Boolean = uriStyle == UriStyle.URI,
    // Activity flags from the registry template. NEW_TASK is always present
    // (non-activity start contexts; CLEAR_TASK also requires it). Azahar,
    // NetherSX2, PPSSPP, and Cemu add CLEAR_TASK|CLEAR_TOP per the registry;
    // Eden, melonDualDS, Dolphin, and Flycast carry none. RetroArch is the
    // exception: the registry carries clear-task/clear-top but on this
    // device that hangs warm relaunches (see retroArch()), so it is
    // NEW_TASK-only.
    val flags: Int = Intent.FLAG_ACTIVITY_NEW_TASK,
)

data class Platform(
    val id: String,
    val displayName: String,
    val shortName: String,
    // Folder names identifying the platform: matched case-insensitively as
    // either the first path segment under a granted tree root, or the tree
    // root folder itself when the grant points straight at a platform folder.
    val folderNames: List<String>,
    // Lowercase, without dots; matching is case-insensitive.
    val extensions: List<String>,
    // Ordered player list; first entry is the default when no preference is set.
    val players: List<PlayerTemplate>,
) {
    /** Default / primary player (first in [players]). */
    val player: PlayerTemplate get() = players.first()

    fun ownsFolder(name: String): Boolean =
        folderNames.any { it.equals(name, ignoreCase = true) }

    fun acceptsExtension(ext: String): Boolean = extensions.contains(ext.lowercase())
}

/** The platform registry. Winlator is excluded: not ROM-launchable. */
object Platforms {

    private const val RA_COMPONENT =
        "com.retroarch.aarch64/com.retroarch.browser.retroactivity.RetroActivityFuture"

    // RetroArch: identical intent shape for every core; only ROM/LIBRETRO
    // change. Verified on device with Snes9x (registry doc).
    // NEW_TASK ONLY — deliberately NOT the registry's clear-task/clear-top:
    // RetroActivityFuture is singleInstance and reloads content from
    // onNewIntent, so a flagless warm relaunch just works, while CLEAR_TASK
    // forces a destroy of the old activity whose native teardown blocks the
    // main thread past the 10 s destroy timeout and leaves the new instance
    // stuck on the splash (any queued input then ANRs). Device-verified
    // 2026-08-06 (see .superpowers/sdd/launch-flags-report.md).
    private fun retroArch(id: String, displayName: String, coreSoName: String) = PlayerTemplate(
        id = id,
        displayName = displayName,
        component = RA_COMPONENT,
        action = "android.intent.action.MAIN",
        uriStyle = UriStyle.PATH,
        extras = linkedMapOf(
            "ROM" to "{file.path}",
            "LIBRETRO" to
                "/data/data/com.retroarch.aarch64/cores/${coreSoName}_libretro_android.so",
            "CONFIGFILE" to
                "/storage/emulated/0/Android/data/com.retroarch.aarch64/files/retroarch.cfg",
            "IME" to "com.android.inputmethod.latin/.LatinIME",
            "DATADIR" to "/data/data/com.retroarch.aarch64",
            "SDCARD" to "/storage/emulated/0",
            "EXTERNAL" to "/storage/emulated/0/Android/data/com.retroarch.aarch64/files",
        ),
    )

    val GB = Platform(
        id = "gb",
        displayName = "Game Boy",
        shortName = "GB",
        folderNames = listOf("gb"),
        extensions = listOf("gb"),
        players = listOf(
            retroArch("ra-gambatte", "RetroArch (Gambatte)", "gambatte"),
            retroArch("ra-sameboy", "RetroArch (SameBoy)", "sameboy"),
        ),
    )

    val GBC = Platform(
        id = "gbc",
        displayName = "Game Boy Color",
        shortName = "GBC",
        folderNames = listOf("gbc"),
        extensions = listOf("gbc"),
        players = listOf(
            retroArch("ra-gambatte", "RetroArch (Gambatte)", "gambatte"),
            retroArch("ra-sameboy", "RetroArch (SameBoy)", "sameboy"),
        ),
    )

    val GBA = Platform(
        id = "gba",
        displayName = "Game Boy Advance",
        shortName = "GBA",
        folderNames = listOf("gba"),
        extensions = listOf("gba", "agb"),
        players = listOf(
            retroArch("ra-mgba", "RetroArch (mGBA)", "mgba"),
            retroArch("ra-gpsp", "RetroArch (gpSP)", "gpsp"),
        ),
    )

    val NES = Platform(
        id = "nes",
        displayName = "Nintendo Entertainment System",
        shortName = "NES",
        folderNames = listOf("nes", "famicom"),
        extensions = listOf("nes", "unf", "unif", "fds"),
        players = listOf(
            retroArch("ra-fceumm", "RetroArch (FCEUmm)", "fceumm"),
            retroArch("ra-nestopia", "RetroArch (Nestopia UE)", "nestopia"),
        ),
    )

    val SNES = Platform(
        id = "snes",
        displayName = "Super Nintendo",
        shortName = "SNES",
        folderNames = listOf("snes"),
        extensions = listOf("smc", "sfc"),
        players = listOf(
            retroArch("ra-snes9x", "RetroArch (Snes9x)", "snes9x"),
            retroArch("ra-bsnes", "RetroArch (bsnes)", "bsnes"),
            retroArch("ra-snes9x2010", "RetroArch (Snes9x 2010)", "snes9x2010"),
        ),
    )

    val GENESIS = Platform(
        id = "genesis",
        displayName = "Genesis / Mega Drive",
        shortName = "GEN",
        // The card's folder is the romm-style "genesis-slash-megadrive".
        folderNames = listOf("genesis-slash-megadrive", "genesis", "megadrive"),
        extensions = listOf("md", "gen", "bin", "smd"),
        players = listOf(
            retroArch("ra-genplus", "RetroArch (Genesis Plus GX)", "genesis_plus_gx"),
            retroArch("ra-picodrive", "RetroArch (PicoDrive)", "picodrive"),
        ),
    )

    val N64 = Platform(
        id = "n64",
        displayName = "Nintendo 64",
        shortName = "N64",
        folderNames = listOf("n64"),
        extensions = listOf("n64", "z64", "v64"),
        players = listOf(
            retroArch("ra-mupen", "RetroArch (Mupen64Plus-Next)", "mupen64plus_next_gles3"),
            retroArch("ra-parallel", "RetroArch (ParaLLEl N64)", "parallel_n64"),
        ),
    )

    val NDS = Platform(
        id = "nds",
        displayName = "Nintendo DS",
        shortName = "NDS",
        folderNames = listOf("nds"),
        extensions = listOf("nds"),
        // melonDualDS primary; melonDS + DraStic + RetroArch as alternates.
        players = listOf(
            PlayerTemplate(
                id = "melondualds",
                displayName = "melonDualDS",
                component = "me.magnum.melondualds/me.magnum.melonds.ui.emulator.EmulatorActivity",
                action = "me.magnum.melondualds.LAUNCH_ROM",
                uriStyle = UriStyle.URI,
                extras = mapOf("uri" to "{file.uri}"),
            ),
            PlayerTemplate(
                id = "melonds",
                displayName = "melonDS",
                component = "me.magnum.melonds/me.magnum.melonds.ui.emulator.EmulatorActivity",
                action = "me.magnum.melonds.LAUNCH_ROM",
                uriStyle = UriStyle.URI,
                extras = mapOf("uri" to "{file.uri}"),
            ),
            PlayerTemplate(
                id = "drastic",
                displayName = "DraStic",
                component = "com.dsemu.drastic/.DraSticActivity",
                action = null,
                uriStyle = UriStyle.URI,
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or CLEAR_FLAGS,
            ),
            retroArch("ra-melonds", "RetroArch (melonDS)", "melonds"),
        ),
    )

    val N3DS = Platform(
        id = "3ds",
        displayName = "Nintendo 3DS",
        shortName = "3DS",
        folderNames = listOf("3ds", "new-nintendo-3ds"),
        extensions = listOf("3ds", "cci", "cxi", "app"),
        // Azahar primary (verified on device).
        players = listOf(
            PlayerTemplate(
                id = "azahar",
                displayName = "Azahar",
                component = "org.azahar_emu.azahar/org.citra.citra_emu.activities.EmulationActivity",
                action = "android.intent.action.VIEW",
                uriStyle = UriStyle.URI,
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or CLEAR_FLAGS,
            ),
        ),
    )

    val SWITCH = Platform(
        id = "switch",
        displayName = "Nintendo Switch",
        shortName = "Switch",
        folderNames = listOf("switch"),
        extensions = listOf("nsp", "xci", "nca"),
        // Eden: file:// is disproven on device ("No game found in arguments
        // or intent"); a SAF content URI + caller grant is mandatory.
        players = listOf(
            PlayerTemplate(
                id = "eden",
                displayName = "Eden",
                component = "dev.eden.eden_emulator/org.yuzu.yuzu_emu.activities.EmulationActivity",
                action = "android.nfc.action.TECH_DISCOVERED",
                uriStyle = UriStyle.URI,
                grantRead = true,
            ),
        ),
    )

    val PS1 = Platform(
        id = "ps1",
        displayName = "PlayStation",
        shortName = "PS1",
        folderNames = listOf("psx", "ps1", "playstation"),
        extensions = listOf("cue", "bin", "chd", "pbp", "iso", "m3u", "img", "ccd"),
        players = listOf(
            retroArch("ra-pcsx", "RetroArch (PCSX ReARMed)", "pcsx_rearmed"),
            retroArch("ra-beetle-psx", "RetroArch (Beetle PSX)", "mednafen_psx"),
        ),
    )

    val PSP = Platform(
        id = "psp",
        displayName = "PlayStation Portable",
        shortName = "PSP",
        folderNames = listOf("psp"),
        extensions = listOf("iso", "cso", "pbp", "chd"),
        players = listOf(
            PlayerTemplate(
                id = "ppsspp",
                displayName = "PPSSPP",
                component = "org.ppsspp.ppsspp/.PpssppActivity",
                action = "android.intent.action.VIEW",
                uriStyle = UriStyle.URI,
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or CLEAR_FLAGS,
            ),
            retroArch("ra-ppsspp", "RetroArch (PPSSPP)", "ppsspp"),
        ),
    )

    val PS2 = Platform(
        id = "ps2",
        displayName = "PlayStation 2",
        shortName = "PS2",
        folderNames = listOf("ps2"),
        extensions = listOf("iso", "bin", "chd", "cso", "gz"),
        // NetherSX2: game reference in the `bootPath` extra, not intent data.
        players = listOf(
            PlayerTemplate(
                id = "nethersx2",
                displayName = "NetherSX2",
                component = "xyz.aethersx2.android/.EmulationActivity",
                action = "android.intent.action.MAIN",
                uriStyle = UriStyle.URI,
                extras = mapOf("bootPath" to "{file.uri}"),
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or CLEAR_FLAGS,
            ),
        ),
    )

    val SATURN = Platform(
        id = "saturn",
        displayName = "Sega Saturn",
        shortName = "SAT",
        folderNames = listOf("saturn"),
        extensions = listOf("cue", "chd", "iso", "bin", "m3u", "ccd"),
        players = listOf(
            retroArch("ra-yabause", "RetroArch (Yabause)", "yabause"),
            retroArch("ra-beetle-saturn", "RetroArch (Beetle Saturn)", "mednafen_saturn"),
        ),
    )

    val DREAMCAST = Platform(
        id = "dreamcast",
        displayName = "Dreamcast",
        shortName = "DC",
        folderNames = listOf("dreamcast"),
        extensions = listOf("gdi", "cdi", "chd", "cue"),
        players = listOf(
            PlayerTemplate(
                id = "flycast",
                displayName = "Flycast",
                component = "com.flycast.emulator/com.flycast.emulator.MainActivity",
                action = "android.intent.action.VIEW",
                uriStyle = UriStyle.URI,
            ),
            retroArch("ra-flycast", "RetroArch (Flycast)", "flycast"),
        ),
    )

    val ARCADE = Platform(
        id = "arcade",
        displayName = "Arcade",
        shortName = "ARC",
        folderNames = listOf("arcade", "mame", "fbneo"),
        extensions = listOf("zip", "7z"),
        players = listOf(
            retroArch("ra-fbneo", "RetroArch (FinalBurn Neo)", "fbneo"),
            retroArch("ra-mame2003plus", "RetroArch (MAME 2003-Plus)", "mame2003_plus"),
        ),
    )

    val GAMECUBE = Platform(
        id = "gamecube",
        displayName = "GameCube",
        shortName = "GC",
        folderNames = listOf("gamecube"),
        extensions = listOf("iso", "gcm", "rvz", "wbfs", "wad", "dol", "elf"),
        // Dolphin: game reference in the `AutoStartFile` extra on MAIN.
        players = listOf(
            PlayerTemplate(
                id = "dolphin",
                displayName = "Dolphin",
                component = "org.dolphinemu.dolphinemu/.ui.main.MainActivity",
                action = "android.intent.action.VIEW",
                uriStyle = UriStyle.URI,
                extras = mapOf("AutoStartFile" to "{file.uri}"),
            ),
        ),
    )

    val WII = Platform(
        id = "wii",
        displayName = "Wii",
        shortName = "Wii",
        folderNames = listOf("wii"),
        extensions = listOf("iso", "gcm", "rvz", "wbfs", "wad", "dol", "elf"),
        // Same Dolphin entry as GameCube, but CocoonFE's Wii template uses
        // MAIN instead of VIEW.
        players = listOf(
            PlayerTemplate(
                id = "dolphin",
                displayName = "Dolphin",
                component = "org.dolphinemu.dolphinemu/.ui.main.MainActivity",
                action = "android.intent.action.MAIN",
                uriStyle = UriStyle.URI,
                extras = mapOf("AutoStartFile" to "{file.uri}"),
            ),
        ),
    )

    val WIIU = Platform(
        id = "wiiu",
        displayName = "Wii U",
        shortName = "Wii U",
        folderNames = listOf("wiiu"),
        extensions = listOf("wua", "wux", "wud", "rpx"),
        // Cemu (Sugar source build): URI data, default MAIN action.
        players = listOf(
            PlayerTemplate(
                id = "cemu",
                displayName = "Cemu",
                component = "info.cemu.cemu/.emulation.EmulationActivity",
                action = "android.intent.action.MAIN",
                uriStyle = UriStyle.URI,
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or CLEAR_FLAGS,
            ),
        ),
    )

    val ALL: List<Platform> = listOf(
        GB, GBC, GBA, NES, SNES, GENESIS, N64, NDS, N3DS, SWITCH,
        PS1, PSP, PS2, SATURN, DREAMCAST, ARCADE, GAMECUBE, WII, WIIU,
    )

    fun byId(id: String): Platform? = ALL.firstOrNull { it.id == id }

    /** The platform owning a folder name (tree root or first path segment). */
    fun platformForFolder(name: String): Platform? =
        ALL.firstOrNull { it.ownsFolder(name) }
}
