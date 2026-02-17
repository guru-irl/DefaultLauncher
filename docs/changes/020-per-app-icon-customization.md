# 020: Per-App Icon Customization

## Summary

Added per-app icon overrides for both home screen and app drawer icons. Users can long-press any app, tap "Customize", and pick a specific icon from any installed icon pack. Includes a two-page bottom sheet with a card-expansion transition, a "Suggested" section showing the matched icon and name-similar variants, a searchable icon grid, and an "Uninstall" shortcut in the app drawer long-press menu.

Also added a "Match home screen icons" switch to the App Drawer settings that makes the drawer use the same icon pack, shape, and size as the home screen.

## Per-App Override Manager

> **Note:** The `IconOverride` data model was redesigned in [021](021-per-app-render-overrides-and-sentinel-redesign.md) with sentinel constants and enums replacing empty strings. The API below reflects the original design.

### `PerAppIconOverrideManager.java` (**NEW**)

Singleton managing two JSON maps in SharedPreferences file `"per_app_icon_overrides"`:

- `"home_overrides"` — `{"com.pkg/Activity": {"pack":"icon.pack.pkg","drawable":"ic_name"}, ...}`
- `"drawer_overrides"` — same format

**`IconOverride` data class** (inner static class):
- `packPackage` — icon pack package name, `""` for system default
- `drawableName` — specific drawable name, `""` for auto-resolve
- `isSystemDefault()` — true when both fields are empty
- `hasSpecificDrawable()` — true when a specific icon was picked

**API**: `getHomeOverride()`, `getDrawerOverride()`, `setHomeOverride()`, `setDrawerOverride()`, `clearOverrides()`, `getOverridesHash()`.

In-memory `Map<String, IconOverride>` caches loaded lazily from JSON. Writes update both map and SharedPreferences atomically.

## Icon Resolution Pipeline

### `LauncherIconProvider.java` — Home Screen

In `getIcon()`, **before** the global pack check:

1. Look up `PerAppIconOverrideManager.getHomeOverride(cn)`
2. If override exists:
   - `isSystemDefault()` → return system icon
   - `hasSpecificDrawable()` → load exact drawable from override pack
   - Otherwise → auto-resolve from override pack
3. If null → existing global pack logic unchanged

Extracted helper `resolveFromPack()` to avoid duplicating the calendar → component → fallback chain.

`updateSystemState()` appends `,perapp:` + `overrideMgr.getOverridesHash()` for cache invalidation.

### `DrawerIconResolver.java` — App Drawer

In `getDrawerIcon()`, **before** the `hasDistinctDrawerSettings()` check:

1. Look up `overrideMgr.getDrawerOverride(cn)`
2. If override exists → resolve icon, create `BitmapInfo`, cache in LRU, return
3. If null → fall through to existing logic

`hasDistinctDrawerSettings()` now also checks `LauncherPrefs.DRAWER_MATCH_HOME`:
```java
boolean matchHome = LauncherPrefs.get(context).get(LauncherPrefs.DRAWER_MATCH_HOME);
if (matchHome) return false; // drawer uses home settings
```

## IconPack Enhancements

### `IconPack.java`

- **`IconCategory`** (inner static class): `title` + `List<IconEntry>`
- **`IconEntry`** (inner static class): `drawableName` + `label` (humanized)
- **`getAllIcons(PackageManager pm)`**: Parses `drawable.xml` for full icon enumeration with categories. Falls back to building a single "All icons" category from `mComponentToDrawable` values.
- **`getDrawableForEntry(String, PackageManager)`**: Public wrapper around `loadDrawableByName()`
- **`getDrawableNameForComponent(ComponentName, PackageManager)`**: Returns the drawable name string (not the Drawable) for a component — used by the "Suggested" section

## System Shortcuts

### `SystemShortcut.java`

**`CUSTOMIZE_ICON`**: Long-press shortcut on any app icon. Opens `AppCustomizeFragment` via `SettingsActivity` with the app name as toolbar title. Uses `ic_customize.xml` (brush vector icon).

**`UNINSTALL_APP_GENERAL`**: Long-press shortcut in the app drawer for non-system apps. Uses `SecondaryDropTarget.getUninstallTarget()` to check eligibility and `performUninstall()` to trigger Android's uninstall dialog.

### `Launcher.java`

Shortcut order: `APP_INFO, WIDGETS, CUSTOMIZE_ICON, UNINSTALL_APP_GENERAL, INSTALL`.

## Customize Fragment

### `AppCustomizeFragment.java` (**NEW**)

`PreferenceFragmentCompat` launched via `SettingsActivity` subpage. Toolbar title = app name.

**Preferences** (built programmatically):
1. **"Home screen icon"** — preview widget (ImageView) showing current effective icon. Summary shows state ("Global (Pack Name)", "System default", "Pack — drawable_name").
2. **"App drawer icon"** — same pattern. Always visible (per-app overrides take priority over the global match-home setting).
3. **"Reset to default"** — clears both overrides, refreshes icons.
4. **Component ID footer** — 10sp centered muted text with 16dp horizontal padding, tagged `"no_card"`.

**Preview resolution** (`resolvePreviewIcon`): Uses `getEffectivePack()` which mirrors the actual drawer resolution pipeline — checks `DRAWER_MATCH_HOME`, then `hasDistinctDrawerPack()`, then falls back to home pack.

