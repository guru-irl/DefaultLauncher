# 044: DPI-Independent Grid System

## Summary

Changing Display Size (DPI) in Android settings no longer breaks the grid layout. Previously, all grid dimensions were computed via `pxFromDp()` which uses `DisplayMetrics.density` — when DPI changed, density changed, producing different pixel values even though the physical screen pixel count was identical. Now, on first computation, the grid gap is persisted in pixels. On all subsequent rebuilds the locked pixel values are used directly, bypassing all dp-to-px conversions.

The invalidation key was also changed from `metrics.density` to navbar height, so the grid only recomputes when column count changes or the user switches between gesture and 3-button navigation.

## Design Decisions

**Why persist pixels, not dp?** The entire point is to eliminate density from the pipeline. Persisting dp would still require a dp-to-px conversion at read time, reintroducing the DPI dependency.

**Why derive edge gap and margin from the persisted gap?** The three dp constants have fixed ratios (edge = 3× gap, margin = 4× gap). Instead of persisting three separate values, we persist one (the inter-cell gap) and derive the others. Fewer persisted values = fewer invalidation concerns.

**Why navbar height as the invalidation key?** Switching between gesture nav and 3-button nav changes `mInsets.bottom`, which directly affects available height and row count. DPI changes don't affect physical pixel count, so they shouldn't invalidate. Column count changes are already tracked by `GRID_ROWS_COLUMNS`.

**Icon fills the cell:** `iconSizePx = cellWidthPx` replaces the old pattern of `pxFromDp(iconDp)` followed by a shrink-to-fit check. At 100% ICON_SIZE_SCALE the icon fills the cell completely; the scale preference (0.5–1.0) is applied at render time by ThemeManager.

## Modified Files

### `LauncherPrefs.kt`
- Added `GRID_ROWS`, `GRID_GAP`, `GRID_ROWS_COLUMNS`, `GRID_ROWS_NAV_HEIGHT` preference items
- New pref key `pref_grid_rows_nav_height` orphans the old `pref_grid_rows_density` key, forcing clean recomputation on upgrade

### `InvariantDeviceProfile.java`
- Added `EDGE_TO_GAP_RATIO = 3` and `MARGIN_TO_GAP_RATIO = 4` constants
- Added `persistedGridRows` and `persistedGridGap` fields
- Read block: looks up portrait navbar height from `displayInfo.supportedBounds`, matches against saved `GRID_ROWS_NAV_HEIGHT` + `GRID_ROWS_COLUMNS`
- Persist block: writes rows, gap, columns, and nav height after first computation

### `DeviceProfile.java`
- `getCellLayoutBorderSpace()`: returns locked pixel gap directly when persisted, bypassing `pxFromDp()`
- Added `getSquareGridEdgeGapPx()` and `getSquareGridMinMarginPx()` helper methods to deduplicate the locked-or-fallback pattern (used in 5 call sites)
- Constructor: `cellLayoutPaddingPx` initialized as empty `Rect()` for square grid (overwritten by `deriveSquareGridRows()`); hotseat margin uses helper
- `updateIconSize()`: edge gap uses helper; `iconSizePx = cellWidthPx` (icon fills cell)
- `deriveSquareGridRows()`: locked path uses persisted rows + gap directly; first-computation path unchanged
- All-apps: row spacing expressed as ratio of locked gap (`allAppsRowSpacingDp / INTER_CELL_GAP_DP * persistedGridGap`)
- `updateAllAppsContainerWidth()`: edge gap uses helper (was missed in initial implementation, caught by slop-detect)

## Behavior by Scenario

| Scenario | Recomputes? | Why |
|----------|-------------|-----|
| First install | Yes | No saved prefs |
| Column slider change | Yes | `GRID_ROWS_COLUMNS` mismatch |
| Nav mode change | Yes | `GRID_ROWS_NAV_HEIGHT` mismatch |
| **DPI / Display Size** | **No** | **Locked pixel values used** |
| Font size change | No | Only sp-based text sizes scale |
| Page swipe animation | No | Padding is stable |
