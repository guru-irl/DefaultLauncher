# 064 — Search drawer: persistent SearchState machine

T2.2 Phase 2 of `docs/plans/002-search-reliability-v2.md`.

## Problem

`ActivityAllAppsContainerView` tracked search lifecycle with a single
`boolean mIsSearching`. That field is:

- Set `true` only at the end of the entering animation (line 618).
- Set `false` only at the end of the exiting animation.

So mid-animation, the boolean still reports the **previous** stable state.
Callers asked the wrong question and got the wrong answer:

- `showAppsWhileSearchActive()` bails on `!mIsSearching` — would bail on a
  brand-new enter animation if a fast user backspaced before the enter
  completed.
- The animation guard `goingToSearch == isSearching()` could not distinguish
  "search is fully entered" from "search bar visible, query empty"
  (`ACTIVE_EMPTY`) — the latter case requires a real entering animation
  again on the next keystroke.
- The deferred exit-work guard (`if (mIsSearching) return;`) was checking a
  field that had just been flipped to false; the intended re-entry guard
  was actually relying on `removeCallbacks` to cancel rather than the
  field check.

## Fix

Introduce an explicit five-state machine:

```java
enum SearchState {
    IDLE,         // No search shown
    ENTERING,     // Animation to search mode running
    SEARCHING,    // Search results visible, non-empty query
    ACTIVE_EMPTY, // Search bar focused with keyboard, query empty,
                  //   A-Z apps list rendered. New keystroke → SEARCHING.
    EXITING,      // Animation back to apps list running
}
```

Transition table (legal moves only — every other write is a bug):

| From | Trigger | To |
|------|---------|----|
| IDLE | `setSearchResults(non-null)` → `animateToSearchState(true)` | ENTERING |
| ENTERING | `mSearchTransitionController` animation end | SEARCHING |
| SEARCHING | `animateToSearchState(false)` | EXITING |
| EXITING | deferred runnable, `mKeepKeyboardOnSearchExit=false` | IDLE |
| EXITING | deferred runnable, `mKeepKeyboardOnSearchExit=true` | ACTIVE_EMPTY |
| ACTIVE_EMPTY | new non-empty keystroke → `animateToSearchState(true)` | ENTERING (then SEARCHING) |
| ACTIVE_EMPTY | back/scroll/home → `animateToSearchState(false)` | EXITING (then IDLE) |

### Invariants preserved verbatim

Per the plan's risk callouts and drawer-invariants doc:

- **`mKeepKeyboardOnSearchExit`** (drawer invariant #2). Read in the
  deferred exit runnable; flipped to false **only** when consumed; the
  asymmetric set/clear lifecycle is unchanged. Decides EXITING → IDLE vs.
  EXITING → ACTIVE_EMPTY at the moment of consumption.
- **`mPendingSearchExitWork`** (drawer invariant #3). Posted in the same
  position; cancelled at the same position (`:600-603` of the original
  file). The new re-entry guard `if (mSearchState != EXITING) return;`
  semantically replaces the old `if (mIsSearching) return;` — identical
  re-entry coverage (a fresh ENTERING or SEARCHING has stolen the state)
  with cleaner read-of-truth.
- **`mSuppressSetupHeader`** (drawer invariant from `docs/changes/029`).
  Set/clear pattern unchanged.

### `isSearching()` contract

Returns `true` for `SEARCHING`, `ACTIVE_EMPTY`, and `ENTERING`. The third
case is new vs. the old boolean — previously `mIsSearching` was false
during the entering animation. The change is intentional per the plan:
"isSearching()" is now interpreted as "user perceives a search context"
(visible search bar or transitioning toward one). All current readers
(15+ sites) treat `isSearching()` as a visual gate and produce the same
end-state with the broadened contract.

`EXITING` deliberately maps to false: by the time the animation ends, the
search recycler has been hidden and the apps recycler is visible. Any
reader during the exit animation sees the same "we're already on apps"
truth that the old code reported at animation-end.

### Animation guard precision

Line `595` previously compared `goingToSearch == isSearching()` to short-
circuit redundant calls. With the wider `isSearching()` contract, the
ACTIVE_EMPTY → SEARCHING path would bail incorrectly. New comparison:

```java
goingToSearch == (mSearchState == SearchState.SEARCHING)
```

This is the precise "are we already in the stable destination?" check.
ENTERING/EXITING fall through (animation in progress); ACTIVE_EMPTY falls
through for goingToSearch=true (we need a real animation to get to
SEARCHING); IDLE falls through for goingToSearch=true and bails for
goingToSearch=false (no-op exit).

### Main-thread assertion

`setSearchState(SearchState)` calls `Preconditions.assertUIThread()`,
which throws only off the main thread. This is the plan's "All writes
assert main thread" point; the assertion fires in any build (not gated on
BuildConfig.DEBUG) because `Preconditions.assertUIThread` is already
unconditional in this codebase and the throw cost is negligible.

## Non-goals carried from plan

- Phase 2 does **not** move `deliverResults` into the adapter provider
  (Phase 3 deferred).
- Phase 2 does **not** delete `DefaultAppSearchAlgorithm` /
  `DefaultSearchAdapterProvider` (Phase 4 deferred).

## Verification

- `assembleDebug` clean.
- `tests-e2e/smoke/` + `regression/`: 21/21 in 59.41s.
- `test_search_text_renders_results` exercises IDLE → ENTERING → SEARCHING.
- `test_search_clear_returns_to_apps` exercises SEARCHING → EXITING → IDLE
  (or ACTIVE_EMPTY depending on dismissal path).
- `test_search_dismiss_via_back` exercises SEARCHING → EXITING → IDLE.
- `test_rapid_typing_no_stale_results` exercises ACTIVE_EMPTY-pivot races.
- Manual: opened search, typed query, backspaced to empty (ACTIVE_EMPTY),
  typed again (SEARCHING), back to dismiss (EXITING → IDLE). No visual
  artifacts. Keyboard kept across ACTIVE_EMPTY as expected.

## Files

- `src/com/android/launcher3/allapps/ActivityAllAppsContainerView.java`
  - +20 lines (enum + field + setSearchState helper + transitions).
  - mIsSearching → mSearchState; ~10 read sites updated.
- `docs/changes/064-search-state-machine.md` (this file)
