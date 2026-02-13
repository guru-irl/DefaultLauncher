# 004: Opinionated Defaults and Separate Gap Constants

**Date:** 2026-02-13
**Branch:** dev
**Status:** Implemented, builds successfully

## Summary

Two categories of changes in one commit:

1. **Opinionated defaults** — Remove unused settings and UI elements to simplify the launcher toward a clean, label-free, dot-free home screen.
2. **Separate gap constants** — Replace the single `squareGridSpacingDp` value with three independent constants controlling inter-cell gaps, edge gaps, and top/bottom margins. Fixes row derivation to treat hotseat as a grid row and correctly accounts for the AOSP view inset system.

## Part 1: Opinionated Defaults

### Changes

| Change | File | Detail |
|--------|------|--------|
| Disable QSB on first screen | `build.gradle` | `QSB_ON_FIRST_SCREEN` set to `false` |
| Hide page indicator dots | `res/layout/launcher.xml` | `PageIndicatorDots` visibility set to `gone` |
| Force page indicator alpha to 0 | `WorkspaceStateTransitionAnimation.java` | Ignores `WORKSPACE_PAGE_INDICATOR` flag, always animates to alpha 0 |
| Remove "Allow rotation" setting | `res/xml/launcher_preferences.xml` | `pref_allowRotation` SwitchPreference removed from XML |
| Remove "Grid density" slider | `res/xml/launcher_preferences.xml` | `pref_grid_spacing` SeekBarPreference removed |
| Remove "Hide workspace labels" toggle | `res/xml/launcher_preferences.xml` | `pref_hide_workspace_labels` SwitchPreference removed |
| Hardcode labels hidden | `InvariantDeviceProfile.java` | `hideWorkspaceLabels = true` (was pref-driven) |
| Remove density prefs | `LauncherPrefs.kt` | `GRID_SPACING` and `HIDE_WORKSPACE_LABELS` constants deleted |
| Remove density strings | `res/values/strings.xml` | `grid_spacing_*` and `hide_workspace_labels_*` strings deleted |
| Remove density slider code | `SettingsActivity.java` | `updateDensitySummary()` and density slider wiring removed |
| Remove density helper | `InvariantDeviceProfile.java` | `densityIndexToSpacingDp()` method deleted |

### Rationale

- **QSB**: The Google search bar on the first screen is a default AOSP feature that takes up valuable space. Disabled for a cleaner look.
- **Page dots**: With the square grid maximizing screen usage, page indicator dots are visual noise. Hidden both in XML and in the animation system.
- **Rotation**: The square grid is designed for portrait. Landscape has known issues with the square grid layout. Removing the toggle simplifies settings.
- **Grid density**: Replaced by the three-constant system (Part 2). A single slider mapping to 8/16/24dp presets is too coarse now that inter-cell, edge, and margin gaps are independent.
- **Hide labels**: Labels are always hidden in the square grid. The toggle added complexity for a feature that's always on.

## Part 2: Separate Gap Constants

### Constants

Defined in `InvariantDeviceProfile.java`:

| Constant | Value | Purpose |
|----------|-------|---------|
| `SQUARE_GRID_INTER_CELL_GAP_DP` | 4dp | Space between adjacent cells on both axes |
| `SQUARE_GRID_EDGE_GAP_DP` | 12dp | Space between outermost cells and screen edges (left/right) |
| `SQUARE_GRID_MIN_TB_MARGIN_DP` | 16dp | Minimum space below hotseat to screen bottom |

### How They're Used

**Cell width** (`updateIconSize()`):
```
availW = availableWidthPx - 2 * edgeGap
cellWidthPx = (availW - (numColumns - 1) * interGap) / numColumns
cellHeightPx = cellWidthPx  // square
```

**CellLayout initial padding** (constructor):
```
cellLayoutPaddingPx = edgeGap on all sides (overridden by deriveSquareGridRows)
```

**Hotseat bottom margin** (constructor):
```
hotseatBarBottomSpacePx = max(MIN_TB_MARGIN, mInsets.bottom)
hotseatBarSizePx = cellHeightPx + hotseatBarBottomSpacePx
```

### Row Derivation (`deriveSquareGridRows`)

The hotseat is treated as one row of the grid. Row derivation uses the full screen height:

```
availH = heightPx - mInsets.top - hotseatBarBottomSpacePx
totalRows = (availH + interGap) / (cellHeightPx + interGap)
numRows = totalRows - 1  // subtract hotseat row
```

Gap is capped so the horizontal grid fits:
```
maxGap = (cellLayoutW - numCols * cellW - 2 * edgeGap) / (numCols - 1)
gap = min(adjustedGap, maxGap)
```

Slop (excess pixels) is split between top padding and hotseat bottom margin.

### Workspace Padding Fix

**Problem discovered**: The Workspace view does NOT implement `Insettable`. When `InsettableFrameLayout` dispatches insets to DragLayer's children, Workspace gets `mInsets` applied as **layout margins** (not view padding). An earlier attempt set `workspacePadding.top = mInsets.top`, which double-counted the status bar inset (once as margin, once as padding), making `getCellLayoutHeight()` return 2736px while the physical CellLayout was only ~2570px. This caused the bottom row to render behind the hotseat.

**Fix**: The square grid branch in `updateWorkspacePadding()` sets:
```java
padding.set(0, 0, 0, hotseatBarSizePx - mInsets.bottom);
```
- `top = 0` because `mInsets.top` is already applied as a margin
- `bottom = hotseatBarSizePx - mInsets.bottom` because `mInsets.bottom` is already applied as a margin

This ensures `getCellLayoutHeight() = availableHeightPx - workspacePadding.y` matches the physical CellLayout view dimensions.

### Model State Fix

Added `numShownHotseatIcons` to `InvariantDeviceProfile.toModelState()` so changes to hotseat column count trigger a proper grid rebuild.

## Files Modified

| File | Changes |
|------|---------|
| `build.gradle` | `QSB_ON_FIRST_SCREEN = false` |
| `res/layout/launcher.xml` | Page indicator `visibility="gone"` |
| `res/values/strings.xml` | Removed density/label strings |
| `res/xml/launcher_preferences.xml` | Removed rotation, density, label prefs |
| `DeviceProfile.java` | Separate gap constants in constructor, `updateIconSize()`, new `deriveSquareGridRows()`, new `updateWorkspacePadding()` square grid branch |
| `InvariantDeviceProfile.java` | Three gap constants, hardcoded `hideWorkspaceLabels`, removed density helper, model state fix |
| `LauncherPrefs.kt` | Removed `GRID_SPACING`, `HIDE_WORKSPACE_LABELS` |
| `WorkspaceStateTransitionAnimation.java` | Page indicator alpha forced to 0 |
| `SettingsActivity.java` | Removed density slider code |

## Behavior

On a Pixel 7 Pro (1440x3120, density 3.5) with 6 columns:
- Cell size: 214x214px (61dp)
- Inter-cell gap: 14px (4dp)
- Edge gap: 42px (12dp)
- 12 workspace rows + 1 hotseat row = 13 total rows
- Gap between last workspace row and hotseat = inter-cell gap (4dp)
- Hotseat bottom margin: ~57px (16dp + slop)
- NxN widgets are perfectly square (same gap on both axes)
