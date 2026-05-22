# 076 — Search state HOME-press reset (change-070 race recurrence fix)

Fixes a race condition where `mSearchState = ACTIVE_EMPTY` persists into
the next test's drawer open, producing the same "empty drawer on first open"
symptom documented in `docs/changes/070`.

## Root cause

`docs/changes/070` fixed the state by calling `mAppsView.reset(exitSearch=true)`
from `Launcher.onNewIntent()` when returning home. However that call was
guarded by `if (!alreadyOnHome)` — it only fires when the launcher is being
brought to the foreground from another app. When the launcher is ALREADY in
the foreground (e.g., the test's `_wake_and_home` fixture confirms workspace
visibility and takes the fast path without pressing HOME), `onNewIntent()` is
never fired, and `mSearchState` can persist.

The secondary cause is a timing race in `reset()` itself:

```java
if (mSearchTransitionController.isRunning()) {
    // Mid-animation: let existing onEnd runnable land the state naturally
} else {
    mSearchState = SearchState.IDLE;   // only fires if NOT running
```

If `reset()` is called while a search-exit animation is in progress (e.g.,
immediately after `clear_search()` starts the `EXITING` animation), the
`else` branch is skipped. The subsequent deferred runnable can then resolve
to `ACTIVE_EMPTY` (if `mKeepKeyboardOnSearchExit = true`), leaving the state
contaminated for the next test.

Together, these two issues produce an intermittent failure:
`test_drawer_intact_after_folder_color_change` opens the drawer and gets 0
icons because `isSearching() = true` causes `updateSearchResultsVisibility()`
to hide `apps_list_view`.

## Fix

### 1. `Launcher.java` — remove `!alreadyOnHome` guard

```java
// Before:
if (!alreadyOnHome) {
    mAppsView.reset(isStarted() /* animate */);
}

// After:
mAppsView.reset(!alreadyOnHome && isStarted() /* animate */);
```

`reset()` now fires on every HOME press, regardless of whether the launcher
was already in the foreground. The `animate` parameter is false when pressing
HOME while already on the workspace, so no visible header animation occurs.

### 2. `conftest.py::_wake_and_home` — always press HOME in fast path

Even when the workspace is already visible, the fast path now presses HOME
before yielding. This fires `onNewIntent()` → `reset()` and guarantees
`mSearchState = IDLE` at the start of every test. Overhead: one keyevent +
0.3s per test (~10s total for the 34-test suite).

## Why the original change-070 fix was incomplete

The fix in 070 used `if (!alreadyOnHome)` because the original repro required
returning TO the launcher FROM another app. The scenario where the test stays
on the launcher (fast path, no app switch) was not present in the original
repro; it was introduced in Session 5 when `is_home()` was made to return True
via the workspace-visibility fallback, eliminating the forced `app_stop + app_start`
that had previously acted as an inadvertent reset mechanism.

## Verification

- `assembleDebug`: clean.
- Full 9-test regression suite run passes including `test_drawer_intact_after_folder_color_change`.

## Files

- `src/com/android/launcher3/Launcher.java` — remove `!alreadyOnHome` guard
- `tests-e2e/conftest.py` — always press HOME in `_wake_and_home` fast path
