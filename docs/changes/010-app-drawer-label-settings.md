# 010 — App Drawer Label Settings & Settings Reorganization

## Summary

Added user-configurable app drawer label size (10–18sp slider) and a two-line labels toggle. Reorganized settings categories so grid columns lives under Appearance and all drawer-specific settings are grouped under a new "App drawer" section.

## Changes

### Settings Reorganization

**Before:**
- Appearance: notification dots, icon pack, icon shape, icon size
- Grid: grid columns, row gap

**After:**
- Appearance: notification dots, icon pack, icon shape, icon size, **grid columns**
- **App drawer**: label size, two-line labels, row gap
- Theme colors: unchanged

### Label Size Slider

- `M3SliderPreference` with key `pref_allapps_label_size`, range 10–18, default 14
- `LauncherPrefs.ALLAPPS_LABEL_SIZE` constant (backed up, default 14)
- `InvariantDeviceProfile.allAppsLabelSizeSp` field, read in `initGrid()`
- `DeviceProfile` uses `inv.allAppsLabelSizeSp` instead of `inv.iconTextSize[mTypeIndex]` for all-apps text size in the square grid block

### Two-Line Labels Toggle

- `SwitchPreference` with key `pref_enable_two_line_toggle` (reuses existing `ENABLE_TWOLINE_ALLAPPS_TOGGLE` pref)
- Removed `Utilities.isEnglishLanguage()` gate in `InvariantDeviceProfile.initGrid()` so the feature works in all locales
- All existing AOSP infrastructure already handles the rest: `BubbleTextView.shouldUseTwoLine()`, `all_apps_icon_twoline.xml` layout, cell height adjustment in `DeviceProfile`

### Change Listeners

Both new preferences are wired to `gridChangeListener` in `SettingsActivity` so changes trigger `InvariantDeviceProfile.onConfigChanged()` and the drawer rebuilds immediately.

## Files Modified

| File | Change |
|------|--------|
| `res/xml/launcher_preferences.xml` | Reorganized categories; added label size slider + two-line switch |
| `res/values/strings.xml` | Added `app_drawer_category`, `allapps_label_size_title`, `allapps_twoline_title`, `allapps_twoline_summary` |
| `src/.../LauncherPrefs.kt` | Added `ALLAPPS_LABEL_SIZE` constant |
| `src/.../InvariantDeviceProfile.java` | Added `allAppsLabelSizeSp` field; read pref; removed English-only gate |
| `src/.../DeviceProfile.java` | Use `inv.allAppsLabelSizeSp` for all-apps text size |
| `src/.../settings/SettingsActivity.java` | Wired `gridChangeListener` to label size and two-line prefs |
