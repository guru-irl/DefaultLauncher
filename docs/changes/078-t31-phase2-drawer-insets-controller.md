# 078 — T3.1 Phase 2: DrawerInsetsController extraction

T3.1 Phase 2 of `docs/plans/004-drawer-decomposition-v2.md`. Extracts inset
and nav-bar scrim plumbing out of `ActivityAllAppsContainerView` (was 1931 LOC)
into a dedicated `DrawerInsetsController` collaborator.

## DrawerInsetsController

Owns:
- `mInsets` (Rect) — the last insets received via `setInsets()`
- `mNavBarScrimHeight` (int) — computed by `computeNavBarScrimHeight()`
- `applyInsets(Rect, DeviceProfile)` — called from `setInsets()`, stores insets +
  propagates `allAppsPadding.left/right` and `max(insets.bottom, navBarScrimHeight)`
  to all adapter holders
- `onDispatchApplyWindowInsets(WindowInsets)` — recomputes nav-bar scrim height via
  `host.computeNavBarScrimHeight()`, re-propagates adapter paddings
- `drawNavBarScrim(Canvas, float, float, int, int)` — draws the scrim rect with
  counter-scale correction for predictive back
- `getNavBarScrimHeight()`, `getInsets()` — read-only accessors

## Container after extraction

`ActivityAllAppsContainerView` retains:
- The `Insettable.setInsets()` contract (margin + padding of the container view itself)
- The `InsettableFrameLayout.dispatchInsets()` delegation
- The `computeNavBarScrimHeight()` protected hook (returns 0 in base; Launcher overrides)
- The FAB positioning in `dispatchApplyWindowInsets()` (SearchFabController)

## Construction order

Per Phase 1 doc (`docs/changes/075`), order is:
```
DrawerColorController(context, activityContext)   [constructor]
SearchFabController(materialCtx)                  [constructor]
DrawerInsetsController(this, mDrawerColorController)  [constructor — Phase 2]
```

`DrawerInsetsController` takes the host view and the color controller (for
`getNavBarScrimPaint()` used in `drawNavBarScrim`). Both are available at
construction time.

## AdapterHolder.applyPadding() fix

`AdapterHolder.applyPadding()` (inner class of `ActivityAllAppsContainerView`)
referenced the outer class's `mInsets.bottom` directly. After moving `mInsets`
to the controller, this became `mInsetsController.getInsets().bottom`. Both are
in the same package so the accessor call compiles without visibility issues.

## Invariants preserved

- **#14 SysUiScrim:** untouched — `SysUiScrim.java` not modified.
- **Insets path** was not previously cited in any invariant; correctness gated
  by smoke (`test_drawer_basics.test_drawer_opens_and_shows_icons`).
- `computeNavBarScrimHeight()` is called via `mHost.computeNavBarScrimHeight(insets)`
  — the protected override in Launcher subclass is honored because the controller
  is in the same package and uses the host reference.

## LOC change

| File | Before | After |
|------|--------|-------|
| `ActivityAllAppsContainerView.java` | 1931 | 1914 |
| `DrawerInsetsController.java` | — | 90 |

## Files added

- `src/com/android/launcher3/allapps/DrawerInsetsController.java`

## Files modified

- `src/com/android/launcher3/allapps/ActivityAllAppsContainerView.java`

## Regression tests

New file `tests-e2e/regression/test_drawer_insets.py` (3 tests):
- `test_drawer_inset_landscape_phone` — rotate to landscape, verify apps list
  visible (insets + padding reapplied)
- `test_drawer_insets_no_error_on_open_close` — logcat scan for E-level errors
  after a drawer open/close cycle
- `test_drawer_padding_survives_orientation_round_trip` — portrait→landscape→portrait,
  verify apps RV and search bar visible after restore

## Verification

- `assembleDebug`: clean.
- Full test suite (44 tests): 39 passed, 0 failed, 2 xfailed, 3 skipped. Exit 0.
