# 031: App Icon Refresh & Workspace Bottom Row Clipping Fix

**Branch:** `dev`

## Summary

Refreshed the launcher app icon (rescaled D glyph, monochrome layer, white background,
vector-only foreground) and fixed a bug where workspace bottom row icons get randomly
clipped due to stale `workspacePadding` after system inset changes.

## Part A: App Icon Refresh

### A1. Rescaled Foreground Glyph

**File:** `res/drawable/ic_launcher_home_foreground.xml`

The "D" vector path was rescaled to sit within the safe zone with more padding, using the
new `scripts/scale-icon-path.js` utility. The path coordinates were recalculated at 84%
scale around the 54,54 center pivot of the 108dp adaptive-icon viewport.

### A2. Monochrome Layer

**Files:** `res/drawable/ic_launcher_home_monochrome.xml` (new), `res/drawable/ic_launcher_home.xml`

Added a `<monochrome>` element to the adaptive icon definition, pointing to a new
`ic_launcher_home_monochrome.xml` vector drawable. This enables themed icon support
(Android 13+) where the system tints the icon to match the wallpaper palette.

### A3. White Icon Background

**File:** `res/values/colors.xml`

Changed `icon_background` from `@color/materialColorSecondaryContainer` (dynamic M3 color)
to `#FFFFFF` (white). This gives the launcher icon a consistent white background across all
devices rather than a theme-dependent color.

### A4. Round Icon & Bitmap Cleanup

**Files:** `AndroidManifest-common.xml`, `res/mipmap-*/ic_launcher_home_foreground.png` (deleted)

- Added `android:roundIcon="@drawable/ic_launcher_home"` to `<application>` and
  `LauncherAppDrawerAlias` activity-alias in the manifest.
- Deleted bitmap foreground PNGs from `mipmap-hdpi/mdpi/xhdpi/xxhdpi` — the icon is now
  fully vector-based.

### A5. Scale Icon Path Script

**File:** `scripts/scale-icon-path.js` (new)

Node.js utility that scales SVG/VectorDrawable path data around a pivot point. Reads path
data from stdin, applies the scale factor, and prints the result with bounding box analysis
to stderr. Used to generate the rescaled D glyph.

## Part B: Icon Size Selector Fix

**File:** `res/layout/preference_icon_size.xml`

Changed `app:selectionRequired` from `false` to `true` on the `MaterialButtonToggleGroup`.
This prevents the user from deselecting all size options, which would leave the icon size
preference in an undefined state.

## Part C: Workspace Bottom Row Clipping Fix

### Root Cause

An inconsistency between `PagedView.mInsets` and `DeviceProfile.workspacePadding` caused
CellLayouts to be measured too short. When system insets changed (accessibility service
toggle, gesture/3-button nav transition, transient inset from keyboard/PIP),
`DeviceProfile.updateInsets()` only updated `mInsets` without recalculating
`workspacePadding`. The stale padding caused workspace CellLayouts to be measured
`(newInsets.bottom - oldInsets.bottom)` pixels too short, clipping the bottom row.

### C1. Recalculate Workspace Padding on Inset Change

**File:** `src/com/android/launcher3/DeviceProfile.java`

`updateInsets()` now:
1. Early-returns if insets haven't changed (avoids unnecessary recalculation)
2. Calls `updateWorkspacePadding()` after setting new insets, ensuring
   `workspacePadding.bottom = hotseatBarSizePx - mInsets.bottom` uses the current insets
3. Logs the inset change and resulting workspace padding in debug builds

### C2. Diagnostic Logging in Inset Dispatch

**File:** `src/com/android/launcher3/LauncherRootView.java`

`handleSystemWindowInsets()` now logs when incoming insets differ from the DeviceProfile's
current insets (before the update), providing visibility into exactly when system inset
changes arrive.

## Files Changed

| File | Change |
|------|--------|
| `AndroidManifest-common.xml` | Added `android:roundIcon` to application and activity-alias |
| `res/drawable/ic_launcher_home.xml` | Added monochrome layer |
| `res/drawable/ic_launcher_home_foreground.xml` | Rescaled D glyph path |
| `res/drawable/ic_launcher_home_monochrome.xml` | New monochrome icon vector |
| `res/layout/preference_icon_size.xml` | `selectionRequired` → true |
| `res/mipmap-*/ic_launcher_home_foreground.png` | Deleted (4 files) |
| `res/values/colors.xml` | `icon_background` → #FFFFFF |
| `scripts/scale-icon-path.js` | New path scaling utility |
| `src/com/android/launcher3/DeviceProfile.java` | `updateInsets()` recalculates workspace padding |
| `src/com/android/launcher3/LauncherRootView.java` | Diagnostic logging for inset changes |
