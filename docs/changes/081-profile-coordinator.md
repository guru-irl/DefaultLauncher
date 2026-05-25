# 081 — T3.1 Phase 3: ProfileCoordinator extraction

**Branch:** `refactor/t0.1-search-4param-override`
**Date:** 2026-05-25
**Tier:** T3.1 Phase 3 (docs/plans/004-drawer-decomposition-v2.md)

## Summary

Extracted `ProfileCoordinator` from `ActivityAllAppsContainerView` as Phase 3
of the T3.1 drawer decomposition. The coordinator now owns all work-profile and
private-profile management: manager construction, `hasWorkApps` / `hasPrivateApps`
flags, and the `onAppsUpdated` work/private branches.

Container reduced from **1912 LOC → 1877 LOC** (−35 lines). New file:
`src/com/android/launcher3/allapps/ProfileCoordinator.java` (194 LOC).

## Files changed

| File | Change |
|------|--------|
| `src/…/allapps/ProfileCoordinator.java` | **NEW** — owns work/private profile management |
| `src/…/allapps/ActivityAllAppsContainerView.java` | Fields + methods delegated to coordinator |
| `tests-e2e/regression/test_profile_coordinator.py` | **NEW** — 4 Phase 3 regression tests |
| `tests-e2e/pyproject.toml` | Registered `profile` pytest mark |

## What moved to ProfileCoordinator

### Fields

| Field | Moved from container | How accessed now |
|-------|----------------------|-----------------|
| `WorkProfileManager mWorkManager` | `protected` field | `mProfileCoordinator.getWorkManager()` |
| `PrivateProfileManager mPrivateProfileManager` | `protected final` field | `mProfileCoordinator.getPrivateProfileManager()` |
| `Predicate<ItemInfo> mPersonalMatcher` | `protected final` field | `mProfileCoordinator.personalMatcher()` |
| `boolean mHasWorkApps` | `private` field | `mProfileCoordinator.hasWorkApps()` |
| `boolean mHasPrivateApps` | `private` field | `mProfileCoordinator.hasPrivateApps()` |

### Methods

| Method | Moved from container | Container now |
|--------|----------------------|--------------|
| `shouldShowTabs()` | local → coordinator | delegates |
| `resetAndScrollToPrivateSpaceHeader()` | local | delegates |
| `inflateWorkCardsIfNeeded()` | `private` method | removed; coordinator calls it |
| `onAppsUpdated()` work/private branches | partial | coordinator owns flag update + manager resets |
| `setWorkManager(WorkProfileManager)` | `@VisibleForTesting` | delegates to coordinator |

## Architecture notes

### Back-reference discipline preserved

`PrivateProfileManager` already holds a `mAllApps: ActivityAllAppsContainerView<?>` 
back-reference (used for `isSearching()`, adapter access, scroll). Per the plan's
risk flag, `ProfileCoordinator` does NOT create a second back-channel. The
coordinator's `mHost` reference is used only for calling back to container
methods in `resetAndScrollToPrivateSpaceHeader()` (search-exit, tab-switch, scroll).

### getActiveAppsRecyclerView() made package-private

The container's `getActiveAppsRecyclerView()` was `private`. It was made
package-private so `ProfileCoordinator.resetAndScrollToPrivateSpaceHeader()` can
pass the recycler view to `PrivateProfileManager.scrollForHeaderToBeVisibleInContainer()`.

### onAppsUpdated() split

The container's `onAppsUpdated()` now:
1. Calls `mProfileCoordinator.onAppsUpdated(mAllAppsStore.getApps())` — updates
   flags, resets managers if needed.
2. Checks `if (!isSearching()) rebindAdapters()` — guard remains in container.
3. Logs stats + pre-caches icons — stays in container.

The search-guard (`!isSearching()`) intentionally stays in the container, not
in the coordinator, because the coordinator owns profile state, not search state.

## Invariants preserved

| # | Invariant | How preserved |
|---|-----------|---------------|
| 9 | Three orthogonal booleans in PPM | Fields (`mIsAnimationRunning`, `mReadyToAnimate`, `mIsStateTransitioning`) stay inside `PrivateProfileManager`; coordinator is passthrough |
| 10 | `mReadyToAnimate` set-before-unlock | Entirely intra-PPM; not touched |
| 11 | `MAIN_EXECUTOR.post(::exitSearchAndExpand)` guard | Stays inside `PrivateProfileManager.postUnlock()`; coordinator does not interpose |
| 12 | `notifyDataSetChanged()` after PS state transition | `PrivateProfileManager.java:704` preserved verbatim; verified by code review and test placeholder |

## Constructor ordering

The coordinator is now the first collaborator constructed in the container's
constructor, before the prefs cache, insets controller, and FABs:

```
mProfileCoordinator = new ProfileCoordinator<>(mActivityContext, this);
// managers created inside coordinator ↑
mDrawerHideTabs = ...
mAH = Arrays.asList(null, null, null);
mDrawerColorController = ...
mSearchFabController = ...
mInsetsController = ...
```

## Test results

**Suite:** 40 passed, 9 skipped, 2 xfailed (51 total)

New tests in `test_profile_coordinator.py`:
- `test_work_tab_appears_when_work_profile_present` — SKIP (no WP on emulator)
- `test_apps_updated_skips_rebind_when_searching` — **PASSED** — fires SEED_WORKSPACE
  broadcast while in SEARCHING state; asserts search results list stays visible and
  `onAppsUpdated` logcat marker is present.
- `test_private_space_header_visible_when_ps_installed` — SKIP (no PS on emulator)
- `test_private_space_lock_notifies_data_set_changed` — SKIP (no PS on emulator)

## Removed imports from ActivityAllAppsContainerView

- `android.os.Process` (moved to coordinator)
- `android.os.UserManager` (moved to coordinator)
- `com.android.launcher3.pm.UserCache` (moved to coordinator)
- `com.android.launcher3.util.ItemInfoMatcher` (moved to coordinator)
- `java.util.stream.Stream` (moved to coordinator)
- `static BaseAllAppsAdapter.VIEW_TYPE_WORK_DISABLED_CARD` (moved to coordinator)
- `static BaseAllAppsAdapter.VIEW_TYPE_WORK_EDU_CARD` (moved to coordinator)
