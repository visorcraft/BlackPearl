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
  <img src="https://img.shields.io/badge/version-0.4.1-informational" alt="0.4.1" />
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

Ghost Galleon is a home-screen replacement for Android handhelds. It is **built and QA’d on the One X Sugar** (Android 14, top 2160×1080 + bottom 1240×1080) and also runs on **single-display** devices via Auto topology.

On dual-screen hardware one panel hosts the interactive deck (grid or carousel) while the other shows a companion surface (hero preview, Now Playing, Perf HUD, or a pinned app). Ghost Galleon holds the Android **HOME** role (and **SECONDARY_HOME** on dual panels), is designed for full gamepad control, and supports swipe-up all-apps.

### Highlights

- **Grid Mode** — curated 3DS/Wii-style icon grid with dock, blank “+” slots, long-press Move/Remove, favorites, folders, pin/unpin to dock. Optional deck clock/battery (off by default).
- **Game Mode** — card carousel with a **minimal** default chip bar (All / Recent / Continue / Fav + platforms + Search/Select). Counts on chips, deep search, details sheets, multi-select bulk actions, and long-press menus for history/sort/related/collections. Power-user rails (Installed, Games, Top, Today, Week, Month, A–Z, New, Random, genre/developer/year chips, letter jump), launchable-only ROMs, Resume chip, clock/battery, and Quick Panel browse shortcuts are **opt-in** under Settings → Display & Grid → Browse chrome (Minimal / Custom / Full).
- **Portable display topology** — interactive vs companion vs launch from `DisplayManager` (no hard-coded 0/1). Profiles: Auto, One X Sugar, Generic dual, Single. Swap/Settings icons sit on the **physically larger** panel in DUAL.
- **Live screen swap** — X (default) swaps interactive and companion roles with a sticky pin.
- **Companion roles** — Hero, Now Playing, Perf HUD, or pinned app on the non-interactive panel.
- **Global input** — gamepad, d-pad, stick, and touch route to the interactive deck regardless of focus; held directions auto-repeat.
- **Swipe-up / re-HOME drawer** — all-apps + ROMs without reloading the deck.
- **Quick Panel** — Select opens Wi‑Fi / Continue / Theme / Settings by default; extra rail shortcuts when browse chrome enables them.
- **ROM library** — SAF tree grants only; offline-first art; hide ROMs; optional SteamGridDB scrape and RetroAchievements.
- **Honest playtime** — sessions pause while the launcher is focused or the device sleeps.
- **Themes** — Ghost, 3DS Teal, OLED Black, Neon; optional custom theme JSON.
- **Settings** — Display & Grid, Apps, Controls (Controller Lab), Library, Stats, System (topology diagnostics), About.
- **Export/import** — full settings + layout JSON.
- **Optional platform packs** — extra platform/player JSON under `docs/platform-packs/` (loadable in Settings).

---

## Displays & topology

| Role | Meaning |
|------|---------|
| **Primary / interactive** | Grid or Game Mode (input target). |
| **Companion** | Other dual surface: hero / Now Playing / Perf / pin. |
| **Secondary home placement** | Panel where `CompanionActivity` runs (first non-default display). |
| **Larger display** | Physically largest panel — hosts Swap + Settings chrome in DUAL. |

On the **One X Sugar**, Auto/Sugar prefers the **bottom** panel for interactive content and the **top** for hero. System `SECONDARY_HOME` is absorbed so swipe-up does not thrash the deck.

**Settings → System** shows the resolved topology (e.g. `primary=1 companion=0 launch=0 secondaryHome=1 larger=0`) plus hardware readings. **Single-display** devices run in SINGLE mode.

---

## ROM library

Scans use Storage Access Framework tree grants only — no broad storage permission.

- **Grant:** Settings → Library → “Add ROM folder”.
- **Scan:** grant triggers a scan; “Rescan library” walks trees off the UI thread. Index is cached as JSON.
- **Matching:** extension + platform folder name (tree root or first path segment, case-insensitive).
- **Grid:** tap “+” → searchable picker (apps + ROMs).
- **Carousel:** Game Mode lists apps and ROMs with filters; Switch updates/DLC are deduped when a base package is present.
- **Launch:** prefers the non-interactive (launch) display so the deck stays put.

