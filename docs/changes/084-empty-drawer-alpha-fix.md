# 084 — Fix: empty drawer after launching app from search results

**Branch:** `refactor/t0.1-search-4param-override`
**Date:** 2026-06-04
**Type:** Bug fix (persistent regression since T3.1 Phase 4)

## Symptom

After:
1. Opening the app drawer
2. Typing a search query and seeing results
3. Tapping an app in the search results (launching it)
4. Pressing HOME (or BACK) to return to the launcher
5. Opening the drawer again

...the drawer opens **empty** — neither the apps grid nor the search results are visible.
The search bar, header, and container are all present, but the RecyclerView content area is
invisible.

## Root cause

`SearchTransitionController.onProgressUpdated(searchToAzProgress)` applies:

```java
appsContainer.setAlpha(clampToProgress(searchToAzProgress, 0.8f, 1f));
```

When entering search mode (`searchToAzProgress = 0`), `appsContainer.alpha` is set to **0**.
The normal exit path — `animateToSearch(false, ...)` → animation runs → `onProgressUpdated(1.0f)`
— restores alpha to 1.

**However**: `reset()` (called from `onNewIntent` when HOME is pressed) calls
`SearchLifecycle.onContainerReset(true)` which sets `mSearchState = IDLE` **synchronously**.
When `resetSearch()` fires later (posted to the main handler), `onClearSearchResult()` calls
`animateToSearch(false)`, which hits its guard:

```java
if (!mSearchTransitionController.isRunning()
        && goingToSearch == (mSearchState == SearchState.SEARCHING)) {
    return;  // ← IDLE != SEARCHING → false == false → returns early
}
```

The exit animation is skipped. `onProgressUpdated(1.0f)` is never called.
`appsContainer.alpha` remains 0.

Android's `View.isVisibleToUser()` returns `false` when `alpha <= 0`, so the accessibility
tree reports the view as non-existent — `uiautomator2` (and the user) sees an empty drawer.

## Why this is a T3.1 regression

The guard in `animateToSearch()` was introduced intentionally: without it, every `resetSearch()`
call during normal drawer open/close cycles would fire a redundant exit animation.

The bug was introduced in **Phase 4** (change 082, `SearchLifecycle` extraction) because Phase 4
moved `mSearchState = IDLE` from inside the animation's deferred runnable (where it ran AFTER
the animation) to `onContainerReset()` (which runs BEFORE `resetSearch()` is posted).
Pre-Phase-4, the sequence was:

```
OLD: animateToSearch(false) → animation runs → onEnd: mSearchState=IDLE → alpha=1 ✓
NEW: onContainerReset → mSearchState=IDLE → animateToSearch(false) guard fires → alpha=0 ✗
```

## Fix

Two complementary sites:

### 1. `SearchLifecycle.onContainerReset(true)`

Reset the search-animation visual state unconditionally after setting `mSearchState=IDLE`:

```java
// Reset visual state left by the search-enter animation.
android.view.View appsContainer = mHost.getAppsRecyclerViewContainer();
appsContainer.setAlpha(1f);
appsContainer.setTranslationY(0f);
mHost.mHeader.setAlpha(1f);
mHost.mHeader.setTranslationY(0f);
```

This covers the **HOME press** path (and any other path that calls `reset()`).

### 2. `Launcher.onResume()`

When the launcher resumes directly into `ALL_APPS` state (e.g., user pressed BACK from an app
launched via `FLAG_ACTIVITY_NEW_TASK`, returning the launcher to its previous ALL_APPS
foreground state), `onNewIntent` is NOT fired, so `reset()` is not called. Add:

```java
if (isInState(LauncherState.ALL_APPS) && mAppsView.isSearching()) {
    mAppsView.reset(false /* animate */);
}
```

This covers the **BACK press** return path.

## Files changed

| File | Change |
|------|--------|
| `src/…/allapps/SearchLifecycle.java` | `onContainerReset(true)` resets alpha/translation |
| `src/com/android/launcher3/Launcher.java` | `onResume()` resets drawer if in ALL_APPS + searching |
| `tests-e2e/regression/test_search_launch_and_return.py` | **NEW** — 2 regression tests |

## Test cases

`test_search_launch_and_return.py`:
- `test_apps_visible_after_launching_from_search_then_home` — HOME path (PASSED)
- `test_apps_visible_after_multiple_search_launch_cycles` — repeat cycles (PASSED)

## Not regressed

- `test_search_state_resets_to_idle_on_home_return` (change 070/079) — still passes.
  The `mKeepKeyboardOnSearchExit = false` unconditional clear in `onContainerReset` is
  preserved; the alpha reset is added AFTER the existing logic.
- Search animation invariants #1-#5 from `drawer-invariants.md` — all preserved.
  The alpha reset only runs when `exitSearch=true` and no animation is running.
