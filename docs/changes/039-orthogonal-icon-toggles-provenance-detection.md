# 039: Orthogonal Icon Toggles & Provenance-Based Detection

## Summary

Replaces the single "Apply adaptive icon shape" toggle with an orthogonal two-toggle
system that independently controls pack icons and system icons. Fixes icon classification
by using provenance (where the icon came from) instead of `instanceof AdaptiveIconDrawable`,
which was broken because icon packs always return plain `BitmapDrawable`.

Adds "Shape unsupported icons" toggle, shaped icon background color picker, and background
opacity slider for both home screen and app drawer.

## Problem

The previous implementation used `icon instanceof AdaptiveIconDrawable` to distinguish
"pack" vs "system" icons. This is fundamentally wrong because:

- Icon packs always return plain `BitmapDrawable` from `res.getDrawable()`, even when
  the pack is "adaptive" (Material Everything, LawnIcons)
- ALL pack icons were classified as "non-adaptive" and only respected the adaptive toggle
- System icons (which ARE `AdaptiveIconDrawable`) could never be independently controlled

## Design

### Two Orthogonal Toggles

| Toggle | Controls | Effect when ON | Effect when OFF |
|--------|----------|----------------|-----------------|
| Apply adaptive shape | Pack icons | Custom shape (wrapped) | Raw (no shape) |
| Shape unsupported icons | System icons | Custom shape | OEM device shape |

### Provenance-Based Detection

Instead of checking Drawable type, icons are tagged at resolution time:

```
IconPack.getIcon() → IconPackDrawable.wrap(drawable)  // tagged as pack
pm.getActivityIcon() → returned as-is                 // no tag = system
```

At render time, factories check `IconPackDrawable.isFromPack(icon)` and unwrap before
passing to the base pipeline.

### Wrapper Background

When pack icons are forced into a shape, the area behind the icon can look empty.
New settings allow picking a background color and opacity for shaped icons.

## New Files

| File | Purpose |
|------|---------|
| `icons/IconPackDrawable.java` | `DrawableWrapper` marker with `wrap()`, `unwrap()`, `isFromPack()` |

## Modified Files

### Icon resolution (wrapping at source)

- **`LauncherIconProvider.java`** — Wrap returns in `resolveFromPack()`, `resolveOverride()`,
  and inline `applyFallbackMask()`. System fallback returns left unwrapped.
- **`DrawerIconResolver.java`** — Wrap in `resolveFromPack()` (single return) and
  `getDrawerIcon()` State C specific-drawable path.
- **`PerAppHomeIconResolver.java`** — Wrap all 7 pack-sourced returns in `resolveIcon()`.
  Added missing `applyFallbackMask` path for global home pack fallback.
- **`AppCustomizeFragment.java`** — Wrap pack returns in `resolveRawIcon()` (settings preview).

### Icon factories (provenance check at render)

- **`LauncherIcons.kt`** — `createBadgedIconBitmap()` uses `isFromPack`/`unwrap` instead
  of `instanceof`. OEM shape flag: `useOemForNative && !isPackIcon`. Added `wrapperBgColorInt`
  for wrapper background, `mUseOemShape` flag for conditional OEM fallback in `getShapePath()`,
  `getIconScale()`, and `drawAdaptiveIcon()`.
- **`DrawerIconResolver.java`** — Same pattern in `DrawerIconFactory` and
  `PerAppDrawerIconFactory`. Both factories now have `mSkipWrapNonAdaptive`, `mUseOemForNative`,
  `mUseOemShape`, and `mWrapperBgColorInt` fields.
- **`PerAppHomeIconResolver.java`** — Same pattern in `PerAppIconFactory`.

### Settings & preferences

- **`LauncherPrefs.kt`** — 6 new prefs: `WRAP_UNSUPPORTED_ICONS[_DRAWER]`,
  `ICON_WRAP_BG_COLOR[_DRAWER]`, `ICON_WRAP_BG_OPACITY[_DRAWER]`.
- **`ThemeManager.kt`** — New `IconState` fields: `skipWrapNonAdaptive[Drawer]`,
  `useOemForNative[Drawer]`, `wrapperBgColor[Drawer]`. Orthogonal shape model selection
  (needed when either toggle is ON). `resolveWrapperBgColor()` helper. Updated `toUniqueId()`.
- **`HomeScreenFragment.java`** — Wires wrap-unsupported toggle, BG color/opacity prefs,
  visibility rules via `refreshIconPrefVisibility()`.
- **`AppDrawerFragment.java`** — Same for drawer equivalents, with match-home hide logic.
- **`SettingsBaseFragment.java`** — Shared `refreshIconPrefVisibility()` method.
- **`strings.xml`** — New strings for wrap-unsupported, BG color, BG opacity.
- **`home_screen_preferences.xml`** / **`app_drawer_preferences.xml`** — New preference entries.

### Other

- **`IconPack.java`** — Simplified `isAdaptivePack()`: fallback mask = non-adaptive,
  everything else = adaptive. Removed unreliable drawable sampling heuristic.
- **`IconSettingsHelper.java`** — `autoDetectAdaptive()` now sets both ON and OFF
  (not just ON) based on pack detection.
- **`ShapesProvider.kt`** — New `findKeyForMask()` utility replacing inline loops
  in per-app factories.
- **`.gitignore`** — Allow `.claude/skills/` to be tracked.

## Settings Added

| Setting | Key | Type | Default |
|---------|-----|------|---------|
| Shape unsupported icons | `pref_wrap_unsupported_icons` | Boolean | false |
| Shape unsupported icons (drawer) | `pref_wrap_unsupported_icons_drawer` | Boolean | false |
| Shaped icon background | `pref_icon_wrap_bg_color` | String (color name) | "" |
| Shaped icon background (drawer) | `pref_icon_wrap_bg_color_drawer` | String (color name) | "" |
| Shaped icon opacity | `pref_icon_wrap_bg_opacity` | Int (0-100) | 100 |
| Shaped icon opacity (drawer) | `pref_icon_wrap_bg_opacity_drawer` | Int (0-100) | 100 |

## Visibility Rules

- **Shape picker**: visible when adaptive ON or wrap-unsupported ON (someone needs the shape)
- **Wrap unsupported toggle**: always visible
- **BG color / opacity**: visible when adaptive ON (pack icons being shaped need a fill)
