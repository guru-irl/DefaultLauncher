# 007: Icon Size Selector & M3 Settings Theme

## Summary

Added an icon size selector with four presets (S/M/L/XL) plus a custom float input, allowing users to control how much the icon shape fills its grid cell. The XL (100%) preset makes shaped icons fill the cell edge-to-edge. Also migrated the settings toolbar theme to Material 3 and introduced the icon size dialog using M3 components.

## Problem

AOSP Launcher3 hardcodes `ICON_VISIBLE_AREA_FACTOR = 0.92` as the scale factor for shaped icons, meaning shapes always occupy 92% of the cell with an 8% margin. Additionally, the private `drawIconBitmap()` method in `BaseIconFactory` enforces a minimum offset via `BLUR_FACTOR` (~0.035), creating a floor at ~92.6% even if a higher scale is requested.

## Approach

### Icon Size Preference

A new `ICON_SIZE_SCALE` string preference stores a float scale factor (0.5–1.0). The value is threaded through `ThemeManager.IconState` for cache invalidation, into `LauncherIcons` where it replaces `ICON_VISIBLE_AREA_FACTOR` in `normalizeAndWrapToAdaptiveIcon()`.

### BLUR_FACTOR Compensation

Since `drawIconBitmap()` is private and computes:
```java
offset = max(ceil(BLUR_FACTOR * size), round(size * (1 - scale) / 2))
```
scales above ~0.93 all produce the same visual result due to the BLUR_FACTOR floor. The fix overrides `drawAdaptiveIcon()` to detect when the actual offset exceeds the user's desired offset, then translates the canvas back and redraws at the correct larger bounds with a fresh shape path. This avoids modifying `iconloaderlib` while achieving smooth scaling from 0.5 to 1.0.

### Settings Dialog

The icon size dialog uses a `MaterialButtonToggleGroup` with four presets:
- **S** (80%) — scale 0.8
- **M** (86%) — scale 0.863, matches 4-sided cookie `iconScale`
- **L** (92%) — scale 0.92, AOSP default
- **XL** (100%) — scale 1.0, fills cell edge-to-edge

A "Custom" button reveals a `TextInputEditText` for entering arbitrary values (0.5–1.0). The dialog uses `MaterialAlertDialogBuilder` via a `ContextThemeWrapper` since `SettingsActivity` extends `FragmentActivity` (not `AppCompatActivity`).

### M3 Theme

The settings `CollapsingToolbar` theme parent was changed from `Theme.MaterialComponents.DayNight` to `Theme.Material3.DayNight` for API 31+.

## New Files

| File | Purpose |
|------|---------|
| `res/layout/dialog_icon_size.xml` | M3 dialog layout with `MaterialButtonToggleGroup` (S/M/L/XL) + Custom button + `TextInputLayout` |

## Modified Files

| File | Changes |
|------|---------|
| `LauncherPrefs.kt` | Added `ICON_SIZE_SCALE` backed-up string preference (default `"1.0"`) |
| `ThemeManager.kt` | Added `iconSizeScale` to `IconState` data class, included in `toUniqueId()` for cache invalidation, registered in pref listener keys, parsed in `parseIconState()` with 0.5–1.0 clamping |
| `LauncherIcons.kt` | Added `iconSizeScale` field; override `normalizeAndWrapToAdaptiveIcon()` to set scale; BLUR_FACTOR compensation in `drawAdaptiveIcon()` for scales > 0.93; updated none-shape bypass to use `iconSizeScale` |
| `SettingsActivity.java` | Added `showIconSizeDialog()` with preset buttons + custom input, `updateIconSizeSummary()`, `getDialogContext()` helper using `ContextThemeWrapper` with `Theme_Material3_DayNight`; wired preference click listener |
| `launcher_preferences.xml` | Added `pref_icon_size_scale` in Appearance category |
| `strings.xml` | Added `icon_size_title`, `icon_size_default`, `icon_size_custom` |
| `res/values-v31/styles.xml` | Changed `HomeSettings.CollapsingToolbar` parent to `Theme.Material3.DayNight` |

## Scale Behavior

| Preset | Scale | Offset (108px bitmap) | Visual Result |
|--------|-------|-----------------------|---------------|
| S (80%) | 0.80 | 11px | Noticeably smaller |
| M (86%) | 0.863 | 7px | Matches cookie shape content scale |
| L (92%) | 0.92 | 4px (BLUR floor) | AOSP default |
| XL (100%) | 1.00 | 0px (compensated) | Shape fills cell edge-to-edge |

## Key Design Decisions

1. **Compensation over modification**: Rather than modifying the shared `iconloaderlib`, the BLUR_FACTOR floor is compensated in `drawAdaptiveIcon()` by detecting the offset mismatch and redrawing at correct bounds
2. **No shadow at XL**: At scale 1.0 with 0px offset, no room exists for the shadow blur — acceptable since the user explicitly wants edge-to-edge icons
3. **M3 via ContextThemeWrapper**: Since `SettingsActivity` uses `Theme.DeviceDefault.Settings` (not AppCompat), M3 components require wrapping the context; only the icon size dialog uses this pattern (icon pack/shape dialogs use standard `AlertDialog.Builder`)
4. **IconState cache key**: `iconSizeScale` is included in `toUniqueId()` so changing the size triggers automatic icon cache invalidation and reload
