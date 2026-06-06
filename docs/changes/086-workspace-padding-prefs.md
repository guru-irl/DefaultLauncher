# 086 — Workspace top/bottom padding prefs (One UI 8.5 layout fix)

**Branch:** `refactor/t0.1-search-4param-override`
**Date:** 2026-06-06
**Type:** Feature + bug fix

## Symptom

After updating a Samsung Galaxy to One UI 8.5, the user reported:

1. A noticeably larger gap above the workspace (between status bar and top
   icon row).
2. A larger gap below the dock (between dock icons and bottom of the screen).
3. The **bottom row of icons and widgets disappeared** — items placed on row
   11 (the bottom workspace row) are no longer visible.

Diagnostic logs from the affected device showed:
- `mInsets.top = 129px` (status bar, ≈36.8dp)
- `mInsets.bottom = 53px` (gesture nav)
- Workspace grid = 11 rows × 6 cols, cell = 214×214 px
- `AvailH = 3120 − 129 − 56 = 2935 px` → fits exactly 12 total rows (one
  workspace short of a 13-row layout)
- `slop = 213 px` distributed as `slopTop=106 / slopBottom=107` → exactly
  the two gaps the user saw.

Database inspection (`favorites` table) revealed Screen 4 still held a
widget at `cellY=10, spanY=2` — a 2-row widget on rows 10–11. Row 11 is
out of bounds in the 11-row grid, so the widget is clipped/hidden. That row
was likely visible on One UI 8.1 when the status-bar reported height was
smaller, allowing 12 workspace rows to fit.

## Root cause

`DeviceProfile.deriveSquareGridRows()` used `mInsets.top` (system status
bar) and `mInsets.bottom` (system nav bar) directly in the row-fit math:

```java
int availH = heightPx - mInsets.top - bottomMargin;
int totalUsed = mInsets.top + gridH + bottomMargin;
```

When the OS bumped the reported status-bar height (One UI 8.5 vs 8.1), the
row count dropped from 12 to 11 workspace rows and the slop padding grew —
both effects user-visible.

## Fix

Replace the OS-reported insets with two user-controlled preferences. The
layout becomes deterministic and independent of OS-bar-height drift across
updates.

### New preferences

| Pref key | Default | Range | Meaning |
|---|---|---|---|
| `pref_workspace_top_padding_dp` | 36 dp | 0..120 dp | Reserved space above the workspace grid. |
| `pref_workspace_bottom_padding_dp` | 16 dp | 0..120 dp | Reserved space between dock icons and screen bottom. |

The defaults intentionally mimic the *previous* visual state on typical
devices (status bar ≈36dp, min margin = 16dp), so users who don't touch
the sliders see no behavioral change from this commit.

### Code touch points

| File | Change |
|---|---|
| `LauncherPrefs.kt` | Add `WORKSPACE_TOP_PADDING_DP` (default 36), `WORKSPACE_BOTTOM_PADDING_DP` (default 16), and the lock-invalidation keys `GRID_ROWS_TOP_PAD` / `GRID_ROWS_BOTTOM_PAD`. |
| `InvariantDeviceProfile.java` | Add `workspaceTopPaddingPx` and `workspaceBottomPaddingPx` fields. Compute them in `applyOverrides()`. Replace the (now-removed) nav-height invalidation key in the `match()` check with the two new pad keys, so changing a slider re-derives the row count. |
| `DeviceProfile.java` | In the square-grid path (`updateAvailableDimensions`, `deriveSquareGridRows`), replace `mInsets.top` / `mInsets.bottom` references with the pref values. Update the DEBUG log format to show the pref values explicitly. |
| `LauncherRootView.java` | In `handleSystemWindowInsets`, when `isSquareGrid`, synthesize the dispatched insets as `Rect(0, workspaceTopPaddingPx, 0, workspaceBottomPaddingPx)`. This makes the synthesized insets the source of truth for everything downstream — workspace positioning, hotseat positioning, child view margins via `InsettableFrameLayout`. |
| `res/xml/grids_preferences.xml` | Add two `M3SliderPreference` entries for the new prefs. |
| `res/values/strings.xml` | Add `workspace_top_padding_title` / `workspace_bottom_padding_title`. |
| `settings/GridsFragment.java` | Wire the new sliders' `onTrackingStop` to `InvariantDeviceProfile.INSTANCE.get(...).onConfigChanged(...)` — the same live-rebuild path used by the existing grid-columns slider. **No launcher restart is required for a pref change to take effect.** |

### Backwards compatibility

- Legacy keys `GRID_ROWS_NAV_HEIGHT` / `GRID_ROWS_NAV_MODE` remain in
  `LauncherPrefs.kt` (unused) so existing users' DBs aren't broken; they're
  harmless dead values.
- A fresh launcher install or pm clear writes the new lock keys with the
  default 36 / 16 values; the math compute then matches the prior visible
  layout on most devices.
- Users whose locked-row state was already 11 will stay at 11 until they
  drag a slider, at which point the row count re-derives against the new
  padding values.

## Recovering the lost row

The user can now reclaim the missing row by lowering the top padding:

- **Settings → Grids → Top padding (dp)**: drag from 36 to ~0.
- AvailH grows by ≈84 px (24dp × density 3.5) → the math fits one more
  total row → workspace grows from 11 → 12 rows on the affected Samsung.
- Trade-off: row 0 icons sit slightly under the status bar (edge-to-edge).
  The window already has `FLAG_LAYOUT_NO_LIMITS` so content draws there;
  the status-bar gradient overlays it as expected.

## Tests

`tests-e2e/regression/test_workspace_padding_prefs.py` adds three
regression tests, all passing on `emulator-5554` (Pixel 7 Pro AVD,
Android 17):

1. `test_workspace_padding_pref_defaults_applied` — fresh `pm clear` +
   first launcher boot → DeviceProfile derivation log shows
   `topPad = round(36 × density)` and `bottomPad = round(16 × density)`,
   confirming the defaults flow from LauncherPrefs → IDP → DeviceProfile.
2. `test_workspace_padding_sliders_present_in_settings` — Grids settings
   page renders all three sliders (columns + top pad + bottom pad).
3. `test_workspace_padding_pref_write_takes_effect_on_fresh_boot` —
   writing the prefs XML produces the expected file content (smoke check
   that the wiring keys are correct).

Why no behavioral "change shifts grid" test: the Launcher process is
sticky across `am force-stop` on this emulator build (the
`NotificationListener` system service keeps a reference), and the only
reliable IDP-rebuild path is `pm clear` — which also wipes the very pref
we just wrote. Behavioral validation is therefore manual: change the
slider in Settings, return to home, confirm the workspace re-layouts.
Verified manually on the Samsung phone before commit.

## Verification

- Build: `assembleDebug` passes.
- AVD tests: 3/3 new tests pass; no other tests broken (defaults are
  unchanged on the AVD).
- Samsung (RFCX712ZQDT, One UI 8.5): DeviceProfile log shows
  `topPad=126 (pref) bottomPad=56 (pref)` matching the 36dp/16dp defaults
  at density 3.5. `handleSystemWindowInsets` shows the override
  `incoming=Rect(0, 126 - 0, 56)` replacing the OS-reported
  `Rect(0, 129 - 0, 53)`.
