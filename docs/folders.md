# Folder System

This document describes the custom folder feature set built on top of AOSP Launcher3's
`Folder`, `FolderIcon`, and `FolderPagedView` foundation. The additions include cover
icons, expanded NxN grid folders, workspace resize, per-folder shapes, and a unified
settings/popup system.

---

## Table of Contents

1. [Overview](#overview)
2. [Cover Icons](#cover-icons)
3. [Expanded Folders (NxN Grid)](#expanded-folders-nxn-grid)
4. [Folder Resize](#folder-resize)
5. [Per-Folder Shapes](#per-folder-shapes)
6. [Folder Colors](#folder-colors)
7. [Popup Menu](#popup-menu)
8. [Animations](#animations)
9. [Data Model](#data-model)
10. [Key Files](#key-files)
11. [Data Flow Diagrams](#data-flow-diagrams)

---

## Overview

The folder system extends AOSP's default folder behavior with four custom feature layers:

| Feature | Description |
|---------|-------------|
| **Cover icons** | Replace the mini-icon preview with a single emoji or icon-pack icon |
| **Expanded folders** | Display folder contents directly on the workspace as an NxN grid |
| **Resize** | Drag corner handles to grow/shrink folders on the workspace |
| **Per-folder shapes** | Override the global icon shape on a per-folder basis |

All per-folder data (covers, shapes) is stored in a dedicated `SharedPreferences` file
(`"folder_covers"`) managed by `FolderCoverManager`, separate from `LauncherPrefs`.

---

## Cover Icons

### What They Are

A cover icon replaces the folder's standard multi-icon preview with a single prominent
icon — an emoji rendered in monochrome via the Noto Emoji font, or an icon from an
installed icon pack.

### Storage

Covers are stored as `"packPackage|drawableName"` strings keyed by folder ID:

| Type | packPackage | drawableName | Example |
|------|-------------|--------------|---------|
| Emoji | `"emoji"` | The emoji string | `"emoji\|🦄"` |
| Icon pack | Package name | Drawable resource name | `"com.foo.icons\|ic_camera"` |

### Rendering

- **Emoji covers** are rendered by `FolderCoverManager.renderEmoji()` using the bundled
  `fonts/NotoEmoji-Medium.ttf` typeface. This guarantees monochrome rendering that
  harmonizes with M3 Expressive theming — the system emoji font renders in color.
- The emoji is drawn at 96dp with `materialColorOnSurface` via a `Paint` + `Canvas`
  to a `BitmapDrawable`.
- `renderEmojiSmall()` creates smaller emoji for the picker grid (26dp, 70% text size).
- **Icon pack covers** are loaded via `IconPackManager` on a background thread.

### Picker UI

`FolderCoverPickerHelper` shows a bottom sheet with:

1. A drag handle
2. "Choose cover icon" title
3. A search bar (EditText with debounced 250ms filter)
4. A `RecyclerView` grid powered by `CategoryGridAdapter<EmojiItem>`

The grid contains ~300 curated emojis across 7 categories: Smileys, Animals, Food,
Activities, Travel, Objects, Symbols. Search matches against:

- **Category name** — typing "food" shows the entire Food category
- **Unicode character name** — typing "cat" matches 🐱 ("CAT FACE"), 🐈 ("CAT"), etc.

### FolderIcon Integration

`FolderIcon.mCoverDrawable` holds the loaded cover. When non-null, `dispatchDraw()`
calls `drawCoverIcon()` instead of the normal preview rendering:

```
dispatchDraw()
  ├─ mCoverDrawable != null → drawCoverIcon()  (60% of view size, centered)
  ├─ mIsExpanded && no cover → drawExpandedFolder()  (NxN grid)
  └─ else → normal AOSP preview (mini icons in circle)
```

---

## Expanded Folders (NxN Grid)

### Concept

An expanded folder occupies multiple cells on the workspace (2x2 or 3x3) and displays
its app icons directly in a grid, eliminating the need to tap-and-open for frequently
used folders. The last cell shows an "expand" indicator — tapping it opens the full
folder.

### Rendering

`FolderIcon.drawExpandedFolder()` draws the NxN grid:

1. Computes grid params: cell size, border spacing, icon size, start offsets
2. Draws app icons row-major from `mExpandedIconCache`
3. The last cell draws `R.drawable.ic_expand_content` tinted `materialColorOnSurfaceVariant`
4. Grid params are cached in `mCachedGridParams` (invalidated on size change)

The expanded shape uses M3 Large token (`R.dimen.m3_shape_large`) as a rounded square,
cached in `mCachedExpandedShape`.

### Touch Handling

Expanded uncovered folders support **direct app launching**:

- `getExpandedCellIndex(x, y)` performs hit detection on the grid cells
- Tapping a cell with an app launches it via `ItemClickHandler.onClickAppShortcut()`
- Tapping the last cell (expand indicator) or an empty cell opens the folder normally
- Long-press always shows the popup menu (resize + cover + shape)

### State Management

The expanded state is tracked by `FolderInfo.FLAG_EXPANDED` (0x10) in the `options`
bitmask, persisted to the Launcher database. Validation in `updateExpandedState()`:

```
expanded = FLAG_EXPANDED is set
         AND spanX > 1
         AND spanY > 1
         AND spanX == spanY  (must be square)
```

If the flag is set but the span is invalid, the flag is cleared automatically.

---

## Folder Resize

### FolderResizeFrame

`FolderResizeFrame` is an `AbstractFloatingView` (type `TYPE_FOLDER_RESIZE_FRAME`)
that overlays the workspace with four corner drag handles.

**Behavior:**

- Dragging a corner changes the folder's span (always kept square: spanX == spanY)
- Minimum span: 1x1, maximum span: `FolderInfo.MAX_SPAN` (3), clamped to grid size
- Resize only succeeds if the target region is vacant on the `CellLayout`
- Corner drag adjusts cell position when resizing from top/left (preserves opposite corner)
- Threshold-based: span increment triggers at 0.66+ cell fractions

**Persistence:**

On touch-up, the final span/position is written to the database via `ModelWriter`.
`FolderSpanHelper.applySpanChange()` centralizes the model + layout + occupation updates.

### FolderSpanHelper

Static helper that centralizes folder span mutations:

| Method | Description |
|--------|-------------|
| `applySpanChange()` | Updates model, layout params, cell occupation, expanded state |
| `expandToSpan(target)` | Finds vacancy, applies change, animates scale-up |
| `collapseToOneByOne()` | Animates scale-down, then applies 1x1 collapse |

`applySpanChange()` is the single source of truth — both `FolderResizeFrame` and
`FolderSpanHelper.expandToSpan()`/`collapseToOneByOne()` call it.

---

## Per-Folder Shapes

### Shape Resolution Priority

Each folder icon resolves its shape through a three-level priority chain:

```
1. Per-folder icon shape key  (FolderCoverManager.getIconShape(folderId))
2. Global folder icon shape    (LauncherPrefs.FOLDER_ICON_SHAPE)
3. Theme default               (ThemeManager.getFolderShape())
```

`FolderIcon.resolveCurrentShape()` walks this chain. The result is pushed to
`PreviewBackground.setResolvedShape()` — making `PreviewBackground` the single source
of truth for drawing and animation.

### Per-Folder Shape Dialog

`FolderSettingsHelper.showIconShapeDialogForFolder()` shows a bottom sheet with shape
options: "Follow global" + all available shapes from `ShapesProvider`. Selecting a shape:

1. Calls `FolderCoverManager.setIconShape(folderId, shapeKey)`
2. Calls `FolderIcon.updatePerFolderShape(shapeKey)`
3. `PreviewBackground.setResolvedShape()` updates the drawing shape
4. `invalidate()` triggers redraw

### Expanded Folder Shape

Expanded uncovered folders use a separate shape: `mCachedExpandedShape`, computed as
an M3 Large token rounded square based on view dimensions. This shape is used by
`FolderAnimationManager` for the open/close reveal animation.

---

## Folder Colors

### Color Settings

Folder colors are managed through `LauncherPrefs` and resolved by `FolderSettingsHelper`:

| Preference | Purpose | Default |
|-----------|---------|---------|
| `FOLDER_COVER_BG_COLOR` | Cover icon background | `materialColorSurface` |
| `FOLDER_BG_COLOR` | Folder icon + open panel background | `materialColorSurfaceContainerLow` |
| `FOLDER_BG_OPACITY` | Open panel opacity (0-100) | 95 |
| `FOLDER_ICON_SHAPE` | Global folder icon shape | Theme default |

Color values are stored as tonal palette key names (e.g., `"primary_container"`,
`"secondary_container"`) and resolved via `AllAppsColorResolver`.

### Color Resolution

| Method | Used For |
|--------|----------|
| `getEffectiveCoverBgColor()` | Cover icon background (1x1 covered folders) |
| `getEffectiveFolderBgColor()` | Folder icon background (1x1 uncovered) |
| `getEffectivePanelColor()` | Open folder panel background (with opacity) |

The color picker UI lives in `AppDrawerColorsFragment` under the "Folders" settings
category.

---

## Popup Menu

### FolderPopupHelper

Long-pressing a folder shows a `PopupContainerWithArrow` with these system shortcuts:

| Shortcut | Icon | Label | Condition |
|----------|------|-------|-----------|
| **CustomCover** | 🦄 (Noto Emoji) | "Set cover" / "Change cover" | Always shown |
| **RemoveCover** | `ic_remove_no_shadow` | "Remove cover" | Only when cover is set |
| **FolderShape** | `ic_shapes` | "Shape" | Hidden for expanded uncovered folders |

**Dynamic label:** The CustomCover label changes based on whether a cover already exists
(`FolderCoverManager.getCover() != null`).

**Resize frame:** For workspace folders (not hotseat), `FolderResizeFrame.showAlongsidePopup()`
displays the resize handles alongside the popup.

---

## Animations

### FolderAnimationManager

The open/close folder animation adapts to three folder modes:

| Mode | Shape Source | Background Color | Item Animation |
|------|-------------|-----------------|----------------|
| Normal (1x1, no cover) | `PreviewBackground.getShape()` | `getEffectiveFolderBgColor()` | Standard AOSP preview item translate |
| Covered | `FolderIcon.resolveCurrentShape()` | `getEffectiveCoverBgColor()` | Item fade-in (no translate) |
| Expanded uncovered | `FolderIcon.getCachedExpandedShape()` | `getEffectiveFolderBgColor()` | Item fade-in (no translate) |

**Shape reveal:** Uses `ShapeDelegate.createRevealAnimator()` to transition the folder
background from the icon's shape to the full-screen folder panel.

### FolderSpanHelper Animations

- **Expand (1x1 → NxN):** Scale-up from old cell size to new cell size
  (`M3Durations.MEDIUM_1`, decelerate interpolator)
- **Collapse (NxN → 1x1):** Scale-down from current size to 1x1 cell
  (`M3Durations.MEDIUM_1`, accelerate interpolator, pivot at 0,0)

---

## Data Model

### FolderInfo Additions

```java
// options bitmask
public static final int FLAG_EXPANDED = 0x00000010;  // folder is expanded on workspace

// Span limit
public static final int MAX_SPAN = 3;  // max NxN grid size
```

The `options` field is persisted in the `favorites` table. `spanX`/`spanY` already
existed in AOSP for widget support — expanded folders reuse this mechanism.

### AbstractFloatingView Addition

```java
public static final int TYPE_FOLDER_RESIZE_FRAME = 1 << 16;
```

Added to `TYPE_ALL` and excluded from `TYPE_ACCESSIBLE` (resize frame should not
capture accessibility focus).

### FolderCoverManager SharedPreferences Schema

File: `"folder_covers"` (separate from main launcher prefs)

| Key Pattern | Value | Purpose |
|------------|-------|---------|
| `"{folderId}"` | `"packPackage\|drawableName"` | Cover icon |
| `"shape_{folderId}"` | Shape key string | Per-folder expanded shape |
| `"iconshape_{folderId}"` | Shape key string | Per-folder icon shape |

---

## Key Files

| File | Purpose |
|------|---------|
| `folder/FolderCoverManager.java` | Singleton: cover/shape storage, emoji rendering |
| `folder/FolderIcon.java` | Workspace view: cover/expanded drawing, touch handling |
| `folder/FolderPopupHelper.java` | Long-press popup: cover, remove, shape shortcuts |
| `folder/FolderResizeFrame.java` | Corner-drag resize overlay |
| `folder/FolderSpanHelper.java` | Static span mutation helpers with animations |
| `folder/FolderAnimationManager.java` | Open/close reveal animations (mode-aware) |
| `folder/PreviewBackground.java` | Background drawing with resolved shape |
| `settings/FolderSettingsHelper.java` | Color/shape resolution, dialog builders |
| `settings/FolderCoverPickerHelper.java` | Emoji picker bottom sheet |
| `settings/CategoryGridAdapter.java` | Shared adapter for categorized bottom sheet grids |
| `model/data/FolderInfo.java` | FLAG_EXPANDED, MAX_SPAN constants |
| `AbstractFloatingView.java` | TYPE_FOLDER_RESIZE_FRAME |
| `LauncherPrefs.kt` | Global folder prefs (colors, shape, opacity) |

---

## Data Flow Diagrams

### Setting a Cover Icon

```
User long-presses folder
  → FolderPopupHelper.showForFolderWithDrag()
    → PopupContainerWithArrow shown
      → User taps "Set cover"
        → FolderCoverPickerHelper.showCoverPicker()
          → Bottom sheet with emoji grid (CategoryGridAdapter)
            → User taps emoji
              → FolderCoverManager.setCover(folderId, CoverIcon("emoji", "🎮"))
              → FolderIcon.updateCoverDrawable()
                → mCoverDrawable = renderEmoji("🎮")
              → FolderIcon.invalidate()
                → dispatchDraw() → drawCoverIcon()
```

### Removing a Cover

```
User long-presses folder (that has a cover)
  → PopupContainerWithArrow shown (with "Remove cover" shortcut)
    → User taps "Remove cover"
      → FolderCoverManager.removeCover(folderId)
      → FolderIcon.updateCoverDrawable()
        → mCoverDrawable = null
      → FolderIcon.invalidate()
        → dispatchDraw() → normal preview or expanded grid
```

### Resizing a Folder

```
User long-presses folder
  → FolderPopupHelper shows popup + FolderResizeFrame
    → User drags corner handle
      → FolderResizeFrame.onControllerTouchEvent()
        → computeNewSpan() checks vacancy
        → FolderSpanHelper.applySpanChange(folderIcon, cellLayout, newSpan, cellX, cellY)
          → Updates FolderInfo (spanX, spanY, options, cellX, cellY)
          → Updates CellLayoutLayoutParams
          → Re-marks cell occupation
          → FolderIcon.updateExpandedState()
        → On touch-up: ModelWriter persists to DB
```

### Shape Resolution

```
FolderIcon.resolveCurrentShape()
  ├─ mPerFolderShapeKey set? → FolderSettingsHelper.resolveShapeKey(key) → return
  ├─ Global FOLDER_ICON_SHAPE set? → FolderSettingsHelper.resolveFolderIconShape() → return
  └─ ThemeManager.getFolderShape() → return

Result → PreviewBackground.setResolvedShape(shape)
       → Used by drawBackground(), FolderAnimationManager
```
