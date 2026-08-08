# Dual-paint / black-screen invariants

Device-facing dual-screen HOME can enter a state where **both physical
panels are pure black** while the accessibility tree still shows content.
That means the view hierarchy was built but the GPU buffer never presented
non-black pixels — almost always from **paint thrash**, not missing layout.

Canonical agent rules live in `AGENTS.md` (“Black screens / dual paint
invariants”). Host-tested policy:

| Module | Role |
|---|---|
| `ui/DualPaintPolicy.kt` | Full-paint re-entrancy/coalesce, **deferred retry** when blocked, display-attach paint, absorb silent, drawer open-only, heal debounce |
| `library/RaProgressGate.kt` | One RA fetch per ROM per process; store only if changed; SELECTION notify only |
| `ui/deck/CompanionHeroMetrics.kt` | Scale hero art/title on short secondary panels (no mid-glyph title clip) |
| `state/DeckState.kt` | `setLibraryBrowse` / `select` `force=` for chip re-taps; SELECTION vs SETTINGS tags |
| `library/LibraryBrowse.kt` | Pure All/Recent/Fav/platform/search + `continueKey` (host-tested) |

## Hard rules (do not violate)

1. **One full `setContentView` at a time** — no nested `renderFromState`.
2. **Coalesce full paints** (`MIN_FULL_RENDER_GAP_MS` = 32) — but **never
   drop** a blocked SETTINGS/browse rebuild permanently: schedule a deferred
   retry (`deferredFullRenderDelayMs` / `DEFER` in `GGPaint` log).
3. **Paint after real `displayId`** — multi-display attach race; track
   `paintedForDisplayId`.
4. **`notifyChanged()` (SETTINGS) is expensive** — settings/library only.
5. **Network never drives full deck rebuilds** — RA uses
   `notifySelectionRefresh()` only.
6. **SECONDARY_HOME absorb is silent** — no All-apps, no peer massacre.
7. **Heal is rare** — 2s debounce; launch only if secondary target empty.
8. **No quiet rescan / RA on every resume.**
9. **All-apps drawer is Main-only** — Companion never opens it.
10. **Companion hero title must fit** — use `CompanionHeroMetrics`, never
    fixed 240dp art + 32sp title on short panels.

## Diagnostics

```bash
adb logcat -s GGPaint
```

Every full rebuild logs: count, reason, role, display id, content epoch,
activity class.

If both panels go pure black after thrash:

1. Force-stop Ghost Galleon (or reboot) once to clear stuck SurfaceFlinger
   buffers.
2. Find the policy violation (nested paint, SETTINGS notify from RA, absorb
   opening drawer, heal loop).
3. Fix the violation; do not paper over with more full rebuilds.

## Device regression checklist (before merge of dual-screen work)

- [ ] Cold boot / force-stop start → top deck + bottom hero both non-black
      within ~2s
- [ ] Idle 30s with no input → no All-apps flash, no black flash
- [ ] Swipe-up once → All-apps opens once; second deliberate swipe closes
- [ ] Settings open/close → both panels still painted
- [ ] Long ROM title on bottom hero → full title visible above actions
      (not mid-glyph clip)
- [ ] Optional: RA credentials set → hero RA line can update without full
      deck flash
- [ ] `adb logcat -s GGPaint` during swipe storms shows **no** rapid-fire
      FULL paint spam (coalesce / absorb silence); blocked paints may show
      `DEFER` then a later `FULL`, never permanent stale UI
- [ ] Game Mode: NDS chip → tap a game → **All** clears platform filter
      (full library / no "Platform · NDS" banner)
- [ ] Game Mode: **Continue** jumps to last launched (toast if none); re-tap
      still re-centers when already selected

## Host tests

```bash
./gradlew :app:testDebugUnitTest \
  --tests 'com.visorcraft.ghostgalleon.ui.DualPaintPolicyTest' \
  --tests 'com.visorcraft.ghostgalleon.library.RaProgressGateTest' \
  --tests 'com.visorcraft.ghostgalleon.ui.deck.CompanionHeroMetricsTest' \
  --tests 'com.visorcraft.ghostgalleon.state.DeckStateTest' \
  --tests 'com.visorcraft.ghostgalleon.library.LibraryBrowseTest' \
  --tests 'com.visorcraft.ghostgalleon.library.LibraryDiscoveryTest'
```
