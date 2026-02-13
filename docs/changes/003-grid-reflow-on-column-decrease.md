# 003 - Grid Reflow on Column Decrease

**Date:** 2026-02-13
**Branch:** dev
**Status:** Implemented, builds successfully

## Summary

When the user decreases the column count in settings, workspace items that no longer fit were permanently deleted from the database by `LoaderCursor.checkItemPlacement()`. This change adds an in-place database reflow that rearranges displaced items (moving them to available cells or new overflow screens) and resizes oversized widgets, instead of deleting them. Hotseat items are also preserved via a monotonic database count.

## Problem

The AOSP grid migration system (`GridSizeMigrationLogic`) only runs when `DeviceGridState.isCompatible()` returns false, which checks `dbFile` equality. Since the square grid override changes `numColumns` without changing `dbFile`, `needsToMigrate()` returns false, no migration runs, and `checkItemPlacement()` deletes any items whose `cellX + spanX > numColumns`.

Increasing columns back doesn't restore the items -- they're gone from the database permanently.

## Solution

Two complementary mechanisms:

1. **SquareGridReflow** -- An in-place database reflow that runs after migration but before item loading. Detects column decreases by comparing persisted `DeviceGridState` with the current one, then rearranges items in a single SQLite transaction.

2. **Monotonic hotseat DB count** -- `numDatabaseHotseatIcons` never shrinks, so `checkItemPlacement()` won't reject hotseat items at positions from a previous (higher) column count. Items beyond `numShownHotseatIcons` are invisible but preserved.

## Files Modified

### New Files

- **`src/com/android/launcher3/model/SquareGridReflow.java`** -- In-place DB reflow utility (~275 lines)

### Modified Files

- **`src/com/android/launcher3/model/LoaderTask.java`** -- Hook `SquareGridReflow.reflowIfNeeded()` after migration, before item loading
- **`src/com/android/launcher3/InvariantDeviceProfile.java`** -- Monotonic hotseat DB count via `HOTSEAT_MAX_DB_COUNT` preference
- **`src/com/android/launcher3/LauncherPrefs.kt`** -- Added `HOTSEAT_MAX_DB_COUNT` constant

## Changes

### LauncherPrefs.kt

1. **`HOTSEAT_MAX_DB_COUNT`** -- New `backedUpItem("pref_hotseat_max_db_count", -1)`. Tracks the highest `numDatabaseHotseatIcons` value ever used.

### InvariantDeviceProfile.java

2. **Monotonic hotseat tracking** -- After setting `numDatabaseHotseatIcons = Math.max(numDatabaseHotseatIcons, userColumns)`, reads `HOTSEAT_MAX_DB_COUNT`. If the current value is higher, persists it. If the persisted value is higher, uses it instead. This ensures `checkItemPlacement()` never rejects hotseat items at old positions.

### LoaderTask.java

3. **Reflow hook** -- In `loadWorkspaceImpl()`, after `attemptMigrateDb()` and before `loadDefaultFavoritesIfNecessary()`:
```java
if (mIDP.isSquareGrid) {
    SquareGridReflow.reflowIfNeeded(mContext, dbController.getDb(), mIDP);
}
```

### SquareGridReflow.java

The reflow has two phases: detection and rearrangement.

**Detection (`reflowIfNeeded`):**
- Compares persisted `DeviceGridState(context)` column count with current `DeviceGridState(idp)`
- Only reflows when columns decreased
- Always saves the current state to prevent stale comparisons
- Skips on first load (persisted columns <= 0)

**Rearrangement (`reflow`):**

4. **Visible row computation** -- Uses `getMaxVisibleRows()` which reads `DeviceProfile.numRows` from `idp.supportedProfiles` (already computed by `deriveSquareGridRows()` at this point). This ensures items are placed within visible rows, not the DB capacity of 20.

