# 014: Settings UI Polish

## Summary

Visual polish pass on the settings page: Danfo branding font, settings restructured into subpages, balanced icon margins, tighter slider gaps, and a redesigned icon pack picker with card-based rows and real-size preview icons.

## Title: Danfo Font & "Default" Branding

### Font Swap

Replaced Dancing Script with **Danfo** (Afrotype, OFL-licensed), a Tuscan slab serif inspired by Lagos bus lettering.

- `res/font/dancing_script.ttf` deleted
- `res/font/danfo.ttf` added
- `SettingsActivity.onCreate()`: `R.font.dancing_script` replaced with `R.font.danfo`
- All toolbar title styles reference `@font/danfo`

### Title Text

`settings_title` changed from "Default Launcher" to "Default" in `res/values/strings.xml` and all 5 locale overrides (`en-rAU`, `en-rCA`, `en-rGB`, `en-rIN`, `en-rXC`).

### Expanded Title Sizing

The main page "Default" title uses a **separate style** (`HomeSettings.ExpandedToolbarTitle.Main`) at 64sp, applied programmatically via `setExpandedTitleTextAppearance()` only on the main page. Sub-pages inherit the base `HomeSettings.ExpandedToolbarTitle` (DisplaySmall = 36sp) unchanged.

### Layout

CollapsingToolbarLayout height increased from 226dp to 260dp with `expandedTitleMarginBottom="32dp"` to push the title up and create a gap before the preference list.

## Settings Structure: Grids Subpage

### Problem

Grid Columns was an inline M3SliderPreference on the main settings page while all other items were subpage links, creating visual inconsistency.

### Solution

- **`res/xml/grids_preferences.xml`** (new): Contains the grid columns M3SliderPreference
- **`GridsFragment.java`** (new): PreferenceFragmentCompat with CardGroupItemDecoration, edge-to-edge insets, and the `onConfigChanged` listener for grid reconfiguration
- **`launcher_preferences.xml`**: Grid columns slider replaced with a `Preference` subpage link pointing to `GridsFragment`
- **`SettingsActivity.java`**: Removed the `pref_grid_columns` change listener from `LauncherSettingsFragment` (now lives in `GridsFragment`)

All 5 main page items are now consistent subpage links: Grids, Home Screen, App Drawer, Notification Dots, Debug.

## Home Screen: "Icons" Category Header

Wrapped the three icon preferences (pack, shape, size) in `home_screen_preferences.xml` inside a `<PreferenceCategory>` with title "Icons" to visually group them.

## Icon Frame Margin Fix

### Problem

AndroidX Preference's `image_frame.xml` gives icon_frame `minWidth=56dp` + `paddingEnd=8dp`. With 24dp icons, the icon-to-text gap (32dp) was double the card-edge-to-icon gap (16dp).

### Root Cause Discovery

Initial attempts to fix this programmatically via `child.findViewById(android.R.id.icon_frame)` had **zero effect** because the library layout uses `@+id/icon_frame` (library namespace), not `@android:id/icon_frame` (framework namespace). The correct ID is `androidx.preference.R.id.icon_frame`.

### Fix

In `LauncherSettingsFragment.onViewCreated()`, a `RecyclerView.OnChildAttachStateChangeListener` finds the icon frame via `androidx.preference.R.id.icon_frame` and sets:
- `setMinimumWidth(0)` — removes the 56dp floor
- `setPaddingRelative(4dp, 4dp, 20dp, 4dp)` — 4dp breathing room on all sides, 20dp end padding to balance with 16dp(listPaddingStart) + 4dp(iconStart) = 20dp card-to-icon gap

Also fixed the same wrong ID in `ColorDebugPreference.java`.

## Slider Title-to-Slider Gap

`M3SliderPreference.onBindViewHolder()`: Slider row top margin reduced from 12dp to 8dp for a tighter title-to-slider spacing.

## Icon Pack Picker Redesign

### Before

Flat list of rows with small 28dp preview icons crammed to the right. Selected pack indicated only by colored text.

### After

Card-based layout where each icon pack is a rounded card:

- **Card background**: `materialColorSurfaceContainerHigh` with 16dp corner radius
- **Selected card**: `materialColorPrimaryContainer` fill + 2dp `materialColorPrimary` border stroke, bold pack name, "Selected" badge
- **Header row**: 40dp pack app icon + pack name (+ selected badge if active)
- **Preview icons**: Rendered at `DeviceProfile.iconSizePx` (real icon size from user's setting), shown below the header with staggered fade-in animation (50ms delay per icon)
- **Preview row**: Hidden by default, revealed only when async icon loading completes

### YouTube Preview Fix

Added fallback activity names for YouTube in `IconPack.PREVIEW_COMPONENTS`:
- `com.google.android.youtube.app.honeycomb.Shell$HomeActivity`
- `com.google.android.apps.youtube.app.WatchWhileActivity`

## M3 Typescale Audit

Verified all settings text sizes against the M3 Major Second (Expressive) type scale:

| Element | Style/Size | M3 Token | Status |
|---------|-----------|----------|--------|
| Preference title | BodyLarge (16sp/400) | Body L | Correct |
| Preference summary | BodyMedium (14sp/400) | Body M | Correct |
| Category header | TitleSmall (14sp/500) | Title S | Correct |
| Collapsed toolbar | TitleLarge (20sp/400) | Title L | Correct |
| Sub-page expanded | DisplaySmall (36sp) | Display S | Correct |
| Slider value label | 14sp bold | Label L | Correct |
| Sheet titles | 22sp | Title L | Correct |
| ColorDebugPreference labels | 11sp | Label S | Fixed to 12sp |

## Files Changed

| File | Change |
|------|--------|
| `res/font/danfo.ttf` | Added (Danfo Regular, OFL) |
| `res/font/dancing_script.ttf` | Deleted |
| `res/layout-v31/settings_activity.xml` | Height 260dp, expandedTitleMarginBottom 32dp |
| `res/values/strings.xml` | "Default", grids/icons category strings |
| `res/values-en-r*/strings.xml` (5 files) | settings_title → "Default" |
| `res/values-v31/styles.xml` | Danfo font, Main expanded title style (64sp) |
| `res/xml/launcher_preferences.xml` | Grid columns → Grids subpage link |
| `res/xml/grids_preferences.xml` | New: grid columns slider |
| `res/xml/home_screen_preferences.xml` | Icons PreferenceCategory wrapper |
| `src/.../settings/GridsFragment.java` | New: Grids subpage fragment |
| `src/.../settings/SettingsActivity.java` | Danfo font, icon_frame fix, removed grid listener |
| `src/.../settings/IconSettingsHelper.java` | Card-based icon pack picker redesign |
| `src/.../settings/M3SliderPreference.java` | Slider top margin 12dp → 8dp |
| `src/.../settings/ColorDebugPreference.java` | Fixed icon_frame ID, label size 11→12sp |
| `src/.../icons/pack/IconPack.java` | YouTube fallback component names |
