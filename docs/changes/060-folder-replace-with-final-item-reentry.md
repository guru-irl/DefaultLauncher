# 060 — Folder.replaceFolderWithFinalItem re-entry guard

Tier 2 / Item 4 of `docs/plans/001-workspace-reliability-v2.md`.

## Problem

`Folder.replaceFolderWithFinalItem()` is reachable from four call sites in
`Folder.java`:

- `:547` — `onDragEnter` path when a stale folder is detected.
- `:970` — `closeComplete()` when `itemCount <= 1` and no drag is active.
- `:1136` — `onDropCompleted()` when `mDeleteFolderOnDropCompleted` was set
  earlier.
- `:1566` — accessibility close path.

The comment at `Folder.java:720-722` (preserved verbatim from upstream)
explicitly documents the hazard:

> One resulting issue is that replaceFolderWithFinalItem() can be called
> twice.

The race is real: drag an item out of a single-item folder (sets
`mDeleteFolderOnDropCompleted = true`), then drag it back in without
dropping; closing the folder hits `closeComplete()` → triggers the destroy
path, and the subsequent `onDropCompleted()` fires again from
DragController. Both paths call `replaceFolderWithFinalItem`.

`LauncherDelegate.replaceFolderWithFinalItem()` (`LauncherDelegate.java:80`)
is **not** idempotent — it removes the folder icon, moves the last child
into the workspace, and logs a stat. A second call would NPE on the
already-removed folder icon, double-add the child, or both.

## Idempotency check (from plan)

The plan offered two implementations:

1. **Token guard** (`mPendingDeleteToken`): `closeComplete()` bumps the token
   on each invocation; `replaceFolderWithFinalItem()` snapshots at entry and
   bails if a fresher token appears. Heavier, defends against subtle
   sequencing across multiple Looper-loop hops.

2. **`mDestroyed` short-circuit**: simpler, applicable when
   `mDestroyed = mLauncherDelegate.replaceFolderWithFinalItem(this)` is set
   before any second-caller can re-enter.

Audit: the delegate call returns `true` synchronously (it kicks off an
async `performDestroyAnimation` runnable, but the field assignment to
`mDestroyed` happens before this method returns). All four call sites run
on the main thread. Therefore `mDestroyed` is set to `true` *before* any
re-entrant call from any of the four sites can be dispatched, and the
simple flag-guard is sufficient.

The plan's downgrade criterion ("if `replaceFolderWithFinalItem` is already
idempotent on `mDestroyed`") — strictly the delegate is not idempotent, but
the Folder wrapper's flag-after-first-call pattern provides the same
guarantee at the only call site that matters. Going with the simpler
guard.

## Fix

Add a top-of-method early-return in `Folder.replaceFolderWithFinalItem()`:

```java
if (mDestroyed) {
    return;
}
```

A comment cites the closeComplete + onDropCompleted race path so future
maintainers don't remove it.

## Verification

- `assembleDebug` clean.
- `tests-e2e/smoke/` + `regression/`: 21/21 in 60.51s.
- Manual: drag the second-to-last item out of a folder, drag it back in,
  drop on the workspace — the folder collapses to its single survivor
  exactly once and no second-removal warning appears in logcat.

## Files

- `src/com/android/launcher3/folder/Folder.java`
  - +5 lines (guard + comment) inside `replaceFolderWithFinalItem`.
- `docs/changes/060-folder-replace-with-final-item-reentry.md` (this file)
