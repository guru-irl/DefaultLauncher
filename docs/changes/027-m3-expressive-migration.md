# 027: M3 Expressive Migration — Colors, Animations, Dimensions

## Summary
Collapse three parallel color systems (AOSP hex, M3 system tokens, M3 wrapper aliases)
onto `materialColor*` tokens, standardize animations to M3 duration/easing/spring tokens,
and extract hardcoded dimensions into `res/values/dimens.xml`.

## Batch 0: Infrastructure
- `res/values/dimens.xml` — M3 shape tokens (`m3_shape_*`)
- `res/values/dimens_settings.xml` — all settings layout tokens (`settings_*`) with M3 shape refs for corner radii
- `res/values/config.xml` — M3 spring profiles (`m3_bounce_snappy/smooth/gentle`)
- `src/.../anim/M3Durations.java` — M3 duration constants (SHORT_1..EXTRA_LONG_4)

## Batch 1: Popup & Shortcuts
- 12 popup/notification colors -> M3 tokens
- 3 drawable updates (scrim, placeholder radius, ripple)
- ArrowPopup duration snapping + popup open spring conversion

## Batch 2: Folder System
- 10 folder colors -> M3 tokens
- 18 duration snaps across 5 files
- Spring conversions: folder open, drop-into-folder, reorder, preview hover

## Batch 3: Workspace & Drop Targets
- 15 workspace/drop-target/pagination colors -> M3 tokens
- styles.xml theme attr fixes (textColorSecondary, colorEdgeEffect, etc.)
- Scrim color state lists (allApps, overview, loading, hints)
- Spring conversions: page snap, page indicator dots

## Batch 4: Widget Picker
- 34 widget_picker_* colors eliminated -> direct M3 refs
- 3 drawable corner radii -> dimen refs

## Batch 5: All Apps & Work Profile
- Tab colors -> M3 tokens
- Drawable radii -> dimen refs
- PrivateProfileManager durations -> M3Durations constants
- Spring conversion: private profile expand/collapse

## Batch 6: Drag & Drop
- delete/uninstall target tints -> M3 tokens
- Duration snapping (drop, fade)
- Spring conversions: drag pickup, widget resize, item drop, cell reorder, app pair hover, drag parallax

## Batch 7: Remaining Drawables
- ~15 drawables with hardcoded hex -> M3 color refs
- ~10 drawables with literal radii -> @dimen refs

## Batch 8: Color State Lists & Theme Attrs
- ~14 res/color/*.xml files: colorAccent -> materialColorPrimary, textColorPrimaryInverse -> materialColorOnPrimary
- Scrim color state lists: overview, widget picker

## Batch 9: Java/Kotlin Animation Durations
- 8+ animation constants -> M3Durations
- Spring conversions: new app bounce, preload icon, page indicator, snackbar, arrow tip
- SysUiScrim.java runtime color resolution
- ColorDebugPreference.java swatch migration

## Batch 10: Settings Density Extraction
- 50+ `(int)(X * density)` -> `getDimensionPixelSize(R.dimen.settings_*)`
- Files: IconSettingsHelper, PerAppIconSheet, SettingsActivity, AppDrawerFragment, IconPickerFragment

## Cleanup
- Deleted ~60 orphaned color entries from colors.xml (popup, folder, notification, widget_picker, misc)
- Removed orphaned overrides from values-v31, values-night-v31, values-v34
- Deleted values-night-v34/colors.xml entirely (all widget_picker dark overrides orphaned)
- Removed orphaned home_settings_header_* and overview_foreground_scrim_color entries

## Stats
- 52 files changed, ~480 insertions, ~615 deletions (net -135 lines)
