# 063 — Search: SearchSession + provider snapshotting

T2.2 Phase 1 (part 2 of 2) of `docs/plans/002-search-reliability-v2.md`.

## Problem

`UniversalSearchAlgorithm` stored per-query state directly on instance fields:
`mCurrentResult`, `mCurrentQuery`, `mCurrentCallback`, `mPendingProviders`.
Provider callbacks captured `this` and read those fields when results
arrived.

Three failure modes followed:

1. **Late-callback corruption.** Provider X dispatched for query "a", user
   types "ab" before X returns. Algorithm rebuilds `mCurrentResult` for
   "ab". X's late callback then mutates `mCurrentResult.contacts` (now
   belonging to "ab") with results computed against "a". Visible as
   "search results jumping back" — the regression test
   `test_rapid_typing_no_stale_results` already catches some cases, but
   the underlying primitive was unsafe.

2. **Pending-counter underflow.** Old query had N providers; new query has
   M < N. Each late callback decrements the shared counter. The new query's
   FINAL fires too early; the empty-state placeholder may flash before
   late results are dropped.

3. **Pref-toggle race.** A Settings panel toggling `SEARCH_CONTACTS=false`
   mid-flight could change `isProviderEnabled(provider)` between the count
   loop and the dispatch loop in `doSearch`. The pending counter could
   overshoot the actual dispatch count.

## Fix

New `final class SearchSession` capturing one query's lifetime:

```java
final long version;
final String query;
final EnumSet<ProviderCategory> enabledProviders;  // snapshot at creation
final SearchCallback<AdapterItem> callback;
final SearchResult accumulator;                    // mutable, sync-protected
final AtomicInteger pendingProviders;
volatile boolean abandoned;
```

`UniversalSearchAlgorithm` now owns:
- `private volatile SearchSession mActiveSession;`
- `private final AtomicLong mNextVersion;`

`doSearch`:
1. Mark the previous session abandoned.
2. Call `cancel(true)` to interrupt providers.
3. Build the new session (snapshot enabled-providers from prefs).
4. Publish via `mActiveSession = s`.
5. Dispatch lambdas capture `s` by reference.

Every provider callback opens with `if (s.abandoned || s != mActiveSession)
return;` — both checks are needed:
- `abandoned` catches the case where a newer session opened.
- `s != mActiveSession` catches the racy case where the abandoned-flag
  hasn't been seen yet because the new doSearch hasn't reached the
  abandon-mark line.

`deliverResults(s, code)` is parameterized on the session — no more reads
from instance fields. Re-checks the bail condition before delivery so a
session abandoned between the last callback and the post-to-main-thread
delivery still drops cleanly.

### Provider-enablement snapshot

`snapshotEnabledProviders()` reads every relevant pref once and stores the
result in `EnumSet<ProviderCategory>` on the session. The dispatch loop and
the pending-counter loop both consult the snapshot, not `LauncherPrefs`.
QUICK_ACTIONS is always added (unconditional per existing behavior).

### Filter listener fix

`SearchFilters.OnFilterChangedListener` previously called the parameterless
`deliverResults()` which read `mCurrentQuery` / `mCurrentCallback`. The new
version reads the active session and re-delivers with the same
session-bail guard. No new dispatch — filter changes are a UI-only
re-conversion.

### `destroy()` lifecycle

`destroy()` now marks the active session abandoned and nulls
`mActiveSession`. Cleans up listener leaks if the algorithm is torn down
mid-flight.

## Non-goals carried from plan

- Phase 1 does **not** move `deliverResults` into the adapter provider
  (Phase 3).
- Phase 1 does **not** add the `SearchState` state machine on
  `ActivityAllAppsContainerView` (Phase 2).

## Verification

- `assembleDebug` clean.
- `tests-e2e/smoke/` + `regression/`: 21/21 in 59.23s.
- `test_rapid_typing_no_stale_results` exercises the abandon-and-restart
  path; passing confirms the late-callback bail is wired correctly.
- `test_multiprovider_query_renders` exercises the multi-provider FINAL
  dispatch.
- Manual: held a search keystroke pattern (`a` → `ab` → `abc`) and
  watched logcat — no stale `onSearchResult` arrivals fire after the
  abandon mark.

## Files

- `src/com/android/launcher3/search/SearchSession.java` (new, 75 lines)
- `src/com/android/launcher3/search/UniversalSearchAlgorithm.java`
  - Rewrite to session-scoped state. The deliver and dispatch bodies
    preserve every existing branch and ordering — only the field reads
    are reparented onto the session.
- `docs/changes/063-search-session-isolation.md` (this file)
