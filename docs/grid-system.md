# Grid System

How the launcher computes the size and position of every cell on the home screen and app drawer, from XML configuration down to pixels on screen.

---

## Architecture Overview

The grid system has three layers:

```
device_profiles.xml          (static XML)
        |
        v
InvariantDeviceProfile       (parsed config, survives rotation)
        |
        v
DeviceProfile                (pixel-level calculations, per-orientation)
        |
        v
CellLayout / RecyclerView    (actual View measurement and layout)
```

**InvariantDeviceProfile** holds values that don't change between portrait and landscape -- the grid dimensions (5 columns, 5 rows), icon size in DP, border spacing in DP. It's a singleton, created once at app startup.

**DeviceProfile** converts those DP values into pixels for the current window size, orientation, and insets. A new one is built every time the window configuration changes (rotation, multi-window, fold/unfold).

**CellLayout** is the `ViewGroup` that draws the workspace grid. It receives cell dimensions from DeviceProfile and positions children (icons, widgets) in a grid.

The **app drawer** (all-apps) uses a `RecyclerView` with a `GridLayoutManager` instead of `CellLayout`, but its column count and cell dimensions still come from DeviceProfile.

---

## Grid Profiles: `device_profiles.xml`

**File:** [`res/xml/device_profiles.xml`](../res/xml/device_profiles.xml)

This XML file defines every available grid configuration. Each `<grid-option>` is a complete grid specification, and each nested `<display-option>` is a calibration point for a specific screen size.

### Phone grids (current configurations)

| Grid Name | Rows | Columns | Folder | Hotseat | All-Apps Cols | Target |
|-----------|------|---------|--------|---------|---------------|--------|
| `3_by_3` | 3 | 3 | 2x3 | 3 | 3 (default) | Small phones |
| `4_by_4` | 4 | 4 | 3x4 | 4 | 4 (default) | Normal phones |
| `5_by_5` | 5 | 5 | 4x4 | 5 | 5 (default) | Large phones |
| `6_by_5` | 5 | 6 | 3x3 | 6 | 6 | Tablets |
| `fixed_landscape_mode` | 3 | 7-8 | 2x3 | 4 | 8 | Phones in forced landscape |

### How a grid is selected

At startup, `InvariantDeviceProfile.initGrid()` ([`InvariantDeviceProfile.java:315`](../src/com/android/launcher3/InvariantDeviceProfile.java)) does:

1. **Parse** all `<grid-option>` elements from `device_profiles.xml`
2. **Filter** by `deviceCategory` (phone/tablet/multi_display) and grid type flags
3. **Match** by name if a specific grid was previously saved in SharedPreferences (`LauncherPrefs.GRID_NAME`)
4. **Interpolate** across `<display-option>` elements using inverse-distance-weighted interpolation against the device's actual screen size in DP

The interpolation means icon sizes and spacing are smoothly scaled between calibration points rather than jumping between fixed presets. Three nearest neighbors are used with a weight power of 5 (heavily favoring the closest match).

### Grid option attributes

These are the key attributes on `<grid-option>`:

| Attribute | Type | Meaning |
|-----------|------|---------|
| `numRows` | int | Workspace rows |
| `numColumns` | int | Workspace columns |
| `numFolderRows` | int | Rows inside a folder |
| `numFolderColumns` | int | Columns inside a folder |
| `numHotseatIcons` | int | Icons in the dock |
| `numAllAppsColumns` | int | Columns in app drawer (defaults to `numColumns`) |
| `dbFile` | string | SQLite database filename for this grid's layout data |
| `deviceCategory` | flags | `phone`, `tablet`, `multi_display` (which devices can use this grid) |
| `isScalable` | bool | Use scalable grid mode (tablet-style, with explicit cell sizes) |
| `defaultLayoutId` | ref | XML resource with default icon placement |

### Display option attributes

Each `<display-option>` calibrates the grid for a specific screen size:

