# 051 — DiffUtil decoration equality

## Summary

`BaseAllAppsAdapter.AdapterItem.isContentSame()` previously checked only
whether both items had a null `itemInfo`. The `decorationInfo` field — a
`SectionDecorationInfo` carrying round-region flags and grouping state —
was completely ignored. As a result, decoration-only changes on header /
divider items (which always have `itemInfo == null`) caused `DiffUtil` to
return same-content and skip the rebind, leaving stale visual state. This
was the bug behind AOSP TODO b/325455879 and the reason
`PrivateProfileManager.java:704` had to fall back to a full
`notifyDataSetChanged()`.

## Change

### `SectionDecorationInfo`
- Add `equals(Object)` and `hashCode()` comparing the visible fields:
  `mRoundRegions` (canonical mask) and `mShouldDecorateItemsTogether`.
  The decoration handler is derived from these, so its identity does not
  need to participate in equality.
- Add private final `mRoundRegions` field so equality can compare the
  canonical mask rather than the derived `mIsTopRound` / `mIsBottomRound`
  pair (which is lossy).
- The XML-bundle constructor sets `mRoundRegions = ROUND_NOTHING`. AOSP's
  XML decoration path is not used by our shipping config.

### `BaseAllAppsAdapter.AdapterItem`
- `isContentSame(AdapterItem other)` now short-circuits to `false` when
  `decorationInfo` differs by `Objects.equals(...)`.
- The original `itemInfo == null && other.itemInfo == null` clause is
  preserved as a secondary check so app-icon items still trigger rebind
  via the existing path.

## Effect

- Header items (`itemInfo == null`) whose decoration changes (e.g. round
  region flipping when private space toggles) now correctly trigger a
  rebind through DiffUtil.
- App-icon items (`itemInfo != null`) continue to always trigger rebind
  via the original clause (unchanged behavior).

## Follow-up (not in this change)

`PrivateProfileManager.java:704`'s `notifyDataSetChanged()` is now
redundant after this fix. Removing it is tracked separately — the change
needs a private-space e2e setup in `tests-e2e/regression/` that exercises
lock / unlock cycles and verifies no stale decoration. Until that test
exists, the workaround stays so we have a definitive safety net.

## Verification

- Build green.
- Smoke suite 19/19 + regression 2/2 = 21/21 passing on `emulator-5554`.
- Behavioral spot-check: drawer open / close / search round-trip cycles
  show no visual regression.

## References

- Drawer invariants doc (`docs/architecture/drawer-invariants.md`) entry
  #12 documents the load-bearing `notifyDataSetChanged` that this change
  enables removing.
- AOSP TODO b/325455879.
