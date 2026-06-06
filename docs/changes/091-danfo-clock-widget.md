# 091: Danfo Clock Widget

## Summary

Ships a launcher-native clock widget (no separate app): a single-line time in
the bundled Danfo font over an adaptive Bebas Neue dateline, in a bold
lock-screen style. It is hosted in-process via the existing `CustomWidgetManager`
path (not RemoteViews), appears in the system widget picker under the launcher's
own name, is resizable from 2x2 up to the grid with automatic font sizing, and is
configurable (alignment, time format, color/theming) from a new Settings sub-tree.
UI and visual e2e tests are included. Implements the spec at
`docs/superpowers/specs/2026-06-06-danfo-clock-widget-design.md`.

## New Files

- `src/com/android/launcher3/widget/custom/DanfoClockView.java` - the widget view.
  A `LinearLayout` with a `TextClock` (time, Danfo) and a `TextView` (date, Bebas
  Neue). Sizes its fonts from measured glyph bounds in `onSizeChanged`, picks the
  widest non-wrapping date format that fits, subscribes to the five clock prefs
  via the unified `PrefChangeDispatcher`, and refreshes on time/date/timezone/
  locale/config and wallpaper-color changes. Tap opens the system clock app.
- `src/com/android/launcher3/widget/custom/DanfoClockWidgetPlugin.java` - the
  concrete `CustomWidgetPlugin`. `updateWidgetInfo` sets the label, preview,
  default 2x2 size, min 2x2, max = grid size, and resize mode; `onViewCreated`
  attaches a `DanfoClockView` to the host.
- `src/com/android/launcher3/settings/WidgetsFragment.java` and
  `res/xml/widgets_preferences.xml` - a new top-level "Widgets" settings sub-tree
  (extensible: each widget is its own entry).
- `src/com/android/launcher3/settings/ClockWidgetFragment.java` and
  `res/xml/clock_widget_preferences.xml` - the clock settings. Choice settings use
  the app's M3 bottom-sheet picker (`SettingsSheetBuilder`), not androidx
  `ListPreference`; colors use the existing `ColorPickerPreference`.
- `res/font/bebas_neue.ttf` - dateline font (SIL OFL).
- `res/drawable/widget_preview_danfo_clock.xml`, `res/drawable/ic_settings_widgets.xml`,
  `res/drawable/ic_settings_clock.xml` - picker preview and settings icons.
- `res/values/arrays.xml` - choice entries/values for the three pickers.
- e2e: `tests-e2e/smoke/test_clock_widget_basics.py`,
  `tests-e2e/regression/test_clock_widget_prefs.py`,
  `tests-e2e/visuals/test_clock_widget_paint.py`, plus selectors, `LauncherDriver`
  helpers, and a `sample_region_grid` visual helper.

## Modified Files

### AOSP-origin edits (justification required per CLAUDE.md)

- `src/com/android/launcher3/widget/custom/CustomWidgetManager.java` - two additive
  changes: (1) a second, unconditional registration loop reads
  `R.array.launcher_custom_widgets` so launcher-shipped widgets register
  independently of the `enableSmartspaceAsAWidget()` flag (the existing
  smartspace-gated loop is untouched); (2) `PLUGIN_PKG` now uses the launcher's
  `BuildConfig.APPLICATION_ID` instead of `"android"`, so the widget picker groups
  the widget under the launcher app rather than "Android System". Custom-widget
  identity keys off the class-name prefix and the negative widget id, not the
  package, so this is safe.
- `src/com/android/launcher3/widget/LauncherWidgetHolder.java` - one-line guard in
  `attachViewToHostAndGetAttachedView`: skip `recycleExistingView` for custom
  widgets (`isCustomWidget()`). Without it, the pre-inflated custom host view (with
  its `DanfoClockView` child, built on the model executor) was recycled and
  replaced by the "Can't load widget" fallback on the model-load path. The guard
  only changes behavior for custom widgets; the ordinary RemoteViews recycle path
  is untouched.
- `src/com/android/launcher3/LauncherPrefs.kt` - five additive `backedUpItem`
  entries (`CLOCK_ALIGNMENT`, `CLOCK_TIME_FORMAT`, `CLOCK_COLOR_MODE`,
  `CLOCK_TIME_COLOR`, `CLOCK_DATE_COLOR`).

### Other

- `res/values/config.xml` - new `launcher_custom_widgets` array.
- `res/values/strings.xml`, `res/xml/launcher_preferences.xml` - Widgets row +
  widget/clock strings.
- `src/com/android/launcher3/testing/WorkspaceSeedReceiver.java` - debug-only
  `PLACE_CLOCK_WIDGET` broadcast that inserts a custom-appwidget favorites row and
  reloads, so e2e tests place the widget deterministically (no flaky drag). Guarded
  by `BuildConfig.DEBUG`; idempotent (`_id = 200` deleted before insert).
