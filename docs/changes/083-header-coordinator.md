# 083 — T3.1 Phase 5: HeaderCoordinator extraction (riskiest phase)

**Branch:** `refactor/t0.1-search-4param-override`
**Date:** 2026-05-25
**Tier:** T3.1 Phase 5 (docs/plans/004-drawer-decomposition-v2.md)

## Summary

Extracted `HeaderCoordinator` from `ActivityAllAppsContainerView` as Phase 5 of the
T3.1 drawer decomposition — the riskiest and final phase. The coordinator owns tab
visibility, RV container layout rules, header scroll animation, and scroll-listener
wiring. A minimal change to `FloatingHeaderView` (AOSP-origin) adds a callback
alternative to the `(ActivityAllAppsContainerView<?>) getParent()` cast.

Container reduced from **~1820 LOC → 1607 LOC** (−213 lines). New file:
`src/com/android/launcher3/allapps/HeaderCoordinator.java` (265 LOC).

## Files changed

| File | Change |
|------|--------|
| `src/…/allapps/HeaderCoordinator.java` | **NEW** — owns header/tab layout |
| `src/…/allapps/ActivityAllAppsContainerView.java` | Fields + methods delegated |
| `src/…/allapps/FloatingHeaderView.java` | Minimal: `setOnHeaderCollapsedChangedCallback()` |
| `tests-e2e/regression/test_header_coordinator.py` | **NEW** — 4 Phase 5 regression tests |

## What moved to HeaderCoordinator

### Fields

| Field | Was | Now |
|-------|-----|-----|
| `mUsingTabs` | `protected boolean` | coordinator field, accessed via `isUsingTabs()` / `setUsingTabs()` |
| `mDrawerHideTabs` | `private boolean` | coordinator field, mutated ONLY via `onDrawerHideTabsChanged()` |
| `mScrollListener` | `private final` field | coordinator field, exposed via `getScrollListener()` |

### Methods moved

| Method | Was in container | Container now |
|--------|-----------------|--------------|
| `setupHeader()` body | package-private | delegates to coordinator |
| `updateRVContainerRules()` | private | delegates to coordinator |
| `replaceAppsRVContainer()` | private | delegates to coordinator |
| `updateHeaderScroll()` | protected | delegates to coordinator |
| `applyCustomTabColors()` | private | removed; coordinator owns |
| `setDeviceManagementResources()` | private | removed; coordinator owns |
| `layoutBelowSearchContainer()` | private helper | moved to coordinator |
| `alignParentTop()` | private helper | moved to coordinator |
| `removeCustomRules()` | private helper | moved to coordinator |

### Package-private promotions

| Field | Reason |
|-------|--------|
| `mAllAppsStore` | coordinator needs `getRecyclerViewPool()` in `replaceAppsRVContainer()` |
| `mSearchLifecycle` | coordinator checks `isSuppressingSetupHeader()` in `setupHeader()` |
| `mDrawerColorController` | coordinator needs `updateHeaderColorState()` in `updateHeaderScroll()` |

## Invariants preserved

| # | Invariant | How preserved |
|---|-----------|---------------|
| 1 | `mSuppressSetupHeader` | **FIRST LINE** of `HeaderCoordinator.setupHeader()`: `if (mSearchLifecycle.isSuppressingSetupHeader()) return;` — line is the FIRST non-blank statement. Any new setupHeader() call site must check this. |
| 2 | `mPendingSearchExitWork` | Phase 4's deferred runnable calls `setupHeader()` via `HeaderCallbacks` → `ActivityAllAppsContainerView.setupHeader()` → `mHeaderCoordinator.setupHeader()`. Suppression check is at the coordinator level. |
| 3 | `mKeepKeyboardOnSearchExit` | Owned by SearchLifecycle; HeaderCoordinator does not read it. |
| 6 | `mHeaderCollapsed` scroll-derived | `FloatingHeaderView.moved()` untouched. The FHV's own scroll listener (lines 57-75) is unchanged except for routing the `invalidateHeader()` call via the new `mOnHeaderCollapsedChanged` callback (cast preserved as fallback). |
| 7 | `getMaxTranslation()` formula | Untouched. `HeaderCoordinator.setupHeader()` calls `mHost.mHeader.getMaxTranslation()` exactly as the container did. |
| 8 | `mUsingTabs` + `DRAWER_HIDE_TABS` co-read | `HeaderCoordinator` holds both; `onDrawerHideTabsChanged(boolean)` is the SINGLE mutation site and triggers `updateRVContainerRules()` + `setupHeader()` synchronously. |
| 13 | `AllAppsRecyclerViewPool` preinflation | `setUpCustomRecyclerViewPool` stays as a static helper in the container; not extracted. |

## FloatingHeaderView change (AOSP-origin)

**Minimal change only:**
- Added `mOnHeaderCollapsedChanged` field (`@Nullable Runnable`) 
- Added `setOnHeaderCollapsedChangedCallback(@Nullable Runnable)` package-private method
- Updated `onScrolled` listener to call callback if set, else use parent cast

The parent cast `(ActivityAllAppsContainerView<?>) getParent()` is preserved as a fallback.
`moved()` (invariant #6) and `getMaxTranslation()` (invariant #7) are NOT touched.

The callback is wired in `ActivityAllAppsContainerView.onFinishInflate()`:
```java
mHeader.setOnHeaderCollapsedChangedCallback(this::invalidateHeader);
```

## Construction order

Per the plan risk note, the coordinator is constructed LAST so `mSearchLifecycle` is
already initialised when it first calls `setupHeader()`:
```
colors → fabs → insets → profiles → search-lifecycle → header (new, final)
```

## Test results

**Suite:** 44 passed, 9 skipped, 2 xfailed (59 total with new tests)

New tests in `test_header_coordinator.py` — 3 passed, 1 skipped:
- `test_setup_header_suppressed_during_search_anim` — PASSED
- `test_drawer_opens_scrolls_and_hides_header_correctly` — PASSED  
- `test_drawer_hide_tabs_toggle_updates_layout` — SKIP (no work profile on emulator)
- `test_replace_rv_container_pool_not_null` — PASSED

## Final container LOC

| Phase | LOC |
|-------|-----|
| Before T3.1 | 2168 |
| After Phase 1 (DrawerColorController + SearchFabController) | 1931 |
| After Phase 2 (DrawerInsetsController) | 1914 |
| After Phase 3 (ProfileCoordinator) | 1877 |
| After Phase 4 (SearchLifecycle) | ~1820 |
| After Phase 5 (HeaderCoordinator) | **1607** |

Total: −561 LOC from the container. Five collaborator classes added totalling ~1330 LOC.
