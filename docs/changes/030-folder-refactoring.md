# 030: Folder Refactoring — Popup Improvements, Shared Adapter, Code Quality

**Branch:** `dev` (working tree changes on top of `01b62d3`)

## Summary

Refactoring pass over the folder feature set: improved the popup menu UX, extracted a
shared `CategoryGridAdapter` from duplicated grid code, centralized span mutation logic,
improved shape resolution, and fixed several code quality issues.

## Part A: Popup Menu Improvements

### A1. Dynamic Cover Label

**File:** `FolderPopupHelper.java`

The "Custom cover icon" popup shortcut now shows a context-aware label:
- **"Set cover"** when no cover is set (`FolderCoverManager.getCover() == null`)
- **"Change cover"** when a cover already exists

The `CustomCover` constructor accepts a `hasCover` boolean and stores the resolved
`mLabelRes` field. Both `setIconAndLabelFor()` and `setIconAndContentDescriptionFor()`
use the dynamic label instead of a static string.

### A2. "Remove cover" Popup Shortcut

**File:** `FolderPopupHelper.java`

New `RemoveCover` inner class extending `SystemShortcut<Launcher>`:
- Icon: `R.drawable.ic_remove_no_shadow` (reused from deleted bottom sheet row)
- Label: `R.string.folder_cover_remove` ("Remove cover")
- Only added to shortcuts list when a cover exists
- `onClick()`: removes cover, updates FolderIcon, closes all floating views

Previously, "Remove cover" was buried inside the emoji picker bottom sheet. Now it's
a top-level popup action visible alongside "Set cover" and "Shape".

### A3. Removed "Remove cover" from Bottom Sheet

**File:** `FolderCoverPickerHelper.java`

Deleted the entire `removeRow` block (~40 lines): the `LinearLayout removeRow`,
`ImageView removeIcon` with error-color tint, `TextView removeCover` with error text,
the click handler, and the `root.addView(removeRow)` call. The unused `colorError`
resolution was also cleaned up.

The bottom sheet now contains only: handle → title → search → emoji grid.

### A4. String Resource Changes

**File:** `res/values/strings.xml`

- Added: `folder_set_cover` ("Set cover"), `folder_change_cover` ("Change cover")
- Removed: `folder_custom_cover` ("Custom cover icon") — no longer referenced

## Part B: Shared CategoryGridAdapter

### B1. New CategoryGridAdapter

**File:** `settings/CategoryGridAdapter.java` (NEW)

A generic `RecyclerView.Adapter<T>` for categorized grids in bottom sheets. Eliminates
code duplication between the emoji picker and icon pack picker.

**Data model:**
```java
static class ListItem<T> {
    final int type;          // VIEW_TYPE_HEADER or VIEW_TYPE_ITEM
    final String headerTitle; // non-null for headers
    final T item;            // non-null for items
}
```

**Binding interface:**
```java
interface ItemBinder<T> {
    void bind(ImageView view, T item, int position);
    void onItemClick(T item);
    boolean matchesQuery(T item, String query);
    @Nullable String getContentDescription(T item);
}
```

**Features:**
- Inflates existing `item_icon_picker_header` and `item_icon_picker` layouts
- Built-in `filter(String query)` with header-aware grouping
- `setItems(List<ListItem<T>>)` to replace all items and reset filter
- Static `createGridLayoutManager()` helper: calculates span count from view width,
  sets SpanSizeLookup for full-width headers

### B2. FolderCoverPickerHelper Refactored

**File:** `settings/FolderCoverPickerHelper.java`

Major rewrite from `GridLayout` + `NestedScrollView` to `RecyclerView` + `CategoryGridAdapter`:

- Removed: `GridLayout` construction loop, `NestedScrollView`, manual `filterEmojis()`
  with visibility toggling, category header/grid tracking lists
- Added: `EmojiItem` inner class (pairs emoji string with lowercase category name)
- Uses `CategoryGridAdapter<EmojiItem>` with an inline `ItemBinder` that:
  - `bind()`: renders emoji via `FolderCoverManager.renderEmojiSmall()`
  - `onItemClick()`: sets cover, updates FolderIcon, dismisses sheet
  - `matchesQuery()`: checks category name + Unicode character names
- RecyclerView setup deferred to `rv.post()` for width-based span calculation
- Search via `adapter.filter()` with same 250ms debounce

Removed imports: `GridLayout`, `NestedScrollView`, `Typeface` (no longer needed).
Changed `emojiMatchesQuery()` from `private` to package-private (used by binder).

### B3. PerAppIconSheet Refactored

