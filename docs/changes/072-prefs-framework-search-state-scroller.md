# 072 — Prefs framework Phase 2: search, state, scroller migrations

T2.3 Phase 2 continued. Completes the four migration targets called out
in the superplan; commit 071 covered `ActivityAllAppsContainerView`. This
commit migrates the remaining three.

## Scope

| Consumer | Prefs migrated | Lifecycle hook |
|----------|----------------|----------------|
| `AppsSearchContainerLayout` | `DRAWER_SEARCH_BG_COLOR`, `DRAWER_SEARCH_BG_OPACITY` | `onAttachedToWindow` / `onDetachedFromWindow` |
| `AllAppsState` | `DRAWER_BG_COLOR`, `DRAWER_BG_OPACITY` | Lazy init on first `getWorkspaceScrimColor()` |
| `RecyclerViewFastScroller` | `DRAWER_SCROLLBAR_COLOR` | New `onAttachedToWindow` / `onDetachedFromWindow` overrides |

All three follow the same idiom: subscribe via
`LauncherPrefs.get(ctx).getPrefChanges().subscribe(subscriber, items...)`,
store the returned `AutoCloseable`, close in the matching teardown hook.

## Per-consumer notes

### `AppsSearchContainerLayout`

View has existing `onAttachedToWindow` / `onDetachedFromWindow`
overrides. Subscribe is appended after `refreshSearchBarColor()`; the
unsubscribe is added inside the existing detach hook. Subscriber calls
`refreshSearchBarColor()` directly — the method already reads both
prefs and rebuilds the `GradientDrawable`, so the cache lives in
`mOriginalBackground` / the active `setBackground()` call.

### `AllAppsState`

This is a process-wide singleton (`LauncherState.ALL_APPS`). There is no
view-style attach hook, so the subscriber is registered lazily on the
first `getWorkspaceScrimColor(launcher)` call via
`ensurePrefCache(launcher)`. The handle is the application context's
`LauncherPrefs` instance, captured into a volatile `mPrefsRef` so the
subscriber lambda can re-read the values without holding any Launcher
reference. The subscription is intentionally never closed — `AllAppsState`
lives for the duration of the process.

The lookup path becomes:
- Hot path: read `mDrawerBgColor` / `mDrawerBgOpacity` volatile fields.
- Pref change: dispatcher coalesces, fires subscriber on the main
  thread, subscriber refreshes the two fields.

The tablet branch (line 105) is unchanged — tablets use the
`widgets_picker_scrim` resource directly, no pref involvement.

### `RecyclerViewFastScroller`

Adds new `onAttachedToWindow` / `onDetachedFromWindow` overrides that
manage `mScrollbarPrefSubscription`. Subscriber calls the existing
`refreshThumbColor()` method on `DRAWER_SCROLLBAR_COLOR` change. The
constructor's initial pref read is left in place — that's the boot-time
cache load. Subsequent changes flow through the subscriber.

The fully-qualified class names in the field declaration
(`com.android.launcher3.PrefSubscriber`, `com.android.launcher3.Item`)
are used in-place because `RecyclerViewFastScroller` already pulls a
large import set and stays self-contained; the verbosity is local.

## Why this is additive

Identical reasoning to 071: existing `IDP.onConfigChanged` writer paths
in `AppDrawerColorsFragment` continue to fire; the new subscribers also
fire. Both paths apply the same effect to the same fields. Phase 3 will
remove the `IDP.onConfigChanged` calls.

## Drawer-invariants check

None of these three touch the search-animation invariants (#1–#5) or
the floating-header geometry path (#6–#8). Invariant #14 (SysUiScrim
hint-driven visibility) is unrelated to drawer background prefs.

## Verification

- `assembleDebug`: clean.
- `tests-e2e/smoke/ + regression/ + visuals/`: 25/25 in 190.41s
  (faster than baseline because no fresh `pm clear` scaffolding
  rebuild on this run).

## Files

- `src/com/android/launcher3/allapps/search/AppsSearchContainerLayout.java`
  - +2 imports.
  - +1 field (`mSearchPrefSubscription`).
  - +1 final field (`mSearchPrefSubscriber`).
  - +4 lines in `onAttachedToWindow()`.
  - +8 lines in `onDetachedFromWindow()`.
- `src/com/android/launcher3/uioverrides/states/AllAppsState.java`
  - +4 imports (`Context`, `Item`, `PrefSubscriber`, `Set`).
  - +3 fields (`mDrawerBgColor`, `mDrawerBgOpacity`,
    `mPrefCacheInitialized`).
  - +1 field (`mPrefsRef`).
  - +1 final field (`mScrimPrefSubscriber`).
  - +1 method (`ensurePrefCache`).
  - `getWorkspaceScrimColor()` now reads cached fields.
- `src/com/android/launcher3/views/RecyclerViewFastScroller.java`
  - +1 field (`mScrollbarPrefSubscription`).
  - +1 final field (`mScrollbarPrefSubscriber`).
  - +2 overrides (`onAttachedToWindow`, `onDetachedFromWindow`).
- `docs/changes/072-prefs-framework-search-state-scroller.md`
  (this file).