| Platform | Player | Launch verified on device |
|---|---|---|
| Game Boy / GBC | RetroArch (Gambatte) | template (same shape as SNES) |
| Game Boy Advance | RetroArch (mGBA) | yes |
| Super Nintendo | RetroArch (Snes9x) | yes |
| Genesis / Mega Drive | RetroArch (Genesis Plus GX) | template |
| Nintendo 64 | RetroArch (Mupen64Plus-Next) | template |
| Nintendo DS | melonDualDS | yes |
| Nintendo 3DS | Azahar | yes |
| Nintendo Switch | Eden | yes |
| PSP / PS2 / Dreamcast / GameCube / Wii / Wii U | PPSSPP / NetherSX2 / Flycast / Dolphin / Cemu | template only |
| Windows | Winlator | app launch only (not ROM-launchable) |

---

## Artwork

Offline-first. Tiles, carousel cards, and hero use box art when available.

- **Local:** `images/` / `media/` / `art/` next to ROMs (romm layout), cached privately.
- **SteamGridDB (optional):** Settings → Library → API key → “Download missing artwork” (only INTERNET use).
- **RetroAchievements (optional):** username + API key for hero progress when configured.

---

## Default controls

| Button | Action |
|---|---|
| D-pad / left stick / HAT | Navigate (auto-repeat when held) |
| Down from last grid row / carousel | Focus dock |
| A / Enter | Launch |
| Tap | Focus; tap again to launch |
| Long-press | Grid/dock: Move / Pin·Unpin / Remove; Game Mode: Details / collections / pin / stats |
| B | Back |
| X | Swap interactive / companion |
| Y | Toggle Grid / Game mode |
| Start | Settings |
| Select | Quick Panel |
| L1 / R1 | Page |
| Swipe up / re-HOME | All-apps drawer |

Remap everything under Settings → Controls. Controller Lab is available for capture/testing.

---

## Settings map

| Page | Contents |
|------|----------|
| **Display & Grid** | Orientation, hints, default mode, themes, wallpaper, device profile, interactive display, companion role, **Browse chrome** (Minimal / Custom / Full + per-feature toggles), grid layout |
| **Apps** | Hidden apps, dock management |
| **Controls** | Haptics, remappable keys, Controller Lab |
| **Library** | ROM folders, Hidden ROMs, rescan, SteamGridDB, RetroAchievements, export/import, platform packs |
| **Stats** | Most played / recently played |
| **System** | Topology (primary / companion / launch / secondaryHome / larger), hardware readings |
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

**Release** builds (signed from `release-signing.properties` on the build host):

```bash
./gradlew :app:assembleRelease
adb install -r app/build/outputs/apk/release/app-release.apk
```

Debug and release use different signing keys — switching requires uninstall. Export settings first (Settings → Library) to keep layout/library state.

### Host unit tests

```bash
./gradlew :app:testDebugUnitTest
```

Pure modules under `display/`, settings migrations, library browse/stats, and input maps are covered without a device.

---

## Releases & updates

Signed release APKs are on the [GitHub releases page](https://github.com/visorcraft/GhostGalleon/releases). On-device updates use Obtainium (GitHub releases source).

A one-shot **BlackPearl → Ghost Galleon** package bridge exists for data migration (`-PbridgeBlackPearl=true`); normal users install the `com.visorcraft.ghostgalleon` release only.

---

## Documentation

- [Credits & attribution](CREDITS.md) and [third-party licenses](docs/credits-third-party.md) — also in-app under Settings → About.
- Example [platform packs](docs/platform-packs/).
- [GitHub releases](https://github.com/visorcraft/GhostGalleon/releases)

---

## License

Ghost Galleon is free and open-source software under the
[GNU General Public License v3.0](LICENSE). Bundled libraries are Apache-2.0;
see [CREDITS.md](CREDITS.md) for the full attribution record.
