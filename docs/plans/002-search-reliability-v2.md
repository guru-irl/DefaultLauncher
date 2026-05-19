# Plan 002 — Search Reliability v2

Supersedes the original search plan in `docs/plans/000-architectural-refactor-superplan.md`. T0.1 (4-param override; `docs/changes/048`) already landed.

## Phase 1 — SearchSession + provider snapshotting

New `SearchSession` class in `com.android.launcher3.search`:
- `final long version` — monotonic counter.
- `final String query`.
- `final EnumSet<ProviderCategory> enabledProviders` — snapshot at session creation.
- `final SearchCallback<AdapterItem> callback`.
- `final SearchResult accumulator`.
- `final AtomicInteger pendingProviders`.
- `volatile boolean abandoned`.

`UniversalSearchAlgorithm` gains `private volatile SearchSession mActiveSession` + `mNextVersion: AtomicLong`.

`doSearch` rewrite (`UniversalSearchAlgorithm.java:128-180`):
1. If `mActiveSession != null`, mark abandoned. Call `cancel(true)`.
2. Build new session.
3. Publish via `mActiveSession = s`.
4. Dispatch provider lambdas capturing `s` by reference (not `this`-fields).

Every callback's entry:
```
if (s.abandoned || s != mActiveSession) return;
```
Both checks needed.

**Provider snapshotting**: snapshot `enabledProviders` once at top of `doSearch`. Callbacks check before mutating.

**`SearchProvider.category()` enum migration**: Tertiary confirmed all providers return hardcoded strings. Introduce `enum ProviderCategory` in `com.android.launcher3.search.providers`. Convert in this phase so it isn't a separate breaking change later.

**Filter listener fix** (`UniversalSearchAlgorithm.java:94-99`):
```kotlin
mFilters.setOnFilterChangedListener {
    val s = mActiveSession
    if (s == null || s.abandoned) return@setOnFilterChangedListener
    adapterProvider.convertResults(s.accumulator, mFilters, s.query, FINAL, s.callback)
}
```

**Tests**:
- `tests-e2e/regression/test_search_session_isolation.py` — rapid typing + filter toggle during in-flight.
- `tests-e2e/stress/test_search_fuzz.py` — 100-iteration type/clear loop.

## Phase 2 — Persistent search state machine

Replace `mIsSearching` with `enum SearchState { IDLE, ENTERING, SEARCHING, ACTIVE_EMPTY, EXITING }` in `ActivityAllAppsContainerView`.

**Preserve verbatim** (drawer invariants #2 #3):
- `mKeepKeyboardOnSearchExit` (`:191`, `:441`, `:650`) — set/check asymmetric across animation boundary.
- `mPendingSearchExitWork` (`:192`, `:642-656`, cancel at `:600-603`) — frame-timing invariant.

Public `isSearching()` (`:780`) returns true for SEARCHING/ACTIVE_EMPTY/ENTERING for back-compat.

**Legal transitions**:
- `IDLE → ENTERING`: first non-empty query.
- `ENTERING → SEARCHING`: animation end runnable (`:618`).
- `SEARCHING → EXITING`: `animateToSearchState(false)`.
- `EXITING → IDLE`: deferred runnable, keyboard flag false.
- `EXITING → ACTIVE_EMPTY`: deferred runnable, keyboard flag true.
- `ACTIVE_EMPTY → SEARCHING`: new non-empty keystroke; internal animation guard changes to use `mSearchState == SEARCHING` rather than `isSearching()` for the comparison.
- `ACTIVE_EMPTY → IDLE`: back / scroll dismissal.

**Empty-vs-null contract** (tertiary): `query == null` → IDLE only; `query.isEmpty()` → ACTIVE_EMPTY.

**Atomicity**: all writes assert main thread.

**Tests**: `tests-e2e/regression/test_search_state_machine.py` with one test per transition.

## Phase 3 — Conversion abstraction

Move `UniversalSearchAlgorithm.deliverResults` (`:237-350`) into `UniversalSearchAdapterProvider` as new public method `convertResults(result, filters, query, code, context)`. No new file, no new interface — the adapter provider already owns view-type knowledge.

Algorithm's deliver becomes one-liner that calls the adapter provider + dispatches via session callback.

## Phase 4 — Dead-code removal

Delete `DefaultAppSearchAlgorithm.java` + `DefaultSearchAdapterProvider.java`. Update `AppSearchProvider.java:27` javadoc to remove historical reference. Leave `docs/changes/016` as historical record.

## Sequencing

Phase 1 → Phase 2 → (Phase 3 + Phase 4 in either order).

## Non-goals

- No `SearchScorer` math changes.
- No new providers.
- No SearchUiManager interface changes; preserve all current overrides.

## Risk callouts

- `ProviderCategory` enum migration touches 10 provider files — do as single atomic commit.
- State machine illegal transitions throw in debug builds only (BuildConfig.DEBUG guard).
- `mPendingSearchExitWork` cancel-before-restart at `:600-603` must keep its current order.

## Critical files

- `src/com/android/launcher3/search/UniversalSearchAlgorithm.java`
- `src/com/android/launcher3/search/UniversalSearchAdapterProvider.java`
- `src/com/android/launcher3/allapps/ActivityAllAppsContainerView.java`
- `src/com/android/launcher3/allapps/search/AppsSearchContainerLayout.java`
- `src/com/android/launcher3/search/providers/SearchProvider.java` + all impls
