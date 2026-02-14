# 008: M3 Settings Redesign, Search Bar Polish, and Scroll Performance

## Summary

Full Material 3 migration for the settings page: new theme, dynamic colors, AppCompat base class, collapsing toolbar with Dancing Script branding, card-grouped preferences, custom M3 slider, color debug swatch, and inline icon size toggle. Also includes search bar M3 polish, popup color migration, and a critical app drawer scroll performance fix.

## Settings: Theme & Activity

### AppCompat Migration

`SettingsActivity` base class changed from `FragmentActivity` to `AppCompatActivity`:
- Enables `setSupportActionBar()` / `getSupportActionBar()` (required for `MaterialToolbar`)
- `DynamicColors.applyToActivityIfAvailable(this)` called before `super.onCreate()` for wallpaper-extracted palette

### M3 Theme

`HomeSettings.Theme` parent changed from `Theme.DeviceDefault.Settings` to `Theme.Material3.DayNight.NoActionBar`:
- Removed `android:windowActionBar`, `android:windowNoTitle`, `android:switchStyle` (all M2/DeviceDefault artifacts)
- Removed `android:fontFamily` feature-flag references to `google-sans-flex` and `google-sans-text`
- Removed the `SwitchStyle` style (custom switch thumb/track drawables) -- M3 `SwitchPreference` uses its own
- Added transparent navigation and status bar colors

### Collapsing Toolbar

**Layout** (`res/layout-v31/settings_activity.xml`):
- `Toolbar` replaced with `com.google.android.material.appbar.MaterialToolbar`
- `android:theme="@style/HomeSettings.CollapsingToolbar"` removed from AppBarLayout (no longer needed with M3 base theme)
- `AppBarLayout` background changed from `?android:attr/colorPrimary` to `@android:color/transparent`
- `app:liftOnScroll="false"` added to prevent M3's default elevation tint conflicting with the scrim
- `contentScrim` changed from `@color/home_settings_header_collapsed` to `@color/materialColorSurfaceContainer`
- Removed `app:scrimVisibleHeightTrigger="174dp"` and `app:scrimAnimationDuration="50"` (CTL defaults work correctly)

**Font** (`SettingsActivity.java`):
- Dancing Script applied via `setCollapsedTitleTypeface()` / `setExpandedTitleTypeface()` (the only APIs that work with `CollapsingTextHelper`)
- Title string split: `settings_button_text` ("Home Settings") for the long-press menu, `settings_title` ("Default Launcher") for the toolbar

**Styles** (`res/values-v31/styles.xml`):
- `HomeSettings.CollapsingToolbar` style removed entirely (was elevation overlay config for M2)
- `HomeSettings.CollapsedToolbarTitle` re-parented to `TextAppearance.Material3.TitleLarge` with `@font/dancing_script`
- `HomeSettings.ExpandedToolbarTitle` re-parented to `TextAppearance.Material3.DisplaySmall` with `@font/dancing_script`
- `HomeSettings.PreferenceTitle` re-parented to `TextAppearance.Material3.BodyLarge`
- `HomeSettings.CategoryTitle` re-parented to `TextAppearance.Material3.TitleSmall` with `?attr/colorPrimary`
- Added `HomeSettings.ConnectedButton` style for `MaterialButtonToggleGroup` items

### Base Layout (pre-v31)

`res/layout/settings_activity.xml`: `Toolbar` replaced with `MaterialToolbar`, removed `style`/`theme`/`titleTextColor` attributes, set `android:background="@color/materialColorSurfaceContainer"`.

## Settings: Card Group UI

### CardGroupItemDecoration

New `RecyclerView.ItemDecoration` in [`CardGroupItemDecoration.java`](../src/com/android/launcher3/settings/CardGroupItemDecoration.java):
- Draws per-item rounded rect backgrounds with position-aware corner radii
- First item: large top corners, small bottom corners
- Last item: small top corners, large bottom corners
- Solo item: large corners all around
- Category headers excluded from cards
- 4dp gap between items (no divider lines)

