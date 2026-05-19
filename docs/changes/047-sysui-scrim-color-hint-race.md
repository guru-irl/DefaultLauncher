# 047: SysUiScrim Color-Hint Race & Deletion-Guard Polish

## Summary

Fixes a long-standing visual bug where the dark gradient behind the status bar
(`SysUiScrim`'s top mask) would appear randomly on light wallpapers for the first
frame(s) after launcher startup. Also applies follow-up review feedback from change
046 — drops the unused `UserHandle` parameter from `ServiceReadiness` and adds a
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

## Modified Files

| File | Change |
|------|--------|
| `src/com/android/launcher3/graphics/SysUiScrim.java` | Hint-driven visibility + listener; mutable bitmaps; lazy `refreshFromColorHints` |
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
