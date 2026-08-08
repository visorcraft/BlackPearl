<p align="center">
  <img src="static/icon-512.png" alt="Ghost Galleon logo" width="250" />
</p>

<h1 align="center">Ghost Galleon</h1>
<p align="center"><i>Ghost Galleon Dual Screen Launcher</i></p>

<p align="center">
  <b>A dual-screen Android launcher built for the One X Sugar handheld.</b>
  <br />
  Grid Mode (3DS/Wii-style icon grid + dock) and Game Mode (card carousel) across one or two displays,
  <br />
  with portable display topology, live screen swap, gyro-aware orientation, remappable gamepad input, and a SAF-scanned ROM library.
</p>

<p align="center">
  <a href="https://github.com/visorcraft/GhostGalleon/releases/latest"><img src="https://img.shields.io/github/v/release/visorcraft/GhostGalleon?sort=semver" alt="Latest release" /></a>
  <img src="https://img.shields.io/badge/platform-Android%208%2B-3ddc84?logo=android&amp;logoColor=white" alt="Android 8+" />
  <img src="https://img.shields.io/badge/language-Kotlin-7f52ff?logo=kotlin&amp;logoColor=white" alt="Kotlin" />
  <img src="https://img.shields.io/badge/API-26%E2%80%9334-0b57a4" alt="API 26–34" />
  <img src="https://img.shields.io/badge/version-0.3.0-informational" alt="0.3.0" />
</p>

---

## Screenshots

<table>
  <tr>
    <td width="50%">
      <img src="docs/screenshots/grid-mode.png" alt="Ghost Galleon Grid Mode on the bottom display with icon grid and dock" />
      <br />
      <sub><b>Grid Mode</b> - curated 3DS-style grid, blank "+" slots, and the dock.</sub>
    </td>
    <td width="50%">
      <img src="docs/screenshots/hero-panel.png" alt="Ghost Galleon hero preview panel on the top display" />
      <br />
      <sub><b>Hero panel</b> - the companion display previews the current selection.</sub>
    </td>
  </tr>
  <tr>
    <td width="50%">
      <img src="docs/screenshots/game-mode.png" alt="Ghost Galleon Game Mode card carousel" />
      <br />
      <sub><b>Game Mode</b> - Daijisho/GameDeck-style card carousel.</sub>
    </td>
    <td width="50%">
      <img src="docs/screenshots/app-picker.png" alt="Ghost Galleon searchable app and ROM picker" />
      <br />
      <sub><b>Picker</b> - search apps and ROMs to fill any grid or dock slot.</sub>
    </td>
  </tr>
</table>

---

## What is Ghost Galleon?

Ghost Galleon (Ghost Galleon Dual Screen Launcher) is a home-screen replacement for Android handhelds. It is **built and QA’d on the One X Sugar** (Android 14, top 2160×1080 + bottom 1240×1080) and also runs on **single-display** devices via Auto topology.

On dual-screen hardware one panel hosts the interactive deck (grid or carousel) while the other shows a companion surface (hero preview, Now Playing, Perf HUD, or a pinned app). Ghost Galleon holds the Android **HOME** role (and **SECONDARY_HOME** on dual panels), is designed for full gamepad control, and supports swipe-up all-apps.

### Highlights

