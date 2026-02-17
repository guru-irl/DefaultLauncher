# 023: Search Animation Performance, Drawer Icon Caching, and Crash Fixes

## Summary

Optimized the search enter/exit animation to eliminate redundant layout passes and jank, added drawer icon pre-caching for zero-miss first scroll, fixed three threading crashes (icon shape change, per-app shape change, dark/light mode switch), fixed dialog window leaks, added color swatch outlines, and widened shortcut query error handling.

## Search Animation Performance

### ActivityAllAppsContainerView.java

- **`mSuppressSetupHeader` flag**: Added a boolean that suppresses `setupHeader()` during search animations. `setFloatingRowsCollapsed()` was triggering `setupHeader()` 2x per call (via `FloatingHeaderView`), causing 6+ layout passes on every animation start. The flag is set before the animation starts and cleared in the end callback.
- **Deferred search exit work**: After the exit animation completes, heavy work (`setupHeader()`, `setSearchResults(null)`, page restoration) is now posted to the next frame via `mPendingSearchExitWork`. This keeps the animation-end frame clean. The runnable is tracked so it can be cancelled if a new animation starts before it executes (rapid back-and-forth).
- **Cancel pending work on new animation**: `animateToSearchState()` cancels any `mPendingSearchExitWork` before starting, preventing collisions between deferred work and new animation first frames.
- **Explicit header reset on exit**: Instead of relying on `setupHeader()` during the animation, the exit path now explicitly calls `mHeader.setFloatingRowsCollapsed(false)` and `mHeader.reset(false)` to restore header state immediately.
- **RecyclerView item view cache**: Set `setItemViewCacheSize(mAdapter.getItemCount())` to cache all rows, eliminating rebinding on scroll direction reversals (~5KB per item, bitmaps shared from IconCache).
- **Drawer icon pre-caching**: After `onAllAppsChanged()`, launches `DrawerIconResolver.preCacheIcons()` on `MODEL_EXECUTOR` with all app components, so the first drawer scroll has zero cache misses.

### FloatingHeaderView.java

- Removed the `setupHeader()` call from `setFloatingRowsCollapsed()`. This was a workaround for AOSP predicted apps that caused redundant layout passes. The search animation now handles header setup explicitly in its completion callback.

### SearchTransitionController.java

- **EMPHASIZED interpolator**: Replaced `DECELERATE_1_7` with `EMPHASIZED` for smoother, M3-standard search transition curves.
- **Hardware layer acceleration**: Sets `LAYER_TYPE_HARDWARE` on header and apps container during animation, reverts to `LAYER_TYPE_NONE` on completion. This offloads translationY + alpha compositing to the GPU.
- **Cached `mTabsMarginTop`**: Dimension resource lookup moved from per-frame `onProgressUpdated()` to constructor, avoiding repeated resource resolution during animation.

### RecyclerViewAnimationController.java

- **`mImmediateRestart` flag**: When an animation is cancelled and immediately restarted (cancel→start in same frame), the end callback's `resetChildViewProperties()` is skipped. This prevents resetting child view transforms that the new animation is about to override, eliminating a flash frame.
- **Cached adapter items list**: `getAdapterItems()` is now called once per `onProgressUpdated()` instead of 3x per iteration, reducing object allocation during animation frames.

## Drawer Icon Cache Improvements

### DrawerIconResolver.java

- **Cache size 200→500**: Increased LRU cache to hold more app icons, reducing eviction churn on devices with many apps.
- **Cached `mHasDistinctSettings`**: `hasDistinctDrawerSettings()` result is now cached in a `volatile Boolean`, cleared on `invalidate()`. This avoids repeated `LauncherPrefs` + `ThemeManager` lookups on every icon bind.
- **`preCacheIcons()` method**: New method that pre-populates the drawer icon cache for all apps on a background thread, resolving icons through the same pack/shape/size pipeline as runtime resolution.

### AllAppsRecyclerView.java

- **Fling velocity scale 1.15→1.3**: Increased for snappier drawer scroll feel.

## Notification Dot Color Cache

### BubbleTextView.java

- **`sCachedDotColor` static cache**: `Themes.getAttrColor(R.attr.notificationDotColor)` is now resolved once and cached statically, avoiding repeated theme attribute resolution on every `applyFromApplicationInfo()` call.
- **`clearDotColorCache()`**: Static method called from `ModelInitializer` on theme changes to reset the cached value.

## Theme Change Listener Consolidation

### IconPackManager.java

- **Removed theme change listener**: The `ThemeManager.addChangeListener()` lambda that called `clearAllIcons()` on the main thread (causing threading crashes) has been removed entirely. Theme change handling is now consolidated in `ModelInitializer`.

### ModelInitializer.kt

- **Added `PerAppHomeIconResolver.invalidate()`**: Theme change listener now also invalidates per-app home icon overrides.
- **Added `BubbleTextView.clearDotColorCache()`**: Theme change listener clears the cached notification dot color so it's re-resolved in the new theme.
- The existing `DrawerIconResolver.invalidate()` + `refreshAndReloadLauncher()` handles the full icon cache clear and model reload on the correct threads.

## Dialog Window Leak Fix

### IconSettingsHelper.java

- **`dismissOnDestroy()` helper**: New private static method that registers a `LifecycleEventObserver` on the fragment's lifecycle. On `ON_DESTROY`, it auto-dismisses the `BottomSheetDialog` if still showing, then removes itself.
- Applied after `sheet.show()` in all four dialog methods: `showIconPackDialog()`, `showPerAppPackDialog()`, `showIconShapeDialog()`, `showPerAppShapeDialog()`.

## Color Swatch Outline

### ColorPickerPreference.java

- **`updateSwatchColor()`**: Added a 1dp `materialColorOutline` stroke to the swatch `GradientDrawable`, so color previews are visible against any background color (light or dark).

## ShortcutRequest Error Handling

### ShortcutRequest.java

- **Widened catch clause**: Changed from `SecurityException | IllegalStateException` to `Exception` to handle all failure modes when querying shortcuts for locked/non-running users (e.g., `DeadSystemException`, general `RuntimeException`). The existing `FileLog.e()` + `QueryResult.DEFAULT` fallback is correct for any failure.

## Files Changed

| File | Change |
|------|--------|
| `ActivityAllAppsContainerView.java` | Search animation deferred work, setupHeader suppression, RV cache, drawer pre-cache |
| `FloatingHeaderView.java` | Removed setupHeader() from setFloatingRowsCollapsed() |
| `SearchTransitionController.java` | EMPHASIZED interpolator, hardware layers, cached dimension |
| `RecyclerViewAnimationController.java` | Immediate restart flag, cached adapter items |
| `AllAppsRecyclerView.java` | Fling velocity 1.15→1.3 |
| `DrawerIconResolver.java` | Cache 200→500, cached hasDistinctSettings, preCacheIcons() |
| `BubbleTextView.java` | Static dot color cache + clearDotColorCache() |
| `IconPackManager.java` | Removed theme listener (moved to ModelInitializer) |
| `ModelInitializer.kt` | Added PerAppHomeIconResolver + BubbleTextView invalidation |
| `IconSettingsHelper.java` | dismissOnDestroy() lifecycle-aware dialog dismissal |
| `ColorPickerPreference.java` | Outline stroke on color swatch |
| `ShortcutRequest.java` | Widened catch to Exception |
