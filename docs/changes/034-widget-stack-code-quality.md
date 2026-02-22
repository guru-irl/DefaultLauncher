# 034: Widget Stack Code Quality Refactoring

## Summary

Comprehensive code quality refactoring of the Widget Stack feature, addressing naming conventions, code duplication, data integrity bugs, type safety, and semantic confusion across 14 files.

## Changes by Priority

### CRITICAL: Data Integrity & Correctness

**`LauncherSettings.java`**
- Added `ITEM_TYPE_WIDGET_STACK` case to `itemTypeToString()` (was returning raw `"12"`)
- Added `isInsideCollection(int container)` utility to replace fragile `container > 0` heuristic

**`ModelUtils.java`**
- Split `WIDGET_FILTER` into two predicates:
  - `APP_WIDGET_FILTER` -- matches actual widgets only (not stacks)
  - `WIDGET_OR_STACK_FILTER` -- matches widgets AND stacks
- The old `WIDGET_FILTER` included stacks but every call site immediately added `.filter(item -> item instanceof LauncherAppWidgetInfo)` to exclude them

**`CacheDataUpdatedTask.java`, `PackageInstallStateChangedTask.java`, `PackageUpdatedTask.java`, `LoaderTask.java`**
- Switched to `APP_WIDGET_FILTER` and removed redundant `instanceof LauncherAppWidgetInfo` filter

**`BaseLauncherBinder.java`**
- Switched to `WIDGET_OR_STACK_FILTER` for item categorization (stacks should be categorized with widgets)

**`Workspace.java` (removeItemsByMatcher)**
- Added `WidgetStackView` handling in `removeItemsByMatcher()` for package uninstall
- Calls `stackInfo.removeMatching(matcher)` on matched children
- Removes entire stack if empty, otherwise calls `rebuildFromStackInfo()`

**`WidgetStackInfo.java` (removeMatching)**
- Added `removeMatching(Predicate<ItemInfo>)` method for package uninstall handling

**`WidgetStackView.java` (rebuildFromStackInfo)**
- Added `rebuildFromStackInfo()` to sync child views with model after mutations

**`WorkspaceItemProcessor.kt`**
- Replaced `c.container > 0` with `Favorites.isInsideCollection(c.container)`

### HIGH: Code Quality & Safety

**`WidgetStackView.java` (full refactor)**
- Renamed 18 private fields to AOSP `m`-prefix convention (e.g. `widgetViews` -> `mWidgetViews`, `activeIndex` -> `mActiveIndex`)
- Extracted `accumulateFromProviderInfo()` with `ProviderInfoAccumulator` interface -- five span/mode methods reduced to one-liners
- Extracted `animateSnap()` with `SnapUpdateCallback` interface -- `snapToIndex()` and `snapBack()` share ~30 lines of ValueAnimator setup
- Added `mLongPressHelper.cancelLongPress()` in `onDetachedFromWindow()` (was missing)

**`ItemInflater.kt`**
- Replaced unsafe `child as LauncherAppWidgetInfo` with safe cast + `Log.e` + `continue`

**`WidgetStackInfo.java` (type safety)**
- `getActiveWidget()` now uses `instanceof` pattern match instead of direct cast
- `setActiveIndex()` guards against empty contents to avoid `Math.min(i, -1)`

**`WidgetStackInfo.java` (mutation/persistence separation)**
- Replaced static `assignWidgetToStack(stack, widget, rank, writer, isNew)` with instance `assignWidget(widget, rank)` that only mutates fields
- DB persistence moved to callers in `Launcher.java` and `Workspace.java`

### MEDIUM: Robustness & Polish

**`WidgetStackView.java` (accessibility)**
- Added `onInitializeAccessibilityNodeInfo()` with scroll forward/backward actions
- Added `performAccessibilityAction()` to navigate via `snapToIndex()`
- Added content description: "Widget %d of %d"

**`WidgetStackView.java` (grid bounds)**
- `expandSpanIfNeeded()` now clamps to `InvariantDeviceProfile.numColumns`/`numRows`

**`WidgetStackView.java` (child persistence)**
- Added `updateChildWidgetSizes(context, spanX, spanY, writer)` overload that persists child span changes to DB

**`AppWidgetResizeFrame.java`**
- Added content description on resize frame: "Widget stack with %d widgets"

**`LoaderCursor.java`**
- Extracted generic `findOrMakeCollection(id, dataModel, type, factory)` -- both `findOrMakeFolder` and `findOrMakeWidgetStack` are now one-liners

### LOW: Nice-to-Have

**`WidgetStackInfo.java` / `WidgetStackView.java` / `Workspace.java`**
- Added `MAX_STACK_SIZE = 6` constant enforced in `add()` and `willAddToExistingWidgetStack()`

**`WidgetStackView.java`**
- Renamed `nextIndex` to `peekIndex` in `applySwipeTranslation()` for consistency with `snapBack()`

## New Resources

| Resource | Value |
|----------|-------|
| `widget_stack_page_description` | "Widget %1$d of %2$d" |

## Documentation

- Added [`docs/widget-stack.md`](../widget-stack.md) -- comprehensive architecture documentation covering data model, loading pipeline, view layer, drag-and-drop, resize, and package uninstall handling
