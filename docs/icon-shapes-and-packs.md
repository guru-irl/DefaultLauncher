# Icon Shapes & Icon Pack Integration

## Overview

The icon system has two independently configurable layers:

1. **Icon shape** — the mask/clip path applied to all icon bitmaps (circle, square, cookie, arch, none)
2. **Icon pack** — third-party ADW-format icon packs that replace individual app icons

Both are configured in Settings > Appearance and interact with each other through the AOSP `BaseIconFactory` pipeline.

## Icon Shape System

### Flag Gate

Icon shapes are gated behind `Flags.enableLauncherIconShapes()`, enabled in both `FeatureFlagsImpl.java` and `CustomFeatureFlags.java`. When enabled:

- `ShapesProvider.iconShapes` returns 6 shapes (circle, square, 4-sided cookie, 7-sided cookie, arch, none)
- `LauncherIcons.kt` overrides `getShapePath()`, `getIconScale()`, and `drawAdaptiveIcon()` to use custom paths
- `ThemeManager.PREF_ICON_SHAPE` preference is wired with listeners that trigger full icon reload

When disabled, only a dummy circle entry is returned and all `LauncherIcons` overrides fall through to `super`, which uses the system/OEM default mask.

### Shape Definitions

Shapes are defined in `ShapesProvider.kt` as SVG path strings on a 100x100 viewport:

| Key | Description | Icon Scale |
|-----|-------------|------------|
| `circle` | Standard circle | 1.0 (default) |
| `square` | Rounded squircle | 1.0 (default) |
| `four_sided_cookie` | 4-lobed clover | 72/83.4 ≈ 0.863 |
| `seven_sided_cookie` | 7-lobed flower | 72/80 = 0.9 |
| `arch` | Arch (rounded top, squared bottom) | 1.0 (default) |
| `none` | Full rectangle `M 0 0 H 100 V 100 H 0 Z` | 1.0 (default) |

Each shape also specifies a `folderPathString` for folder icons.

### Rendering Pipeline

The icon rendering flows through `BaseIconFactory`:

```
createBadgedIconBitmap(icon, options)
  → normalizeAndWrapToAdaptiveIcon(icon)
    → wrapToAdaptiveIcon(icon)          // wraps non-adaptive in AdaptiveIconDrawable
  → createIconBitmap(adaptive, scale)
    → drawIconBitmap(canvas, bitmap)
      → addPathShadow(shapePath)        // draws shadow behind shape
      → drawAdaptiveIcon(canvas, drawable, overridePath)
```

`LauncherIcons.kt` overrides several methods in this pipeline:

#### `getShapePath()` — Custom clip path
Returns `themeManager.iconShape.getPath(iconBounds)` instead of the system default.

#### `getIconScale()` — Per-shape icon scaling
Returns `themeManager.iconState.iconScale` which varies by shape (e.g., 0.863 for 4-sided cookie).

#### `wrapToAdaptiveIcon()` — Configurable background
Sets `mWrapperBackgroundColor` to the user's chosen wrapper BG color (defaults to transparent) before calling `super`. This allows icon pack icons to optionally get a colored fill behind the shape.

#### `drawAdaptiveIcon()` — Shape-aware rendering
Four branches:
1. **Flag disabled or OEM mode** (`mUseOemShape`): delegates to `super`
2. **"None" shape**: clears the shadow canvas with `PorterDuff.Mode.CLEAR`, then draws bg/fg layers without any clip path
3. **BLUR_FACTOR compensation**: when the user's `iconSizeScale` would produce a smaller offset than the blur floor allows, redraws at the correct size
4. **Other shapes**: clips to `overridePath`, fills transparent, scales by `iconScale`, draws bg/fg layers

#### `createBadgedIconBitmap()` — Provenance-based classification
Uses `IconPackDrawable.isFromPack(icon)` to distinguish pack icons from system icons by provenance, then `IconPackDrawable.unwrap(icon)` to get the real drawable for rendering:

- **Pack icons + (none shape or `skipWrapNonAdaptive`)**: bypasses the adaptive pipeline entirely, draws raw icon at user's size scale
- **System icons + `useOemForNative`**: sets `mUseOemShape = true` so `getShapePath()`/`getIconScale()`/`drawAdaptiveIcon()` fall through to OEM defaults
- **All other icons**: rendered through the custom shape pipeline

### "None" Shape Details

The "none" shape (`M 0 0 H 100 V 100 H 0 Z`) is a full-rectangle path that effectively disables clipping. Special handling is needed because:

1. **Shadow**: `addPathShadow()` draws a visible gray rectangle for the full-rect path. The `drawAdaptiveIcon()` override clears this with `PorterDuff.Mode.CLEAR`.
2. **Non-adaptive icons**: Without the `createBadgedIconBitmap()` bypass, icons go through `LEGACY_ICON_SCALE` (≈0.467) + `ICON_VISIBLE_AREA_FACTOR` (0.92), rendering at ~43% of cell size. The bypass draws at 100%.
3. **Adaptive icons**: The override draws bg/fg layers without clip path, so adaptive icons show their full rectangular extent.

### Preference & Auto-Reload

The icon shape preference is stored via `ThemeManager.PREF_ICON_SHAPE` (key: `"icon_shape_model"`). ThemeManager registers a `LauncherPrefChangeListener` that triggers:

```
pref change → verifyIconState() → onThemeChanged() → full icon reload
```

No manual `forceReload()` is needed — ThemeManager handles it automatically.

## Icon Pack Integration

See `docs/changes/006-adw-icon-pack-support.md` for the core ADW icon pack architecture (parsing, discovery, fallback masking).

### Provenance-Based Icon Classification

