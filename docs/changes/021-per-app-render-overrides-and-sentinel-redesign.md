# 021: Per-App Render Overrides, Adaptive Toggle, and Sentinel Redesign

## Summary

Extended per-app icon customization with shape, size, and adaptive rendering overrides for both home screen and app drawer. Added a global "Apply adaptive icon shape" toggle. Redesigned `IconOverride` to use enums and sentinel constants instead of empty strings, fixing multiple bugs where system default and render-only overrides were confused.

## IconOverride Sentinel Redesign

### Problem

`IconOverride` used empty strings (`""`) for all fields as a "follow global" sentinel. This created ambiguity:

| `packPackage` | Intended meaning | Actual behavior |
|---|---|---|
| `""` | System default icon | `isSystemDefault()` returned true |
| `""` + shape/size fields set | Render-only override (keep global pack) | `isSystemDefault()` also returned true |

### Solution

**File:** `PerAppIconOverrideManager.java`

Introduced sentinel constants and enums:

```java
public static final String FOLLOW_GLOBAL = "__follow_global__";

public enum PackSource {
    FOLLOW_GLOBAL("__follow_global__"),
    SYSTEM_DEFAULT("__system_default__"),
    CUSTOM(null);
}

public enum AdaptiveOverride {
    FOLLOW_GLOBAL("__follow_global__"),
    ON("true"),
    OFF("false");
}
```

All query methods now delegate to enums: `getPackSource()`, `getAdaptiveOverride()`, `isSystemDefault()`, `hasPackOverride()`, `isFollowGlobalPack()`, `hasShapeOverride()`, `hasSizeOverride()`, `hasAdaptiveOverride()`.

### Backward Compatibility

`fromJson()` migrates legacy empty strings:
- Empty `pack` with no other data → `SYSTEM_DEFAULT`
- Empty `pack` with shape/size/adaptive data → `FOLLOW_GLOBAL` (render-only)
- Empty `drawable`/`shape`/`size`/`adaptive` → respective `FOLLOW_GLOBAL` sentinels

`toJson()` always writes all five fields.

## Adaptive Shape Toggle

### ThemeManager.kt

New `APPLY_ADAPTIVE_SHAPE` and `APPLY_ADAPTIVE_SHAPE_DRAWER` preferences control whether icons are wrapped in a shape mask. When OFF, forces shape to `ShapesProvider.NONE_KEY` regardless of selected shape.

`migrateNoneShape()` one-time migration converts old "none" shape selections into adaptive=OFF + clear shape pref.

`IconState` extended with `applyAdaptiveShape` and `applyAdaptiveShapeDrawer` fields.

### HomeScreenFragment / AppDrawerFragment

Both fragments now include:
- "Apply adaptive icon shape" `SwitchPreferenceCompat` that controls shape picker visibility
- "Reset all custom icons" button (visible only when per-app overrides exist) with confirmation dialog

Icon size toggle and summary code extracted to `IconSettingsHelper` shared helpers.

### LauncherPrefs.kt

```kotlin
val APPLY_ADAPTIVE_SHAPE = backedUpItem("pref_apply_adaptive_shape", true)
val APPLY_ADAPTIVE_SHAPE_DRAWER = backedUpItem("pref_apply_adaptive_shape_drawer", true)
```

## PerAppHomeIconResolver (NEW)

**File:** `PerAppHomeIconResolver.java`

Home screen counterpart to `DrawerIconResolver`. Singleton with LRU cache (size 100).

**`getHomeIcon()`** — called from `BubbleTextView` for `DISPLAY_WORKSPACE`. Returns a `FastBitmapDrawable` rendered with per-app shape/size/adaptive settings, or null if no override exists.

**`PerAppIconFactory`** (public inner class) — `BaseIconFactory` subclass that reads per-app override fields, falling back to global home values from `ThemeManager.IconState`. Handles none-shape (raw icons), adaptive icon compensation, and size scaling.

### BubbleTextView Integration

New branch in icon resolution for `DISPLAY_WORKSPACE`:
```java
FastBitmapDrawable homeOverride =
        PerAppHomeIconResolver.getInstance().getHomeIcon(info, getContext(), flags);
iconDrawable = homeOverride != null ? homeOverride : info.newIcon(getContext(), flags);
```

## DrawerIconResolver Changes

**Three-way per-app branch:**
1. `isSystemDefault()` → system icon via PM
2. `hasPackOverride()` → resolve from specific pack (existing logic)
3. `isFollowGlobalPack()` → resolve from global drawer pack (NEW — was treated as system default before)

`PerAppDrawerIconFactory` made `public` for preview reuse.

## AppCustomizeFragment Expansion

### Per-App Render Controls