### Preference Reorganization

`launcher_preferences.xml` restructured:
- `pref_icon_badging` and `pref_add_icon_to_home` moved inside the "Appearance" category
- `pref_add_icon_to_home` (auto-add shortcuts) removed from the top level
- New "Theme colors" category with `ColorDebugPreference` swatch grid
- `SeekBarPreference` replaced with `M3SliderPreference` for grid columns and row gap

## Settings: Custom Preferences

### M3SliderPreference

Replaces AndroidX `SeekBarPreference` with a Material 3 `Slider` widget. Hides the default preference widget area and injects a `Slider` + value label into `onBindViewHolder`. Supports `min`, `max`, `stepSize`, and `showSeekBarValue` attributes.

### ColorDebugPreference

Displays a grid of `materialColor*` dynamic color swatches. Each swatch is a rounded rect with the color name label. Useful for verifying M3 dynamic color integration across light/dark themes.

### Icon Size Inline Toggle

The icon size preference now uses a custom layout (`res/layout/preference_icon_size.xml`) with a `MaterialButtonToggleGroup` embedded directly in the preference row, replacing the previous click-to-open dialog pattern. Button selection animates corner radii from inner-group shape to pill shape using `ValueAnimator`. The "Custom" star button opens a `MaterialAlertDialogBuilder` dialog for percentage input.

## Dynamic Color Resources

Added M3 dynamic color resource definitions mapped to Android's `system_accent1/2/3` and `system_neutral1/2` palette:

| File | Colors |
|------|--------|
| `res/values-v31/colors.xml` | 27 `materialColor*` resources (light theme) |
| `res/values-night-v31/colors.xml` | 25 `materialColor*` resources (dark theme) |

These resources are used by the settings theme, search bar, popup menus, and toolbar.

## Popup Colors

Launcher popup menu colors migrated from hardcoded light/dark resources to dynamic M3 colors:

| Attr | Old | New |
|------|-----|-----|
| `popupColorPrimary` | `popup_color_primary_light/dark` | `materialColorSurfaceContainer` |
| `popupColorSecondary` | `popup_color_secondary_light/dark` | `materialColorSurfaceContainerHigh` |
| `popupColorTertiary` | `popup_color_tertiary_light/dark` | `materialColorOutlineVariant` |
| `popupTextColor` | `system_on_surface_light/dark` | `materialColorOnSurface` |

## Search Bar Polish

### Layout (`search_container_all_apps.xml`)
- Height changed from `@dimen/all_apps_search_bar_field_height` to `56dp` (M3 standard)
- Background pill radius changed from `@dimen/rounded_button_radius` to `28dp` (M3 search bar spec)
- Background fill changed from `?attr/popupColorPrimary` to `@color/materialColorSurfaceContainer`
- Removed border stroke
- Added `android:elevation="2dp"`
- Gravity changed from `center` to `center_vertical`
- Padding changed from uniform `8dp` to `16dp` horizontal

### Search Icon
- Moved from inline `prefixTextWithIcon()` (prepends icon to hint text) to `setCompoundDrawablesRelative()` (proper compound drawable)
- Icon sized to 24dp with 8dp drawable padding
- Icon tint changed from `?attr/widgetPickerSearchTextColor` to `@color/materialColorOnSurfaceVariant`

### Hint & Text Colors
- Hint color changed from `?android:attr/colorAccent` to `@color/materialColorOnSurfaceVariant`
- Text color changed from `?android:attr/textColorSecondary` to `@color/materialColorOnSurface`

## App Drawer Scroll Performance

### Critical Fix: `setLettersToScrollLayout()` called every frame

**File:** `AllAppsRecyclerView.java`, `onUpdateScrollbar()`

The fast-scroller letter sidebar was being rebuilt on **every scroll event**. The code `if (true/*Flags.letterFastScroller()*/)` always evaluated to true (AOSP TODO), and `setLettersToScrollLayout()`:
1. Called `removeAllViews()` on a ConstraintLayout
2. Inflated ~27 TextViews (A-Z) via LayoutInflater
3. Added them all back with `addView()`
4. Built a full `ConstraintSet` chain and called `applyTo()`

