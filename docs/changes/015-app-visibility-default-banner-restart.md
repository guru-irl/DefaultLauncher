# 015: App Visibility, Set-Default Banner, and Debug Restart

## Summary

Three usability improvements: DefaultLauncher now appears in other launchers' app drawers, a banner prompts users to set it as default, and a restart option in Debug settings enables quick recovery during development.

## App Drawer Visibility

### Problem

The `Launcher` activity only declares `HOME`/`DEFAULT` categories in its intent filter. Without a `LAUNCHER` category, other launchers don't list DefaultLauncher in their app drawers — users can't find or open it after installing.

### Solution

Added an `<activity-alias>` in `AndroidManifest.xml` targeting the `Launcher` activity with a `MAIN/LAUNCHER` intent filter (no `HOME` category). When users tap the icon from another launcher, it opens the Launcher activity, which triggers the OS home-app selection prompt if not already default.

## "Set as Default" Banner

### Detection

Uses `RoleManager.isRoleHeld(RoleManager.ROLE_HOME)` (API 29+, minSdk 33) to check whether DefaultLauncher is the current home app.

### Banner Preference

A `Preference` with a custom layout is added dynamically at order -1 (before all other preferences) in `LauncherSettingsFragment.onCreatePreferences()`. The banner is removed in `onResume()` if the app has become the default home app (e.g., after returning from the role picker).

### Banner Layout

M3 expressive style:
- `LinearLayout` with `materialColorPrimaryContainer` fill, 28dp corner radius
- 24dp home icon tinted `materialColorOnPrimaryContainer`
- Title "Set as default launcher" (14sp bold) + subtitle (12sp)
- Ripple feedback via `selectableItemBackground` foreground
- Tagged `no_card` to exclude from `CardGroupItemDecoration`

### CardGroupItemDecoration Exclusion

Items with `android:tag="no_card"` on their root view are:
- Skipped in `onDraw()` (no card background drawn)
- Skipped in `getItemOffsets()` (no card margins applied)
- Treated as group boundaries (adjacent preferences get large top/bottom corners)

A helper method `isNoCardItem()` checks the tag via `ViewHolder.itemView.getTag()`.

## Restart Launcher (Debug)

Added a "Restart launcher" preference to `debug_preferences.xml` before the color debug swatch. The click handler calls `Process.killProcess(Process.myPid())` — the standard launcher restart pattern where Android auto-restarts the default home app after it dies.

## Files Changed

| File | Change |
|------|--------|
| `AndroidManifest.xml` | Added `<activity-alias>` with `MAIN/LAUNCHER` intent filter |
| `res/layout/preference_set_default_banner.xml` | New: M3 banner layout |
| `res/drawable/set_default_banner_bg.xml` | New: rounded rect background |
| `res/drawable/ic_home_banner.xml` | New: 24dp home icon vector |
| `res/values/strings.xml` | Added banner + restart strings |
| `res/xml/debug_preferences.xml` | Added restart preference |
| `src/.../settings/SettingsActivity.java` | Banner creation/removal with RoleManager |
| `src/.../settings/CardGroupItemDecoration.java` | `no_card` tag exclusion logic |
| `src/.../settings/DebugFragment.java` | Restart click handler |
