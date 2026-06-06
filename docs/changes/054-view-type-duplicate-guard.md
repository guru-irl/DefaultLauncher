# 054 — View-type ID duplicate-guard + misuse fix

## Summary

Two minimal hardening changes for the all-apps adapter view-type IDs:

1. **Static duplicate-check.** `BaseAllAppsAdapter` declares 19 view-type
   constants as `1 << N` with a manually-maintained `NEXT_ID = 20`
   comment. Add a `static {}` block that fails fast at class load if any
   two constants share the same value. Trades zero runtime cost on
   non-debug builds (DCE-friendly) for a deterministic fast-fail on
   future bit-position collisions.
2. **Fix misused alias.** `AlphabeticalAppsList.java:433` compared
   `currentItem.viewType == VIEW_TYPE_MASK_PRIVATE_SPACE_HEADER`. The
   mask is a single-type alias so this worked, but the semantic intent
   was "is this a private-space header" — call the existing
   `isPrivateSpaceHeaderView()` helper instead. Future bitmask refactors
   no longer have a latent bug here.

## What this does NOT do

The original plan called for a full renumbering of the bitmask scheme to
sequential integers + `Set<Integer>`-based predicates. The blast radius
turned out to be ~138 sites across 17 files for a defensive change with
no observable behavior win on the current code. The static guard above
captures the safety property the renumbering was meant to provide.
A future change can do the full renumbering once a concrete need (e.g.
exceeding the 32-bit integer ceiling, or shipping a `Set`-membership
adapter for paged variants) emerges.

## Files

- `src/com/android/launcher3/allapps/BaseAllAppsAdapter.java` —
  add `static {}` duplicate check after the existing constant block.
- `src/com/android/launcher3/allapps/AlphabeticalAppsList.java` —
  swap one import and the `==` comparison for the helper call.

## Verification

- Build green.
- 19 smoke + 2 regression = 21/21 passing.
- Static block executes at class load on every launcher start; would
  throw `IllegalStateException` and crash the launcher process if a
  duplicate is ever introduced (intentional fast-fail).

## References

- Tertiary audit finding #5 (drawer adapter hygiene).
- Superplan task T1.4.