**Pack selection flow**: Tapping a row opens `PerAppIconSheet` — a two-page bottom sheet.

## Two-Page Bottom Sheet

### `PerAppIconSheet.java` (**NEW**)

Single `BottomSheetDialog` with two pages and a card-expansion transition between them.

**Page 1 — Pack Selection:**
- Drag handle + "Choose icon pack" title
- "Follow global setting" card — removes override
- "System default" card — sets empty-pack override
- One card per installed pack with async preview of the app's auto-resolved icon

**Transition Animation:**
When a pack card is tapped:
1. An overlay `View` is created matching the card's position, size, and background
2. `ValueAnimator` (350ms, `FastOutSlowInInterpolator`) expands the overlay to fill the sheet
3. Corner radius morphs from 16dp (card) → 28dp (M3 sheet radius) via `ViewOutlineProvider` + `setClipToOutline(true)`
4. Pack page fades out simultaneously
5. On animation end: overlay fades out, icon picker page fades in

**Page 2 — Icon Picker Grid:**
- Drag handle + back button (← arrow) + "Choose an icon" title
- Search bar with 300ms debounce (same pattern as `fragment_icon_picker.xml`)
- `RecyclerView` with `GridLayoutManager`, span count computed from width (64dp cells)
- **"Suggested" section** at top: primary matched icon (from `getDrawableNameForComponent()`) + variants (other drawables whose name contains the app's simple name, e.g. "chrome" from `com.android.chrome`)
- Remaining categories with suggested items de-duplicated
- Tag-based cancellation for async icon loading
- On icon tap: saves override, dismisses sheet, triggers callback

**Back navigation**: Cross-fade from icon picker back to pack page. Device back key intercepted via `Dialog.setOnKeyListener()`.

## Match Home Screen Icons

### `LauncherPrefs.kt`

```kotlin
@JvmField
val DRAWER_MATCH_HOME = backedUpItem("pref_drawer_match_home", true)
```

### `res/xml/app_drawer_preferences.xml`

`SwitchPreference` at top of Icons category (default: on).

### `AppDrawerFragment.java`

- Reads `DRAWER_MATCH_HOME` on create, hides icon pack/shape/size prefs when on
- On toggle change: toggles visibility, invalidates drawer cache, forces model reload
- Does NOT clear the drawer pack setting — preserving it so turning match-home off restores previous settings

## New Layouts

| Layout | Purpose |
|--------|---------|
| `fragment_icon_picker.xml` | Search EditText + RecyclerView (used by `IconPickerFragment`, kept for compatibility) |
| `item_icon_picker.xml` | 64dp square FrameLayout with ImageView for grid cells |
| `item_icon_picker_header.xml` | Category header TextView (14sp bold, primary color) |
| `preference_icon_widget.xml` | 40dp FrameLayout with ProgressBar + ImageView for preference row previews |

## New Drawables

| Drawable | Purpose |
|----------|---------|
| `ic_customize.xml` | Material Symbols brush icon (24dp) for the "Customize" shortcut |
| `ic_arrow_back.xml` | Material arrow_back icon (24dp) for the icon picker back button |

## Files Changed

| File | Change |
|------|--------|
| `src/.../icons/pack/PerAppIconOverrideManager.java` | **NEW**: Per-app override storage |
| `src/.../icons/pack/IconPack.java` | `getAllIcons()`, `IconCategory`/`IconEntry`, `getDrawableNameForComponent()` |
| `src/.../icons/LauncherIconProvider.java` | Per-app override check in `getIcon()`, hash in `updateSystemState()` |
| `src/.../icons/DrawerIconResolver.java` | Per-app drawer override, `DRAWER_MATCH_HOME` check |
| `src/.../settings/AppCustomizeFragment.java` | **NEW**: Per-app customize screen |
| `src/.../settings/PerAppIconSheet.java` | **NEW**: Two-page bottom sheet with card expansion |
| `src/.../settings/IconPickerFragment.java` | **NEW**: Standalone icon picker fragment (kept for reference) |
| `src/.../settings/IconSettingsHelper.java` | `showPerAppPackDialog()`, `PerAppPackCallback` |
| `src/.../popup/SystemShortcut.java` | `CUSTOMIZE_ICON` + `UNINSTALL_APP_GENERAL` |
| `src/.../Launcher.java` | Register both new shortcuts |
| `src/.../LauncherPrefs.kt` | `DRAWER_MATCH_HOME` |
| `src/.../settings/AppDrawerFragment.java` | Wire match-home switch |
| `res/xml/app_drawer_preferences.xml` | `pref_drawer_match_home` SwitchPreference |
| `res/values/strings.xml` | All new strings |
| `res/drawable/ic_customize.xml` | **NEW**: Customize menu icon |
| `res/drawable/ic_arrow_back.xml` | **NEW**: Back arrow icon |
| `res/layout/fragment_icon_picker.xml` | **NEW**: Icon picker layout |
| `res/layout/item_icon_picker.xml` | **NEW**: Icon grid cell |
| `res/layout/item_icon_picker_header.xml` | **NEW**: Category header |
| `res/layout/preference_icon_widget.xml` | **NEW**: Icon preview widget |
