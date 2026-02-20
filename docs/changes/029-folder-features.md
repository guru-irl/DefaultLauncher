# 029: Folder Features — Cover Icons, Expanded NxN Grid, Resize, Per-Folder Shapes

**Commit:** `01b62d3` (Folder feature: pre-refactoring snapshot)

## Summary

Added a comprehensive folder customization system with four interlocking features:
cover icons, expanded NxN grid folders, corner-drag resize, and per-folder icon shapes.
All per-folder data is stored in a dedicated `SharedPreferences` file managed by
`FolderCoverManager`.

## New Files

| File | Purpose |
|------|---------|
| `folder/FolderCoverManager.java` | Singleton managing cover icon + shape storage, Noto Emoji font loading and rendering |
| `folder/FolderPopupHelper.java` | Long-press popup menu for folder actions (cover, shape, resize) |
| `folder/FolderResizeFrame.java` | `AbstractFloatingView` overlay with corner drag handles for workspace resize |
| `folder/FolderSpanHelper.java` | Static helpers for span mutation (expand/collapse) with animations |
| `settings/FolderCoverPickerHelper.java` | Bottom sheet emoji picker for cover icon selection |
| `settings/FolderSettingsHelper.java` | Centralized folder appearance resolver (colors, shapes) and dialog builders |
| `assets/fonts/NotoEmoji-Medium.ttf` | Bundled Noto Emoji monochrome font (~880KB) |
| `res/layout/folder_resize_frame.xml` | Resize frame layout with four corner handles |
| `res/drawable/folder_resize_frame_border.xml` | Resize frame border stroke |
| `res/drawable/ic_expand_content.xml` | Expand indicator icon for expanded folders |
| `res/drawable/ic_shapes.xml` | Shape picker icon for popup menu |

## Modified Files

### FolderIcon.java (Major)

- **Three rendering modes:** Normal preview, cover icon, expanded NxN grid
- `mCoverDrawable` field holds loaded cover; `drawCoverIcon()` renders at 60% of view size
- `mIsExpanded` tracks expanded state; `drawExpandedFolder()` draws app icons in grid
- `mExpandedIconCache` (SparseArray) caches loaded app icons for the grid
- Direct app launching via `getExpandedCellIndex()` hit detection
- Per-folder shape resolution chain: per-folder → global → theme
- Long-press opens `FolderPopupHelper` popup instead of default AOSP behavior

### FolderAnimationManager.java

- Dual-mode animation: detects cover/expanded folders and adapts reveal strategy
- Cover/expanded folders use view dimensions (not preview circle) for the reveal origin
- Shape-aware: uses per-folder shape for covered, expanded shape for uncovered, global for normal
- Item animation: fade-in for cover/expanded (no translate), standard for normal

### PreviewBackground.java

- Added per-folder shape support via `setPerFolderShape()` / `setResolvedShape()`
- `getShape()` returns resolved shape, falling back to global/theme resolution

### FolderInfo.java

- Added `FLAG_EXPANDED = 0x10` to the options bitmask
- Added `MAX_SPAN = 3` constant

### AbstractFloatingView.java

- Added `TYPE_FOLDER_RESIZE_FRAME = 1 << 16`
- Included in `TYPE_ALL`, excluded from `TYPE_ACCESSIBLE`

### LauncherPrefs.kt

- Added folder appearance prefs: `FOLDER_COVER_BG_COLOR`, `FOLDER_ICON_SHAPE`,
  `FOLDER_BG_COLOR`, `FOLDER_BG_OPACITY`

### Folder.java

- Added cover picker dismiss hook — `FolderCoverPickerHelper.dismissIfShowing()`
  called during folder close

### Workspace.java / DragView.java

- Minor integrations for folder resize frame interaction

### AppDrawerColorsFragment.java

- Added folder color pickers to the Colors settings page

## String Resources Added

```
folder_cover_title, folder_cover_remove, folder_emoji_search_hint,
folder_custom_cover, folder_resize, folder_icon_shape_popup,
folder_shape_follow_global, folder_no_space,
folder_emoji_smileys, folder_emoji_animals, folder_emoji_food,
folder_emoji_activities, folder_emoji_travel, folder_emoji_objects,
folder_emoji_symbols, folder_settings_category, folder_icon_shape_title,
folder_icon_color_title, folder_bg_color_title, folder_bg_color_summary,
folder_cover_bg_title, folder_cover_bg_summary, folder_bg_opacity_title,
folder_color_default, folder_color_primary_container,
folder_color_secondary_container, folder_color_tertiary_container,
folder_color_surface_container_high, folder_color_primary,
folder_shape_default, folder_shape_circle, folder_shape_rounded_square,
folder_expanded_shape_title, folder_expanded_shape, folder_rename
```

## Dimension Resources Added

```
folder_resize_frame_handle_size, folder_resize_frame_stroke_width,
folder_resize_frame_corner_radius, folder_resize_frame_padding
```

## Architecture Decisions

1. **Dedicated SharedPreferences** — Per-folder covers and shapes stored separately
   from `LauncherPrefs` to avoid polluting the main preferences with per-item data.

2. **Bundled Noto Emoji font** — System emoji renders in color; the Noto Emoji Medium
   font guarantees monochrome rendering that harmonizes with M3 tonal theming.

3. **Square-only expansion** — Expanded folders are constrained to square spans
   (spanX == spanY) to simplify grid calculations and ensure consistent aesthetics.

4. **FLAG_EXPANDED in options bitmask** — Reuses AOSP's existing `options` column
   in the favorites database, avoiding schema changes.

5. **Shape resolution chain** — Per-folder → global → theme, matching the pattern
   used by per-app icon customization.

## See Also

- [docs/folders.md](../folders.md) — Full folder system architecture documentation
