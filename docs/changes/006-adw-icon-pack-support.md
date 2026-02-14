# 006: ADW Icon Pack Support, Icon Shapes & Loading UX

## Summary

Added support for ADW-format icon packs, the de facto standard across Android launchers. Users can select an installed icon pack from Settings > Appearance to customize all home screen and app drawer icons. Additionally, enabled the AOSP icon shape system with 6 shape options (circle, square, 4-sided cookie, 7-sided cookie, arch, none) and a dedicated settings picker. The icon pack apply flow includes a loading UX with full disk+memory cache clearing to ensure icons update instantly without visible lag.

## New Files

| File | Purpose |
|------|---------|
| `src/.../icons/pack/IconPack.java` | Model: parses appfilter.xml, provides icon lookups and fallback masking |
| `src/.../icons/pack/IconPackManager.java` | Dagger singleton: discovery, selection, lazy parsing, broadcast receiver |
| `src/.../icons/pack/IconPackReceiver.java` | BroadcastReceiver: auto-refresh on pack install/uninstall/update |
| `docs/icon-shapes-and-packs.md` | Architecture doc for icon shape system and icon pack integration |

## Modified Files

| File | Changes |
|------|---------|
| `LauncherIconProvider.java` | Added `IconPackManager` constructor param, override `getIcon()` for pack lookups with calendar/exact/fallback-mask chain, append pack ID to system state |
| `LauncherPrefs.kt` | Added `ICON_PACK` string preference |
| `SettingsActivity.java` | Icon pack dialog with loading UX (disable controls, "Applying..." title, pre-parse on MODEL_EXECUTOR, clear caches, sentinel-based dismiss). Icon shape dialog with single-choice list. Helper methods for shape display names. |
| `launcher_preferences.xml` | Added Appearance category with `pref_icon_pack` and `pref_icon_shape` preferences |
| `strings.xml` | Added: `appearance_category_title`, `icon_pack_title`, `icon_pack_default`, `icon_pack_applying`, `icon_shape_title`, `icon_shape_default`, `icon_shape_none`, `icon_shape_circle`, `icon_shape_square`, `icon_shape_four_sided_cookie`, `icon_shape_seven_sided_cookie`, `icon_shape_arch` |
| `LauncherBaseAppComponent.java` | Exposed `IconPackManager` via Dagger component |
| `FeatureFlagsImpl.java` | `enableLauncherIconShapes()` → `true` |
| `CustomFeatureFlags.java` | `enableLauncherIconShapes()` → `true` |
| `BaseIconFactory.java` | `mWrapperBackgroundColor` visibility `private` → `protected`; `drawAdaptiveIcon()` fill `BLACK` → `TRANSPARENT` |
| `LauncherIcons.kt` | Override `createBadgedIconBitmap()` (none shape bypass), `wrapToAdaptiveIcon()` (transparent bg), `drawAdaptiveIcon()` (shape-aware rendering with none/clipped branches) |
| `ShapesProvider.kt` | Added `NONE_KEY`, `NONE_PATH` constants and "none" `IconShapeModel` entry |
| `IconCache.java` | Added `clearAllIcons()` method (clears disk DB + memory cache) |
| `AndroidManifest.xml` | Registered `IconPackReceiver` broadcast receiver |

## Architecture

### Icon Pack Loading Flow
1. `LauncherIconProvider.getIcon(ComponentInfo, int)` checks `IconPackManager.getCurrentPack()`
2. If pack selected: try calendar icon → exact component match → fallback mask → super
3. If no pack: delegates to standard AOSP pipeline

### Icon Pack Apply Flow (Loading UX)
1. User selects pack → save pref, invalidate manager
2. Disable dialog controls, show "Applying icon pack..."
3. On `MODEL_EXECUTOR`: pre-parse pack (`ensureParsed()`), clear disk+memory cache (`clearAllIcons()`)
4. Post to main thread: clear icon pool, `forceReload()`
5. Sentinel on `MODEL_EXECUTOR` (runs after `LoaderTask`): dismiss dialog

Clearing the disk cache (`iconDb.clear()`) is critical — without it, `BaseIconCache.cacheLocked()` serves stale bitmaps from SQLite during workspace binding, and `SerializedIconUpdateTask` slowly replaces them one-by-one causing visible lag.

### Icon Shape Rendering
`LauncherIcons.kt` overrides the `BaseIconFactory` pipeline:
- `getShapePath()` → returns custom path from `ThemeManager`
- `getIconScale()` → per-shape scale factor (e.g., 0.863 for 4-sided cookie)
- `wrapToAdaptiveIcon()` → `Color.TRANSPARENT` background (no white fill)
- `drawAdaptiveIcon()` → three branches: flag disabled (super), none shape (clear shadow, no clip), shaped (clip + scale + draw)
- `createBadgedIconBitmap()` → for none + non-adaptive: bypass adaptive pipeline entirely (scale=1.0, MODE_DEFAULT)

### "None" Shape Special Handling
The none path (`M 0 0 H 100 V 100 H 0 Z`) is a full rectangle. It needs special handling because:
- `addPathShadow()` creates a visible gray rectangle → cleared with `PorterDuff.Mode.CLEAR`
- Non-adaptive icons through normal pipeline get `LEGACY_ICON_SCALE` (≈0.467) → bypassed via `createBadgedIconBitmap()` override
- Adaptive icons draw bg/fg layers without clip path

### Cache Invalidation
- `updateSystemState()` appends `,iconpack:<pkg>` to system state string
- On pack change: `IconCache.clearAllIcons()` (disk+memory) + `LauncherIcons.clearPool()` + `LauncherModel.forceReload()`
- On shape change: `ThemeManager` pref listener triggers `verifyIconState()` → `onThemeChanged()` → automatic reload

### Pack Discovery
Queries PackageManager for three standard intents:
- `org.adw.launcher.THEMES`
- `com.gau.go.launcherex.theme`
- `com.novalauncher.THEME`

### appfilter.xml Parsing
Supports both `res/xml/appfilter.xml` (compiled) and `assets/appfilter.xml` (raw).
Parses: `<item>`, `<calendar>`, `<iconback>`, `<iconmask>`, `<iconupon>`, `<scale>`.

### Fallback Masking
Apps without a mapped icon get iconback/iconmask/iconupon treatment:
1. Draw random iconback as background
2. Scale and center original icon
3. Apply iconmask via PorterDuff.Mode.DST_OUT
4. Draw iconupon overlay

### Broadcast Handling
| Event | Action |
|-------|--------|
| Icon pack installed | Refresh pack list (shows in settings) |
| Active pack uninstalled | Revert pref to "", reload with system icons |
| Active pack updated | Invalidate + reload (picks up new icons) |
