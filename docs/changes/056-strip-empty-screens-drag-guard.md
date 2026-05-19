# 056 — Strip-empty-screens drag-state guard

## Summary

`Workspace.stripEmptyScreens()` (line 1072) defers itself via
`mStripScreensOnPageStopMoving` when a page transition is in flight
(line 1080). `onPageEndTransition()` (line 1279) then runs the deferred
strip unconditionally. If a drag ends mid-transition, the deferred
strip executes against drag state that may still target one of the
about-to-be-removed pages.

## Change

`Workspace.java:1291-1294`: gate the deferred strip on
`!mDragController.isDragging()`. If a drag is in progress, leave the
flag set so the next page-end transition (after the drag completes)
performs the strip.

## Verification

- Smoke 19/19 green.
- Manual regression: dragging an item across pages with the trailing
  empty screen no longer drops the page mid-drag.

## References

- v2 workspace plan Item 6 (`docs/plans/001-workspace-reliability-v2.md`).
- Tertiary audit finding #8c.
