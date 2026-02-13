# Grid Reflow System

How workspace items are preserved when the user decreases the column count in settings.

---

## The Problem

AOSP Launcher3 stores workspace item positions as `(cellX, cellY, spanX, spanY)` in the `favorites` SQLite table. When the column count decreases, items at positions beyond the new grid bounds (e.g., `cellX=4` in a 4-column grid) fail the bounds check in `LoaderCursor.checkItemPlacement()` and are permanently deleted via `markDeleted()`.

The AOSP migration system (`GridSizeMigrationLogic`) doesn't help because it only runs when the `dbFile` changes. The square grid system changes column count without changing `dbFile`, so `DeviceGridState.isCompatible()` returns true and migration is skipped.

---

## Architecture

```
User changes columns in Settings
    |
    v
InvariantDeviceProfile.onConfigChanged()
    |
    v
Model reload -> LoaderTask.loadWorkspaceImpl()
    |
    v
attemptMigrateDb()              <-- AOSP migration (skipped: same dbFile)
    |
    v
SquareGridReflow.reflowIfNeeded()   <-- NEW: in-place DB reflow
    |
    v
loadDefaultFavoritesIfNecessary()
    |
    v
Load items from DB -> checkItemPlacement()  <-- all items now fit
```

The reflow runs **after** AOSP's migration attempt and **before** items are loaded from the database. This ensures all items are repositioned before `checkItemPlacement()` validates them.

---

## Two Mechanisms

### 1. SquareGridReflow (workspace items)

**File:** [`src/com/android/launcher3/model/SquareGridReflow.java`](../src/com/android/launcher3/model/SquareGridReflow.java)

Handles all `CONTAINER_DESKTOP` items. Triggered when columns decrease.

#### Detection

Compares the persisted `DeviceGridState` (from SharedPreferences) with the current one (from `InvariantDeviceProfile`). The persisted state retains the old column count from before the settings change.

```
persisted = DeviceGridState(context)   -> reads old columns from prefs
current   = DeviceGridState(idp)       -> reads new columns from IDP
```

After comparison, the current state is always written to prefs to prevent stale state on next load.

#### Span Clamping

Before placement, items that exceed the new grid dimensions are clamped:

- `spanX > newCols` -> `spanX = newCols`, `cellX = 0`
- `spanY > visibleRows` -> `spanY = visibleRows`

No items are dropped. A slightly undersized widget is always preferable to a deleted one. The user can manually resize afterwards.

#### Three-Pass Placement

**Pass 1 -- Keep in place.** Items that still fit at their original `(cellX, cellY)` with their (possibly clamped) spans stay put. A `GridOccupancy` grid tracks occupied cells. On screen 0, row 0 is reserved for the smartspace/QSB when enabled.

**Pass 2 -- Same-screen reflow.** Displaced items are placed on the same screen using `GridOccupancy.findVacantCell()`, which scans top-to-bottom, left-to-right for a region large enough.

**Pass 3 -- Overflow screens.** Items that can't fit on any existing screen go to new screens (`maxScreenId + 1`, incrementing). AOSP discovers these screens automatically via `BgDataModel.collectWorkspaceScreens()`, which derives the screen set from loaded items.

#### Visible Rows

The reflow uses actual visible rows (from `DeviceProfile.numRows`, computed by `deriveSquareGridRows()`) rather than the DB capacity of 20. This is obtained via `getMaxVisibleRows()`, which reads the maximum `numRows` across all supported device profiles. This ensures items are placed within the visible area.

#### DB Transaction

All changes are written in a single SQLite transaction:

```sql
UPDATE favorites SET cellX=?, cellY=?, spanX=?, spanY=?, screen=?
WHERE _id=?
```

Only items with `moved=true` are updated. Unchanged items are not touched.

### 2. Monotonic Hotseat DB Count (hotseat items)

**File:** [`src/com/android/launcher3/InvariantDeviceProfile.java`](../src/com/android/launcher3/InvariantDeviceProfile.java)

