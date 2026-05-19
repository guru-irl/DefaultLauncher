# 047: SysUiScrim Color-Hint Race, Status-Bar Shadow Toggle & Deletion-Guard Polish

## Summary

Fixes two visual bugs around the launcher's status-bar gradient (`SysUiScrim`'s
top mask) and exposes a Desktop setting to turn it off entirely.

1. **Cold-start race**: on launcher startup right after device unlock, the dark
   scrim could flash over a light wallpaper for a frame or two because the theme
   attribute (`isWorkspaceDarkText`) was cached at construction time and
   lagged the wallpaper color hints.
2. **State-transition fade noise**: on dark wallpapers the scrim fades back in
   every time the user returns to the home screen from the app drawer or other
   launcher states, which reads as a random shadow "appearing." A new user
   setting under **Home screen → Wallpaper → Status bar shadow** lets the user
   turn the scrim off entirely.

Also applies follow-up review feedback from change 046 — drops the unused
`UserHandle` parameter from `ServiceReadiness` and adds a
`targetComponent.packageName` fallback to the widget deletion guard.

## The Scrim Race

`SysUiScrim` cached `R.attr.isWorkspaceDarkText` once at construction. That theme
attribute is decided by `Themes.getActivityThemeRes` from `WallpaperColorHints`,
which in turn synchronously reads `WallpaperManager.getWallpaperColors(FLAG_SYSTEM)`.

On cold launcher startup right after device unlock or process restart, that synchronous
read can return a stale/empty `WallpaperColors` object (`colorHints = 0`). The theme
then falls back to the default `AppTheme` — `isWorkspaceDarkText = false` — and
`SysUiScrim` builds itself in "show scrim" mode, even when the user's wallpaper is
light. The scrim renders. Later `WallpaperManager` delivers the real colors,
`WallpaperThemeManager.onColorHintsChanged` calls `activity.recreate()`, and the
new `SysUiScrim` instance gets the right value — but the dark band is visible during
the gap.

## Fix

`SysUiScrim` now drives its visibility directly from `WallpaperColors.HINT_SUPPORTS_DARK_TEXT`
rather than the cached theme attribute, and implements `OnColorHintListener` so it
re-evaluates when hints update — without waiting for the activity recreation roundtrip.

- `mHideSysUiScrim`, `mTopMaskBitmap`, `mBottomMaskBitmap` are now mutable. Bitmaps
  are created/discarded lazily by `refreshFromColorHints(int)`.
- `WallpaperColorHints` listener is registered in `onViewAttachedToWindow` and
  unregistered in `onViewDetachedFromWindow`, with a `refreshFromColorHints` call
  on attach to catch hint changes that arrived while detached.
- Constructor seeds the initial state from `WallpaperColorHints.get(...).hints`
  rather than from the theme attribute.

This makes the scrim self-correcting on any hint delivery, so the visible artifact
disappears even before `WallpaperThemeManager` triggers the activity recreation
that would otherwise eventually fix it.

## Follow-up on 046

Two findings from the self-review of change 046:

- **Unused `UserHandle` parameter dropped.** `ServiceReadiness.isPackageProbablyInstalled`
  accepted a `user` parameter that was never used — `PackageManager.getPackageInfo`
  queries only the launcher's own user namespace. Documenting the cross-user limitation
  is more honest than the implied parameter. All call sites updated.
- **`targetComponent` fallback added.** `WidgetInflater`'s guard now derives the
  provider package from `item.providerName ?: item.targetComponent`. If `providerName`
  is null but `targetComponent` is set (rare), the guard still runs.

## Status-Bar Shadow Toggle

A new boolean preference, `pref_show_top_shadow` (default `true`), gates whether
`SysUiScrim` draws its masks. The wallpaper-hint signal is OR'd with the user's
preference: scrim is hidden if either the wallpaper supports dark text **or** the
user has disabled it. `SysUiScrim` registers a `LauncherPrefChangeListener` in
`onViewAttachedToWindow` so the toggle updates take effect immediately without
needing to reopen the launcher.

## Settings Added

| Setting | Key | Type | Default |
|---------|-----|------|---------|
| Status bar shadow | `pref_show_top_shadow` | Boolean | `true` |

## Modified Files

| File | Change |
|------|--------|
| `src/com/android/launcher3/graphics/SysUiScrim.java` | Hint-driven visibility + listener; mutable bitmaps; lazy `refreshFromColorHints`; honors `SHOW_TOP_SHADOW` preference via `LauncherPrefChangeListener` |
| `src/com/android/launcher3/LauncherPrefs.kt` | Add `SHOW_TOP_SHADOW` preference item |
| `res/xml/home_screen_preferences.xml` | Add `pref_show_top_shadow` switch under Wallpaper category |
| `res/values/strings.xml` | Add `top_shadow_title` and `top_shadow_summary` strings |
| `src/com/android/launcher3/util/ServiceReadiness.java` | Drop `UserHandle` param; add cross-user limitation kdoc |
| `src/com/android/launcher3/widget/WidgetInflater.kt` | Provider-pkg fallback to `targetComponent` at both guard sites |
| `src/com/android/launcher3/model/WorkspaceItemProcessor.kt` | Update calls to drop `c.user` arg |

## Design Decisions

**Why hints directly, not theme attribute?** The theme attribute is set once per activity
lifetime via `activity.setTheme()`; it can only become "correct" via `activity.recreate()`.
Hints, in contrast, can be re-read on every listener fire. By skipping the attribute
indirection, the scrim becomes consistent with the underlying signal that
`WallpaperThemeManager` already drives off of.

**Why null bitmaps when hidden?** A hidden scrim doesn't need its alpha mask in memory.
For light-wallpaper users — the common case — this saves the bitmap allocation
entirely until the wallpaper changes.

**Why not just keep the attribute and add `setTheme` calls?** `Activity.setTheme` after
inflation doesn't actually re-style already-inflated views. Re-styling requires
activity recreation. Hint-driven state avoids both.
