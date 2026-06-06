# 065 — Search: move result→adapter conversion into the adapter provider

T2.2 Phase 3 of `docs/plans/002-search-reliability-v2.md`.

## Problem

`UniversalSearchAlgorithm.deliverResults` was responsible for both
**dispatch** (gating on session abandonment, calling the callback) and
**conversion** (turning a `SearchResult` into the ordered
`AdapterItem` list with view-type labels and section headers). Conversion
is purely view-layer work — it depends on:

- The `VIEW_TYPE_SEARCH_*` constants owned by `BaseAllAppsAdapter`.
- The filter mask and category labels (string resources).
- The empty-state placeholder shape (an `AdapterItem` with
  `VIEW_TYPE_EMPTY_SEARCH`).

`UniversalSearchAdapterProvider` already owns every other view-type-aware
concern — `isViewSupported`, `onCreateViewHolder`, `onBindView`, the
category-priority best-match tracking. The conversion logic belonged
there too. Keeping it in the algorithm meant:

- Adding a new search category required edits in three places (provider,
  algorithm dispatch, algorithm deliver).
- The algorithm carried `R`, `AppInfo`, and ~10 `VIEW_TYPE_*` imports
  purely for the conversion body.
- Filter changes had to call back into the algorithm to re-deliver — the
  filter listener at `UniversalSearchAlgorithm.java:94` was the only
  consumer of the old `deliverResults()` no-arg path.

## Fix

New public static method:

```java
public static ArrayList<AdapterItem> convertResults(
        Context context,
        SearchResult result,
        SearchFilters filters,
        String query,
        int resultCode);
```

on `UniversalSearchAdapterProvider`. The body is the verbatim conversion
sequence from the old `deliverResults` — same category order, same
section-header gating, same filter checks, same `WEB_SEARCH` skip, same
empty-state fallback. `synchronized(result)` is taken inside the method
to match the producer-side locking pattern.

`UniversalSearchAlgorithm.deliverResults(SearchSession s, int resultCode)`
collapses to:

```java
if (s.abandoned || s != mActiveSession) return;
ArrayList<AdapterItem> items = UniversalSearchAdapterProvider.convertResults(
        mContext, s.accumulator, mFilters, s.query, resultCode);
s.callback.onSearchResult(s.query, items, resultCode);
```

The algorithm sheds 10 imports (VIEW_TYPE_* constants, `R`, `AppInfo`,
and the result types that were used only inside the conversion body — the
result types used for cast in `dispatchProvider` stay).

## Why static, not an instance method

`convertResults` has no per-instance state — it consumes inputs, returns
the converted list. Making it a static utility avoids forcing the
algorithm to hold a reference to the adapter provider (which only exists
inside `ActivityAllAppsContainerView` after `setAppsList` is wired in).

The adapter provider's instance state (`mBestMatch`, `mHighlightedView`,
`mAppsList`) is touched only by `onBindView` and `launchHighlightedItem`
— neither participates in the conversion. Keeping conversion side-effect-
free preserves the property that two parallel sessions could compute
their adapter lists independently if we ever wanted that.

## Non-goals carried from plan

- Phase 3 does **not** delete `DefaultAppSearchAlgorithm` /
  `DefaultSearchAdapterProvider` (Phase 4 deferred).
- No new file. The plan explicitly says "no new file, no new interface".

## Verification

- `assembleDebug` clean.
- `tests-e2e/smoke/` + `regression/`: 21/21 in 60.09s. Search smoke +
  multi-provider + rapid-typing regression all exercise the new
  conversion path.
- Manual: opened search, toggled the filter bar chips (Apps, Tools,
  Contacts) — the filter listener calls back through
  `deliverResults → convertResults` and the visible adapter list updates
  immediately with no re-dispatch.

## Files

- `src/com/android/launcher3/search/UniversalSearchAdapterProvider.java`
  - +110 lines: `convertResults` static method, two new imports (`AppInfo`,
    `VIEW_TYPE_EMPTY_SEARCH`, `ArrayList`).
- `src/com/android/launcher3/search/UniversalSearchAlgorithm.java`
  - −110 lines: conversion body removed. Imports pruned: VIEW_TYPE_*,
    `R`, `AppInfo` no longer needed.
- `docs/changes/065-search-convert-results-abstraction.md` (this file)
