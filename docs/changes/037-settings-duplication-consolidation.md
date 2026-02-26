# 037: Settings Component Deduplication

## Summary

Consolidated duplicated code across the settings package into shared utilities,
eliminating ~600+ lines of copy-paste duplication without changing any visual behavior.

## New Files

### `BaseCardItemDecoration.java`
Abstract base class for RecyclerView card-style item decorations. Extracts shared
Paint/Path/RectF setup, corner radius computation, ripple application, and onDraw
loop from `CardGroupItemDecoration` and `WidgetStackEditorItemDecoration`.

Key API:
- `computeCornerRadii(isFirst, isLast, largeR, smallR)` — static, also used by
  `WidgetStackEditorView.computePositionRadii()`
- Abstract hooks: `shouldSkipItem()`, `isFirstInGroup()`, `isLastInGroup()`
- Shared `applyRipple()` with configurable tag ID

### `SettingsSheetBuilder.java`
Fluent builder for bottom sheet dialogs + static factory methods:

```java
SheetComponents c = new SettingsSheetBuilder(ctx)
    .setTitle(R.string.title)
    .dismissOnDestroy(fragment)
    .build();
// populate c.contentArea
c.showScrollable();
```

Static helpers:
- `addSheetHandle(root, ctx, res)` — standard M3 drag handle
- `dismissOnDestroy(fragment, sheet)` — lifecycle-aware dismiss
- `createCard(ctx, label, icon, bgColor, textColor, listener)` — settings card
- `createSearchBar(ctx, hintResId, debounceMs, onFilter)` — styled EditText with debounce
- `hideDefaultViews(holder)` — hides default preference child views

## Modified Files

### Phase 1: BaseCardItemDecoration
- **CardGroupItemDecoration** — extends BaseCardItemDecoration, implements
  PreferenceGroupAdapter-aware hooks
- **WidgetStackEditorItemDecoration** — extends BaseCardItemDecoration, implements
  simple position-based hooks
- **WidgetStackEditorView** — delegates to `BaseCardItemDecoration.computeCornerRadii()`
- **Themes** — added `createRoundedRipple()` utility

### Phase 2: Shape Picker Consolidation
- **IconSettingsHelper** — added unified `showShapePickerDialog()`, migrated
  `showIconShapeDialog()` and `showPerAppShapeDialog()` to thin wrappers,
  migrated `showIconPackDialog()`/`showPerAppPackDialog()` to use builder/createCard
- **FolderSettingsHelper** — `showShapeDialogInternal()` delegates to
  `IconSettingsHelper.showShapePickerDialog()`

### Phase 3-4: Caller Migrations
- **PerAppIconSheet** — uses `SettingsSheetBuilder.addSheetHandle()`,
  `createCard()`, `createSearchBar()`; deleted dead `createPackCard()` method
- **FolderCoverPickerHelper** — uses `SettingsSheetBuilder.addSheetHandle()`,
  `createSearchBar()`; removed manual TextWatcher and search bar construction
- **ColorPickerPreference** — uses `SettingsSheetBuilder.addSheetHandle()`;
  replaced inline handle bar with standard dimen-based handle
- **M3SliderPreference** — uses `SettingsSheetBuilder.hideDefaultViews()`
- **ColorDebugPreference** — uses `SettingsSheetBuilder.hideDefaultViews()`
- **GridPreviewPreference** — uses `SettingsSheetBuilder.hideDefaultViews()`

## Architecture Notes

- Search bars use a deferred adapter reference pattern (`Object[] adapterRef`)
  when the adapter is created asynchronously (e.g., inside `rv.post()`)
- The `createSearchBar()` debounce callback fires on the main thread, making
  it safe to call `adapter.filter()` directly
- `hideDefaultViews()` iterates root's direct children and sets GONE + resets
  minimum height — equivalent to the previous per-view-ID hiding approach