| Attribute | Meaning |
|-----------|---------|
| `minWidthDps` / `minHeightDps` | Screen size this calibration targets (shortest dimension in DP) |
| `iconImageSize` | Icon size in DP |
| `iconTextSize` | Label text size in SP |
| `minCellWidth` / `minCellHeight` | Minimum cell size in DP (scalable grids only) |
| `borderSpaceHorizontal` / `borderSpaceVertical` | Gap between cells in DP (scalable grids only) |
| `horizontalMargin` | Left/right workspace padding in DP (scalable grids only) |
| `allAppsCellWidth` / `allAppsCellHeight` | App drawer cell size in DP |
| `allAppsBorderSpace*` | App drawer gap between cells in DP |
| `hotseatBarBottomSpace` | Space below the dock in DP |

For non-scalable phone grids (3x3, 4x4, 5x5), only `iconImageSize`, `iconTextSize`, and the all-apps values are specified. Cell sizes are computed dynamically from available space. For scalable grids (tablets), explicit `minCellWidth`/`minCellHeight` and `borderSpace` values are provided.

---

## InvariantDeviceProfile

**File:** [`src/com/android/launcher3/InvariantDeviceProfile.java`](../src/com/android/launcher3/InvariantDeviceProfile.java)

After parsing `device_profiles.xml`, the following fields are populated:

```
numRows = 5                         // workspace row count
numColumns = 5                      // workspace column count
numAllAppsColumns = 5               // app drawer column count
numShownHotseatIcons = 5            // dock icon count
numFolderRows[4]                    // folder rows per orientation index
numFolderColumns[4]                 // folder columns per orientation index
iconSize[4]                         // icon size (DP) per orientation index
iconTextSize[4]                     // label size (SP) per orientation index
minCellSize[4]                      // PointF(width, height) in DP, scalable grids only
borderSpaces[4]                     // PointF(x, y) gap in DP, scalable grids only
allAppsCellSize[4]                  // PointF(width, height) in DP
allAppsBorderSpaces[4]              // PointF(x, y) gap in DP
horizontalMargin[4]                 // workspace side padding in DP
```

The `[4]` arrays are indexed by orientation:

| Index | Constant | Meaning |
|-------|----------|---------|
| 0 | `INDEX_DEFAULT` | Portrait |
| 1 | `INDEX_LANDSCAPE` | Landscape |
| 2 | `INDEX_TWO_PANEL_PORTRAIT` | Foldable open, portrait |
| 3 | `INDEX_TWO_PANEL_LANDSCAPE` | Foldable open, landscape |

### Database per grid

Each grid option has its own SQLite database file (e.g., `launcher_4_by_4.db`, `launcher_5_by_5.db`). This is critical: **changing the grid row/column count means a different database is used**, because icon positions are stored as (cellX, cellY) in the database. A 5x5 grid's positions don't make sense in a 4x4 grid.

The current grid state is persisted by [`DeviceGridState`](../src/com/android/launcher3/model/DeviceGridState.java) in SharedPreferences as `"columns,rows"` format.

---

## DeviceProfile: DP to Pixels

**File:** [`src/com/android/launcher3/DeviceProfile.java`](../src/com/android/launcher3/DeviceProfile.java)

DeviceProfile takes InvariantDeviceProfile's DP values and converts them to pixel values for the current screen. Here are the key fields and how they're computed.

### Grid modes

There are three calculation paths depending on the grid type:

| Mode | Condition | How cell size is determined |
|------|-----------|----------------------------|
| **Responsive** | Grid has `workspaceSpecsId` XML | Cell size from responsive spec XML (percentage-based) |
| **Scalable** | `isScalable = true` | Cell size from `minCellSize` in DP, converted to pixels |
| **Default** (phones) | Neither of above | Cell size computed from icon size + padding |

For the phone grids (3x3, 4x4, 5x5), the **default** mode is used.

### Workspace cell size: default mode (phones)

