# 061 — CellLayout.resetCellSize occupancy: documentation

Tier 2 / Item 8 of `docs/plans/001-workspace-reliability-v2.md`. Docs-only.

## Summary

The plan flagged a theoretical hazard in `CellLayout.resetCellSize`: the
method clears `mCellWidth = mCellHeight = -1` and `mFixedCellWidth = -1` but
does **not** clear the `mOccupied` / `mTmpOccupied` `GridOccupancy` bitmaps.
If a caller were to change `mCountX` / `mCountY` between `resetCellSize` and
the next measure pass without going through `setGridSize`, stale occupancy
entries could survive a grid-size change.

Audit (this change): the combination is **unreachable** in the current
codebase. There are exactly three callers and none can race the occupancy
bitmap:

| Caller | Behavior |
|--------|----------|
| `Hotseat.onDeviceProfileChanged` (lines 173-177) | Always calls `setGridSize(1, dp.numShownHotseatIcons)` or the landscape equivalent immediately after `resetCellSize`. `setGridSize` reallocates `mOccupied`. |
| `FolderPagedView.java:150` | Uses `setGridSize` directly, never calls `resetCellSize`. |
| `FolderPagedView.java:297` | Same. |

Therefore no code change is required. The audit observation is preserved in
`docs/grid-system.md` so future maintainers who add a new caller see the
constraint and re-validate at the new site.

## Fix

Added a new subsection "CellLayout.resetCellSize and grid occupancy (audit
note)" under "## CellLayout: The Workspace Grid View" in
`docs/grid-system.md`. The note:

- Explains why occupancy bitmaps survive `resetCellSize` intentionally
  (same `mCountX` / `mCountY` → same logical grid).
- Enumerates the three current callers and their post-conditions.
- Names the invariant a future change must preserve.

## Verification

- Markdown-only; no build needed.
- No code or test changes.

## Files

- `docs/grid-system.md` — +1 subsection (~12 lines).
- `docs/changes/061-celllayout-resetcellsize-occupancy-note.md` (this file)
