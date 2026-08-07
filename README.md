<p align="center">
  <img src="static/icon-512.png" alt="Ghost Galleon logo" width="250" />
</p>

<h1 align="center">Ghost Galleon</h1>
<p align="center"><i>Ghost Galleon Dual Screen Launcher</i></p>

<p align="center">
  <b>A dual-screen Android launcher built for the One X Sugar handheld.</b>
  <br />
  Grid Mode (3DS/Wii-style icon grid + dock) and Game Mode (card carousel) across two displays,
  <br />
  with live screen swap, gyro-aware orientation, remappable gamepad input, and a SAF-scanned ROM library.
</p>

<p align="center">
  <a href="https://github.com/visorcraft/GhostGalleon/releases/latest"><img src="https://img.shields.io/github/v/release/visorcraft/GhostGalleon?sort=semver" alt="Latest release" /></a>
  <img src="https://img.shields.io/badge/platform-Android%2014-3ddc84?logo=android&amp;logoColor=white" alt="Android 14" />
  <img src="https://img.shields.io/badge/language-Kotlin-7f52ff?logo=kotlin&amp;logoColor=white" alt="Kotlin" />
  <img src="https://img.shields.io/badge/API-34-0b57a4" alt="API 34" />
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
      <sub><b>Hero panel</b> - the non-interactive display previews the current selection.</sub>
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

Ghost Galleon (Ghost Galleon Dual Screen Launcher) is a home-screen replacement for dual-screen Android handhelds,
built and tuned on the One X Sugar (Android 14, top 2160×1080 + bottom
1240×1080). One display runs the interactive deck - the grid or the carousel -
while the other shows a hero preview of whatever is selected. It holds the
Android HOME role, starts at boot, and is designed to be driven entirely from
the built-in gamepad.

Ghost Galleon can:

- Run two launcher modes: **Grid Mode** (3DS/Wii-style icon grid with a dock)
  and **Game Mode** (Daijisho/GameDeck-style card carousel), switchable at any
  time with a persisted preference.
- Curate the grid your way: blank "+" slots, a searchable app/ROM picker,
  long-press Move/Remove, and 3DS-style swap-reorder. Nothing appears in the
  grid unless you put it there.
- Dock favorite apps for one-press access from either mode; empty dock slots
  show "+" placeholders that open the picker.
- Swap which display is interactive with a single button (X by default) -
  the deck and hero panel trade screens instantly.
- Navigate by gamepad, d-pad, stick, or touch. Input is global: keys and
  sticks route to the interactive display no matter which window has focus.
  Held directions auto-repeat (1s initial delay, 350ms repeat).
- Launch apps and games onto the **other** display, so the deck stays put -
  single-screen apps like browsers get the full second panel.
- Stay right-side-up: gyro/orientation awareness keeps both panels aligned
  with how you're holding the device.
- Hide the Android status bar on launcher screens for full-height layouts,
  with custom wallpapers, optional icon labels, accent theming, and a
  radial-glow hero background.
- Remap every gamepad binding in Settings.
- Scan ROM folders through Storage Access Framework grants and launch them
  directly in the right emulator (see **ROM library** below).
- Fetch box art offline-first: local card art is matched automatically, and an
  optional SteamGridDB key enables a polite background scraper - the only
  feature that uses the app's single INTERNET permission.
- Export/import all settings and layout as JSON for backup or migration.

## ROM library

Ghost Galleon scans ROM folders through Storage Access Framework tree grants -
the app requests no storage permissions; ROM access comes only from folders
you explicitly grant.

- **Grant a folder:** Settings → Library → "Add ROM folder" → pick the folder
  (e.g. the microSD `roms` root) in the system picker. The grant is persisted
  across reboots.
- **Scan:** granting triggers a scan; "Rescan library" re-walks all granted
  trees off the UI thread and toasts the count. The index is cached as JSON,
  so cold starts never rescan.