Icon pack icons are tagged at resolution time with `IconPackDrawable.wrap()`, a thin
`DrawableWrapper` marker. At render time, `IconPackDrawable.isFromPack()` checks provenance
and `IconPackDrawable.unwrap()` extracts the real drawable.

This replaces the previous `instanceof AdaptiveIconDrawable` check, which was broken
because icon packs always return plain `BitmapDrawable` (never `AdaptiveIconDrawable`).

Wrapping happens in:
- `LauncherIconProvider.resolveFromPack()` and `resolveOverride()`
- `DrawerIconResolver.resolveFromPack()` and `getDrawerIcon()` State C
- `PerAppHomeIconResolver.resolveIcon()` for all pack-sourced returns
- `AppCustomizeFragment.resolveRawIcon()` for settings previews

System icon returns (`pm.getActivityIcon()`, `systemFallback.get()`) are never wrapped.

### Orthogonal Toggle System

Two independent toggles control which icons receive custom shapes:

| Toggle | Controls | ON | OFF |
|--------|----------|-----|-----|
| Apply adaptive shape | Pack icons | Custom shape | Raw (no shape) |
| Shape unsupported icons | System icons | Custom shape | OEM device shape |

The toggles map to `IconState` flags:
- `skipWrapNonAdaptive = !applyAdaptive` — pack icons drawn raw when adaptive OFF
- `useOemForNative = !wrapUnsupported` — system icons use OEM shape when unsupported OFF

### Interaction with Icon Shapes

Icon pack icons are typically plain `Drawable`s (not `AdaptiveIconDrawable`). When combined with shapes:

- **Adaptive ON**: Pack icons get wrapped in `AdaptiveIconDrawable` with configurable background color (via `wrapToAdaptiveIcon()` override), then clipped to the shape path.
- **Adaptive OFF**: Pack icons bypass the adaptive pipeline entirely via `createBadgedIconBitmap()`, rendering at full size without any mask or background.
- **Unsupported ON**: System adaptive icons get the custom shape.
- **Unsupported OFF**: System adaptive icons get the OEM device shape (factory falls through to `super`).

### Icon Pack Apply Flow

When the user selects an icon pack in settings:

```
1. Save pref, invalidate IconPackManager
2. Show "Applying icon pack..." loading state (disable dialog controls)
3. On MODEL_EXECUTOR:
   a. Pre-parse the icon pack (mgr.getCurrentPack() → ensureParsed())
   b. Clear both disk and memory icon cache (iconDb.clear() + clearMemoryCache())
4. Post to main thread:
   a. Clear LauncherIcons pool (stale factory instances)
   b. forceReload() → submits LoaderTask to MODEL_EXECUTOR
5. Queue sentinel on MODEL_EXECUTOR (runs after LoaderTask):
   a. Post to main thread: dismiss dialog
```

The critical step is **clearing the disk cache** (`iconDb.clear()`). Without this, `BaseIconCache.cacheLocked()` finds stale disk entries and serves old icons during workspace binding. The `SerializedIconUpdateTask` would then slowly update them one-by-one in the background, causing a visible delay. With disk cache cleared, `cacheLocked()` falls through to `loadFallbackIcon()` → `cachingLogic.loadIcon()` which loads fresh icons from the provider synchronously.

### Cache Invalidation Chain

```
IconCache.clearAllIcons()
  → iconDb.clear()           // drop & recreate SQLite table
  → clearMemoryCache()       // clear in-memory ConcurrentHashMap

LauncherIcons.clearPool()    // discard pooled factory instances (may hold stale ThemeManager state)

LauncherModel.forceReload()
  → stopLoader()
  → mModelLoaded = false
  → rebindCallbacks()        // submits new LoaderTask on MODEL_EXECUTOR
```

## Settings UI

### Appearance Category (`launcher_preferences.xml`)

```xml
<PreferenceCategory android:key="category_appearance">
    <Preference android:key="pref_icon_pack" />
    <Preference android:key="pref_icon_shape" />
</PreferenceCategory>
```

Both are click-to-open `AlertDialog`s with `setSingleChoiceItems`.

### Icon Pack Dialog
- Lists "System default" + all installed ADW icon packs
- On selection: shows loading state, pre-parses pack, clears cache, reloads, dismisses after LoaderTask completes

### Icon Shape Dialog
- Lists "System default" + all shapes from `ShapesProvider.iconShapes`
- On selection: saves pref to `ThemeManager.PREF_ICON_SHAPE`, ThemeManager auto-reloads icons

## Files

| File | Role |
|------|------|
| `ShapesProvider.kt` | Shape definitions (SVG paths, keys, scales), `findKeyForMask()` |
| `LauncherIcons.kt` | Rendering overrides (provenance check, shape clipping, OEM bypass, wrapper bg) |
| `IconPackDrawable.java` | `DrawableWrapper` marker for provenance-based icon classification |
| `BaseIconFactory.java` | Base pipeline (`protected mWrapperBackgroundColor`, transparent fill) |
| `LauncherIconProvider.java` | Icon pack lookups, wraps pack drawables with `IconPackDrawable` |
| `DrawerIconResolver.java` | Drawer-specific icon resolution, wraps pack drawables, two factories |
| `PerAppHomeIconResolver.java` | Per-app home icon resolution, wraps pack drawables, factory |
| `IconCache.java` | `clearAllIcons()` for disk+memory cache clear |
| `ThemeManager.kt` | Shape/toggle preference storage, `IconState` with orthogonal flags |
| `SettingsActivity.java` | Icon pack dialog (loading UX), icon shape dialog |
| `FeatureFlagsImpl.java` | `enableLauncherIconShapes()` flag |
| `CustomFeatureFlags.java` | `enableLauncherIconShapes()` flag |