In default mode ([`DeviceProfile.java:1343-1358`](../src/com/android/launcher3/DeviceProfile.java)), cell dimensions are derived from icon size:

```
cellWidthPx  = iconSizePx + iconDrawablePaddingPx
cellHeightPx = iconSizeWithOverlap + iconDrawablePaddingPx + textHeight
```

Where:
- `iconSizePx` = icon bitmap size in pixels (from `iconImageSize` DP value)
- `iconDrawablePaddingPx` = gap between icon and label
- `iconSizeWithOverlap` = icon size plus the "overlap" for the notification dot
- `textHeight` = measured height of the label text at `iconTextSizePx`

**These are the "logical" cell content dimensions** -- the actual space each cell occupies on screen is larger. The real cell footprint comes from `getCellSize()`.

### Workspace cell size: actual footprint

The physical cell size on screen is calculated by `getCellSize()` ([`DeviceProfile.java:1692-1706`](../src/com/android/launcher3/DeviceProfile.java)):

```java
cellSize.x = calculateCellWidth(
    getCellLayoutWidth() - cellLayoutPaddingPx.left - cellLayoutPaddingPx.right,
    cellLayoutBorderSpacePx.x,
    numColumns);

cellSize.y = calculateCellHeight(
    getCellLayoutHeight() - cellLayoutPaddingPx.top - cellLayoutPaddingPx.bottom,
    cellLayoutBorderSpacePx.y,
    numRows);
```

Where `calculateCellWidth` ([`DeviceProfile.java:2175`](../src/com/android/launcher3/DeviceProfile.java)) is:

```
cellWidth = (availableWidth - ((columnCount - 1) * borderSpacing)) / columnCount
```

And the available dimensions come from:

```
getCellLayoutWidth()  = (availableWidthPx - workspacePadding.left - workspacePadding.right) / panelCount
getCellLayoutHeight() = availableHeightPx - workspacePadding.top - workspacePadding.bottom
```

### The full calculation chain (phones)

Here's the complete pipeline from screen size to cell pixels for a phone in portrait:

```
Screen: 1080 x 2400 px
  - System bars (status bar, nav bar) subtracted → availableWidthPx, availableHeightPx
  - workspacePadding subtracted:
      top    = workspaceTopPadding + edgeMarginPx
      bottom = hotseatBarSizePx + workspaceBottomPadding - nav_bar + page_indicator
      left   = desiredWorkspaceHorizontalMarginPx
      right  = desiredWorkspaceHorizontalMarginPx
  - cellLayoutPadding subtracted (small internal padding)
  = shortcutAndWidgetContainerWidth, shortcutAndWidgetContainerHeight

Cell footprint:
  width  = (containerWidth  - (numColumns - 1) * borderSpaceX) / numColumns
  height = (containerHeight - (numRows - 1)    * borderSpaceY) / numRows
```

For default (non-scalable) phone grids, `borderSpaceX` and `borderSpaceY` are **both 0** -- there are no explicit gaps between cells. The cells tile edge-to-edge and all spacing is internal (the icon + label are centered within each cell, leaving visual space around them).

### Why cells aren't square

On a typical phone, the workspace area is much taller than it is wide (portrait orientation). The cell width and height are calculated independently:

```
width  = containerWidth / numColumns       (e.g., 1000 / 5 = 200px)
height = containerHeight / numRows         (e.g., 1600 / 5 = 320px)
```

This produces **tall rectangles** (200x320 in this example), not squares. The icon and label are vertically centered within each cell, so the visual gap above and below each icon is larger than the gap to the sides.

---

## CellLayout: The Workspace Grid View

**File:** [`src/com/android/launcher3/CellLayout.java`](../src/com/android/launcher3/CellLayout.java)

CellLayout is the `ViewGroup` that draws one page of the workspace grid. Key fields:

| Field | Meaning |
|-------|---------|
| `mCellWidth`, `mCellHeight` | Pixel dimensions of one cell |
| `mCountX`, `mCountY` | Grid dimensions (columns, rows) |
| `mBorderSpace` | `Point(x, y)` gap between cells in pixels |

