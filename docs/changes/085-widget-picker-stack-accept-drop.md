# 085 — Fix: widget from picker rejected when stack fills the workspace page

**Branch:** `refactor/t0.1-search-4param-override`
**Date:** 2026-06-05
**Type:** Bug fix

## Symptom

Dragging a widget from the system widget picker onto an existing
`WidgetStackView` fails silently. The drag indicator highlights the stack
during hover, but on release the user sees an "out of space" toast and the
widget is not added.

## Root cause (confirmed from Samsung logs 2026-06-05)

`Workspace.acceptDrop()` evaluates whether a drag should be allowed BEFORE
`onDrop()` runs. For external drops it checks:
- folder-create / folder-add cases → accepted via early return
- otherwise: `performReorder(... MODE_ACCEPT_DROP)` to find a vacant cell
- if no vacant cell → `onNoCellFound()` + toast + return false

The **widget-stack cases were missing** from this list. When a stack fills
the page (common on Samsung's larger grids — e.g. 6×6 stack at cell (0,5)),
no vacant cell exists for the dragged widget. `acceptDrop` rejects the drop
before `onDropExternal()` can consume `mPendingExternalStackTarget`.

Samsung log evidence:
```
Workspace: manageFolderFeedback: addToStackPending=true dragMode=5
Workspace: willAddToExistingWidgetStack: ... result=true   ← hover detection works
Toast:     caller = com.android.launcher3.Workspace.onNoCellFound:2518
StateManager: at LauncherDragController.exitDrag(:227)     ← drag rejected
```

The diagnostic log I added to `onDropExternal` (`onDrop: mAddToWidgetStackOnDrop=...`)
never fires — confirming the drop is rejected upstream in `acceptDrop`.

## Fix

Add widget-stack acceptance checks in `Workspace.acceptDrop()` immediately
after the folder checks, mirroring the same pattern:

```java
if (mAddToWidgetStackOnDrop && WidgetStackInfo.willAcceptItemType(d.dragInfo.itemType)) {
    View dropOverView = dropTargetLayout.getChildAt(mTargetCell[0], mTargetCell[1]);
    if (dropOverView instanceof WidgetStackView) {
        mAcceptedDropLayout = dropTargetLayout;
        return true;
    }
}
if (mCreateWidgetStackOnDrop && WidgetStackInfo.willAcceptItemType(d.dragInfo.itemType)) {
    View dropOverView = dropTargetLayout.getChildAt(mTargetCell[0], mTargetCell[1]);
    if (dropOverView instanceof LauncherAppWidgetHostView
            && willCreateWidgetStack(d.dragInfo, dropOverView)) {
        mAcceptedDropLayout = dropTargetLayout;
        return true;
    }
}
```

This allows the drop to proceed to `onDrop()` → `onDropExternal()`, which
already has the stack-handling logic (sets `mPendingExternalStackTarget`,
then `Launcher.completeAddAppWidget()` consumes it via
`addWidgetToExistingStack()` or `createStackFromExternalDrop()`).

## Files changed

| File | Change |
|------|--------|
| `src/com/android/launcher3/Workspace.java` | `acceptDrop()` — add widget-stack accept checks |
| `docs/changes/085-widget-picker-stack-accept-drop.md` | this doc |

## Verification

- Build: `assembleDebug` passes.
- Manual: drag widget from picker onto existing stack on Samsung Galaxy.
  Expected: widget joins the stack (no toast).
- Test: emulator does not exercise this path (no widget picker support);
  test coverage relies on the physical phone.

## Related diagnostic logging

The Workspace.java DEBUG-only diagnostic logging added in change 084
remains in place (`willAddToExistingWidgetStack`, `manageFolderFeedback`,
`onDrop: mAddToWidgetStackOnDrop=...`). It does not affect production
behavior and is useful for future widget-drop debugging. Can be removed in
a cleanup pass.
