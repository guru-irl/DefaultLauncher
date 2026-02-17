# 022: Settings Surface Colors, M3 Switch, and UI Polish

## Summary

Refined the settings visual design with light/dark-aware surface colors for card groups and page backgrounds, migrated all switches to `SwitchPreferenceCompat` with Material 3 thumb icons, added search bar opacity control, and rewrote the README as a user-facing feature overview.

## Card Group Surface Colors

### CardGroupItemDecoration.java

Card background color is now light/dark-aware:
- **Dark mode:** `colorSurfaceContainer` (cards are brighter than the page background)
- **Light mode:** `colorSurface` (cards are brighter than the tinted page background)

Uses `Themes.getAttrColor()` with Material theme attributes instead of hardcoded color resources.

### SettingsActivity.java

Page background set dynamically:
- **Dark mode:** `colorSurface` (darker than cards)
- **Light mode:** `colorSurfaceContainer` (slightly tinted, cards appear as white on gray)

Status bar and navigation bar appearance set via `WindowInsetsControllerCompat` based on dark/light mode. Collapsing toolbar expanded title margins zeroed for proper centering.

The combined effect creates the M3 "cards on surface" visual hierarchy in both themes.

## SwitchPreferenceCompat Migration

All `SwitchPreference` instances migrated to `SwitchPreferenceCompat`:

| File | Switches migrated |
|------|-------------------|
| `app_drawer_preferences.xml` | `pref_drawer_match_home`, `pref_enable_two_line_toggle`, `pref_drawer_hide_tabs` |
| `search_preferences.xml` | All search toggle switches |
| `AppDrawerFragment.java` | Java references updated |
| `AppCustomizeFragment.java` | Uses `SwitchPreferenceCompat` for all toggles |

### M3 Switch Styling

- `switchPreferenceCompatStyle` in `res/values-v31/styles.xml` uses `Preference.SwitchPreferenceCompat.Material` parent
- Custom `android:widgetLayout=@layout/preference_widget_material_switch` with `MaterialSwitch`
- New thumb icon drawables: `ic_switch_check.xml` (checkmark) and `ic_switch_close.xml` (X mark)
- `switch_thumb_icon_selector.xml` selects between check/close based on checked state

## Search Bar Opacity

### LauncherPrefs.kt

```kotlin
val DRAWER_SEARCH_BG_OPACITY = backedUpItem("pref_drawer_search_bg_opacity", 95)
```

### AppsSearchContainerLayout.java

`refreshSearchBarColor()` reads `DRAWER_SEARCH_BG_OPACITY` and applies alpha to the search bar background. Creates a programmatic `GradientDrawable` with the alpha-adjusted color when opacity < 100 or a custom color is set.

### AppDrawerColorsFragment.java

New M3 slider for search bar opacity wired to trigger `onConfigChanged`.

### drawer_colors_preferences.xml

Added `M3SliderPreference` for `pref_drawer_search_bg_opacity` (default 95, range 0–100).

## Surface Color Tuning

| Element | Before | After |
|---------|--------|-------|
| All-apps scrim | `materialColorSurface` | `materialColorSurfaceContainerLow` |
| All-apps header protection | `materialColorSurface` | `materialColorSurfaceContainerLow` |
| Search bar background | `materialColorSurfaceContainerHigh` | `materialColorSurfaceContainer` |
| Search result cards | `colorSurfaceContainerLow` | `colorSurfaceContainer` |
| Search chips (checked) | `colorSecondaryContainer` | `colorPrimaryContainer` |
| Fast scroller thumb (light) | `materialColorSurfaceBright` | `materialColorSurfaceContainerHighest` |
| Widget picker surface | `system_accent2_50` | `system_neutral1_50` |
| Drawer bg opacity default | 100 | 95 |
| `ic_apps.xml` tint | Hardcoded `#FFFFFF` | `?android:attr/textColorSecondary` |

## README Rewrite

Complete rewrite from a technical developer document to a user-facing feature overview organized by:
- Privacy First — offline, no telemetry
- Smart Grid — configurable columns, automatic rows, unified spacing
- Icon Customization — icon packs, shapes, per-app customization
- App Drawer — separate pack/shape/size, visibility, two-line labels
- Universal Search — apps, contacts, files, calculator, web
- Colors — dynamic Material You, per-element customization
- Gestures — swipe-down notifications

Removed build instructions, project structure tables, and detailed documentation links. Added Lawnchair credit for settings UI patterns.

## Files Changed

| File | Change |
|------|--------|
| `src/.../settings/CardGroupItemDecoration.java` | Light/dark-aware card background |
| `src/.../settings/SettingsActivity.java` | Dynamic page background, status/nav bar, toolbar margins |
| `src/.../allapps/search/AppsSearchContainerLayout.java` | Search bar opacity support |
| `src/.../settings/AppDrawerColorsFragment.java` | Search opacity slider, updated defaults |
| `src/.../search/UniversalSearchAdapterProvider.java` | Checked chip color |
| `src/.../views/RecyclerViewFastScroller.java` | Light/dark-aware thumb color |
| `res/values-v31/styles.xml` | `SwitchPreferenceCompat` style with M3 thumb icons |
| `res/values/styles.xml` | Updated scrim/header colors |
| `res/xml/app_drawer_preferences.xml` | `SwitchPreferenceCompat` migration |
| `res/xml/search_preferences.xml` | `SwitchPreferenceCompat` migration |
| `res/xml/drawer_colors_preferences.xml` | Search opacity slider |
| `res/drawable/bg_all_apps_searchbox.xml` | Updated background color |
| `res/layout/search_result_*.xml` | Updated card background color (6 files) |
| `res/color-v31/widget_picker_primary_surface_color_light.xml` | Neutral palette |
| `res/drawable/ic_apps.xml` | Dynamic tint |
| `res/layout/preference_widget_material_switch.xml` | **NEW**: M3 switch widget layout |
| `res/drawable/ic_switch_check.xml` | **NEW**: Switch check thumb icon |
| `res/drawable/ic_switch_close.xml` | **NEW**: Switch close thumb icon |
| `res/drawable/switch_thumb_icon_selector.xml` | **NEW**: Thumb icon state selector |
| `README.md` | User-facing feature overview rewrite |
| `CONTRIBUTING.md` | **NEW**: Contribution guidelines |