**File:** `settings/PerAppIconSheet.java`

Replaced internal grid infrastructure with `CategoryGridAdapter`:

- Removed: inner `ListItem` class, `IconGridAdapter` class, `filterItems()` method,
  `VIEW_TYPE_HEADER`/`VIEW_TYPE_ICON` constants
- Uses `CategoryGridAdapter<IconPack.IconEntry>` with an inline `ItemBinder` that:
  - `bind()`: async loads icon via `Executors.MODEL_EXECUTOR` with tag-based cancellation
  - `onItemClick()`: dismisses sheet, calls `callback.onIconSelected()`
  - `matchesQuery()`: matches against `entry.label` and `entry.drawableName`
- `loadIconsWithSuggested()` now builds `List<CategoryGridAdapter.ListItem<...>>`
  and calls `adapter.setItems(items)` on the main thread
- Search via `adapter.filter()` instead of `filterItems()`

Removed imports: `LayoutInflater`, `@NonNull`, `GridLayoutManager` (accessed through
`CategoryGridAdapter.createGridLayoutManager()`).

## Part C: Code Quality Improvements

### C1. Centralized Span Mutation

**Files:** `FolderResizeFrame.java`, `FolderSpanHelper.java`

`FolderResizeFrame` previously duplicated span mutation logic (updating FolderInfo,
CellLayoutLayoutParams, cell occupation, expanded state flag). Now calls
`FolderSpanHelper.applySpanChange()` as the single source of truth.

Removed: ~20 lines of inline model updates from `FolderResizeFrame.applyResize()`.

### C2. Single Source of Truth for Shape

**Files:** `FolderIcon.java`, `PreviewBackground.java`, `FolderAnimationManager.java`

- `PreviewBackground.setPerFolderShape()` renamed to `setResolvedShape()` — it now
  receives the fully resolved shape from `FolderIcon.resolveCurrentShape()`
- `PreviewBackground.getShape()` made package-private (was private) — used by
  `FolderAnimationManager` for normal folder reveal shape
- `FolderAnimationManager` no longer independently resolves shapes; reads from
  `FolderIcon` (covers/expanded) or `PreviewBackground` (normal)
- Shape resolution happens once in `FolderIcon.inflateIcon()` and `updatePerFolderShape()`

### C3. Grid Params Caching

**File:** `FolderIcon.java`

`computeExpandedGridParams()` now caches results in `mCachedGridParams`, invalidated
on `onSizeChanged()` and `updateExpandedState()`. Previously recomputed every frame
during `drawExpandedFolder()`.

### C4. Expanded Shape Accessor

**File:** `FolderIcon.java`

Added `getCachedExpandedShape()` — returns the M3 Large token rounded square used by
`FolderAnimationManager`. Previously the animation manager recomputed this independently.

### C5. ItemClickHandler for App Launches

**File:** `FolderIcon.java`

Expanded folder cell taps now use `ItemClickHandler.onClickAppShortcut()` instead of
inline `startActivitySafely()`. This routes through the standard click handler which
handles disabled apps, promise icons, work profile, safe mode, analytics, etc.

### C6. MAX_SPAN Constant

**File:** `FolderInfo.java`

Extracted `MAX_SPAN = 3` constant (was hardcoded `3` in `FolderResizeFrame`).

### C7. Pref Rename for Clarity

**Files:** `LauncherPrefs.kt`, `FolderSettingsHelper.java`, `AppDrawerColorsFragment.java`

Renamed `FOLDER_ICON_COLOR` to `FOLDER_COVER_BG_COLOR` for clarity — the pref key
`"pref_folder_icon_color"` is preserved for backward compatibility, only the Kotlin
constant name changed.

### C8. Resize Frame Type Fix

**File:** `FolderResizeFrame.java`

`isOfType()` now checks `TYPE_FOLDER_RESIZE_FRAME` instead of `TYPE_WIDGET_RESIZE_FRAME`.
Previously the folder resize frame identified as a widget resize frame, which could
cause incorrect close behavior.

## Impact

- **Lines removed:** ~650 (duplicated grid code, inline model updates, bottom sheet row)
- **Lines added:** ~250 (CategoryGridAdapter, RemoveCover, EmojiItem)
- **Net reduction:** ~400 lines
- **Files changed:** 13 (8 modified, 2 new, 0 deleted)
- **Build verified:** `assembleDebug` passes

## See Also

- [docs/folders.md](../folders.md) — Full folder system architecture documentation
- [029-folder-features.md](029-folder-features.md) — Original folder feature implementation
