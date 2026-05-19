# 058 — Close open folder on DeviceProfile change

Tier 2 / Item 5 of `docs/plans/001-workspace-reliability-v2.md`.

## Problem

When a `Folder` is open and the device profile changes (rotation, multi-window
resize, grid-config swap from Settings, dpi-preference toggle), the workspace
rebuilds its `CellLayout` children but the open `Folder` view retains stale
references:

- `centerAboutIcon()` cached the folder's screen position against the old
  DeviceProfile.
- The `mFolderIcon` reference can be detached/replaced during grid rebind.
- Touch geometry no longer matches what the user sees on screen.

The user-visible failure mode was a folder snapping to a wrong screen location
or accepting taps at coordinates that don't correspond to its rendered tiles.

## Fix

Listen for `DeviceProfile.OnDeviceProfileChangeListener` callbacks while the
folder is open. On any callback, close immediately (animation off — the DP
change itself is a hard cut). FolderInfo lives on the model, so user data is
untouched; only the transient open view is dismissed.

Wiring:

- New field `mDpChangeListener` on `Folder` (lambda → `close(false)`).
- `animateOpen(...)` registers it on `mActivityContext` right after the folder
  view is attached to the DragLayer. Guarded by the same `if (getParent() ==
  null)` block so we don't double-register if `animateOpen` is re-entered after
  a quick reopen (defensive — the existing parent check already logs that case
  as a known bug).
- `closeComplete(...)` unregisters in the same lifecycle phase where the view
  is detached from the drag layer. Safe to call even if the listener was never
  registered — the underlying `ArrayList.remove(Object)` is a no-op.

## Constraints respected

- No changes to `FolderInfo` or its model-side persistence.
- No change to `FolderIcon.updateExpandedState()` (per drawer-invariants doc).
- DragController drop-target lifecycle is unchanged — the listener register/
  unregister mirrors the existing `addDropTarget` / `removeDropTarget` pair.

## Verification

- `assembleDebug` clean.
- `tests-e2e/smoke/` + `regression/`: 21/21 in 59.19s.
- Manual: open a folder, rotate the AVD — folder dismisses cleanly without
  layout artifacts. Re-open works.

## Files

- `src/com/android/launcher3/folder/Folder.java`
  - +1 field, +1 register, +1 unregister.
- `docs/changes/058-folder-close-on-dp-change.md` (this file)
