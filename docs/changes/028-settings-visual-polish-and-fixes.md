# 028 — Settings Visual Polish & Fixes

## Summary

Six UI fixes covering settings ripple clipping, stale animation state, debug logging,
search result contrast, icon pack transition simplification, notification dot colors,
and hotseat widget resizing.

## Changes

### 1. Preference ripple clipped to card shape

`CardGroupItemDecoration` now applies a position-aware `RippleDrawable` with a
`GradientDrawable` mask to each preference item. The mask's corner radii match the
card's (large for first/last, small for middle). The default rectangular ripple from
`Preference.Material` is suppressed via `android:background=@android:color/transparent`
in both the v31 `HomeSettings.PreferenceStyle` and a new base `HomeSettings.PreferenceStyleBase`.

New resource: `res/values/ids.xml` (`card_ripple_tag`) for tag-based reuse tracking.

### 2. Stale ripple cleared on back navigation

`SettingsBaseFragment.onResume()` iterates RecyclerView children and calls
`jumpDrawablesToCurrentState()` to cancel any in-progress ripple animations.

### 3. Debug guards auto-enable in debug builds

Five files changed `DEBUG_*` constants from `= false` to `= BuildConfig.DEBUG`:

| File | Constants |
|------|-----------|
| `DeviceProfile.java` | `DEBUG_SQUARE_GRID` |
| `Launcher.java` | `DEBUG_WS_PAD`, `DEBUG_LAUNCHER_ANIM` |
| `LauncherRootView.java` | `DEBUG_WS_PAD` |
| `Workspace.java` | `DEBUG_WS_PAD` |
| `FloatingSurfaceView.java` | `DEBUG_LAUNCHER_ANIM` |

`CLAUDE.md` convention updated to match.

### 4. Search result card contrast improved

Six `search_result_*.xml` layouts changed `app:cardBackgroundColor` from
`?attr/colorSurfaceContainer` to `@color/materialColorSurfaceContainerHigh` —
two surface-container steps above the drawer background (`SurfaceContainerLow`).

### 5. Icon pack picker transition simplified to M3 slide

Replaced the 350ms card-expand morph + 200ms crossfade in `PerAppIconSheet` with a
300ms (`M3Durations.MEDIUM_2`) X-axis shared axis transition: pack page slides left +
fades out while icon page slides in from the right. Back transition is the reverse.
Removed unused `Animator`, `ValueAnimator`, `AnimatorListenerAdapter` imports.
Search debounce also uses `M3Durations.MEDIUM_2`.

### 6. Notification dot color corrected per M3

Changed `notificationDotColor` from `materialColorTertiaryContainer` (muted container)
to `materialColorTertiary` (vivid role color) in both `LauncherTheme` and
`LauncherTheme.Dark`.

### 7. Hotseat widget resize enabled

Removed the `container != CONTAINER_HOTSEAT` guard in `Workspace.dropComplete()` that
prevented the resize frame from appearing for widgets placed on the hotseat.

## Files Modified

| File | Change |
|------|--------|
| `CardGroupItemDecoration.java` | Shaped RippleDrawable per item |
| `SettingsBaseFragment.java` | `onResume()` clears stale ripples |
| `res/values/ids.xml` | New — `card_ripple_tag` |
| `res/values/styles.xml` | Dot color, base preference style |
| `res/values-v31/styles.xml` | Transparent background on preference styles |
| `DeviceProfile.java` | `DEBUG_SQUARE_GRID = BuildConfig.DEBUG` |
| `Launcher.java` | `DEBUG_WS_PAD/LAUNCHER_ANIM = BuildConfig.DEBUG` |
| `LauncherRootView.java` | `DEBUG_WS_PAD = BuildConfig.DEBUG` |
| `Workspace.java` | `DEBUG_WS_PAD = BuildConfig.DEBUG`, hotseat resize |
| `FloatingSurfaceView.java` | `DEBUG_LAUNCHER_ANIM = BuildConfig.DEBUG` |
| `PerAppIconSheet.java` | Slide transition, M3 duration tokens |
| `search_result_*.xml` (6 files) | Card color to SurfaceContainerHigh |
| `CLAUDE.md` | Debug logging convention |
