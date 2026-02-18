# 025: Wallpaper Scroll Toggle, Adaptive Shape Refresh, Close Animation

## Summary

Three targeted fixes: a new wallpaper scrolling toggle in Home Screen settings, a fix for the adaptive shape switch not visually updating after icon pack changes, and improved app close animations that produce a visible cross-fade transition instead of an instant cut.

## What Changed

### Wallpaper scrolling toggle (new setting)

- **`LauncherPrefs.kt`**: Added `WALLPAPER_SCROLL` pref (default: `true`, matching existing behavior)
- **`home_screen_preferences.xml`**: New "Wallpaper" category with a `SwitchPreferenceCompat`
- **`strings.xml`**: Three new strings (`home_screen_wallpaper_category`, `wallpaper_scroll_title`, `wallpaper_scroll_summary`)
- **`Workspace.java`**: `computeScroll()` and `onLayout()` now check `WALLPAPER_SCROLL` before calling `mWallpaperOffset.syncWithScroll()`. When off, the wallpaper stays fixed regardless of page position.

### Adaptive shape switch refresh fix

**Problem:** When `autoDetectAdaptive()` writes `APPLY_ADAPTIVE_SHAPE` after an icon pack change, the `SwitchPreferenceCompat` in the open Settings fragment doesn't visually update. The value is correct in prefs but the UI is stale.

- **`HomeScreenFragment.java`** / **`AppDrawerFragment.java`**: Added `refreshAdaptiveShapeState()` — re-reads the pref and calls `setChecked()` + `setVisible()` on the adaptive switch and shape picker.
- **`IconSettingsHelper.java`**: The `applyIconPack` callback now calls `refreshAdaptiveShapeState()` on the parent fragment after completion.

### Adaptive auto-detect: only flip ON, never force OFF

**Problem:** `autoDetectAdaptive()` unconditionally wrote `isAdaptive` to the pref, meaning a non-adaptive pack would force the switch OFF even if the user had it on intentionally.

- **`IconSettingsHelper.java`**: `autoDetectAdaptive()` now only writes `true` when an adaptive pack is detected. Non-adaptive packs leave the switch as-is.

### App close animation improvement

**Problem:** The close transition used a no-op enter animation (alpha 1.0->1.0) and no exit animation (`0`), producing an instant cut on Pixel.

- **`launcher_home_enter.xml`** (updated): Subtle fade-in from 0.8 to 1.0 over 550ms with `decelerate_cubic`.
- **`launcher_app_close_exit.xml`** (new): Closing app fades out (1.0->0.0) + scales down (1.0->0.92) over 400ms with `accelerate_cubic`.
- **`Launcher.java`**: `overrideActivityTransition` now uses both animations. Wallpaper zoom animation synced to 550ms.

### Wallpaper zoom (hidden API limitation)

`WallpaperManager.setWallpaperZoomOut()` is a `@SystemApi` that Pixel's WallpaperManagerService silently ignores for third-party apps. The reflection-based call is kept for devices where it works, but on Pixel the depth-zoom effect is not available to non-system launchers.

### Other fixes

- **`WorkProfileManager.java`**: Safety guard preventing work FAB from showing when AllApps isn't visible.
- **`FloatingSurfaceView.java`**: Debug logging for gesture contract icon resolution.

## Files Changed

| File | Change |
|------|--------|
| `LauncherPrefs.kt` | Added `WALLPAPER_SCROLL` pref |
| `home_screen_preferences.xml` | Added Wallpaper category + toggle |
| `strings.xml` | Three new strings |
| `Workspace.java` | Wallpaper scroll pref check in `computeScroll()` and `onLayout()` |
| `HomeScreenFragment.java` | Added `refreshAdaptiveShapeState()` |
| `AppDrawerFragment.java` | Added `refreshAdaptiveShapeState()` |
| `IconSettingsHelper.java` | Fragment refresh after icon pack apply; auto-detect only flips ON |
| `Launcher.java` | Close transition animations, wallpaper zoom methods |
| `launcher_home_enter.xml` | Real fade-in animation (was no-op) |
| `launcher_app_close_exit.xml` | New exit animation (fade + scale) |
| `WorkProfileManager.java` | FAB visibility guard |
| `FloatingSurfaceView.java` | Debug logging |