Each section (home/drawer) now includes:
- **Icon picker** (unchanged)
- **Match global shape/size toggle** — hides/shows render controls
- **Apply adaptive shape switch** — controls shape picker visibility
- **Icon shape picker** — per-app shape selection via dialog
- **Icon size toggle** — inline `MaterialButtonToggleGroup` with presets + custom
- **Reset button** — clears override for that section

### Factory-Based Preview Rendering

`resolvePreviewIcon()` rewritten to use the same factories as production:

1. `resolveRawIcon()` — resolves the raw drawable via `PackSource` switch (SYSTEM_DEFAULT / CUSTOM / FOLLOW_GLOBAL)
2. Wraps in `PerAppIconFactory` (home) or `PerAppDrawerIconFactory` (drawer)
3. Returns `BitmapInfo.newIcon()` — identical to what the actual launcher renders

### Preview Stability Fix

Preview rows are tagged with `isHome` boolean via `row.setTag(R.id.app_icon_preview, isHome)` on first identification. Subsequent refreshes use the tag instead of `getBindingAdapterPosition()`, which returns stale values after visibility changes shift adapter positions. Tags are cleared in `onChildViewDetachedFromWindow`.

### Card Decoration Fix

- `onChildViewDetachedFromWindow` now clears tags (both default and keyed) from recycled views
- `invalidateCardDecorations()` helper called after all visibility-change sites (6 locations)

## IconSettingsHelper Shared Helpers

**Extracted from HomeScreenFragment/AppDrawerFragment:**
- `bindIconSizeToggle()` — full toggle group binding with preset selection and custom button
- `animateButtonCorners()` — pill shape animation for toggle buttons
- `getIconSizeSummary()` — human-readable size label

**New:**
- `ShapePreviewDrawable` — renders an SVG path as a filled shape preview (36dp)
- `showIconShapeDialog()` — visual shape picker with shape previews, filters out "none"
- `showPerAppShapeDialog()` — per-app shape picker with "System default" option
- `applyIconPack()` enhanced — auto-detects adaptive packs via `IconPack.isAdaptivePack()`

## IconPack.isAdaptivePack()

Samples up to 5 icons from component mappings and checks if they are `AdaptiveIconDrawable`. Packs using `iconback`/`iconmask` fallback masking are immediately considered non-adaptive. Result is cached.

## Bugs Fixed

1. **Reset doesn't change previews** — Fixed by factory-based preview rendering + tag-based row identification
2. **Previews don't respect shape** — Fixed by rendering through `PerAppIconFactory`/`PerAppDrawerIconFactory`
3. **Card decoration breaks on visibility changes** — Fixed by clearing tags on detach + `invalidateItemDecorations()`
4. **System default vs render-only ambiguity** — Fixed by `PackSource` enum with distinct sentinels
5. **Pack-only home overrides ignored** — Fixed by removing `hasAnyRenderOverride()` guard in `PerAppHomeIconResolver`

## Files Changed

| File | Change |
|------|--------|
| `src/.../icons/pack/PerAppIconOverrideManager.java` | `PackSource`/`AdaptiveOverride` enums, sentinel constants, migration |
| `src/.../icons/PerAppHomeIconResolver.java` | **NEW**: Per-app home icon resolution with `PerAppIconFactory` |
| `src/.../icons/DrawerIconResolver.java` | Three-way per-app branch, public `PerAppDrawerIconFactory` |
| `src/.../icons/pack/IconPack.java` | `isAdaptivePack()`, `applyFallbackMask()` |
| `src/.../BubbleTextView.java` | Per-app home icon rendering hook |
| `src/.../graphics/ThemeManager.kt` | Adaptive toggle, `migrateNoneShape()`, extended `IconState` |
| `src/.../LauncherPrefs.kt` | `APPLY_ADAPTIVE_SHAPE`, `APPLY_ADAPTIVE_SHAPE_DRAWER` |
| `src/.../settings/AppCustomizeFragment.java` | Full per-app render controls, factory preview, tag fix |
| `src/.../settings/IconSettingsHelper.java` | Shared helpers, `ShapePreviewDrawable`, auto-detect adaptive |
| `src/.../settings/HomeScreenFragment.java` | Adaptive toggle, reset all, shared helper migration |
| `src/.../settings/AppDrawerFragment.java` | Adaptive toggle, reset all, shared helper migration |
| `res/xml/home_screen_preferences.xml` | `pref_apply_adaptive_shape`, `pref_reset_all_custom_icons` |
| `res/xml/app_drawer_preferences.xml` | `pref_apply_adaptive_shape_drawer`, `pref_reset_all_custom_icons` |
| `res/values/strings.xml` | New strings for adaptive toggle, match global, reset, categories |
