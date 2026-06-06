# 066 — Search: delete unused DefaultAppSearchAlgorithm + DefaultSearchAdapterProvider

T2.2 Phase 4 of `docs/plans/002-search-reliability-v2.md`.

## Summary

Two leftover classes from before universal search was wired in:

- `src/com/android/launcher3/allapps/search/DefaultAppSearchAlgorithm.java`
- `src/com/android/launcher3/allapps/search/DefaultSearchAdapterProvider.java`

A pre-deletion `grep` for both class names across `src/` found:

- Zero callers in production code (no `new DefaultAppSearchAlgorithm(...)`,
  no `new DefaultSearchAdapterProvider(...)`).
- One historical javadoc reference in `AppSearchProvider.java:27`
  ("Extracted from DefaultAppSearchAlgorithm").

The `AllAppsSearchUiDelegate` and `ActivityAllAppsContainerView` paths
that used to instantiate these classes already construct
`UniversalSearchAlgorithm` and `UniversalSearchAdapterProvider` instead;
the dead classes are not referenced from any factory, DI binding, or
manifest entry.

## Fix

- `rm` both files.
- Reword the `AppSearchProvider` javadoc to drop the historical
  reference (it referred to a class that no longer exists).
- `docs/changes/016` retained as the historical record of the universal
  search migration; the deletion is a follow-up, not a rewrite of the
  history.

## Verification

- `assembleDebug` clean (no compile errors — no callers existed).
- `tests-e2e/smoke/` + `regression/`: 21/21 in 60.09s. The search tests
  continue to exercise the universal-search path; deletion did not
  touch any live class.
- Manual: opened search, typed `calc 2+2`, hit Enter — calculator
  result rendered and launched. No regressions to the still-used
  `SearchAdapterProvider` interface.

## Files

- `src/com/android/launcher3/allapps/search/DefaultAppSearchAlgorithm.java` (deleted)
- `src/com/android/launcher3/allapps/search/DefaultSearchAdapterProvider.java` (deleted)
- `src/com/android/launcher3/search/providers/AppSearchProvider.java`
  - 1-line javadoc reword.
- `docs/changes/066-search-default-providers-dead-code.md` (this file)
