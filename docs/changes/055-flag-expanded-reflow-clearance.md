# 055 — FLAG_EXPANDED cleared at reflow site

## Summary

When `SquareGridReflow` clamps an expanded folder's span to fit a smaller
column count, the resulting span may violate the FLAG_EXPANDED render
invariant (`spanX == spanY && spanX >= 2`). Previously the flag stayed
set in the database while the new spans were persisted alongside it,
leaving a logically inconsistent row. Any code that read the flag
without re-validating spans inherited a latent bug; refactor-proofing
that property starts here.

## Why not in `FolderIcon.updateExpandedState`

`FolderIcon.java:409-411` carries a load-bearing comment (referenced as
drawer invariant #...; documented in change 029) explicitly forbidding
the bit from being cleared during bind: "the flag should survive in the
database for when spans are properly restored — e.g. after config
change." Clearing on bind would erase user intent on every config
change. The clearance therefore happens at the only site where the
database state is *actually* being made inconsistent: inside the reflow
clamp logic.

## Change

`src/com/android/launcher3/model/SquareGridReflow.java`:
- Extend `ReflowEntry` with `int options` and `boolean optionsChanged`
  fields. Constructor reads the `OPTIONS` column (cursor projects all
  columns at line 152, so no projection change needed).
- After span clamping, if `(options & FLAG_EXPANDED) != 0` and the
  post-clamp span no longer satisfies the expanded-render invariant,
  clear the bit and set `optionsChanged = true`.
- DB write loop persists `OPTIONS` whenever `optionsChanged`, regardless
  of whether `moved` was also set.

## Verification

- Build green.
- Smoke + regression: 21/21.
- Manual repro plan (documented for future verification on a device
  with restored content): create a 2x2 expanded folder at column 5,
  decrease columns to 3 via Settings, restart launcher process, observe
  the folder rendered as 1x1 (collapsed). Re-increase columns to 5 and
  verify the folder stays 1x1 — proving FLAG_EXPANDED was cleared in
  DB, not just suppressed at render.

## References

- v2 workspace plan Item 1 (`docs/plans/001-workspace-reliability-v2.md`).
- Drawer invariants doc (FLAG_EXPANDED comment is load-bearing).
- Change 029 — folder features (established the FLAG_EXPANDED contract).
- Tertiary audit finding #1 (corrected the v1 misframing).
