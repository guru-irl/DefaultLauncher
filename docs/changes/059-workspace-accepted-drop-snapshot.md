# 059 — Workspace: snapshot accepted drop layout

Tier 2 / Item 7 of `docs/plans/001-workspace-reliability-v2.md`.

## Problem

`Workspace.acceptDrop(d)` and `Workspace.onDrop(d, options)` both read
`mDropToLayout` independently:

- `acceptDrop` at `Workspace.java:1799` reads it into a local; if the read is
  non-null, the method may return `true`.
- `onDrop` at `Workspace.java:2187` re-reads the field from scratch.

In between the two calls, `onDragEnter()` can fire and null `mDropToLayout`
out (`:2534`). This happens in practice on:

- Synthetic re-enter from the accessibility drag path.
- DragController re-entry when a drag is canceled and immediately restarted
  with the same DragObject (rare but reproducible).

The visible failure is `onDrop` operating on a null `dropTargetLayout` while
the caller (DragController → DragObject) already assumed `acceptDrop` returned
true, producing either a silently dropped item or a NullPointerException
inside `mapPointFromDropLayout` (depending on the call site).

## Fix

Snapshot `mDropToLayout` into a new transient field `mAcceptedDropLayout` at
every `return true` in `acceptDrop`. `onDrop` reads the snapshot first, then
falls back to the live `mDropToLayout` for backward compatibility. The
snapshot is cleared in `onDragEnd()`, mirroring the existing lifecycle reset
of `mDragInfo` and `mDragSourceInternal`.

### Where it's set

- After `willCreateUserFolder` succeeds (folder-creation path).
- After `willAddToExistingUserFolder` succeeds (folder-add path).
- After the general `return true` past the reorder/cell-found path.

(Skipped on every `return false` — by definition there is no accepted layout.)

### Where it's read

- `onDrop(...)` line 2189 reads `mAcceptedDropLayout != null ?
  mAcceptedDropLayout : mDropToLayout`. Falling back instead of replacing
  means existing internal call sites (e.g. drag re-entry where acceptDrop
  was bypassed) still behave as before.

### Where it's cleared

- `onDragEnd()` — alongside `mDragInfo = null;` / `mDragSourceInternal =
  null;`. Ensures no stale snapshot survives into the next drag session.

## Constraints respected

- No change to `DragController` contract.
- No change to the meaning of `mDropToLayout` itself; this is an additive
  field. Other readers (hotseat drop paths, external drop logic) keep their
  behavior.
- Doesn't break the `onDragExit → mDropToLayout = mDragTargetLayout`
  protocol that the existing code relies on.

## Verification

- `assembleDebug` clean.
- `tests-e2e/smoke/` + `regression/`: 21/21 in 58.38s.
- Manual: drag an app icon between workspace pages and into a folder — drop
  position and folder-add both honor the accepted target.

## Files

- `src/com/android/launcher3/Workspace.java`
  - +1 field, +3 capture sites, +1 read-with-fallback, +1 clear in `onDragEnd`.
- `docs/changes/059-workspace-accepted-drop-snapshot.md` (this file)
