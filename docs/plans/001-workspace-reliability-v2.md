# Plan 001 — Workspace Reliability v2

Supersedes the original workspace reliability v1 plan in `docs/plans/000-architectural-refactor-superplan.md`.

## Why v2

The v1 plan failed secondary review on four points; v2 addresses each, drops one item entirely as out-of-scope, and absorbs five tertiary-audit hazards in the same subsystem.

## Non-goals (explicit)

- **No edits to `FolderIcon.updateExpandedState()` or its comment at `FolderIcon.java:409-411`.** Load-bearing per drawer-invariants doc + `docs/changes/029`.
- **No "compaction" of hotseat items.** The monotonic `HOTSEAT_MAX_DB_COUNT` at `InvariantDeviceProfile.java:521-524` is intentional per change 002.
- **No new `FolderInfo.normalizeFlags()` helper or bind-time fix in `WorkspaceItemProcessor.kt`.** Clean state must be guaranteed at the reflow site.
- **No SQLite transaction wrapper around `ModelWriter.updateItemInDatabase()`** — single-row UPDATE is already atomic.
- **No new public ModelWriter API.**

## Items

### Item 1 — FLAG_EXPANDED preserved through DB reflow (FIRST)

When a column-count decrease clamps an expanded folder to an invalid span, `SquareGridReflow.java` must clear FLAG_EXPANDED in the entry's `options` field and persist alongside the new spans. `FolderIcon.updateExpandedState` stays untouched.

**Implementation:**
- `SquareGridReflow.java:62-83` — extend `ReflowEntry` with `int options` and `boolean optionsChanged`.
- `SquareGridReflow.java:71-78` — read `Favorites.OPTIONS` in constructor (cursor already projects all columns at line 152).
- `SquareGridReflow.java:167-182` — after clamp, if `options & FLAG_EXPANDED != 0` and post-clamp span violates `spanX==spanY && spanX>=2`, clear the bit and set `optionsChanged=true`.
- `SquareGridReflow.java:266-286` — extend write gate to include `optionsChanged`; put `OPTIONS` into ContentValues when it changed.

**Test:** `tests-e2e/regression/test_folder_flag_expanded_reflow.py`.

### Item 2 — Hotseat: no action

Removed from scope. Document the rationale in `docs/grid-reflow.md` if not already explicit.

### Item 3 — Folder span persistence: call-site consolidation

The same span change persists from multiple sites within a single touch sequence. Consolidate.

**Implementation:**
- Audit `FolderResizeFrame.onTouchUp` (`:520`, persist at `:538`) and `handleClose` (`:552`, persist at `:563`).
- Remove redundant persist at `FolderResizeFrame.java:563`, guard with `mPersistedOnTouchUp` flag for paths that bypass `onTouchUp`.
- Do NOT touch `FolderSpanHelper.java:150`/`:249` — reachable from popup menu without resize frame.

**Test:** `tests-e2e/smoke/test_folder_resize_single_persist.py` (assert single `updateItemInDatabase` per gesture via logcat tag parsing).

### Item 4 — `Folder.onDropCompleted` re-entry race

Token guard. `Folder.java:720-722` documents the hazard.

**Implementation:**
- Introduce `private long mPendingDeleteToken` in `Folder.java`.
- `closeComplete()` `:964` and `:1124` bump the token.
- `replaceFolderWithFinalItem()` `:1332` snapshots at entry and bails if a fresher token has been issued.
- If `mLauncherDelegate.replaceFolderWithFinalItem` is already idempotent on `mDestroyed`, downgrade to `if (mDestroyed) return;`.

**Test:** `tests-e2e/regression/test_folder_drag_out_in_race.py`.

### Item 5 — Open folder + grid rebuild dangling ref

Close the folder on `OnDeviceProfileChangeListener.onDeviceProfileChanged`.

**Implementation:**
- Register listener in `Folder.animateOpen()` (~`:701`); override `onDeviceProfileChanged` to call `close(false)`; unregister in `closeComplete` (~`:934`).
- User data is safe: `FolderInfo` is model-side; only the open `Folder` view is detached.

**Test:** `tests-e2e/regression/test_folder_open_during_grid_change.py`.

### Item 6 — `stripEmptyScreens` during drag

Add `!mDragController.isDragging()` guard in `Workspace.java:1291` before deferred strip; keep `mStripScreensOnPageStopMoving=true` so strip runs at next end-of-transition.

**Test:** `tests-e2e/regression/test_strip_screens_drag_safety.py`.

### Item 7 — `acceptDrop` async `mDropToLayout`

Snapshot `mDropToLayout` into a transient field `mAcceptedDropLayout` on successful return of `acceptDrop()`. `onDrop()` reads `mAcceptedDropLayout` if non-null. Clear on `onDragEnd`.

**Test:** logcat-asserting regression test.

### Item 8 — `CellLayout.resetCellSize` occupancy

Scope out. Bug is unreachable in current configuration. Document in `docs/grid-system.md`.

## Sequencing

1. Item 1 (data integrity at reflow). Smoke gate.
2. Items 5, 6, 7 in parallel (drag state — independent files). Smoke gate.
3. Item 4 (after delegate-idempotency check). Smoke gate.
4. Item 3 (consolidation) — lowest risk, hardest to test.
5. Item 8 — docs-only, any time.

## Risks

- Item 1: any code path reading `options & FLAG_EXPANDED` without span validation could subtly change. Grep showed only FolderSpanHelper / FolderIcon / FolderResizeFrame, all of which validate spans.
- Item 4: `replaceFolderWithFinalItem` is third-party AOSP code; audit before merging.
- Item 5: closing folder on DP change is UX-visible but acceptable.
- Item 7: snapshot field must be cleared on `onDragEnd` to avoid leaks.

## Critical files

- `src/com/android/launcher3/model/SquareGridReflow.java`
- `src/com/android/launcher3/folder/Folder.java`
- `src/com/android/launcher3/Workspace.java`
- `src/com/android/launcher3/folder/FolderResizeFrame.java`
- `src/com/android/launcher3/folder/FolderSpanHelper.java`