### How CellLayout computes cell size

In `onMeasure()` ([`CellLayout.java:987`](../src/com/android/launcher3/CellLayout.java)):

```java
int childWidthSize = widthSize - paddingLeft - paddingRight;
int childHeightSize = heightSize - paddingTop - paddingBottom;
mCellWidth = DeviceProfile.calculateCellWidth(childWidthSize, mBorderSpace.x, mCountX);
mCellHeight = DeviceProfile.calculateCellHeight(childHeightSize, mBorderSpace.y, mCountY);
```

This is the same formula as DeviceProfile -- the CellLayout re-derives cell size from its measured size. They should agree, but CellLayout is the ground truth for layout.

### Child positioning

Each child (icon, widget) is positioned by [`ShortcutAndWidgetContainer`](../src/com/android/launcher3/ShortcutAndWidgetContainer.java) using [`CellLayoutLayoutParams`](../src/com/android/launcher3/celllayout/CellLayoutLayoutParams.java):

```
child.x = leftMargin + (cellX * cellWidth) + (cellX * borderSpaceX)
child.y = topMargin + (cellY * cellHeight) + (cellY * borderSpaceY)
child.width  = (cellHSpan * cellWidth)  + ((cellHSpan - 1) * borderSpaceX)
child.height = (cellVSpan * cellHeight) + ((cellVSpan - 1) * borderSpaceY)
```

A 2x2 widget at position (1, 2) with `borderSpaceX = 10` and `cellWidth = 200` would be positioned at x = `0 + (1 * 200) + (1 * 10) = 210` with width = `(2 * 200) + (1 * 10) = 410`.

---

## App Drawer Grid (All-Apps)

The app drawer does **not** use CellLayout. It uses a `RecyclerView` with a `GridLayoutManager` from AndroidX.

### Column count

Set in [`AllAppsGridAdapter`](../src/com/android/launcher3/allapps/AllAppsGridAdapter.java) from `DeviceProfile.numShownAllAppsColumns`, which comes from `InvariantDeviceProfile.numAllAppsColumns`.

If `numAllAppsColumns` is not specified in the grid XML, it defaults to `numColumns` (the workspace column count).

### Cell sizing

All-apps cell dimensions are calculated in [`DeviceProfile.updateAllAppsIconSize()`](../src/com/android/launcher3/DeviceProfile.java):

**Non-scalable grids (phones):**
```
allAppsCellWidthPx  = allAppsIconSizePx + (2 * allAppsIconDrawablePaddingPx)
allAppsCellHeightPx = allAppsCellSize.y (from XML) + allAppsBorderSpacePx.y
```

**Scalable grids (tablets):**
```
allAppsCellWidthPx  = allAppsCellSize.x (from XML, converted to px)
allAppsCellHeightPx = allAppsCellSize.y (from XML, converted to px) + allAppsBorderSpacePx.y
```

Note that border space is **added to the cell height** (not between cells) because the RecyclerView doesn't have real inter-cell spacing -- each cell includes its own bottom margin.

### How all-apps differs from workspace

| Aspect | Workspace (CellLayout) | App Drawer (RecyclerView) |
|--------|----------------------|---------------------------|
| Layout engine | Custom `ViewGroup` | AndroidX `GridLayoutManager` |
| Cell size source | Fills available space / numRows&Cols | Explicit cell dimensions from XML |
| Border space | Real gap between cells | Baked into cell height |
| Rows | Fixed (`numRows`) | Scrollable (as many as needed) |
| Columns | Fixed (`numColumns`) | Fixed (`numAllAppsColumns`) |
| Supports widgets | Yes | No |
| Drag-and-drop | Between cells | Only drag out of list |

---

## Where Space Goes: The Full Padding/Margin Model

Before changing anything, you need to understand where every pixel of space goes between the screen edge and the icon. There are **five layers** of space consumption, and the current phone grids waste a lot of it.

