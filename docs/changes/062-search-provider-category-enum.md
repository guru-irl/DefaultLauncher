# 062 — Search: replace stringly-typed provider category with enum

T2.2 Phase 1 (part 1 of 2) of `docs/plans/002-search-reliability-v2.md`.

## Problem

`SearchProvider.category()` returned a `String`. Every dispatch site
(`UniversalSearchAlgorithm.isProviderEnabled`, `dispatchProvider`) did a
`switch` on the literal value. Adding a provider required:

- Picking a new string verbatim and reusing it at the call site.
- Adding a new `case "literal":` arm at every dispatch site.

If either step was missed, the result was a silent default branch: the new
provider was either always-enabled (mis-routed into "default: return true")
or its results were silently dropped (no matching case in `dispatchProvider`).
Tertiary audit confirmed every provider returns a unique hardcoded constant,
so the indirection bought nothing.

## Fix

New `enum ProviderCategory { APPS, SHORTCUTS, CONTACTS, CALENDAR, FILES,
CALCULATOR, UNIT_CONVERTER, TIMEZONE, QUICK_ACTIONS }` in
`com.android.launcher3.search.providers`.

- `SearchProvider.category()` now returns `ProviderCategory`.
- All 9 implementations changed (one literal each).
- `UniversalSearchAlgorithm` switches over the enum at both sites. A new
  `case APPS:` arm in `dispatchProvider` documents that AppSearchProvider
  uses its own dispatch path (progressive INTERMEDIATE delivery in
  `doSearch`) and is never routed through the generic switch.

## Why this is one atomic commit

The interface return-type change is breaking. Touching it incrementally
means leaving the build red between sub-commits, which violates the
"build/test green per commit" invariant for the refactor branch. The total
diff is small (one new file + one-line return changes in 9 providers + two
switch updates), so it lands as one commit per the plan's "single atomic
commit" risk callout.

## Verification

- `assembleDebug` clean.
- `tests-e2e/smoke/` + `regression/`: 21/21 in 59.14s. The
  `test_multiprovider_query_renders` and `test_rapid_typing_no_stale_results`
  regression tests exercise four providers in parallel (apps, shortcuts,
  calculator, timezone), so the dispatch switches are exercised end-to-end.
- Manual: opened search drawer, typed "calc 2+2" — calculator result
  rendered; typed "tokyo" — timezone result rendered; typed an app name —
  app section rendered.

## Files

- `src/com/android/launcher3/search/providers/ProviderCategory.java` (new, 23 lines)
- `src/com/android/launcher3/search/providers/SearchProvider.java`
- `src/com/android/launcher3/search/providers/AppSearchProvider.java`
- `src/com/android/launcher3/search/providers/CalculatorProvider.java`
- `src/com/android/launcher3/search/providers/CalendarSearchProvider.java`
- `src/com/android/launcher3/search/providers/ContactSearchProvider.java`
- `src/com/android/launcher3/search/providers/FileSearchProvider.java`
- `src/com/android/launcher3/search/providers/QuickActionProvider.java`
- `src/com/android/launcher3/search/providers/ShortcutSearchProvider.java`
- `src/com/android/launcher3/search/providers/TimezoneProvider.java`
- `src/com/android/launcher3/search/providers/UnitConverterProvider.java`
- `src/com/android/launcher3/search/UniversalSearchAlgorithm.java`
- `docs/changes/062-search-provider-category-enum.md` (this file)