5. **Span clamping** -- Any item with `spanX > newCols` is clamped to `spanX = newCols` with `cellX = 0`. Any item with `spanY > visibleRows` is clamped similarly. No items are dropped -- a slightly undersized widget is better than a deleted one.

6. **Per-screen placement (Pass 1)** -- Items that still fit at their original `(cellX, cellY)` keep their position. `GridOccupancy` tracks occupied cells. Smartspace row (row 0 on screen 0) is reserved when QSB is enabled.

7. **Per-screen overflow (Pass 2)** -- Displaced items are placed on the same screen via `GridOccupancy.findVacantCell()`.

8. **New screens (Pass 3)** -- Items that can't fit on any existing screen are placed on newly created screens (`maxScreenId + 1`, incrementing). Screens are implicitly created -- AOSP derives the screen set from items via `BgDataModel.collectWorkspaceScreens()`.

9. **DB transaction** -- All moved items are batch-updated in a single SQLite transaction: `UPDATE favorites SET cellX=?, cellY=?, spanX=?, spanY=?, screen=? WHERE _id=?`.

## Call Flow

```
User decreases columns in Settings
  -> InvariantDeviceProfile.onConfigChanged()
  -> Model reload -> LoaderTask.loadWorkspaceImpl()
  -> attemptMigrateDb() -> needsToMigrate() returns false (same dbFile)
  -> SquareGridReflow.reflowIfNeeded()
      -> Detects column decrease via DeviceGridState comparison
      -> Clamps oversized spans
      -> Pass 1: keep items at original positions if they fit
      -> Pass 2: findVacantCell for displaced items on same screen
      -> Pass 3: overflow items to new screens
      -> Batch UPDATE in single transaction
      -> Saves new DeviceGridState
  -> loadDefaultFavoritesIfNecessary()
  -> Load items from DB
  -> checkItemPlacement() -> all items now fit, no deletions
```

## Edge Cases

| Scenario | Handling |
|----------|----------|
| Columns increase | No reflow needed. Items already fit. State saved. |
| Widget `spanX > newCols` | Clamped to `newCols`, `cellX` set to 0. User can manually resize after. |
| Widget `spanY > visibleRows` | Clamped to visible rows. |
| First load (no persisted state) | `getColumns() <= 0`, skip reflow, save initial state. |
| Spacing change only (same columns) | No reflow (columns unchanged). |
| Hotseat items beyond visible count | Preserved in DB via monotonic `numDatabaseHotseatIcons`. Invisible but not deleted. Reappear when columns increase. |
| Folders/app-pairs | 1x1 items, reflow like shortcuts. Children reference folder by ID, unaffected. |
| Screen 0 smartspace | Row 0 reserved for QSB (follows AOSP pattern). |
| Items on overflow screens | Visible because AOSP derives screens from items (`collectWorkspaceScreens()`). |
| Idempotency | Second load sees `oldCols == newCols`, skips reflow. |

## Bug Fixes During Implementation

Three bugs were found and fixed in the initial implementation:

1. **Widget drop logic was fatally flawed** -- `getWidgetMinSpanX()` fell back to the original `spanX` (the oversized value) when `LauncherAppWidgetProviderInfo.minSpanX` was 0 (uninitialized -- `initSpans()` hasn't run at reflow time). This caused widgets to be dropped from the reflow, left untouched in the DB, and then permanently deleted by `checkItemPlacement()`. Fixed by removing the widget-specific drop logic entirely and always clamping.

2. **Used DB row capacity (20) instead of visible rows** -- `GridOccupancy` used `idp.numRows` (20) instead of the actual visible row count from `DeviceProfile.deriveSquareGridRows()`. Overflow items were placed at rows 7-19, invisible on a ~6-row screen. Fixed by reading `DeviceProfile.numRows` from `idp.supportedProfiles`.

3. **Widget `spanY` not checked** -- Widgets taller than the visible row count were not clamped, causing vertical overflow. Fixed by clamping `spanY` to `visibleRows`.