- **Grid Mode** — 3DS/Wii-style icon grid with a dock, blank “+” slots, long-press Move/Remove, swap-reorder, favorites, and folders. Optional deck clock/battery (off by default).
- **Game Mode** — card carousel with a **minimal** default chip bar (All / Recent / Continue / Fav + platforms + Search/Select). Power-user rails (Installed, Games, Top, Week, Month, A–Z, New, Random, genre chips, letter jump) and Quick Panel browse shortcuts are **opt-in** under Settings → Display & Grid → Browse chrome.
- **Portable display topology** — resolves interactive vs companion vs launch displays from `DisplayManager` (no hard-coded 0/1). Profiles: Auto, One X Sugar, Generic dual, Single.
- **Live screen swap** — X (default) swaps interactive and companion roles with a sticky pin so Auto refresh does not undo it.
- **Companion roles** — Hero, Now Playing, Perf HUD, or pinned app on the non-interactive panel (large status pill).
- **Global input** — gamepad, d-pad, stick, and touch route to the interactive deck regardless of which window has focus; held directions auto-repeat.
- **Swipe-up / re-HOME drawer** — all-apps + ROMs without reloading the deck.
- **Quick Panel** — Select opens Wi‑Fi / Continue / Theme / Settings by default; Random/Top/Fav/Games/Installed shortcuts enable with **Quick Panel browse shortcuts**.
- **ROM library** — SAF tree grants only (no broad storage permission); offline-first art; hide ROMs from library (Settings unhide); optional SteamGridDB scrape and RetroAchievements credentials.
- **Honest playtime** — sessions pause while the launcher is focused or the device sleeps.
- **Themes** — Ghost, 3DS Teal, OLED Black, Neon; optional custom theme JSON.
- **Settings** — Display & Grid, Apps, Controls (incl. Controller Lab), Library, Stats, System (topology diagnostics), About.
- **Export/import** — full settings + layout JSON for backup or migration.
- **Optional platform packs** — extra platform/player JSON under `docs/platform-packs/` (loadable in Settings).

---

## Displays & topology

Ghost Galleon separates three concepts:

| Role | Meaning |
|------|---------|
| **Primary / interactive** | Where the grid or Game Mode carousel lives (input target). |
| **Companion** | Other dual surface for hero / Now Playing / Perf / pin content. |
| **Secondary home placement** | Physical panel where `CompanionActivity` runs (first non-default display). |

On the **One X Sugar**, Auto/Sugar profile prefers the **bottom** panel for interactive content and the **top** for hero. System `SECONDARY_HOME` is absorbed so swipe-up does not thrash the bottom grid.

**Settings → System** shows the resolved line (e.g. `DUAL · profile=onex-sugar`) plus interactive display mode and device profile overrides. **Single-display** devices skip companion launch and run in SINGLE mode.

---

## ROM library

Ghost Galleon scans ROM folders through Storage Access Framework tree grants — the app requests no broad storage permissions; access comes only from folders you grant.

- **Grant a folder:** Settings → Library → “Add ROM folder” → pick the folder (e.g. microSD `roms` root). The grant persists across reboots.
- **Scan:** granting triggers a scan; “Rescan library” re-walks granted trees off the UI thread. The index is cached as JSON so cold starts do not rescan.
- **Matching:** a file counts as a ROM when its extension fits a platform and it sits under that platform’s folder (tree root or first path segment, case-insensitive — e.g. `roms/snes`, `roms/new-nintendo-3ds`).
- **Add to the grid:** tap a blank “+” → picker’s ROMs section (searchable). ROM tiles use a per-platform placeholder until art is available.
- **Carousel:** Game Mode lists apps and ROMs with filters; Switch updates/DLC are deduped when a base package is present.
- **Launch:** apps and ROMs prefer the non-interactive (launch) display so the deck stays put. RetroArch platforms boot with the matching core path; other players receive a content/file URI with read grant.

| Platform | Player | Launch verified on device |
|---|---|---|
| Game Boy / GBC | RetroArch (Gambatte) | template (same shape as SNES) |
| Game Boy Advance | RetroArch (mGBA) | yes |
| Super Nintendo | RetroArch (Snes9x) | yes |
| Genesis / Mega Drive | RetroArch (Genesis Plus GX) | template (same shape as SNES) |
| Nintendo 64 | RetroArch (Mupen64Plus-Next) | template (same shape as SNES) |
| Nintendo DS | melonDualDS (`LAUNCH_ROM` + `uri` extra) | yes |
| Nintendo 3DS | Azahar (`VIEW` + URI to EmulationActivity) | yes |
| Nintendo Switch | Eden (`TECH_DISCOVERED` + SAF content URI + grant) | yes |
| PSP / PS2 / Dreamcast / GameCube / Wii / Wii U | PPSSPP / NetherSX2 / Flycast / Dolphin / Dolphin / Cemu | template only — no content on device |
| Windows | Winlator | not ROM-launchable (container app) — launch as an app |