### The five layers (outside-in)

```
Screen edge
 |
 |  1. System insets (status bar, nav bar)           -- untouchable
 |     └─ availableWidthPx, availableHeightPx
 |
 |  2. Workspace padding                             -- configurable
 |     ├─ left/right: desiredWorkspaceHorizontalMarginPx  (8dp on phones)
 |     ├─ top: workspaceTopPadding + edgeMarginPx         (10.77dp edge)
 |     └─ bottom: hotseatBarSizePx + workspaceBottomPadding + page indicator
 |        └─ getCellLayoutWidth(), getCellLayoutHeight()
 |
 |  3. Cell layout padding (cellLayoutPaddingPx)     -- configurable
 |     All four sides: 10.77dp on phones (from R.dimen.cell_layout_padding)
 |        └─ shortcutAndWidgetContainerWidth/Height
 |
 |  4. Border space (cellLayoutBorderSpacePx)        -- configurable
 |     Gap between cells. Currently 0 on phone grids.
 |
 |  5. Cell interior padding                         -- inherent
 |     The cell footprint is larger than the icon+label content.
 |     Icons are centered within the cell. This is the visual "gap".
 |
Icon
```

### Concrete example: 5x5 grid on a 1080x2400 phone

Here's approximately what happens with the stock layout in portrait:

```
Screen: 1080 x 2400 px
System insets: ~100px top (status bar), ~132px bottom (nav bar)
Available: 1080 x 2168 px

Workspace padding:
  left/right: ~24px each (8dp × density)
  top: ~64px (workspaceTopPadding + edgeMarginPx)
  bottom: ~360px (hotseat + QSB + bottom space + page indicator)
  → CellLayout gets: ~1032 x ~1744 px

CellLayout padding: ~32px each side
  → ShortcutAndWidgetContainer gets: ~968 x ~1680 px

Border space: 0px (default phone grid)

Cell footprint: 968/5 = ~193px wide, 1680/5 = ~336px tall   ← tall rectangles

Icon content inside cell: ~168px icon + ~12px gap + ~48px text = ~228px tall
Cell Y padding: (336 - 228) / 2 = ~54px above and below     ← wasted as invisible margin
```

### Where the waste is

| Space | Pixels (approx) | Purpose |
|-------|-----------------|---------|
| Workspace horizontal margin | 24px × 2 = 48px | Empty strip on left/right edge |
| CellLayout padding (left+right) | 32px × 2 = 64px | Empty strip inside CellLayout |
| CellLayout padding (top+bottom) | 32px × 2 = 64px | Empty strip inside CellLayout |
| Cell interior vertical padding | 54px × 2 × 5 rows = 540px | Invisible space above/below each icon |
| **Total horizontal waste** | **112px** | **~10% of screen width** |
| **Total vertical waste** | **604px** | **~35% of available workspace height** |

The grid doesn't look edge-to-edge because of layers 2 + 3 (workspace margin and cell layout padding), and the cells aren't square because the 540px of interior vertical padding inflates cell height beyond cell width.

### What the sources are in code

| Space layer | Source in code | File/Line |
|---|---|---|
| Workspace left/right margin | `desiredWorkspaceHorizontalMarginPx` = `R.dimen.dynamic_grid_left_right_margin` (8dp) | [`DeviceProfile.java:922-934`](../src/com/android/launcher3/DeviceProfile.java) |
| Workspace top padding | `workspaceTopPadding` (from DevicePaddings or 0) + `edgeMarginPx` (`R.dimen.dynamic_grid_edge_margin`, 10.77dp) | [`DeviceProfile.java:1834`](../src/com/android/launcher3/DeviceProfile.java) |
| Workspace bottom padding | `hotseatBarSizePx` + `workspaceBottomPadding` + page indicator height | [`DeviceProfile.java:1829-1833`](../src/com/android/launcher3/DeviceProfile.java) |
| CellLayout padding | `cellLayoutPaddingPx` = `R.dimen.cell_layout_padding` (10.77dp all sides) | [`DeviceProfile.java:813-817`](../src/com/android/launcher3/DeviceProfile.java) |
| Cell border space | `cellLayoutBorderSpacePx` = Point(0, 0) for phone grids | [`DeviceProfile.java:1055-1072`](../src/com/android/launcher3/DeviceProfile.java) |
| Cell interior padding | Computed: `cellYPaddingPx = (getCellSize().y - cellContentHeight) / 2` | [`DeviceProfile.java:1349`](../src/com/android/launcher3/DeviceProfile.java) |

