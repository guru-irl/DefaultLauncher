# 082 — T3.1 Phase 4: SearchLifecycle extraction

**Branch:** `refactor/t0.1-search-4param-override`
**Date:** 2026-05-25
**Tier:** T3.1 Phase 4 (docs/plans/004-drawer-decomposition-v2.md)

## Summary

Extracted `SearchLifecycle` from `ActivityAllAppsContainerView` as Phase 4 of the
T3.1 drawer decomposition. The lifecycle now owns the search state machine: the
`SearchState` enum, all state fields, and the `animateToSearch()` method with its
deferred-work machinery. This is the "HIGHER RISK" phase per the plan.

Container reduced from **1877 LOC → ~1820 LOC** (−57 lines). New file:
`src/com/android/launcher3/allapps/SearchLifecycle.java` (~290 LOC).

## Files changed

| File | Change |
|------|--------|
| `src/…/allapps/SearchLifecycle.java` | **NEW** — owns SearchState + state machine |
| `src/…/allapps/ActivityAllAppsContainerView.java` | Fields + methods delegated to lifecycle |
| `tests-e2e/regression/test_search_lifecycle.py` | **NEW** — 4 Phase 4 regression tests |

## What moved to SearchLifecycle

### Moved

| Item | Was in container | Now |
|------|-----------------|-----|
| `enum SearchState { IDLE, ENTERING, SEARCHING, ACTIVE_EMPTY, EXITING }` | inner enum | `SearchLifecycle.SearchState` |
| `mSearchState` | `private SearchState` field | lifecycle field |
| `mSuppressSetupHeader` | `private boolean` field | lifecycle field |
| `mRebindAdaptersAfterSearchAnimation` | `private boolean` field | lifecycle field |
| `mKeepKeyboardOnSearchExit` | `private boolean` field | lifecycle field |
| `mPendingSearchExitWork` | `private Runnable` field | lifecycle field |
| `mSearchTransitionController` | `private final` field | lifecycle field |
| `animateToSearchState(boolean, long)` body | local → lifecycle | `animateToSearch()` |
| `showAppsWhileSearchActive()` body | local → lifecycle | fully moved |
| `setSearchState()` private helper | local → lifecycle | `setSearchState()` |

### Added

| Method | Purpose |
|--------|---------|
| `SearchLifecycle.onContainerReset(boolean)` | Change-070 reset hook; single named entry point |
| `SearchLifecycle.isSearchTransitionRunning()` | Replaces direct STC.isRunning() calls in container |
| `SearchLifecycle.isSuppressingSetupHeader()` | Invariant #1 gate for setupHeader() |
| `SearchLifecycle.markRebindPendingIfAnimating()` | Replaces the inline check in rebindAdapters() |
| `ActivityAllAppsContainerView.setCurrentPage(int)` | HeaderCallbacks implementation |

### Delegations in container

| Method | Was | Now |
|--------|-----|-----|
| `animateToSearchState(boolean, long)` | full body | delegates to lifecycle |
| `showAppsWhileSearchActive()` | full body | delegates to lifecycle |
| `isSearching()` | computes from `mSearchState` | delegates to lifecycle |
| `setupHeader()` first line | `if (mSuppressSetupHeader)` | `if (mSearchLifecycle.isSuppressingSetupHeader())` |
| `rebindAdapters()` first check | direct field access | `mSearchLifecycle.markRebindPendingIfAnimating()` |
| `reset(…, exitSearch)` reset block | direct field mutation | `mSearchLifecycle.onContainerReset(exitSearch)` |
| `getSearchTransitionController()` | returns field | delegates to lifecycle |
| `updateSearchFabs()` isRunning check | direct STC access | lifecycle methods |
| `onActivePageChanged()` isRunning check | direct STC access | lifecycle method |

## Architecture notes

### HeaderCallbacks anonymous class

The container does NOT implement `SearchLifecycle.HeaderCallbacks` directly.
Java interface methods are implicitly public, but `setupHeader()` and
`updateSearchResultsVisibility()` are package-private/protected in the container.
Rather than promoting them to public, the constructor passes an anonymous inner
class that wraps the container method calls:

```java
mSearchLifecycle = new SearchLifecycle<>(
    this,
    new SearchLifecycle.HeaderCallbacks() {
        @Override public void setupHeader() { ActivityAllAppsContainerView.this.setupHeader(); }
        // ... etc.
    },
    new SearchTransitionController(this));
```

### Package-private promotions

Two fields were promoted from `private` to package-private so SearchLifecycle
can access them directly:
- `mProfileCoordinator` — lifecycle calls `getWorkManager().onActivePageChanged(SEARCH)`
- `mAllAppsTransitionController` — lifecycle calls `animateAllAppsToNoScale()` on exit

Fields `mFastScroller`, `mHeader`, `mViewPager`, `mSearchUiDelegate` are already
`protected` and accessible from the same package.

### DEFAULT_SEARCH_TRANSITION_DURATION_MS

Changed from `private` to package-private so `SearchLifecycle.showAppsWhileSearchActive()`
can reference the constant.

## Invariants preserved

| # | Invariant | How preserved |
|---|-----------|---------------|
| 1 | `mSuppressSetupHeader` | Moved to lifecycle; `setupHeader()` checks `mSearchLifecycle.isSuppressingSetupHeader()`. Any setupHeader() call site (current and future) must go through this check. |
| 2 | `mPendingSearchExitWork` | Cancelled at start of `animateToSearch()`; posted to `mHost` View so `removeCallbacks` is symmetric. |
| 3 | `mKeepKeyboardOnSearchExit` | Set in `showAppsWhileSearchActive()` BEFORE animation; consumed in deferred runnable AFTER animation. Temporal asymmetry preserved; documented inline. |
| 4 | `mImmediateRestart` | Untouched — lives in RecyclerViewAnimationController. |
| 5 | Hardware-layer toggle | Untouched — lives in SearchTransitionController:87-92. |

### Change-070 reset hook

`SearchLifecycle.onContainerReset(boolean exitSearch)` is the single named entry point
for the reset hook. It clears `mKeepKeyboardOnSearchExit` **unconditionally before**
checking `isSearchTransitionRunning()` — this is the key invariant from docs/changes/079:
- If reset() fires mid-animation, the flag is cleared so the deferred runnable resolves to IDLE
- Without this ordering, the race documented in change 079 would be reintroduced

## Test results

**Suite:** 40 passed, 9 skipped, 2 xfailed (55 total with new tests)

New tests in `test_search_lifecycle.py` — all 4 passed:
- `test_search_state_resets_to_idle_on_home_return` — repro of change-070 race; PASSED
- `test_rapid_enter_exit_no_setup_header_storm` — rapid type/clear; PASSED
- `test_pending_exit_work_cancelled_on_restart` — deferred runnable cancellation; PASSED
- `test_search_animation_in_flight_reset_is_safe` — mid-animation HOME; PASSED