**Fix:** Added a reference comparison (`sections != mLastFastScrollSections`) so the letter layout is only built when the sections list object changes (app install/uninstall), not on every scroll pixel.

### Minor Fix: Debug logging in onBindViewHolder

**File:** `BaseAllAppsAdapter.java`

Removed a `Log.d()` call with 6 string concatenations that fired on every icon bind during scroll (private space state logging).

## New Files

| File | Purpose |
|------|---------|
| `res/font/dancing_script.ttf` | Dancing Script font for toolbar title |
| `res/layout/preference_icon_size.xml` | Inline icon size toggle group layout |
| `res/color/btn_connected_bg_tint.xml` | Toggle button background color state list |
| `res/color/btn_connected_icon_tint.xml` | Toggle button icon color state list |
| `res/color/btn_connected_stroke_color.xml` | Toggle button stroke color state list |
| `res/color/btn_connected_text_tint.xml` | Toggle button text color state list |
| `res/drawable/ic_star_custom.xml` | Star icon for custom size button |
| `res/drawable/slider_thumb_line.xml` | M3 slider thumb drawable |
| `src/com/android/launcher3/settings/CardGroupItemDecoration.java` | Lawnchair-style card group item decoration |
| `src/com/android/launcher3/settings/M3SliderPreference.java` | M3 Slider preference replacing SeekBarPreference |
| `src/com/android/launcher3/settings/ColorDebugPreference.java` | Dynamic color swatch grid preference |

## Modified Files

| File | Changes |
|------|---------|
| `AndroidManifest-common.xml` | Activity label changed to `@string/settings_title` |
| `SettingsActivity.java` | `AppCompatActivity` base, `DynamicColors`, `MaterialToolbar`, Dancing Script typeface, card decoration, inline icon size binding, bottom sheet dialogs, removed `AlertDialog`/`ContextThemeWrapper` patterns |
| `res/layout-v31/settings_activity.xml` | `MaterialToolbar`, transparent bg, `liftOnScroll=false`, native `contentScrim`, removed scrim timing overrides |
| `res/layout/settings_activity.xml` | `MaterialToolbar` replacing `Toolbar`, surface container bg |
| `res/values-v31/styles.xml` | M3 theme parent, M3 typography, Dancing Script font, removed CollapsingToolbar/M2 styles |
| `res/values/styles.xml` | M3 theme parent, removed `SwitchStyle`, M3 popup colors |
| `res/values-v31/colors.xml` | Added 27 `materialColor*` light dynamic color resources |
| `res/values-night-v31/colors.xml` | Added 25 `materialColor*` dark dynamic color resources |
| `res/values/strings.xml` | Split `settings_button_text` into menu label + `settings_title` for toolbar; added `theme_colors_category` |
| `res/xml/launcher_preferences.xml` | Restructured categories, `M3SliderPreference`, inline icon size layout, color debug preference |
| `res/drawable/bg_all_apps_searchbox.xml` | M3 surface container fill, 28dp radius, no stroke |
| `res/drawable/all_apps_search_hint.xml` | `materialColorOnSurfaceVariant` hint color |
| `res/drawable/ic_allapps_search.xml` | `materialColorOnSurfaceVariant` tint |
| `res/layout/search_container_all_apps.xml` | 56dp height, M3 colors, compound drawable icon, 16dp padding |
| `AppsSearchContainerLayout.java` | Compound drawable search icon replacing `prefixTextWithIcon()` |
| `AllAppsRecyclerView.java` | Cached `mLastFastScrollSections` to skip redundant letter layout rebuilds |
| `BaseAllAppsAdapter.java` | Removed per-bind debug logging |
| `SessionCommitReceiver.java` | (minor) Import/formatting cleanup |
| Localized `strings.xml` | Updated `settings_button_text` across en-rAU, en-rCA, en-rGB, en-rIN, en-rXC |
