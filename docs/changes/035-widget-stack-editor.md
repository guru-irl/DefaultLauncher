# 035: Widget Stack Editor

## Summary

Bottom sheet editor for reordering, removing, and adding widgets within a stack. Extends `AbstractSlideInView<Launcher>` with M3 card grouping, animated drag reorder, and consistent theming with the Widgets bottom sheet.

## New Files

| File | Purpose |
|------|---------|
| `src/.../widget/WidgetStackEditorView.java` | Bottom sheet editor view (AbstractSlideInView + Insettable) |
| `src/.../widget/WidgetStackEditorAdapter.java` | RecyclerView adapter with TYPE_WIDGET and TYPE_ADD view types |
| `src/.../widget/WidgetStackEditorItemDecoration.java` | Position-aware rounded card backgrounds (mirrors CardGroupItemDecoration) |
| `src/.../widget/WidgetStackPopupHelper.java` | Adds "Edit stack" and "Remove stack" popup shortcuts |
| `res/layout/widget_stack_editor.xml` | Editor bottom sheet layout (drag handle, centered title, RecyclerView) |
| `res/layout/widget_stack_editor_item.xml` | M3 two-line list item (preview, label, app name, drag handle, remove button) |
| `res/drawable/ic_add_widget.xml` | 24dp plus icon for the add row |
| `res/drawable/ic_drag_handle.xml` | Drag handle icon |
| `res/drawable/ic_edit.xml` | Edit icon for popup shortcut |
| `res/drawable/bg_preview_rounded.xml` | Rounded widget preview thumbnail background |
| `res/drawable/bg_icon_badge.xml` | App icon badge circular background |

## Modified Files

| File | Change |
|------|--------|
| `res/values/styles.xml` | `WidgetContainerTheme` primary surface changed from `surfaceContainerHigh` to `surfaceContainerLow` (M3 bottom sheet spec) |
| `src/.../widget/WidgetsBottomSheet.java` | Uses `WidgetStackEditorView.createSheetBackground()` with native `widgetsTheme` context (removed HomeSettings_Theme wrapper) |
| `src/.../popup/PopupContainerWithArrow.java` | Calls `WidgetStackPopupHelper` to add stack-specific shortcuts |
| `src/.../AbstractFloatingView.java` | Added `TYPE_WIDGET_STACK_EDITOR` floating view type |
| `src/.../Workspace.java` | Added `setPendingExternalStackTarget()` / `consumePendingExternalStackTarget()` for picker-to-stack flow |
| `src/.../graphics/DragPreviewProvider.java` | WidgetStackView recognized for content-view drag preview |
| `src/.../model/WorkspaceItemProcessor.kt` | Collection membership check for widget stack children |
| `res/values/strings.xml` | `widget_stack_editor_title`, `widget_stack_add_widget`, `widget_stack_remove_widget`, `widget_stack_drag_handle` |
| `res/values/ids.xml` | `editor_drag_state_tag`, `editor_ripple_tag` |
| `res/values/dimens.xml` | `widget_stack_editor_drag_elevation` |

## Key Design Decisions

### AbstractSlideInView over AbstractFloatingView

The editor uses `AbstractSlideInView` for swipe-to-dismiss, predictive back animation, and the `TRANSLATION_SHIFT` open/close pattern. This matches `WidgetsBottomSheet` and `BaseWidgetSheet`.

### WidgetContainerTheme for color consistency

Both sheets resolve background colors from `WidgetContainerTheme` attrs (`widgetPickerPrimarySurfaceColor`, `widgetPickerSecondarySurfaceColor`) rather than M3 material attrs directly. The editor inflates with `HomeSettings_Theme` (for MaterialToolbar/MaterialButton) but creates a separate `widgetsTheme`-wrapped context for color resolution via `getWidgetThemeContext()`.

### M3 bottom sheet color spec

Updated `WidgetContainerTheme` primary surface from `surfaceContainerHigh` to `surfaceContainerLow` to match the [M3 bottom sheet spec](https://m3.material.io/components/bottom-sheets/specs). The hierarchy is: sheet (`surfaceContainerLow`) < items (`surfaceContainer`) < dragged item (`surfaceContainerHighest`).

### "Add widget" as a list item

The "Add widget" action is the last item in the RecyclerView (TYPE_ADD) rather than a separate tonal button below the list. This follows the Pixel Settings pattern for "add another item" actions and keeps the UI as a single scrollable card group. The add row has a smaller 56dp height, 40dp `+` icon, and is excluded from drag reorder via `getMovementFlags()` and `canDropOver()`.

### Drag animation

Pickup and drop animations run at 100ms (`M3Durations.SHORT_2`) with corner radii morphing between position-aware and fully-rounded states. Scale (1.02x) and elevation (8dp) provide visual lift.

## Documentation

- Updated [`docs/widget-stack.md`](../widget-stack.md) with Widget Stack Editor section covering architecture, adapter view types, item decoration, drag reorder, drag animations, color hierarchy, theme context chain, nav bar scrim, and popup integration. Updated file manifest and resources.