### Rounding slop

After dividing available space by column/row count, there's usually a few leftover pixels due to integer division. `CellLayout.getUnusedHorizontalSpace()` ([`CellLayout.java:1057`](../src/com/android/launcher3/CellLayout.java)) computes this and centers the grid by splitting it evenly on both sides. There's no vertical equivalent -- vertical slop is simply absorbed by the bottom of the last row.

---

## Square Grid Design: Derived Rows from Columns

### The model

AOSP configures both `numRows` and `numColumns` as fixed values in XML. This doesn't work for square cells on non-square screens, because the row count that fits depends on the cell size, which depends on the screen.

The better model: **columns are the input, rows are derived**.

```
User configures:   numColumns, gap
Derived:           cellSize = (availableWidth - (numColumns - 1) * gap) / numColumns
Derived:           numRows  = floor((availableHeight + gap) / (cellSize + gap))
Remainder:         slop = availableHeight - (numRows * cellSize + (numRows - 1) * gap)
```

- Cells are square (`cellSize × cellSize`)
- Gaps are equal in both directions (`gap` is the same horizontally and vertically)
- The grid fills the full width
- Rows are maximized to fill available height
- A small remainder (`slop`) is distributed as top/bottom padding or extra gap

### Concrete example: 5 columns on a 1080x2400 phone

```
Screen: 1080 x 2400 px
System insets: status bar 100px, nav bar 132px
Available: 1080 x 2168 px
Hotseat reservation: ~310px
Workspace area: 1080 x 1858 px

gap = 16dp × 3.0 density = 48px

cellSize = (1080 - 4 * 48) / 5 = (1080 - 192) / 5 = 888 / 5 = 177px

numRows = floor((1858 + 48) / (177 + 48))
        = floor(1906 / 225)
        = floor(8.47)
        = 8

Grid: 5 columns × 8 rows, 177px square cells, 48px gaps
Total grid height = 8 * 177 + 7 * 48 = 1416 + 336 = 1752px
Slop = 1858 - 1752 = 106px → 53px top + 53px bottom padding
```

Compare to stock AOSP 5×5: you get 8 rows instead of 5, with cells that are square and uniformly spaced.

### What the gap value should be

The gap is a design choice. For reference:

| Source | Gap | Notes |
|--------|-----|-------|
| AOSP tablet grid (6×5) | 16dp horizontal, 64dp vertical | Not uniform |
| AOSP `dynamic_grid_cell_border_spacing` | 16dp | Defined but unused on phone grids |
| Material Design grid guidance | 8dp or 16dp | Standard touch spacing |
| iOS home screen | ~26pt (~52px at 3x) | Between icon centers minus icon size |

16dp is a reasonable starting point. 8dp for denser grids. This could also be a user-configurable setting.

### How this changes `device_profiles.xml`

Under this model, `numRows` in the XML becomes a **maximum or hint**, not the truth. The grid option only needs to declare `numColumns`:

```xml
<grid-option
    launcher:name="5_col"
    launcher:numColumns="5"
    launcher:numRows="99"
    launcher:numFolderRows="3"
    launcher:numFolderColumns="4"
    launcher:numHotseatIcons="5"
    launcher:dbFile="launcher_5_col.db"
    launcher:defaultLayoutId="@xml/default_workspace_5x5"
    launcher:deviceCategory="phone|multi_display" >

    <display-option
        launcher:name="Phone"
        launcher:minWidthDps="255"
        launcher:minHeightDps="400"
        launcher:iconImageSize="52"
        launcher:iconTextSize="13.0"
        launcher:canBeDefault="true" />
</grid-option>
```

