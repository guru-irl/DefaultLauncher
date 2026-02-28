# 045: Bug Fixes and Folder Cover Icon Color

## Summary

Fixes four UI bugs (search "None" icon, Enter key for quick actions, settings ripple corners,
custom icon size re-tap) and adds a new setting to customize the emoji color on folder covers.

## Bug Fixes

### 1. AI search "None" icon
The "None (disabled)" option in the AI search app selector reused the AI sparkle icon,
which was confusing. Replaced with a dedicated Material "block" icon (`ic_none_disabled.xml`).

### 2. Enter key triggers quick actions
When a quick action (phone, email, URL) is shown in search, pressing Enter now launches
it instead of opening the first app result. Quick actions are tracked at priority 0,
which takes precedence over app results (priority 1).

### 3. Settings ripple corner rounding
The bottom row of a settings card lost its rounded-corner ripple after AndroidX Preference
rebinding replaced the background drawable. Added a stale-tag check
(`child.getBackground() == tag`) so the ripple is reapplied when the background changes.

### 4. Custom icon size button re-tap
Two issues: (a) `setOnClickListener` on the Custom button overwrote the toggle group's
internal click handler, breaking state management. Replaced with `setOnTouchListener`
(returns `false`) which detects re-taps without interfering. (b) Custom values like 80%
saved as `"0.8"` which string-matched the S preset, causing "S (80%)" display instead of
"Custom (80%)". Changed to 5-decimal format (`"0.80000"`) which avoids preset matching
while parsing identically via `toFloatOrNull()`.

## New Feature: Folder Cover Icon Color

Added a color picker setting for the emoji icon tint on folder covers. Follows the
established `FolderSettingsHelper` pattern with default/resolve/effective methods.

## New Files

- `res/drawable/ic_none_disabled.xml` — Material "block" icon for the disabled/none option

## Modified Files

- `SearchFragment.java` — Use `ic_none_disabled` for "None" option
- `UniversalSearchAdapterProvider.java` — Track quick actions at priority 0, check before app results
- `BaseCardItemDecoration.java` — Add stale-background check in `applyRipple()`
- `IconSettingsHelper.java` — Replace `setOnClickListener` with `setOnTouchListener`, use `%.5f` format
- `LauncherPrefs.kt` — Add `FOLDER_COVER_ICON_COLOR` preference
- `FolderSettingsHelper.java` — Add `DEFAULT_COVER_ICON_COLOR_RES`, resolver, and effective color methods
- `FolderCoverManager.java` — Use `getEffectiveCoverIconColor()` instead of hardcoded onSurface
- `FolderCoverPickerHelper.java` — Use custom cover icon color for emoji picker preview
- `AppDrawerColorsFragment.java` — Wire up folder cover icon color picker
- `drawer_colors_preferences.xml` — Add ColorPickerPreference for cover icon color
- `strings.xml` — Add title/summary strings for cover icon color

## Settings Added

| Setting | Key | Type | Default |
|---------|-----|------|---------|
| Folder cover icon color | `pref_folder_cover_icon_color` | String (color name) | `""` (materialColorOnSurface) |
