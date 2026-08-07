package com.visorcraft.ghostgalleon.settings

import com.visorcraft.ghostgalleon.state.UIMode

data class Settings(
    val theme: String = "dark",
    val accentColor: Int = 0xFF3F51B5.toInt(),
    val background: String = "solid",
    val gridColumns: Int = 5,
    val iconSizeDp: Int = 72,
    val cardSizeDp: Int = 200,
    val animationMs: Int = 200,
    val defaultMode: UIMode = UIMode.GRID,
    val primaryDisplay: Int = 1,
    val gyroEnabled: Boolean = true,
    val angleLock: Boolean = false,
    val haptics: Boolean = true,
    val showHints: Boolean = true,
    val showLabels: Boolean = true,
    // Grid scroll axis: "vertical" (default) or "horizontal" (3DS-style
    // left-to-right page flipping).
    val gridDirection: String = "vertical",
    // SAF-persisted image URI drawn dimmed behind the grid; null = black.
    val wallpaperUri: String? = null,
    // SAF tree grants for ROM folders (persistable read permission held per
    // entry); scanned by the Stage 2 RomScanner.
    val romTreeUris: List<String> = emptyList(),
    // SteamGridDB API key for the optional "download missing artwork"
    // scraper (Settings → Library); null = not set, scraper row disabled.
    val sgdbApiKey: String? = null,
    // Auto-growing dock (schema v6): canonical in-memory form is CAPACITY
    // (9) entries — filled keys in slot order first, trailing nulls. The
    // store persists only the filled keys; the bar renders
    // DockSlots.visibleCount slots (max(4, min(filled+1, 9))), empty slots
    // showing as "+" placeholders that open the picker. v4/v5 fixed-slot
    // lists and v3 dockPackages migrate via DockSlots.compact.
    val dockSlots: List<String?> = DockSlots.blank(),
    // Curated grid: ordered fixed-size slots, null = blank. The grid shows
    // exactly these slots, never the full installed-app list.
    val gridSlots: List<String?> = GridSlots.blank(),
    val hiddenPackages: Set<String> = emptySet(),
    // Per-app display overrides, keyed by package name. customNames replaces
    // the label everywhere labels resolve (grid/picker/carousel/hero);
    // customIcons holds a SAF-persisted image URI rendered instead of the
    // package icon. Both added within schema v3, no bump.
    val customNames: Map<String, String> = emptyMap(),
    val customIcons: Map<String, String> = emptyMap(),
    // Android KeyEvent keyCode -> Action
    val keyMap: Map<Int, Action> = DEFAULT_KEY_MAP,
    // --- schema v5: library browse / play / collections / players / art ---
    // Slot keys (package or "rom:<id>") -> last launch epoch ms.
    val lastLaunchedMs: Map<String, Long> = emptyMap(),
    // Slot keys -> accumulated playtime ms across sessions.
    val playtimeMs: Map<String, Long> = emptyMap(),
    // platformId -> preferred PlayerTemplate.id
    val defaultPlayers: Map<String, String> = emptyMap(),
    // romId -> SAF image URI override (wins over scanner artUri)
    val artOverrides: Map<String, String> = emptyMap(),
    // Slot keys marked favorite
    val favorites: Set<String> = emptySet(),
    // Named collections: name -> ordered slot keys
    val collections: Map<String, List<String>> = emptyMap(),
    // First-run / empty-library setup card dismissed (within schema v6).
    val setupDismissed: Boolean = false,
    // --- schema v7: companion roles, profiles, folders, themes, RA ---
    // Companion panel role: HERO | NOW_PLAYING | PERF_HUD | PINNED_APP
    val companionRole: String = CompanionRole.HERO.name,
    val companionPinnedPackage: String? = null,
    // rom entry id → PlayerTemplate.id override
    val romProfiles: Map<String, String> = emptyMap(),
    // folder id → FolderSpec (name + ordered member keys)
    val folders: Map<String, FolderSpec> = emptyMap(),
    // Built-in theme pack id (ghost | threeds | oled | neon) or custom id
    val themePackId: String = ThemePack.GHOST.id,
    // Optional imported theme JSON; when valid, overrides built-in tokens
    val themeCustomJson: String? = null,
    // Optional RetroAchievements API key + username (like sgdbApiKey)
    val raApiKey: String? = null,
    val raUsername: String? = null,
    // --- schema v8: portable display topology ---
    // Device profile catalog id: auto | onex-sugar | generic-dual | single
    val deviceProfileId: String = "auto",
    // Interactive display: auto | default | secondary | id:<n>
    val interactiveDisplayMode: String = "auto",
    // Orientation: auto | sensor_landscape | lock_landscape
    val orientationMode: String = "auto",
    // Sticky pin after manual swap; null = follow profile/auto
    val userPinnedPrimaryId: Int? = null,
    val schemaVersion: Int = 8,
) {
    companion object {
        val DEFAULT_KEY_MAP: Map<Int, Action> = mapOf(
            19 to Action.NAV_UP,       // KEYCODE_DPAD_UP
            20 to Action.NAV_DOWN,     // KEYCODE_DPAD_DOWN
            21 to Action.NAV_LEFT,     // KEYCODE_DPAD_LEFT
            22 to Action.NAV_RIGHT,    // KEYCODE_DPAD_RIGHT
            23 to Action.CONFIRM,      // KEYCODE_DPAD_CENTER
            66 to Action.CONFIRM,      // KEYCODE_ENTER
            96 to Action.CONFIRM,      // KEYCODE_BUTTON_A
            4 to Action.BACK,          // KEYCODE_BACK
            97 to Action.BACK,         // KEYCODE_BUTTON_B
            99 to Action.SWAP_SCREENS, // KEYCODE_BUTTON_X
            100 to Action.TOGGLE_MODE, // KEYCODE_BUTTON_Y
            108 to Action.OPEN_SETTINGS, // KEYCODE_BUTTON_START
            102 to Action.PAGE_PREV,   // KEYCODE_BUTTON_L1
            103 to Action.PAGE_NEXT,   // KEYCODE_BUTTON_R1
            109 to Action.OPEN_QUICK_PANEL, // KEYCODE_BUTTON_SELECT
        )
        val DEFAULT = Settings()
    }
}
