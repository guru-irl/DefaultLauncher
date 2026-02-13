# Hotseat Architecture

Documents the hotseat subsystem across multiple files, reflecting the current state with square grid integration.

## Hotseat.java

- Extends `CellLayout`, acts as a 1D grid: N×1 (portrait) or 1×N (landscape)
- `resetLayout()` sets grid size via `setGridSize(dp.numShownHotseatIcons, 1)`
- `setInsets()` sets the hotseat height from `hotseatBarSizePx` and applies padding from `getHotseatLayoutPadding()`
- Contains QSB as a child view, positioned in `onLayout()`

## DeviceProfile.java — Sizing

### `updateHotseatSizes(int hotseatIconSizePx)`

Four branches (checked in order):
1. **Square grid** (early return): `hotseatCellHeightPx = cellHeightPx`, `hotseatBarSizePx = cellHeightPx + hotseatBarBottomSpacePx`. Gap between workspace and hotseat comes from workspace's bottom `cellLayoutPaddingPx`.
2. **Vertical bar** (landscape phones): `hotseatBarSizePx = icon + edgePadding + workspaceSpace`
3. **Inline QSB**: `hotseatBarSizePx = max(icon, qsbHeight) + bottomSpace`
4. **Standard** (portrait): `hotseatBarSizePx = icon + qsbSpace + qsbHeight + bottomSpace`

Key fields set:
- `hotseatCellHeightPx` — cell height (from `getIconSizeWithOverlap`, or `cellHeightPx` in square grid)
- `hotseatBarSizePx` — total hotseat bar height
- `hotseatBarBottomSpacePx` — bottom margin (set during construction, from resources)
- `hotseatBorderSpace` — gap between hotseat icons (matches `cellLayoutBorderSpacePx.x` in square grid)

### `updateIconSize()` — Square grid hotseat border space

Sets `hotseatBorderSpace = cellLayoutBorderSpacePx.x` when `inv.isSquareGrid` is true, ensuring hotseat inter-icon spacing matches the workspace inter-cell gap.

### `getHotseatLayoutPadding(Context)`

Returns padding `Rect` for the hotseat. Branches (checked in order):
1. **Vertical bar**: edge padding + insets
2. **Fixed landscape**: centered with QSB
3. **Taskbar present**: aligned with nav buttons
4. **Scalable grid**: centered based on QSB width
5. **Square grid**: uses `cellLayoutPaddingPx.left/right + mInsets` for left/right so hotseat columns align exactly with workspace columns
6. **Fallback** (default phones): aligns hotseat icon edges with workspace column edges using width ratio

### `getHotseatBarBottomPadding()`

Returns vertical offset from screen bottom:
- Taskbar/inline QSB: `hotseatBarBottomSpacePx - iconCenteringAdjustment`
- Standard: `hotseatBarSizePx - hotseatCellHeightPx`

### `deriveSquareGridRows()`

Distributes rounding remainder to top padding (`cellLayoutPaddingPx.top = adjustedGap + remainder`) and keeps bottom padding exact (`cellLayoutPaddingPx.bottom = adjustedGap`). This ensures the gap between workspace bottom and hotseat equals the inter-cell gap.

## InvariantDeviceProfile.java — Grid Config

### Square grid hotseat override (in `initGrid()`)

When `isSquareGrid` is true:
- `numShownHotseatIcons = userColumns` (matches workspace column count directly)
- `numDatabaseHotseatIcons = Math.max(numDatabaseHotseatIcons, userColumns)` (ensures DB capacity)
- No `Math.min` cap — hotseat always matches workspace columns

### Density setting

- `GRID_SPACING` preference stores an int index (0=Dense, 1=Comfortable, 2=Cozy)
- `densityIndexToSpacingDp()` maps index to dp: `0 → 8`, `1 → 16`, `2 → 24`
- Migration guard: try/catch for `ClassCastException` when old String values exist

## CellLayout.java

### `resetCellSizeInternal()`

HOTSEAT branch sets `mBorderSpace` to `(hotseatBorderSpace, hotseatBorderSpace)`.

### `acceptsWidget()`

Returns `true` for WORKSPACE, and also for HOTSEAT when `isSquareGrid` is enabled.

### `onMeasure()`

Square grid enforcement (`ch = cw`) applies to both `WORKSPACE` and `HOTSEAT` container types.

## Workspace.java

### `shouldUseHotseatAsDropLayout()`

Returns `false` if `isDragWidget(dragObject)` only when `!isSquareGrid`. In square grid mode, widgets can be dropped onto the hotseat.

## LoaderCursor.java

### `checkItemPlacement()` — Hotseat section

- 1D occupancy: `GridOccupancy[numDatabaseHotseatIcons][1]`
- Position from `item.screenId`, span from `item.spanX`
- Multi-span support: checks and marks all positions from `startPos` to `startPos + spanX - 1`
- Bounds check: `startPos + spanX - 1 >= numDatabaseHotseatIcons` rejects out-of-range items

## SettingsActivity.java

### `updateDensitySummary(Preference, int)`

Maps density slider index to human-readable label string resource:
- `0` → `R.string.grid_spacing_dense` ("Dense")
- `1` → `R.string.grid_spacing_comfortable` ("Comfortable")
- `2` → `R.string.grid_spacing_cozy` ("Cozy")

Called on init and on each slider value change to keep the summary in sync.

## Database Model

- Items use `CONTAINER_HOTSEAT` container
- `screenId` = position index (0-based, left to right)
- `cellX`/`cellY` used for rendering position
- `spanX`/`spanY` — shortcuts default to 1; widgets can span multiple columns in square grid mode
