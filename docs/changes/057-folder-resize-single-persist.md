# 057 — Folder resize: single-persist per gesture

Tier 2 / Item 3 of `docs/plans/001-workspace-reliability-v2.md`.

## Problem

`FolderResizeFrame` persisted the folder's span twice per typical resize-then-close
gesture:

1. `onTouchUp()` (line 538) — fires on `ACTION_UP`/`ACTION_CANCEL`, immediately
   after the user releases the corner handle.
2. `handleClose(animate)` (line 563) — fires when the frame dismisses, which
   happens via outside-touch, back/ESC key, drag-start from `DragController`,
   *or* immediately after a successful `onTouchUp` once the user taps outside.

Both call `ModelWriter.updateItemInDatabase(mFolderIcon.mInfo)`, producing two
single-row SQLite UPDATEs back-to-back with identical payloads. Harmless but
wasteful, and a real concern because the resize gesture is one of the few user
flows that fires multiple ModelWriter writes in <100ms.

## Constraints from the plan

- Do **not** touch `FolderSpanHelper.applySpanChange` (lines 150 / 249) — those
  persist paths are reachable from the popup menu without the resize frame
  ever being shown, so they're independent persistence sites.
- Do **not** introduce a new public ModelWriter API.
- Keep `handleClose` persisting on the paths that genuinely bypass `onTouchUp`
  (outside-touch dismiss, back/ESC, drag-start cancel).

## Fix

Add a `mPersistedOnTouchUp` flag on `FolderResizeFrame`.

- `onTouchUp()` sets `mPersistedOnTouchUp = true;` immediately after the persist
  call.
- `handleClose(animate)` persists **only if** `!mPersistedOnTouchUp`.

Net effect:

| Gesture | Before | After |
|---------|--------|-------|
| Drag handle → release → tap outside to close | 2 writes | 1 write |
| Drag handle → release → press Back | 2 writes | 1 write |
| Tap outside without resizing | 1 write | 1 write |
| Press Back without resizing | 1 write | 1 write |
| Start a drag (DragController) on the frame | 1 write | 1 write |

`mPersistedOnTouchUp` is instance state — each frame instance is short-lived
(per resize session), so the flag resets implicitly on the next gesture.

## Verification

- `assembleDebug` clean (1 build warning unchanged).
- `tests-e2e/smoke/` + `tests-e2e/regression/`: 21/21 in 60.99s.
- Manual: resize a folder on the AVD, dismiss via outside-touch, then via back —
  span/position survive both paths and the resize frame redraws correctly on
  re-open.

## Files

- `src/com/android/launcher3/folder/FolderResizeFrame.java`
  - +5 lines (flag + comment + guard)
- `docs/changes/057-folder-resize-single-persist.md` (this file)
