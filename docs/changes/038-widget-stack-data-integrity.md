# 038: Widget Stack Data Integrity & Restore Fixes

## Summary

Fixed several data integrity issues in the widget stack system: empty stacks are now
sanitized from the DB on load, pending widgets inside stacks can reinflate correctly,
the workspace can look up widgets nested inside stacks, and the active index is persisted
with debouncing to survive launcher restarts without excessive DB writes.

## Modified Files

### Data Integrity

- **LoaderTask.java** — Added `sanitizeWidgetStacks()` which runs after
  `sanitizeFolders()` during data loading. Delegates to
  `ModelDbController.deleteEmptyWidgetStacks()` and removes deleted IDs from
  `BgDataModel.itemsIdMap`.

- **ModelDbController.java** — Added `deleteEmptyWidgetStacks()`: queries for
  `ITEM_TYPE_WIDGET_STACK` rows whose `_id` does not appear as a `container` for
  any item, then deletes them. Follows the same pattern as `deleteEmptyFolders()`.

- **BgDataModel.java** — Added `ITEM_TYPE_WIDGET_STACK` to the studio-build
  collection validation filter so widget stacks are verified alongside folders
  and app pairs.

- **ItemInflater.kt** — `inflateWidgetStack()` now tracks inflated child IDs and
  removes dead children from the in-memory `WidgetStackInfo.contents` list after
  inflation, keeping the model in sync with the views.

- **Workspace.java** — `removeItemsByMatcher()` now deletes the stack row from the
  DB when a stack becomes empty after package uninstall (previously only removed
  the view).

### Widget Restore in Stacks

- **PendingAppWidgetHostView.java** — `reInflate()` checks if the view's parent is
  a `WidgetStackView` and delegates to `reInflateChildWidget()` instead of the
  standard `removeItem`/`bindAppWidget` path, which fails for stacked widgets
  (they aren't direct CellLayout children and have `cellX/cellY = -1`).

- **WidgetStackView.java** — Added `reInflateChildWidget()` which reinflates a
  pending widget in-place: creates a new view via `WidgetInflater`, handles
  `TYPE_DELETE` (removes from stack), calls `prepareAppWidget()` for proper
  focusability, and swaps the view in the stack's child list.

### Widget Lookup

- **Workspace.java** — `getWidgetForAppWidgetId()` now searches inside widget
  stacks using `findChildByAppWidgetId()` after checking direct workspace children.
  Uses a holder array to capture the result from inside `mapOverItems`, avoiding a
  double lookup.

- **WidgetStackView.java** — Added `findChildByAppWidgetId()` which searches the
  stack's child views by `LauncherAppWidgetInfo.appWidgetId`.

### Code Quality

- **WidgetStackView.java** — Added `forEachWidgetHostView(Consumer)` helper method
  that iterates over child `LauncherAppWidgetHostView` instances, eliminating
  duplicated iteration loops.

- **Launcher.java** — `pauseExpensiveViewUpdates()` and `resumeExpensiveViewUpdates()`
  now use `forEachWidgetHostView()` for stacked widgets instead of inline loops.

- **WidgetStackView.java** — Active index persistence is now debounced (1.5s delay)
  via `scheduleActiveIndexSave()` so rapid swipes don't fire a DB write per gesture.
  Pending save is cancelled in `onDetachedFromWindow()`.

## Design Decisions

- **Sanitization mirrors folders**: `sanitizeWidgetStacks()` follows the exact same
  pattern as `sanitizeFolders()` — check after item deletion, query for orphaned
  containers, delete in a transaction. This keeps the model layer consistent.

- **Debounced persistence over immediate writes**: The active index is a cosmetic
  preference (which widget is shown). Writing to DB on every swipe is excessive;
  a 1.5s debounce catches the final swipe and survives restarts without I/O churn.

- **Holder array for mapOverItems**: `mapOverItems` returns the top-level View, not
  nested children. A `LauncherAppWidgetHostView[1]` holder captures the nested
  result from inside the predicate, avoiding a second search and a TOCTOU race.
