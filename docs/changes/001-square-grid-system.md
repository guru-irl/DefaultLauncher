# 001 - Square Grid System with User-Configurable Columns and Spacing

**Date:** 2026-02-13
**Branch:** dev
**Status:** Implemented, builds successfully

## Summary

Replaces the default AOSP Launcher3 rectangular cell grid with a square cell grid system. Users can configure the number of columns, grid density, toggle workspace icon labels, and app drawer row spacing via Home Settings.

The key insight: fix the number of columns (user's choice), compute a square cell size from available width, then derive how many rows fit vertically. Leftover vertical space is distributed uniformly across all gaps so spacing is consistent everywhere.

## Settings Added

| Setting | Key | Type | Options | Default |
|---------|-----|------|---------|---------|
| Grid columns | `pref_grid_columns` | SeekBar | 4-10 | 5 |
| Grid density | `pref_grid_spacing` | ListPreference | Dense (8dp), Comfortable (16dp), Cozy (24dp) | Comfortable |
| Hide labels | `pref_hide_workspace_labels` | Switch | on/off | off |
| App drawer row spacing | `pref_allapps_row_spacing` | SeekBar | 8-48 dp (step 4) | 24 |

Accessible via: Long-press workspace > Home settings > Grid category

## Files Modified

### Resources
- **`res/xml/launcher_preferences.xml`** - Added `PreferenceCategory` with SeekBarPreference (columns), ListPreference (density), SwitchPreference (labels), SeekBarPreference (app drawer spacing)
- **`res/values/strings.xml`** - Added strings for grid settings category, titles, summaries, and string arrays for density labels/values

### Preferences
- **`src/com/android/launcher3/LauncherPrefs.kt`** - Registered four `ConstantItem` prefs: `GRID_COLUMNS` (int), `GRID_SPACING` (String — ListPreference), `HIDE_WORKSPACE_LABELS` (boolean), `ALLAPPS_ROW_SPACING` (int)

### Grid Configuration (InvariantDeviceProfile)
- **`src/com/android/launcher3/InvariantDeviceProfile.java`**
  - Added fields: `squareGridSpacingDp`, `isSquareGrid`, `hideWorkspaceLabels`, `allAppsRowSpacingDp`
  - In `initGrid()`: reads user prefs, overrides `numColumns`, `numAllAppsColumns`, `numShownHotseatIcons`, `squareGridSpacingDp`
  - Sets `numRows = 20` for database capacity (actual visible rows derived per-orientation by DeviceProfile)
  - Handles migration from old SeekBarPreference (int) to new ListPreference (String) via ClassCastException catch

### Cell Layout (DeviceProfile)
- **`src/com/android/launcher3/DeviceProfile.java`** - Core square cell math:
  - Added `numRows` field — per-orientation visible row count (portrait and landscape derive independently)
  - Added `mSquareGridBaseIconSizePx` — icon size after cell-width fitting but before workspace label adjustments, used for hotseat and all-apps so they stay stable when labels are toggled
  - `getCellLayoutBorderSpace()` - Returns uniform spacing for square grid before responsive/scalable checks
  - `getHorizontalMarginPx()` - Returns 0 for square grid (full width usage)
  - Constructor:
    - Sets `cellLayoutPaddingPx` to the base gap value (uniform edge gaps matching inter-cell gaps)
    - Skips `edgeMarginPx` in workspace top padding and page indicator height in bottom padding for square grid
    - Skips `insetPadding()` for square grid (would clobber edge gap padding since workspace top is 0)
    - Calls `deriveSquareGridRows()` after workspace padding is computed
  - `updateAvailableDimensions()` - Early return for square grid (no scaling pass needed)
  - `updateIconSize()` - Square grid branch:
    - `cellWidthPx = cellHeightPx = (availableWidth - (columns+1) × gap) / columns` — accounts for edge gaps
    - Saves `mSquareGridBaseIconSizePx` before label adjustments
    - Shrinks icon via `IconSizeSteps.getIconSmallerThan()` if needed
    - Fits icon+label via `CellContentDimensions.resizeToFitCellHeight()` when labels shown
    - Hotseat uses `mSquareGridBaseIconSizePx` so size doesn't change with label toggle
  - `deriveSquareGridRows()`:
    - Derives row count using base gap as minimum: `rows = (cellLayoutH - baseGap) / (cellH + baseGap)`
    - Distributes ALL vertical space uniformly: `adjustedGap = (cellLayoutH - rows × cellH) / (rows + 1)`
    - Sets `cellLayoutBorderSpacePx.y = adjustedGap` and `cellLayoutPaddingPx.top/bottom = adjustedGap`
    - No slop — gaps absorb all leftover space, so edge gaps ≈ inter-cell gaps
  - `getCellSize()` - Returns `(cellWidthPx, cellHeightPx)` directly for square grid
  - All-apps override: uses `mSquareGridBaseIconSizePx`, own text size, user-configurable row spacing; cell height = content height (not capped to square since drawer scrolls)

### CellLayout Fixes
- **`src/com/android/launcher3/CellLayout.java`**
  - Constructor: `mCountY = deviceProfile.numRows` (reads per-orientation value instead of shared `inv.numRows`)
  - `onMeasure()`: forces `cellHeight = cellWidth` for workspace when `isSquareGrid` (CellLayout independently computes cell sizes; this ensures they stay square)
- **`src/com/android/launcher3/MultipageCellLayout.java`**
  - Uses `deviceProfile.numRows` instead of `deviceProfile.inv.numRows`

### Settings Propagation
- **`src/com/android/launcher3/settings/SettingsActivity.java`**
  - Null guard in `initPreference()` for PreferenceCategory elements (no key → NPE in switch)
  - Wires `OnPreferenceChangeListener` for all four grid pref keys
  - Posts `InvariantDeviceProfile.onConfigChanged()` via `getListView().post()` to ensure value is persisted before grid reconfigures

## Design Decisions

### Per-orientation row count (`dp.numRows` vs `inv.numRows`)
Both portrait and landscape DeviceProfiles share the same InvariantDeviceProfile singleton. Writing derived rows to `inv.numRows` caused the landscape DP (created second) to overwrite portrait's value. Solution: each DP stores its own `numRows`. `inv.numRows` stays at 20 for database capacity.

### Uniform gap distribution
Instead of fixed gaps with leftover "slop" padding at top/bottom, the baseline dp value (Dense=8, Comfortable=16, Cozy=24) is used only for row derivation. The actual gap is `(totalHeight - rows × cellSize) / (rows + 1)`, evenly spread across top edge, between rows, and bottom edge. This maximizes space usage with no visible padding imbalance.

### Edge gaps
`cellLayoutPaddingPx` is set to the base gap value on all 4 sides, and `insetPadding()` is skipped for square grid. This gives uniform spacing between icons and the workspace boundary, matching inter-cell spacing. Cell width computation subtracts `2 × gap` from available width to account for left/right edges.

### Icon size stability across label toggle
`mSquareGridBaseIconSizePx` saves the icon size after cell-width fitting but before workspace label adjustments. Hotseat and all-apps use this value so their layout doesn't shift when labels are toggled on/off.

### Why early return in `updateAvailableDimensions`?
The AOSP scaling logic compares `getCellLayoutHeightSpecification()` (which uses `numRows`) against actual height and scales down if needed. With numRows=20, it would always try to scale. The square grid doesn't need this — cells are computed to fit the width exactly, and rows are derived to fit the height.

## What's Not Changed
- Database file (`dbFile`) — continues using the selected grid profile's DB
- Hotseat layout — only the icon count is clamped; sizing is independent
- Folder grid — unchanged
- App drawer labels — always shown (only workspace labels can be hidden)
- Widget sizing — follows the square cell dimensions naturally

## Known Limitations
- When column count changes (e.g. 5 to 7), icons at new column positions won't exist. The grid starts empty and the user populates it.
- At very high column counts (8-10) with labels enabled, text becomes very small. `CellContentDimensions` handles this gracefully by shrinking progressively.
- `isSquareGrid` is hardcoded to `true`. A future toggle could gate this if a user wants to revert to AOSP layout.