The `numRows="99"` is a placeholder -- the real value is calculated at runtime. This matters because `numRows` is used for the database schema (icon positions go up to `numRows - 1`). Setting it high ensures the database can accommodate any derived row count.

### Where to implement this

The changes span two files:

#### 1. `InvariantDeviceProfile.initGrid()` -- derive numRows

**File:** [`InvariantDeviceProfile.java:365`](../src/com/android/launcher3/InvariantDeviceProfile.java)

After the grid option is selected and `numColumns` is set, but before anything consumes `numRows`:

```java
// After existing grid selection logic populates numColumns, iconSize, etc.

// Derive numRows from available height, square cell size, and gap
int gapPx = pxFromDp(GAP_DP, metrics);  // e.g., GAP_DP = 16f
int cellSizePx = (availableWidthPx - (numColumns - 1) * gapPx) / numColumns;
numRows = (availableHeightForGrid + gapPx) / (cellSizePx + gapPx);
// availableHeightForGrid = screen height - insets - hotseat reservation
```

The tricky part: `availableHeightForGrid` isn't fully known at `initGrid()` time because workspace padding depends on DeviceProfile, which hasn't been built yet. You may need to approximate the hotseat height here (it's roughly `iconSize + qsbHeight + bottomSpace`) or do the row derivation inside DeviceProfile instead.

#### 2. `DeviceProfile` constructor -- square cells with uniform gaps

**File:** [`DeviceProfile.java`](../src/com/android/launcher3/DeviceProfile.java)

Several modifications in the constructor and `updateIconSize()`:

**a) Zero out outer margins** (around line 770):

```java
// Replace getHorizontalMarginPx() result with 0 for our grid mode
desiredWorkspaceHorizontalMarginPx = 0;
```

**b) Zero out cell layout padding** (around line 813-817):

```java
cellLayoutPaddingPx = new Rect(0, 0, 0, 0);
```

**c) Force square cells and uniform gap in `updateIconSize()`** (around line 1343-1358):

```java
// Replace the default mode cell size calculation:
int gapPx = pxFromDp(GAP_DP, mMetrics);
cellWidthPx = (availableWidthPx - (inv.numColumns - 1) * gapPx) / inv.numColumns;
cellHeightPx = cellWidthPx;  // square

cellLayoutBorderSpacePx = new Point(gapPx, gapPx);  // uniform gap
```

**d) Derive numRows if not done in InvariantDeviceProfile** -- after workspace padding is known:

```java
// After updateWorkspacePadding()
int workspaceHeight = getCellLayoutHeight();
int gapPx = cellLayoutBorderSpacePx.y;
int derivedRows = (workspaceHeight + gapPx) / (cellHeightPx + gapPx);
if (derivedRows != inv.numRows) {
    // Update inv.numRows -- this affects CellLayout.setGridSize() downstream
    inv.numRows = derivedRows;
}
```

**e) Distribute slop as top/bottom padding** in `updateWorkspacePadding()`:

```java
int gridHeight = inv.numRows * cellHeightPx + (inv.numRows - 1) * gapPx;
int slop = getCellLayoutHeight() - gridHeight;
// Add slop/2 to top padding, slop/2 to bottom padding
// Or: add slop to workspaceTopPadding to push grid down slightly
```

### Database implications

AOSP stores icon positions as `(cellX, cellY, spanX, spanY)` in the database, with valid ranges `0..numColumns-1` and `0..numRows-1`. The database filename is tied to the grid option (`launcher_5_col.db`).

If `numRows` changes between sessions (e.g., the user rotates from portrait to landscape, or the hotseat size changes), icons in rows that no longer exist would need to be handled. Two strategies:

