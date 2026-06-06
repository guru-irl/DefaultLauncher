# 075 — T3.1 Phase 1: DrawerColorController + SearchFabController extraction

T3.1 Phase 1 of `docs/plans/004-drawer-decomposition-v2.md`. Extracts two
non-stateful concerns out of `ActivityAllAppsContainerView` (was 2168 LOC):

1. `DrawerColorController` — all paint state and color computation
2. `SearchFabController` — FAB container lifecycle and show/hide logic

## DrawerColorController

Owns:
- `mHeaderPaint`, `mNavBarScrimPaint` (Paint objects used in scrim drawing)
- `mBottomSheetBackgroundColor`, `mBottomSheetBackgroundAlpha` (sheet fill)
- `mHeaderColor`, `mTabsProtectionAlpha` (scroll-derived header state)
- `mScrimColor`, `mHeaderProtectionColor` (theme constants)
- `refresh()` — replaces `refreshCustomColors()`
- `applyTabColors(View, View)` + private `applyTabBackground()` — replaces `applyCustomTabColors()`
- `getHeaderColor(float blendRatio)` — replaces the container's `getHeaderColor()`
- `updateHeaderColorState(int, int)` — replaces the `mHeaderColor`/`mTabsProtectionAlpha` update in `updateHeaderScroll()`
- `onScrimViewChanged(ScrimView)` — called from `setScrimView()` to keep the scrim reference current

The container retains the actual canvas drawing (`drawOnScrimWithScaleAndBottomOffset`,
`dispatchDraw`) and reads state via the controller's getters.

## SearchFabController

Owns:
- `mFabContainer`, `mAiSearchFab`, `mSearchOnlineFab` (FAB views)
- FAB show/hide animation — replaces `updateSearchFabs(String)`
- `launchWebSearch()`, `launchAiSearch()`, `resolveAiPackage()`, `isPackageInstalled()`
- `refreshAiIcon()` — replaces `loadAiAppIcon()`
- `applyImeInsets(int, int)` — replaces the FAB margin update in `dispatchApplyWindowInsets()`

The container creates the controller in its constructor and adds the FAB container
to the view hierarchy via `mSearchFabController.buildContainer()` in `initContent()`.

## Construction order

```
DrawerColorController(context, activityContext)   [constructor]
SearchFabController(materialCtx)                  [constructor]
DrawerColorController.onAttach(this, scrimView)   [onAttachedToWindow]
DrawerColorController.onDetach()                  [onDetachedFromWindow]
DrawerColorController.onScrimViewChanged(sv)      [setScrimView]
```

## Invariants preserved

- **#14 SysUiScrim:** untouched — lives in `SysUiScrim.java`, not modified.
- **#1 / #8 (DRAWER_HIDE_TABS, mSuppressSetupHeader):** `mDrawerPrefSubscriber` still
  calls `setupHeader()` from the container for `DRAWER_HIDE_TABS` changes. Only color
  paths leave. Verified by keeping `mDrawerHideTabs` + `setupHeader()` +
  `updateRVContainerRules()` calls in the container.

## Removed from ActivityAllAppsContainerView

- Fields: `mHeaderPaint`, `mNavBarScrimPaint`, `mHeaderColor`, `mBottomSheetBackgroundColor`,
  `mBottomSheetBackgroundAlpha`, `mTabsProtectionAlpha`, `mFabContainer`, `mAiSearchFab`,
  `mSearchOnlineFab`, `mCurrentSearchQuery`
- Methods: `refreshCustomColors()` (→ thin wrapper → removed body),
  `applyCustomTabColors()` (→ thin wrapper), `applyTabBackground()`,
  `getHeaderColor()` (→ thin wrapper), `launchWebSearch()`, `loadAiAppIcon()`,
  `launchAiSearch()`, `resolveAiPackage()`, `isPackageInstalled()`
- Imports: `SearchManager`, `ComponentName`, `PackageManager`, `ColorStateList`,
  `MaterialColors`, `ExtendedFloatingActionButton`, `FloatingActionButton`, `ColorUtils`

## Files added

- `src/com/android/launcher3/allapps/DrawerColorController.java`
- `src/com/android/launcher3/allapps/SearchFabController.java`

## Files modified

- `src/com/android/launcher3/allapps/ActivityAllAppsContainerView.java`

## Regression tests

New tests in `tests-e2e/regression/test_drawer_decomposition.py` (pre-existing file):
- `test_drawer_color_pref_invalidates_paint_without_relayout`
- `test_drawer_tab_color_pref_no_setup_header_storm`
- `test_search_fabs_appear_when_query_nonempty`
- `test_search_fabs_disappear_on_query_clear`
- `test_search_fab_web_intent_launches_browser`

## Verification

- `assembleDebug`: clean.
- `tests-e2e/smoke/ + regression/ + visuals/`: 25/25 pass.