- **Matching:** a file counts as a ROM when its extension fits a platform and
  it sits under that platform's folder (tree root or first path segment,
  case-insensitive - e.g. `roms/snes`, `roms/new-nintendo-3ds`).
- **Add to the grid:** tap a blank "+" slot → the picker's ROMs section
  (searchable). ROM tiles get a deterministic per-platform placeholder color
  until artwork is available. Move/Remove work exactly like app tiles.
- **Carousel:** Game mode lists all scanned ROMs after the curated apps,
  sorted by platform then name. Switch updates/DLC are deduped: when a base
  package is present, its update/DLC files stay in the library but are hidden
  from the carousel and picker.
- **Launch:** ROMs launch on the non-interactive display, like apps. RetroArch
  platforms (GB/GBC/GBA/SNES/Genesis/N64) boot straight into the game with the
  right core; other platforms start their emulator's emulation activity with
  the ROM URI and a read grant.

## Artwork

Offline-first. ROM tiles, carousel cards, and the hero panel render box art
when available, the platform placeholder otherwise.

- **Local card art (no network):** during a scan, an `images/` (or `media/`,
  `art/`) folder next to a platform's ROMs is matched by ROM filename stem
  (romm layout) and cached downscaled under the app's private storage.
- **SteamGridDB (optional):** paste an API key in Settings → Library →
  "SteamGridDB API key" (long-press the row to clear it), then tap
  "Download missing artwork". The job only touches ROMs that have no local
  art, runs cancelably in the background with live progress, and is polite
  (~5 requests/second). This is the only feature that uses the app's single
  INTERNET permission; everything else works fully offline.

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
| PSP / PS2 / Dreamcast / GameCube / Wii / Wii U | PPSSPP / NetherSX2 / Flycast / Dolphin / Dolphin / Cemu | template only - no content on device |
| Windows | Winlator | not ROM-launchable (container app) - launch it as an app |

RetroArch is path-only: it receives the raw filesystem path plus the core
`.so`. Everyone else receives a content or file URI with
`FLAG_GRANT_READ_URI_PERMISSION`; Eden refuses anything else.

## Default controls

| Button | Action |
|---|---|
| D-pad / left stick / HAT | Navigate (auto-repeats when held) |
| Down from last grid row / carousel | Move focus into the dock |
| A / Enter | Launch focused app or ROM |
| Tap | Focus a tile; tap again to launch |
| Long-press | Move / Remove / Cancel on a grid or dock slot |
| B | Back (consumed on the home screen) |
| X | Swap screens - moves the interactive deck to the other display |
| Y | Toggle Grid/Game mode (persists) |
| Start | Settings |
| L1 / R1 | Page |

All bindings are remappable in Settings.

The bottom display is the interactive one by default: it shows the grid or
carousel while the top display shows a hero preview of the selection. X swaps
which display is interactive. Input is global - gamepad and touch work no
matter which display currently holds focus. Button hints are shown on both
screens.

## Build from source

Requires the Android SDK (set `sdk.dir` in `local.properties`).

```bash
git clone https://github.com/visorcraft/GhostGalleon.git
cd Ghost Galleon

./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Run the host unit tests:

```bash
./gradlew :app:testDebugUnitTest
```

## Releases & updates

Signed release APKs are published on the
[GitHub releases page](https://github.com/visorcraft/GhostGalleon/releases).
On-device updates are tracked via Obtainium (source: GitHub releases).
Note: release builds are signed with the release key (not the debug key) -
switching from a debug install requires uninstall; use Settings → Library →
Export/Import settings to carry your layout and library across.

## Documentation

- [Credits & attribution](CREDITS.md) and
  [third-party licenses](docs/credits-third-party.md) - also bundled in-app
  under Settings → About.
- [GitHub releases](https://github.com/visorcraft/GhostGalleon/releases)

## License

Ghost Galleon is free and open-source software, distributed under the
[GNU General Public License v3.0](LICENSE). Every bundled library is
Apache-2.0; see [CREDITS.md](CREDITS.md) for the full attribution record.