RetroArch is path-only (filesystem path + core `.so`). Everyone else receives a URI with `FLAG_GRANT_READ_URI_PERMISSION`; Eden refuses anything else.

---

## Artwork

Offline-first. ROM tiles, carousel cards, and the hero panel use box art when available, platform placeholder otherwise.

- **Local card art (no network):** during a scan, an `images/` (or `media/`, `art/`) folder next to a platform’s ROMs is matched by ROM filename stem (romm layout) and cached under private storage.
- **SteamGridDB (optional):** Settings → Library → API key, then “Download missing artwork”. Cancelable background job; polite rate limit. This is the only feature that uses the app’s single INTERNET permission.
- **RetroAchievements (optional):** username + API key for hero progress lines when configured.

---

## Default controls

| Button | Action |
|---|---|
| D-pad / left stick / HAT | Navigate (auto-repeats when held) |
| Down from last grid row / carousel | Move focus into the dock |
| A / Enter | Launch focused app or ROM |
| Tap | Focus a tile; tap again to launch |
| Long-press | Move / Remove / Cancel on a grid or dock slot |
| B | Back (consumed on the home screen) |
| X | Swap interactive / companion displays (sticky pin) |
| Y | Toggle Grid / Game mode (persists) |
| Start | Settings |
| Select | Quick Panel |
| L1 / R1 | Page |
| Swipe up / re-HOME | All-apps drawer (apps + ROMs) |

All bindings are remappable in Settings → Controls. Controller Lab is available for capture/testing.

On Sugar, the bottom panel is interactive by default; the top shows the companion (Hero by default). X swaps roles. Input remains global across both activities.

---

## Settings map

| Page | Contents |
|------|----------|
| **Display & Grid** | Orientation, hints, mode, themes, wallpaper, grid, companion role, **Browse chrome** (Minimal/Full preset + per-feature toggles) |
| **Apps** | Hidden apps, dock management |
| **Controls** | Haptics, remappable keys, Controller Lab |
| **Library** | ROM folders, rescan, SteamGridDB, RetroAchievements, export/import, platform packs |
| **Stats** | Most played / recently played |
| **System** | Device profile, interactive display mode, topology diagnostics |
| **About** | Version, git SHA, credits, licenses |

---

## Build from source

Requires the Android SDK (`sdk.dir` in `local.properties`). minSdk **26**, target/compileSdk **34**.

```bash
git clone https://github.com/visorcraft/GhostGalleon.git
cd GhostGalleon

./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

**Release** builds (signed from `release-signing.properties` on the build host) are what Obtainium and production installs use:

```bash
./gradlew :app:assembleRelease
adb install -r app/build/outputs/apk/release/app-release.apk
```

Debug and release use different signing keys — switching from one to the other requires uninstall. Export settings first (Settings → Library) if you need to keep layout/library state.

### Host unit tests

```bash
./gradlew :app:testDebugUnitTest
```

Pure modules under `display/`, settings migrations, library browse/stats, and input maps are covered without a device.

---

## Releases & updates

Signed release APKs are on the [GitHub releases page](https://github.com/visorcraft/GhostGalleon/releases). On-device updates are tracked with Obtainium (GitHub releases source).

A one-shot **BlackPearl → Ghost Galleon** package bridge exists for data migration (`-PbridgeBlackPearl=true`); normal users install the `com.visorcraft.ghostgalleon` release only.

---

## Documentation

- [Credits & attribution](CREDITS.md) and [third-party licenses](docs/credits-third-party.md) — also in-app under Settings → About.
- [Dual-paint invariants](docs/dual-paint-invariants.md) — dual-display activity/window rules.
- Example [platform packs](docs/platform-packs/).
- [GitHub releases](https://github.com/visorcraft/GhostGalleon/releases)

---

## License

Ghost Galleon is free and open-source software under the
[GNU General Public License v3.0](LICENSE). Bundled libraries are Apache-2.0;
see [CREDITS.md](CREDITS.md) for the full attribution record.
