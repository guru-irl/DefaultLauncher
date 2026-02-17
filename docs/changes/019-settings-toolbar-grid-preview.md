# 019: Settings Toolbar Polish & Grid Preview

## Summary

Refined the settings toolbar layout (transparent scrim, centered titles, reduced height) and added a live animated grid preview to the Grids subpage. Also added an auto-keyboard toggle for search settings and a slider tracking-stop callback.

## Settings Toolbar (`SettingsActivity.java`, `settings_activity.xml`)

### Layout Changes (`res/layout-v31/settings_activity.xml`)

- `AppBarLayout`: added `stateListAnimator="@null"` and `app:elevation="0dp"` to eliminate lifted-state color change
- `CollapsingToolbarLayout`: height reduced from 260dp to 200dp (set programmatically for both main and sub-pages)
- `contentScrim` changed to `@android:color/transparent` — no color flash on collapse
- Expanded title margins reduced: 24dp → 16dp start/end
- `MaterialToolbar`: added `contentInsetStart="16dp"` and `contentInsetStartWithNavigation="16dp"`

### Programmatic Changes (`SettingsActivity.java`)

- `appBar.setLiftable(false)` prevents AppBarLayout from changing color on scroll
- Main page: 200dp height, title centered horizontally + bottom gravity
- Sub-pages: 140dp height, title centered horizontally + bottom gravity
- Both use `setExpandedTitleGravity(CENTER_HORIZONTAL | BOTTOM)`

## Grid Preview

### `GridPreviewView.java` (**NEW**)

Custom `View` that draws a scaled-down phone frame with an animated grid overlay:

- Uses real device parameters from `DeviceProfile` for accurate grid computation
- Phone frame: rounded rectangle with fill + stroke, aspect ratio matches actual device
- Grid cells: rounded squares positioned using the same formula as `DeviceProfile.deriveSquareGridRows()`
- Row/column number labels outside the phone frame
- Animated transitions when column count changes:
  - Cells shared between old/new states stay put while size/gap interpolate
  - New cells fade in with a subtle slide-up offset (staggered at 40% progress)
  - Removed cells fade out in the first 50% of the animation
  - Uses `PathInterpolator(0.05, 0.7, 0.1, 1.0)` (Material emphasized decelerate)
  - Duration: 350ms
- Status bar height read from real `WindowInsets`

### `GridPreviewPreference.java` (**NEW**)

Custom `Preference` wrapping `GridPreviewView`:

- Tagged `"no_card"` so `CardGroupItemDecoration` skips it
- Hides all default preference views, adds preview + explanation text
- `updateColumns(int)` method called by `GridsFragment` on slider change
- Reads initial column count from shared preferences

### `GridsFragment.java`

- Finds `GridPreviewPreference` and wires it to the column slider
- Slider `onPreferenceChangeListener` now calls `previewPref.updateColumns()` for real-time visual feedback on every tick
- Grid reconfig (`onConfigChanged`) deferred to `onStopTrackingTouch` via new `OnTrackingStopListener`, so the actual grid only rebuilds when the user lifts their finger

### `M3SliderPreference.java`

- Added `OnTrackingStopListener` interface with `onStopTracking(int finalValue)` callback
- Added `Slider.OnSliderTouchListener` to fire the callback on finger release
- Allows separating "preview on every tick" from "commit on release"

## Search Auto-Keyboard (`res/xml/search_preferences.xml`)

Added `pref_search_auto_keyboard` SwitchPreference to the Search sub-page:
- Title: "Auto-open keyboard"
- Summary: "Automatically open the keyboard when opening the app drawer"
- Default: false

## Files Changed

| File | Change |
|------|--------|
| `res/layout-v31/settings_activity.xml` | Transparent scrim, reduced height, centered insets |
| `res/xml/grids_preferences.xml` | Added `GridPreviewPreference` above column slider |
| `res/xml/search_preferences.xml` | Added auto-keyboard switch |
| `src/.../settings/SettingsActivity.java` | Liftable=false, centered titles, programmatic heights |
| `src/.../settings/GridPreviewPreference.java` | **NEW**: Preference wrapper for grid preview |
| `src/.../settings/GridPreviewView.java` | **NEW**: Animated phone frame with grid overlay |
| `src/.../settings/GridsFragment.java` | Wire preview to slider, defer reconfig to stop-tracking |
| `src/.../settings/M3SliderPreference.java` | Added OnTrackingStopListener + touch listener |
