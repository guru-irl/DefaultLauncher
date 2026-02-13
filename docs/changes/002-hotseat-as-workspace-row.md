# 002: Hotseat as Workspace Row with Widget Support

## Summary

Makes the hotseat behave as "just another row" in the square grid system: same cell size, same gaps, same icon size. Also enables widget drops into the hotseat. Includes density slider redesign and hotseat column count fix.

## Changes

### DeviceProfile.java

1. **`updateHotseatSizes()`** — New square grid branch (early return): sets `hotseatCellHeightPx = cellHeightPx` and `hotseatBarSizePx = cellHeightPx + hotseatBarBottomSpacePx`. The gap between workspace and hotseat comes from workspace's bottom `cellLayoutPaddingPx`.

2. **`updateIconSize()`** — Sets `hotseatBorderSpace = cellLayoutBorderSpacePx.x` for square grid, so hotseat icon spacing matches workspace.

3. **`deriveSquareGridRows()`** — Moved rounding remainder from bottom padding to top: `cellLayoutPaddingPx.top = adjustedGap + remainder`, `cellLayoutPaddingPx.bottom = adjustedGap`. This ensures exact gap between workspace bottom and hotseat.

4. **`getHotseatLayoutPadding()`** — New `inv.isSquareGrid` branch before the default fallback. Uses `cellLayoutPaddingPx.left/right + mInsets` for left/right padding so hotseat columns align with workspace columns.

### CellLayout.java

5. **`onMeasure()`** — Extended square cell enforcement to include HOTSEAT: `(mContainerType == WORKSPACE || mContainerType == HOTSEAT)`.

6. **`acceptsWidget()`** — Now returns `true` for HOTSEAT when `isSquareGrid` is enabled.

### Workspace.java

7. **`shouldUseHotseatAsDropLayout()`** — Widget drag block now only applies when `!isSquareGrid`, allowing widget drops onto hotseat in square grid mode.

### LoaderCursor.java

8. **`checkItemPlacement()`** — Hotseat occupancy now supports multi-span items. Uses `item.spanX` to check and mark all occupied positions instead of just `item.screenId`.

### InvariantDeviceProfile.java

9. **Density setting: `ListPreference` to `SeekBarPreference` migration** — The `GRID_SPACING` preference was redesigned from a `ListPreference` (which stored raw dp strings like `"8"`, `"16"`, `"24"`) to a `SeekBarPreference` with index-based presets (0=Dense/8dp, 1=Comfortable/16dp, 2=Cozy/24dp). The preference now stores an `int` index instead of a String.

10. **`ClassCastException` migration guard** — Reading the old String-typed `GRID_SPACING` value via `getInt()` throws `ClassCastException`. Added a try/catch in `initGrid()` that catches this, resets to the default index (`1`), and persists the corrected value.

11. **`densityIndexToSpacingDp()`** — New static helper that maps a density preset index to actual spacing dp: `0 → 8`, `1 → 16`, `2 → 24`.

12. **Hotseat column count fix** — Removed the `Math.min(numColumns, numShownHotseatIcons)` cap that was preventing the hotseat from matching workspace column count. Now `numShownHotseatIcons = userColumns` directly. Also expanded `numDatabaseHotseatIcons` via `Math.max(numDatabaseHotseatIcons, userColumns)` to ensure the database can accommodate all user-configured columns.

### SettingsActivity.java

13. **`updateDensitySummary()`** — New helper method on `LauncherSettingsFragment` that maps the density slider index to a human-readable label: `0 → "Dense"`, `1 → "Comfortable"`, `2 → "Cozy"`. Called on init and on each slider change.

14. **Density slider wiring** — `onCreatePreferences()` reads the current `pref_grid_spacing` int value, calls `updateDensitySummary()` to set the initial summary, then wraps the existing `gridChangeListener` to also update the summary on change.

### launcher_preferences.xml

15. **Density preference redesign** — Changed `pref_grid_spacing` from `ListPreference` (with `entries`/`entryValues` arrays) to `SeekBarPreference` with `min=0`, `max=2`, `seekBarIncrement=1`, `defaultValue=1`, `showSeekBarValue=false`. Summary displays the density label instead.

### LauncherPrefs.kt

16. **`GRID_SPACING` type change** — Changed from `backedUpItem("pref_grid_spacing", "16")` (String default) to `backedUpItem("pref_grid_spacing", 1)` (Int default, index-based).

### strings.xml

17. **Density label strings** — Added `grid_spacing_dense`, `grid_spacing_comfortable`, `grid_spacing_cozy` for the density slider summary labels.

## New Files

- `docs/hotseat-architecture.md` — Documents the hotseat subsystem architecture

## Behavior

- Hotseat cell size = workspace cell size (square)
- Hotseat icon size = workspace base icon size (label-independent)
- Hotseat inter-icon gap = workspace inter-cell gap
- Gap between last workspace row and hotseat = inter-cell gap
- Widgets can be dragged into hotseat (occupy multiple columns)
- Column alignment between workspace and hotseat is exact
- Hotseat column count always matches workspace column count
- Density slider shows labeled presets (Dense/Comfortable/Cozy) instead of raw dp values

## Bug Fixes

- **ClassCastException crash** — Upgrading from the old `ListPreference` (String) density setting to the new `SeekBarPreference` (int) caused a crash on `getInt()`. Fixed with a try/catch migration guard in `InvariantDeviceProfile.initGrid()`.
- **Hotseat truncated to fewer columns** — The `Math.min` cap on `numShownHotseatIcons` prevented the hotseat from having the same number of icons as workspace columns. Removed the cap so hotseat always matches the user-configured column count.

## Non-square grid

All changes are gated behind `inv.isSquareGrid` checks. Non-square grid behavior is unchanged.
