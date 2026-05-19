# 071 — Prefs framework Phase 2: ActivityAllAppsContainerView migration

T2.3 Phase 2 of `docs/plans/003-unified-prefs-framework-v2.md`. Migrates
`ActivityAllAppsContainerView`'s on-demand reads of three drawer prefs to
the subscribe-and-cache path provided by `PrefChangeDispatcher` (shipped
dormant in change 067).

## Scope

Three preferences:

- `DRAWER_BG_COLOR` — drawer bottom-sheet background color.
- `DRAWER_BG_OPACITY` — drawer bottom-sheet opacity.
- `DRAWER_HIDE_TABS` — hide personal/work tab bar.

## Changes

`src/com/android/launcher3/allapps/ActivityAllAppsContainerView.java`:

- New `mDrawerHideTabs` field caches the boolean value of
  `DRAWER_HIDE_TABS`. Initialized in the constructor before
  `onFinishInflate()` runs `rebindAdapters(force=true)`, since the
  callers consult the value during that path.
- New `mDrawerPrefSubscriber` (anonymous `PrefSubscriber`) handles
  pref-change callbacks on the main thread:
  - On `DRAWER_HIDE_TABS` change: re-read the value into
    `mDrawerHideTabs`, then call `updateRVContainerRules()` and
    `setupHeader()` (the latter remains guarded by `mSuppressSetupHeader`
    per invariant #1).
  - On `DRAWER_BG_COLOR` / `DRAWER_BG_OPACITY` change: call
    `refreshCustomColors()` (already idempotent; existing callers via
    `onDeviceProfileChanged` continue to work).
- `onAttachedToWindow()` subscribes via
  `LauncherPrefs.get(getContext()).getPrefChanges().subscribe(...)` and
  stores the `AutoCloseable` in `mDrawerPrefSubscription`.
- `onDetachedFromWindow()` closes the subscription.
- The two on-demand reads at the former `setupHeader()` and
  `updateRVContainerRules()` call sites now read `mDrawerHideTabs`
  directly.

## Why this is additive (no behavior change)

The existing `AppDrawerColorsFragment` /
`AppDrawerFragment.gridChangeListener` paths still call
`InvariantDeviceProfile.onConfigChanged()` on each color / opacity /
hide-tabs write. Both paths drive the same refresh actions
(`refreshCustomColors()`, `setupHeader()`,
`updateRVContainerRules()`), so the user-visible effect is identical —
the new subscriber simply also reacts. Phase 3 will remove the
`IDP.onConfigChanged` calls and the framework will own the cascade
exclusively.

## Why this respects the drawer invariants

Per `docs/architecture/drawer-invariants.md`:

- **Invariant #1 (`mSuppressSetupHeader`)**: the new code path calls
  `setupHeader()` which already early-returns when
  `mSuppressSetupHeader` is true. No new bypass of this gate.
- **Invariant #8 (`setupHeader()` reads `mUsingTabs` and
  `DRAWER_HIDE_TABS` together)**: this migration is the first half of
  routing both reads through one cache — `DRAWER_HIDE_TABS` now lives
  in `mDrawerHideTabs`. `mUsingTabs` remains derived from work-profile
  state and is set elsewhere; collapsing them is the v2-plan-phase-3
  cleanup, not this commit.

## Verification

- `assembleDebug`: clean (1 file modified, 2 imports added).
- `tests-e2e/smoke/ + regression/ + visuals/`: 25/25 in 337.88s.

## Files

- `src/com/android/launcher3/allapps/ActivityAllAppsContainerView.java`
  - +2 imports (`Item`, `PrefSubscriber`).
  - +1 field (`mDrawerHideTabs`).
  - +1 field (`mDrawerPrefSubscription`).
  - +1 final field (`mDrawerPrefSubscriber`).
  - +1 constructor-line cache init.
  - +5 lines in `onAttachedToWindow()` (subscribe).
  - +9 lines in `onDetachedFromWindow()` (close).
  - 2 on-demand reads replaced with cached field.
- `docs/changes/071-prefs-framework-drawer-container.md` (this file).
