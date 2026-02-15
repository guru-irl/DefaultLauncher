# 012: App Drawer Colors

## Summary
Adds a "Colors" sub-page under App Drawer settings, allowing users to customize
the Material color tokens used for the drawer background, search bar, scrollbar,
and personal/work tabs. Also adds a background opacity slider, tab hiding toggle,
and fixes excessive top padding in the floating header.

## New Files
| File | Purpose |
|------|---------|
| `AllAppsColorResolver.java` | Maps Material Expressive color resource names to `R.color` IDs |
| `ColorPickerPreference.java` | Custom preference with swatch preview + BottomSheet grid picker |
| `AppDrawerColorsFragment.java` | PreferenceFragment for the Colors sub-page |
| `drawer_colors_preferences.xml` | Preference hierarchy for the sub-page |
| `preference_color_swatch.xml` | Widget layout for the swatch circle in preference rows |

## Modified Files
| File | Change |
|------|--------|
| `LauncherPrefs.kt` | 7 new `ConstantItem` preferences (color names, opacity, hide tabs) |
| `launcher_preferences.xml` | "Colors" navigation entry under App Drawer category |
| `SettingsActivity.java` | `EXTRA_FRAGMENT_CLASS` for launching arbitrary fragment sub-pages |
| `strings.xml` | 13 new string resources |
| `ActivityAllAppsContainerView.java` | Custom BG color, opacity, and tab text colors |
| `AppsSearchContainerLayout.java` | Custom search bar background color |
| `RecyclerViewFastScroller.java` | Custom scrollbar thumb color |
| `FloatingHeaderView.java` | Tab hiding via pref + padding fix (2x -> 1x multiplier) |

## Design Decisions

### Color storage as resource names
Colors are stored as Material color resource name strings (e.g., `"materialColorPrimary"`)
rather than hex values. This ensures colors automatically adapt to light/dark theme and
wallpaper-based dynamic color changes. Empty string = use component default.

### Sub-page navigation
Added `EXTRA_FRAGMENT_CLASS` intent extra to `SettingsActivity` so any fragment class
can be launched as a sub-page, not just `LauncherSettingsFragment`.

### Tab hiding preserves swipe
The `DRAWER_HIDE_TABS` preference only hides the tab bar visually. The ViewPager
still supports swipe-to-switch between personal and work profiles.

### Padding fix
The `2 * mTabsAdditionalPaddingBottom` in `getMaxTranslation()` was creating excessive
gap between the tab bar and first icon row. Changed to `1 *` for correct spacing.

## Preferences
| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `pref_drawer_bg_color` | String | `""` | Drawer background color resource name |
| `pref_drawer_bg_opacity` | int | `100` | Background opacity (0-100%) |
| `pref_drawer_search_bg_color` | String | `""` | Search bar background color |
| `pref_drawer_scrollbar_color` | String | `""` | Scrollbar thumb color |
| `pref_drawer_tab_selected_color` | String | `""` | Selected tab text color |
| `pref_drawer_tab_unselected_color` | String | `""` | Unselected tab text color |
| `pref_drawer_hide_tabs` | boolean | `false` | Hide personal/work tab bar |
