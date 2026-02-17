# 017: Search Animation, Home Transition, Tab Padding, and Work FAB Fixes

Post-016 bug fixes addressing search crossfade animation, home-press transitions,
tab/icon spacing consistency, and a complete rewrite of the work profile FAB.

## Changes

### Search Crossfade Animation (Bug 1)

`showAppsWhileSearchActive()` previously toggled views manually (visibility, alpha,
translationY), bypassing the animation system. This left `mAnimatorProgress` at 0,
causing subsequent enter/exit crossfades to be no-ops (animating 0→0).

Replaced with a proper `animateToSearchState(false)` call. This plays the standard
exit crossfade, resets `mAnimatorProgress` to 1, and recalculates padding via
`setupHeader()`. A `mKeepKeyboardOnSearchExit` flag skips keyboard dismissal and
focus clearing in the end callback so the search bar stays active.

### Search Transition Tab Offset (related)

In `SearchTransitionController`, the tab-offset translationY was applied as a
constant, causing a visual jump at animation end when `translationY(0)` was set
in the end callback. Fixed by scaling the offset by `searchProgress` (1→0) so it
smoothly reaches 0.

### Home Press Instant Transition (Bug 2)

`Launcher.onNewIntent()` animated the state change from ALL_APPS to NORMAL even
when returning from another app, producing a distracting drawer-close animation.
Conditioned animation on `alreadyOnHome` — only animate when the user is already
on the home screen (natural swipe/gesture), not when returning from another app.

### Tab Padding Uniformity (Bug 3)

`FloatingHeaderView.getMaxTranslation()` returned different values for tabs-visible
(60dp) vs tabs-hidden (30dp), creating inconsistent spacing between header elements
and the first icon row. Simplified to return the uniform
`all_apps_search_bar_bottom_padding` (34dp) whenever there are no floating rows
(predicted apps), regardless of tab visibility. Tuned `all_apps_header_top_padding`
from 36dp to 32dp to balance overall spacing.

### Work FAB Rewrite (Bug 5)

Completely rewrote the work profile FAB system to replace the complex AOSP
expand/shrink animation with a simple always-expanded FAB.

**WorkUtilityView** — Reduced from ~500 lines to ~170 lines. Removed all animation
code: AnimatorSet, ValueAnimator, flags (FLAG_IS_EXPAND, FLAG_FADE_ONGOING,
FLAG_TRANSLATION_ONGOING), extend/shrink methods, scroll threshold, text width/alpha
animations, scheduler button, and StringCache dependency. The FAB always shows
icon + text. Keyboard insets only affect translationY. Text is set directly from
string resources (`work_apps_pause_btn_text` / `work_apps_enable_btn_text`) via
`updateForState()`, removing the async `StringCache` (DevicePolicyManager enterprise
API) which could return null before loading completed.

**WorkProfileManager** — Unified click handler for both pause and enable states.
Removed scroll listener (`newScrollListener()`). FAB visibility is now: show on
WORK tab, show in no-tabs mode (combined list), hide on PERSONAL tab, hide during
SEARCH. FAB stays attached during both ENABLED and DISABLED states (no detach on
disable). Removed `VIEW_TYPE_WORK_DISABLED_CARD` — grayed icons shown instead.

**Grayed work app icons** — `BaseAllAppsAdapter.onBindViewHolder()` calls
`setIconDisabled(true)` on work app `BubbleTextView` icons when the work profile
is paused. Tapping a grayed icon shows a toast ("Work apps are paused") via a check
in `ItemClickHandler`.

### FAB M3 Styling

- Background: `materialColorPrimary` → `materialColorPrimaryContainer`
- Text/icon: `materialColorOnPrimary` → `materialColorOnPrimaryContainer`
- Elevation: 6dp → 0dp (flat M3 tonal style)
- Icon fill uses M3 color resource directly (removed `android:tint`)
- Increased internal padding (icon start 4→16dp, text start 8→12dp, text end 10→20dp)
- Search online FAB: null stateListAnimator to prevent default M3 shadow

### No-tabs FAB Preservation

`replaceAppsRVContainer()` no longer calls `detachWorkUtilityViews()` when switching
to no-tabs mode. Instead calls `mWorkManager.reset()` so the FAB remains visible
when work apps appear in the combined list. `applyPadding()` accounts for FAB height
in both work-tab and no-tabs modes.

## Files Changed

| File | Change |
|------|--------|
| `src/.../allapps/ActivityAllAppsContainerView.java` | Search keyboard flag, animateToSearchState path, removed scroll listener, FAB padding for no-tabs, removed StringCache caller |
| `src/.../allapps/WorkUtilityView.java` | Complete rewrite — removed all animation, scheduler, StringCache; added `updateForState()` |
| `src/.../allapps/WorkProfileManager.java` | Unified click handler, simplified visibility, always-attach FAB, removed scroll listener and disabled card |
| `src/.../allapps/FloatingHeaderView.java` | Simplified `getMaxTranslation()` for uniform spacing |
| `src/.../allapps/SearchTransitionController.java` | Scale tab offset by search progress |
| `src/.../allapps/BaseAllAppsAdapter.java` | Gray out work app icons when profile paused |
| `src/.../touch/ItemClickHandler.java` | Toast on tap for paused work apps |
| `src/.../Launcher.java` | Condition state animation on `alreadyOnHome` |
| `res/drawable/ic_corp.xml` | M3 color, removed tint |
| `res/drawable/ic_corp_off.xml` | M3 color |
| `res/drawable/work_mode_fab_background.xml` | M3 primaryContainer, removed padding |
| `res/layout/work_mode_fab.xml` | 0dp elevation, M3 colors, updated padding |
| `res/layout/work_mode_utility_view.xml` | Removed scheduler button |
| `res/values/dimens.xml` | Tuned search/header padding, FAB internal spacing |
