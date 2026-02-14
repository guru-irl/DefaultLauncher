# 009 - M3 App Drawer & Widget Polish

## Summary

M3-compliant polish pass on the all apps drawer and widget pane: background colors, search bar styling, header spacing, tab outlines, cursor color, icon pack theme reactivity, and widget tab text fix.

## Changes

### App Drawer Background Colors (styles.xml)
- **Light mode**: `allAppsScrimColor` and `allappsHeaderProtectionColor` both set to `materialColorSurface` (matched to prevent header darkening on scroll)
- **Dark mode**: Both set to `materialColorSurfaceContainer` (neutral1_800, visibly lighter than the previous neutral1_900 tokens)
- Header protection color now matches scrim in both themes, eliminating the color shift when scrolling

### Search Bar M3 Compliance
- **Fill color**: `materialColorSurfaceContainerHigh` — provides contrast against the drawer background per M3 spec
- **Elevation**: Removed (0dp) — M3 search bars rely on fill color, not shadows
- **Icon-to-text gap**: 16dp (was 12dp) — matches M3 search bar spec for both all apps and widgets
- **Cursor color**: New `search_cursor.xml` drawable using `materialColorPrimary` — fixes black cursor in dark mode
- **Corner radius**: 28dp (full) — unchanged, already M3 compliant

### Header Spacing (dimens.xml)
- `all_apps_header_top_margin`: 44dp -> 52dp (more breathing room between search bar and tabs)
- `all_apps_header_bottom_padding`: 14dp -> 4dp (tighter gap between header and first app row)

### Tab Outline (all_apps_tabs_background_unselected.xml)
- Added 1dp `materialColorOutlineVariant` stroke to unselected tabs for visibility in light mode

### Icon Pack Theme Reactivity (ThemeManager.kt)
- Added `nightMode` field to `IconState` data class
- Registered `ComponentCallbacks2` on app context to detect dark mode and wallpaper color changes
- Icon cache now invalidates on dark/light mode toggle, re-applying icon packs

### Widget Pane Fixes
- **Tab text**: `WidgetsFullSheet.java` — replaced enterprise `StringCache` pattern with direct `R.string` resources (StringCache returns empty on consumer devices)
- **Search bar**: `widgets_search_bar.xml` — updated to match all apps search bar design (56dp height, M3 colors, 16dp icon gap, 28dp radius)
- **Search background**: `bg_widgets_searchbox.xml` — updated to `materialColorSurfaceContainer` with 28dp radius

## Files Modified
- `res/values/styles.xml` — drawer background and header protection colors
- `res/values/dimens.xml` — header spacing
- `res/drawable/bg_all_apps_searchbox.xml` — search bar fill color
- `res/drawable/bg_widgets_searchbox.xml` — widget search bar fill
- `res/drawable/all_apps_tabs_background_unselected.xml` — tab outline
- `res/drawable/search_cursor.xml` — new cursor drawable
- `res/layout/search_container_all_apps.xml` — cursor drawable, elevation removal
- `res/layout/widgets_search_bar.xml` — M3 search bar styling
- `src/com/android/launcher3/allapps/search/AppsSearchContainerLayout.java` — 16dp icon gap
- `src/com/android/launcher3/graphics/ThemeManager.kt` — nightMode, ComponentCallbacks2
- `src/com/android/launcher3/widget/picker/WidgetsFullSheet.java` — tab text fix
