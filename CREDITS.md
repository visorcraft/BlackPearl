# Credits and Attribution

## Copyright

BlackPearl is © VisorCraft LLC and contributors, distributed under the
[GNU General Public License v3.0](LICENSE).

## Design inspiration

BlackPearl's dual-screen launcher model - an interactive deck on one
panel with a hero preview on the other, plus SECONDARY_HOME handling for
the bottom display - follows the trail blazed by
**[Cocoon](https://github.com/inssekt/CocoonFE)**, the dual-screen shell
for the One X Sugar. Game Mode's card carousel takes its cues from
**[Daijisho](https://github.com/TapiocaFox/Daijishou)** and
**[GameDeck](https://play.google.com/store/apps/details?id=app.gamedeck)**.
The Grid Mode layout is a loving homage to the Nintendo 3DS and Wii home
menus. None of these projects' code is used; they inspired the design.

## Runtime dependencies

BlackPearl relies on the following system components and external
services at execution time. None are bundled - the Android components
are provided by the OS, and the network service is operated by its own
provider. The same list is viewable in-app under
Settings → About → Licenses → "Runtime components".

| Component | License | Project |
| --------- | ------- | ------- |
| Android 14 platform framework (API 34) | Apache-2.0 | https://source.android.com |
| Android Runtime (ART) | Apache-2.0 | https://source.android.com |
| Storage Access Framework (SAF / DocumentsUI) | Apache-2.0 | https://source.android.com |
| System media codecs (animated WebP / GIF) | Apache-2.0 | https://source.android.com |
| SteamGridDB Web API (optional, user-supplied key) | service terms | https://www.steamgriddb.com |
| Emulator apps (RetroArch, Azahar, Eden, melonDualDS, Dolphin, NetherSX2, PPSSPP, Flycast, Cemu, Winlator) | various - separate installs, never bundled | their respective projects |

## Gradle dependencies

BlackPearl's only direct dependencies are the Kotlin standard library
and three AndroidX libraries; the rest are AndroidX/Kotlin transitives.
Every bundled library is Apache-2.0. The full list with exact resolved
versions is bundled in-app (Settings → About → Licenses →
"Third-party") and mirrored at
[`docs/credits-third-party.md`](docs/credits-third-party.md).

| Library | Version | License | Project |
| ------- | ------- | ------- | ------- |
| `kotlin-stdlib` | 1.9.24 | Apache-2.0 | [JetBrains/kotlin](https://github.com/JetBrains/kotlin) |
| `androidx.appcompat:appcompat` | 1.7.0 | Apache-2.0 | [AndroidX](https://developer.android.com/jetpack/androidx) |
| `androidx.recyclerview:recyclerview` | 1.3.2 | Apache-2.0 | [AndroidX](https://developer.android.com/jetpack/androidx) |
| `androidx.documentfile:documentfile` | 1.0.1 | Apache-2.0 | [AndroidX](https://developer.android.com/jetpack/androidx) |

Test-only dependencies (JUnit 4.13.2, org.json 20240303) are never
bundled in the app.

## License compatibility

GPL-3.0-only is compatible with every license listed above:
Apache-2.0 is permissive and combines freely with GPL-3.0.

## Reporting attribution gaps

If you find code or assets in this repository that we have failed to
credit, please open an issue at
<https://github.com/visorcraft/BlackPearl/issues> and we will correct
the record.
