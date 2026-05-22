# 079 — Empty drawer race: mKeepKeyboardOnSearchExit not cleared on mid-animation reset()

Fixes the "empty drawer on first open" bug recurrence: when the user presses BACK
within 300ms of backspacing to empty in the search bar, `mSearchState` is left as
`ACTIVE_EMPTY` and the next drawer open shows an empty `search_results_list_view`
instead of the apps grid.

## Root cause

`showAppsWhileSearchActive()` (called when text becomes empty while the IME is up)
sets `mKeepKeyboardOnSearchExit = true` and starts `animateToSearchState(false)`,
a 300ms animation.

If the user presses BACK before the 300ms elapses:
1. `Launcher.onStateSetEnd()` fires `getAppsView().reset(false, true)`.
2. Inside `reset()`, the `if (mSearchTransitionController.isRunning())` guard is
   True → the `else` branch is skipped.
3. `mKeepKeyboardOnSearchExit` is NOT cleared.
4. The animation ends → `onEndRunnable` posts `mPendingSearchExitWork`.
5. The deferred runnable checks `mKeepKeyboardOnSearchExit == true` →
   `setSearchState(ACTIVE_EMPTY)`.
6. Drawer is now closed with `mSearchState = ACTIVE_EMPTY`.
7. Next drawer open: `isSearching() == true` → `search_results_list_view` shown
   (empty) instead of `apps_list_view`.

## Why previous fixes were incomplete

- **Change 070** added `mSearchState = IDLE` and `removeCallbacks(mPendingSearchExitWork)`
  inside the `else` branch. Good, but the `if (isRunning())` guard bypasses this.
- **Change 076** ensured `reset()` fires on every HOME press (not just `!alreadyOnHome`).
  Also good, but still vulnerable when HOME fires during the animation window.

Both fixes assumed `reset()` firing at the right time would clean up the state.
Neither addressed the case where `reset()` fires during the 300ms window.

## Fix

Move `mKeepKeyboardOnSearchExit = false` unconditionally **before** the `isRunning()`
guard. The deferred runnable checks this flag to decide whether to set `ACTIVE_EMPTY`
or `IDLE` — clearing it in `reset()` regardless of animation state ensures the
runnable always resolves to `IDLE` when the user has already left ALL_APPS.

```java
// Before (in reset() exitSearch branch):
if (mSearchTransitionController.isRunning()) {
    // skips everything
} else {
    mSearchState = SearchState.IDLE;
    mKeepKeyboardOnSearchExit = false;   // only reached when NOT running
    removeCallbacks(mPendingSearchExitWork);
}

// After:
mKeepKeyboardOnSearchExit = false;       // always cleared
if (mSearchTransitionController.isRunning()) {
    // animation ends → deferred runnable → sees false → IDLE
} else {
    mSearchState = SearchState.IDLE;
    removeCallbacks(mPendingSearchExitWork);
}
```

## Why this is safe re: invariant #3

Invariant #3 says `mKeepKeyboardOnSearchExit` is NOT a state enum candidate because
it has asymmetric set/clear timing: set before animation, checked after. This is
preserved — we do NOT change when the flag is set. We only change when it's cleared
(earlier, from the animation-end deferred runnable to the `reset()` call site).
When `reset()` fires, the user has left ALL_APPS, so keeping the keyboard
is irrelevant. Clearing early is correct.

## Additional fixes in this change

- **`open_launcher_settings()`**: after the workspace disappears, wait for the
  PreferenceFragment `RecyclerView` to render before returning. Prevents a
  semi-transparent white screen where tests interact with settings before it's ready.
- **`test_search_fab_web_intent_launches_browser`**: also poll for "Complete action
  using" / "Open with" / "Choose an app" text in the hierarchy. On Android 17,
  `app_current()` returns the launcher's package even when the intent chooser is
  showing as an overlay, causing the test to time out incorrectly.
- **Folder drag duration**: `1.0s → 1.5s` in `test_folder_visual._create_folder()`.
  The 1.0s drag was marginal on a loaded emulator and was intermittently classified
  as a tap instead of a long-press-drag.
- **`test_drawer_intact_after_folder_color_change`**: add `rv.fling.toBeginning()` +
  `time.sleep(0.3)` before searching for "Settings" icon. On a loaded emulator the
  RV was populated but scrolled, so the accessibility child query found nothing.

## New regression tests

`tests-e2e/regression/test_drawer_state.py` (3 tests):
- `test_drawer_not_empty_after_backspace_and_quick_back` — directly exercises the
  race: type, clear_text immediately (no sleep), press BACK, reopen → apps visible.
- `test_drawer_not_empty_after_repeated_search_and_dismiss` — broader coverage: 3
  search-clear-close cycles, final open must show apps grid.
- `test_search_results_list_hidden_when_not_searching` — fundamental invariant:
  fresh drawer open from HOME must show apps grid, not empty search list.

## Files modified

- `src/com/android/launcher3/allapps/ActivityAllAppsContainerView.java`
  — move `mKeepKeyboardOnSearchExit = false` before `isRunning()` guard in `reset()`
- `tests-e2e/lib/launcher.py` — `open_launcher_settings()` waits for RecyclerView
- `tests-e2e/regression/test_drawer_decomposition.py` — chooser dialog detection
- `tests-e2e/regression/test_folder_color_migration.py` — scroll-to-top + settle
- `tests-e2e/regression/test_folder_visual.py` — drag duration 1.0 → 1.5

## Files added

- `tests-e2e/regression/test_drawer_state.py` — 3 regression tests

## Verification

- `assembleDebug`: clean.
- Full test suite (44 tests): 39 passed, 0 failed, 2 xfailed, 3 skipped. Exit 0.
