# 036: Widget Stack Touch Interception & Visibility Fixes

## Summary

Fixes two widget stack bugs: (1) vertical swipes on stacked widgets incorrectly triggering global gestures (notification shade, AllApps drawer), and (2) widget stacks randomly disappearing on Samsung devices.

## Bug 1: Vertical Scroll Triggers Global Gestures

### Problem

Swiping vertically on a scrollable widget inside a multi-widget stack triggered the notification shade (StatusBarSwipeController) or AllApps drawer (AllAppsSwipeController) instead of scrolling the widget.

### Root Cause

In `WidgetStackView.onInterceptTouchEvent()`, the vertical gesture handler called `requestDisallowInterceptTouchEvent(childCanScroll)`. When the child couldn't scroll (at scroll bounds, or non-scrollable), this passed `false`, which **cleared** the disallow flag on all parent ViewGroups. `DragLayer.onInterceptTouchEvent()` then ran `findActiveController()`, and the swipe controllers (initialized on `ACTION_DOWN`) detected accumulated vertical displacement and intercepted.

Standalone widgets work because `LauncherAppWidgetHostView.onInterceptTouchEvent()` calls `requestDisallowInterceptTouchEvent(true)` on `ACTION_DOWN` and never clears it.

### Fix

**`WidgetStackView.java` -- `onInterceptTouchEvent()`**
- Removed the `requestDisallowInterceptTouchEvent(childCanScroll)` call from the vertical gesture branch
- Now just cancels long-press and lets the child widget handle everything
- The child's `LauncherAppWidgetHostView` already blocks DragLayer for scrollable widgets (set on `ACTION_DOWN`), matching standalone behavior
- Removed now-unused `getActiveWidgetView()` helper (only caller was the removed code)

## Bug 2: Disappearing Stacks on Samsung

### Problem

Widget stacks randomly vanished from the workspace on Samsung devices but reappeared on launcher restart. Database was intact -- view-level visibility bug.

### Root Cause

`prepareDrawDragView()` set non-active children to `View.GONE` and relied on a `SafeCloseable` lambda to restore visibility. `GONE` removes views from the layout pass entirely, and Samsung's aggressive layout optimization may not properly restore views transitioning from `GONE` to `INVISIBLE`/`VISIBLE`. The rest of the codebase uses `INVISIBLE` exclusively for hidden children. Additionally, if the stack was detached and re-attached (Samsung memory management), child visibility was never restored.

### Fix

**`WidgetStackView.java` -- `prepareDrawDragView()`**
- Changed `GONE` to `INVISIBLE` for non-active children (consistent with all other visibility management)
- Explicitly sets active child to `VISIBLE` (guards against stale state from cancelled animations)

**`WidgetStackView.java` -- `onAttachedToWindow()`**
- Added override that calls `updateChildVisibility()` on re-attach
- Safety net for Samsung's aggressive view recycling/re-attachment

## Additional: Workspace Resize-on-Drop for Widget Stacks

**`Workspace.java` -- `completeDropFromDrag()`**
- Extended resize-on-drop logic to handle `WidgetStackView` (previously only `AppWidgetHostView`)
- Calls `wsv.updateChildWidgetSizes()` to propagate new span to all children
- Added `ITEM_TYPE_WIDGET_STACK` to the `isWidget` check for drop animation type selection

## Files Changed

| File | Changes |
|------|---------|
| `WidgetStackView.java` | Remove `requestDisallowInterceptTouchEvent` from vertical gesture; remove `getActiveWidgetView()`; `GONE` -> `INVISIBLE` in `prepareDrawDragView()`; add `onAttachedToWindow()` |
| `Workspace.java` | Resize-on-drop support for widget stacks; `isWidget` check includes stacks |