- `AndroidManifest-common.xml` - registers the new debug action.

## Settings Added

| Setting | Pref key | Type | Options / default |
|---|---|---|---|
| Alignment | `pref_clock_alignment` | choice | Centered (default) / Left |
| Time format | `pref_clock_time_format` | choice | Follow system (default) / 12-hour (`9:41`) / 24-hour (`09:41`) |
| Color | `pref_clock_color_mode` | choice | White (default) / Custom / Material You / Auto from wallpaper |
| Time color | `pref_clock_time_color` | color | empty = white (Custom mode only) |
| Date color | `pref_clock_date_color` | color | empty = same as time (Custom mode only) |

Choices persist through `LauncherPrefs` so the `PrefChangeDispatcher` restyles
every live widget instantly (no rebuild). Settings are global to all clock
instances.

## Design Decisions

- **In-process custom widget, not RemoteViews:** needed for full control of the
  Danfo/Bebas fonts, the measured auto-sizing, and live theming.
- **Registration decoupled from the smartspace flag:** launcher-shipped widgets
  use a separate, ungated array so they always register.
- **Responsive sizing from real glyph bounds:** `onSizeChanged` measures the
  worst-case time (`00:00`) width and `Paint.getTextBounds` height, so Danfo's tall
  glyphs never clip (including at 2x2), and the date shrinks/abbreviates
  independently rather than forcing the time smaller.
- **Adaptive non-wrapping date:** locale-aware ladder
  (`SATURDAY, JUNE 6` to `SAT 6`), widest that fits.
- **M3 bottom-sheet choice pickers:** the app uses no `ListPreference`; choices
  reuse the existing `SettingsSheetBuilder` card pattern for visual consistency.
- **Deterministic test placement:** a debug broadcast seam avoids flaky drag-drop
  in e2e, and uncovered the `LauncherWidgetHolder` recycle bug above.

## Post-implementation refinements (on-device testing)

Found and fixed while testing on a Pixel emulator (Android 17) and a Galaxy S24
(One UI / Android 16):

- **Resizable on device:** `maxSpanX/Y` were 0, which disables the resize-frame
  handles (`AppWidgetResizeFrame` needs `max > 1 && min < max`). Set to the grid
  size so the widget resizes from 2x2 up to the grid.
- **Picker grouping + visibility:** the custom widget is built by cloning an
  arbitrary installed provider (`CustomWidgetManager.getAndAddInfo`), so it
  inherited that provider's fields. `PLUGIN_PKG` is now the launcher's own
  package (groups under the launcher in the picker, not "Android System"), and
  `DanfoClockWidgetPlugin.updateWidgetInfo` now normalizes the inherited fields:
  `widgetCategory = HOME_SCREEN` (otherwise the home-screen picker filters it out
  on devices where the cloned provider was keyguard/search-only -- this was the
  cause of the widget not appearing in the picker on the Galaxy S24),
  `widgetFeatures = 0`, `generatedPreviewCategories = 0`, `previewLayout`,
  `descriptionRes`, `configure` cleared.
- **Picker preview:** the preview is a generated PNG (`drawable-nodpi`) rendered
  from the real Danfo/Bebas fonts. Resource loading for a custom widget resolved
  against the cloned provider's package and failed ("Can't load widget preview
  drawable"); `getAndAddInfo` now reflectively roots the info's hidden
  `providerInfo` ActivityInfo in the launcher package so its own preview/icon
  load. `previewLayout` is unusable for in-process custom widgets (routes through
  a RemoteViews host view that cannot load -> "Can't load widget").
- **Responsive sizing redesign:** sizing now uses font metrics (line height),
  the date is a small capped one-liner pinned right below the time with a
  proportional negative gap, and an explicit `requestLayout()` after `setTextSize`
  avoids a `TextClock` stale-bounds clip. Verified clean at 2x2..6x6.
- **Settings IA:** a top-level "Widgets" sub-screen now holds a "Clock widget"
  entry (extensible for more widgets), with a tightened icon-row layout.
- **Appearance options:** soft-shadow toggle, and an outline/cutout mode (stroke
  the time only, date stays filled) with a thickness slider.

## Known Limitations

- Settings are global, not per-widget-instance.
- Material You and wallpaper-auto color tuning is conservative; a placed widget
  recovers white-or-dark legibility but does not yet expose contrast fine-tuning.
- An already-placed widget from before the `PLUGIN_PKG` change would orphan (its DB
  provider string no longer matches); not a concern for unreleased work, just
  re-add it.