**Strategy A: Fixed maximum rows.** Set `numRows` to a high constant (e.g., 20) in the XML. Derived rows only affect layout, not the database. CellLayout is set to the derived count, but the DB can hold more. Icons beyond the visible rows are simply off-screen (accessible by scrolling, or migrated to a new page).

**Strategy B: Re-derive on config change.** Recalculate rows on every configuration change and migrate icons. This is more complex and risks losing positions.

Strategy A is simpler and matches how AOSP already handles the `numDatabaseHotseatIcons > numShownHotseatIcons` pattern -- the database capacity is larger than what's displayed, allowing seamless transitions.

### Impact on widgets

With uniform gaps (`gapX == gapY`), a 2×2 widget occupies:

```
width  = 2 * cellSize + 1 * gap
height = 2 * cellSize + 1 * gap   → square!
```

A 3×2 widget would be wider than tall, a 2×3 taller than wide -- natural behavior. The key improvement over stock AOSP is that square-span widgets (2×2, 3×3) are actually square.

### Impact on all-apps

The app drawer scrolls vertically, so rows aren't bounded. Apply the same square-cell logic:

```java
allAppsCellWidthPx = cellWidthPx;   // same square size as workspace
allAppsCellHeightPx = cellWidthPx;  // square
allAppsBorderSpacePx = new Point(gapPx, gapPx);  // uniform gap
```

The column count can be the same as the workspace or different (set via `numAllAppsColumns`).

---

## Key Files Reference

| File | Role |
|------|------|
| [`device_profiles.xml`](../res/xml/device_profiles.xml) | All grid specifications |
| [`InvariantDeviceProfile.java`](../src/com/android/launcher3/InvariantDeviceProfile.java) | Parses XML, selects grid, stores invariant config |
| [`DeviceProfile.java`](../src/com/android/launcher3/DeviceProfile.java) | DP-to-pixel conversion, all cell size calculations |
| [`CellLayout.java`](../src/com/android/launcher3/CellLayout.java) | Workspace grid ViewGroup, measures/positions cells |
| [`ShortcutAndWidgetContainer.java`](../src/com/android/launcher3/ShortcutAndWidgetContainer.java) | Positions child views within CellLayout cells |
| [`CellLayoutLayoutParams.java`](../src/com/android/launcher3/celllayout/CellLayoutLayoutParams.java) | Per-child position and size within the grid |
| [`DeviceGridState.java`](../src/com/android/launcher3/model/DeviceGridState.java) | Persists current grid choice across restarts |
| [`AllAppsGridAdapter.java`](../src/com/android/launcher3/allapps/AllAppsGridAdapter.java) | Sets up app drawer RecyclerView grid |
| [`attrs.xml`](../res/values/attrs.xml) | XML attribute definitions for grid parsing |
| [`config.xml`](../res/values/config.xml) | Icon size steps, default spacing values |

---

## Appendix: Scalable vs Responsive vs Default Grid Modes

### Default mode (phones: 3x3, 4x4, 5x5)

- Cell size derived from icon size (not explicitly specified)
- Border space between cells is 0
- Icon centered horizontally in cell; icon + label centered vertically
- All spacing is visual (internal to each cell), not structural

### Scalable mode (tablets: 6x5)

- Cell size explicitly set via `minCellWidth`/`minCellHeight` in DP
- Border space explicitly set via `borderSpaceHorizontal`/`borderSpaceVertical`
- Workspace margins explicitly set via `horizontalMargin`
- If icon doesn't fit in cell, border space is reduced first, then icon is shrunk

### Responsive mode (fixed landscape)

- Cell size calculated from responsive spec XML files using percentage-of-available-space formulas
- Border space (gutter) calculated as percentage of available space
- Padding calculated as percentage of available space
- Most flexible, but most complex
- Uses XML files like [`spec_handheld_workspace_3_row.xml`](../res/xml/spec_handheld_workspace_3_row.xml)