Hotseat items use `screenId` as their position index (not `cellX`). `checkItemPlacement()` rejects hotseat items where `screenId >= numDatabaseHotseatIcons`.

When columns decrease, `numDatabaseHotseatIcons` would shrink, causing items at old positions to be deleted. The fix: `numDatabaseHotseatIcons` never shrinks.

```java
// In initGrid(), after setting numDatabaseHotseatIcons:
int persistedMax = mPrefs.get(LauncherPrefs.HOTSEAT_MAX_DB_COUNT);
if (numDatabaseHotseatIcons > persistedMax) {
    mPrefs.put(LauncherPrefs.HOTSEAT_MAX_DB_COUNT, numDatabaseHotseatIcons);
} else if (persistedMax > numDatabaseHotseatIcons) {
    numDatabaseHotseatIcons = persistedMax;
}
```

The `HOTSEAT_MAX_DB_COUNT` preference (in `LauncherPrefs.kt`) tracks the all-time maximum. Items beyond `numShownHotseatIcons` are invisible but preserved in the database. They reappear when columns increase again.

---

## Key Design Decisions

### Why not extend AOSP's GridSizeMigrationLogic?

AOSP's migration system is designed for grid-to-grid database migrations (different `dbFile`). It copies data between tables, handles schema differences, and manages backup tables. Our use case is simpler: same database, same schema, just different column count. An in-place UPDATE is faster and less error-prone.

### Why clamp widgets instead of querying minSpanX?

`WidgetManagerHelper.getLauncherAppWidgetInfo()` returns a `LauncherAppWidgetProviderInfo` with `minSpanX = 0` during reflow because `initSpans()` hasn't been called yet (it runs during widget binding, not during background loading). Using the uninitialized value as a fallback would cause widgets to be incorrectly dropped. Unconditional clamping is safe -- the widget framework handles rendering at reduced sizes gracefully.

### Why use visible rows, not DB capacity (20)?

The database allows `numRows = 20` for capacity, but CellLayout only renders `DeviceProfile.numRows` rows (typically 5-8). Items placed at row 15 would exist in the database and pass `checkItemPlacement()`, but would be invisible. Using visible rows ensures overflow items actually appear on their new screens.

---

## Interaction with checkItemPlacement

`LoaderCursor.checkItemPlacement()` validates items against `mIDP.numColumns` and `mIDP.numRows`:

```java
final int countX = mIDP.numColumns;   // new column count
final int countY = mIDP.numRows;      // 20 (DB capacity)
if (item.cellX + item.spanX > countX || item.cellY + item.spanY > countY) {
    return false;  // -> markDeleted()
}
```

After reflow:
- All items have `cellX + spanX <= newCols` (clamped and repositioned)
- All items have `cellY + spanY <= visibleRows <= 20` (clamped)
- No overlaps (GridOccupancy prevents them)
- All items pass validation

---

## Key Files

| File | Role |
|------|------|
| [`SquareGridReflow.java`](../src/com/android/launcher3/model/SquareGridReflow.java) | Detection, span clamping, three-pass placement, DB transaction |
| [`LoaderTask.java`](../src/com/android/launcher3/model/LoaderTask.java) | Hook point (after migration, before loading) |
| [`InvariantDeviceProfile.java`](../src/com/android/launcher3/InvariantDeviceProfile.java) | Monotonic hotseat DB count |
| [`LauncherPrefs.kt`](../src/com/android/launcher3/LauncherPrefs.kt) | `HOTSEAT_MAX_DB_COUNT` preference |
| [`DeviceGridState.java`](../src/com/android/launcher3/model/DeviceGridState.java) | Persisted grid state used for detection |
| [`GridOccupancy.java`](../src/com/android/launcher3/util/GridOccupancy.java) | Cell occupancy tracking and vacant cell search |
| [`LoaderCursor.java`](../src/com/android/launcher3/model/LoaderCursor.java) | `checkItemPlacement()` -- the bounds check reflow prevents |
