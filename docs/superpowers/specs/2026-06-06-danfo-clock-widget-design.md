# Danfo Clock Widget - Design Spec

- **Date:** 2026-06-06
- **Status:** Approved design, pending implementation plan
- **Topic:** A launcher-native, in-process clock widget

## 1. Summary

Ship a clock widget inside the launcher itself (not a separate app). It shows a
single-line time in the bundled **Danfo** display font with a **Bebas Neue**
all-caps dateline underneath, in a bold lock-screen style. It is hosted
in-process via the launcher's existing `CustomWidgetManager`, appears in the
system widget picker under the launcher's name, is resizable from 2x2 upward
with automatic font sizing, and is themeable. UI and visual tests are
first-class deliverables.

## 2. Goals

- A polished, launcher-native clock widget that reads like a lock-screen clock.
- Full control over fonts, sizing, and color (hence in-process, not RemoteViews).
- Responsive: looks correct at any size from 2x2 up, with no hardcoded font size.
- Configurable: alignment, time format, and color/theming, via launcher settings.
- Covered by automated UI and visual regression tests.

## 3. Non-goals (v1)

- The stacked / two-line / accent layouts explored during design (only the
  single-line layout ships).
- Seconds display.
- Per-widget-instance configuration (settings are global to all clock instances;
  per-instance config is a possible future enhancement).
- World clocks, alarms, timers, or any clock-app functionality beyond launching
  the system clock app on tap.

## 4. User-facing behavior

### 4.1 Appearance
- **Time:** single line (for example `9:41`), Danfo font, the visual hero.
- **Date:** one line below the time, Bebas Neue, all-caps, locale-aware.
- Soft text shadow under both lines for legibility on busy wallpapers, in all
  color modes.

### 4.2 Adaptive date format
The dateline never wraps. The view picks the longest locale-correct format that
fits the available width at the current date font size, stepping down this ladder
(expressed as locale skeletons resolved via
`DateFormat.getBestDateTimePattern`):

1. `EEEEMMMMd` -> SATURDAY, JUNE 6
2. `EEEEMMMd`  -> SATURDAY, JUN 6
3. `EEEMMMd`   -> SAT, JUN 6
4. `EEEd`      -> SAT 6

If even the shortest does not fit, the date font shrinks further (see 4.3) but
still never wraps.

### 4.3 Responsive sizing
- **Resize range:** resizable on both axes, minimum **2x2**, **no maximum** (the
  launcher bounds it to the grid). Default drop size is **2x2**.
- **Automatic font sizing:** `DanfoClockView` computes font sizes from its
  measured bounds on every layout pass:
  - The **time** scales to fill the available width, capped by a height budget
    that reserves room for the dateline, so it never clips at 2x2 and grows large
    at big spans. Clamped to sane min/max bounds so a huge widget renders crisply
    rather than pixelating.
  - The **date** font is a fixed ratio of the resolved time size, and shrinks
    independently (and abbreviates per 4.2) if a narrow width would clip it, so a
    long date never forces the time smaller.
- **Centering:** both lines use the container gravity, so they stay correctly
  centered (or left-aligned per the alignment setting) at every size. The measure
  pass re-runs on resize, so size and centering re-resolve automatically.

### 4.4 Settings (global, "Clock widget" settings sub-screen)

| Setting | Pref key | Type | Options / default |
|---|---|---|---|
| Alignment | `CLOCK_ALIGNMENT` | enum string | Centered (default) / Left |
| Time format | `CLOCK_TIME_FORMAT` | enum string | Follow system (default) / 12-hour / 24-hour |
| Color source | `CLOCK_COLOR_MODE` | enum string | White (default) / Custom / Material You / Auto from wallpaper |
| Time color | `CLOCK_TIME_COLOR` | color string | empty = white (Custom mode only) |
| Date color | `CLOCK_DATE_COLOR` | color string | empty = same as time (Custom mode only) |

- **Time format:** "Follow system" lets the time track the phone's 12/24h
  setting; the overrides force a format regardless of the system setting.
- **Color source:**
  - **White:** time and date both white.
  - **Custom:** time = `CLOCK_TIME_COLOR` (white if empty); date =
    `CLOCK_DATE_COLOR` (falls back to the time color if empty). The two pickers
    are the "separate time/date colors" capability.
  - **Material You:** both use the system dynamic accent (`system_accent1`
    resources), tracking wallpaper/theme changes.
  - **Auto from wallpaper:** derive a legible white-or-dark color from
    `WallpaperColorHints`, re-evaluated when the wallpaper changes.

### 4.5 Tap action
Tapping the widget opens the system clock/alarm app via
`AlarmClock.ACTION_SHOW_ALARMS`, with a graceful fallback if no handler exists.

## 5. Architecture

### 5.1 Hosting: in-process custom widget
Built on the launcher's existing `CustomWidgetManager` path (the same machinery
the QSB / smartspace-as-widget uses), not RemoteViews, so we get full control
over fonts, measuring, animation, and color.

- **`DanfoClockWidgetPlugin`** implements the kept `CustomWidgetPlugin`
  interface (`src/com/android/systemui/plugins/CustomWidgetPlugin.java`):
  - `updateWidgetInfo(info, context)` sets the label, preview, default size,
    and resize mode (min 2x2, no max).
  - `onViewCreated(hostView)` instantiates a `DanfoClockView` and attaches it to
    the `AppWidgetHostView`.

- **Registration decoupling.** Today `CustomWidgetManager`'s constructor only
  registers entries from `R.array.custom_widget_providers` and only when
  `enableSmartspaceAsAWidget()` is true. We add a small, additive registration so
  launcher-shipped widgets (a new `R.array.launcher_custom_widgets`) register
  **unconditionally**, independent of the smartspace flag, leaving the smartspace
  path untouched. `CustomWidgetManager.java` is an AOSP-origin file, so this edit
  carries an explicit justification in the change doc per CLAUDE.md.

### 5.2 The view: `DanfoClockView`
A custom `ViewGroup` (vertical, centered/left per pref) containing:
- **Time:** a `TextClock` with typeface `@font/danfo`. TextClock gives automatic
  ticking and 12/24h handling. Hour padding differs by mode:
  - **12-hour** pattern `h:mm` - no leading zero (for example `9:41`).
  - **24-hour** pattern `HH:mm` - zero-padded hour (for example `09:41`).
  - Follow system: set the 12h slot to `h:mm` and the 24h slot to `HH:mm`, so
    TextClock picks the right one based on the phone setting.
  - Forced 12h or 24h: set both slots to the same forced pattern (`h:mm` or
    `HH:mm`) so the system setting is ignored.
  - Minutes are always zero-padded (`mm`).
- **Date:** a plain `TextView` with typeface Bebas Neue, text set by the view
  using the adaptive format (4.2). Not a TextClock, because we need to choose the
  format string based on measured width. It is refreshed on date, timezone,
  locale, and size changes.
- **Measure logic:** `onMeasure`/`onSizeChanged` compute the time and date font
  sizes (4.3) and re-pick the date abbreviation (4.2).

### 5.3 Fonts
- `res/font/danfo.ttf` already exists.
- Add `res/font/bebas_neue.ttf` (Bebas Neue, OFL) for the dateline.

### 5.4 Live updates
- A lightweight `BroadcastReceiver`, registered while the view is attached,
  listens for `ACTION_TIME_CHANGED`, `ACTION_TIMEZONE_CHANGED`,
  `ACTION_DATE_CHANGED`, `ACTION_LOCALE_CHANGED`, and wallpaper-color changes,
  and refreshes the dateline / color. The time TextClock self-ticks.
- The view subscribes to the five clock prefs via the unified prefs framework
  (`LauncherPrefs.get(ctx).getPrefChanges().subscribe(subscriber, ...)`) in
  `onAttachedToWindow` and closes the subscription in `onDetachedFromWindow`, so
  changing a setting restyles every live instance instantly (no widget rebuild).

### 5.5 Settings UI
- New `res/xml/clock_widget_preferences.xml` and a `ClockWidgetFragment`
  (mirroring `AppDrawerColorsFragment`), reachable from the home-screen settings.
- The two color pickers reuse the existing `ColorPickerPreference`. The picker
  rows are shown only when Color source = Custom (controlled by the fragment).
- New typed `ConstantItem` entries in `LauncherPrefs.kt` via `backedUpItem()` for
  the five keys in 4.4.

### 5.6 Color resolution
Resolved in `DanfoClockView` from `CLOCK_COLOR_MODE`:
- White -> `#FFFFFF` for both.
- Custom -> per 4.4 (time then date with fallback).
- Material You -> `context.getColor(android.R.color.system_accent1_*)` (light/dark
  variant chosen for contrast), both lines.
- Auto from wallpaper -> `WallpaperColorHints` luminance picks white or a near
  black, both lines.

## 6. Testing (first-class deliverables)

- **UI smoke** (`tests-e2e/smoke/test_clock_widget_basics.py`): the clock
  provider appears in the widget picker, drops onto the workspace, and renders.
  Driven through `LauncherDriver`; new selectors added to `lib/selectors.py`.
- **UI regression** (`tests-e2e/regression/test_clock_widget_prefs.py`): toggling
  alignment, time format, and each color mode updates the live widget; resizing
  keeps it centered and legible.
- **Visual baseline** (`tests-e2e/visuals/`): golden images of the rendered clock
  at **2x2 and a large span** (size extremes), default white plus one custom
  color, to catch font, color, layout, and scaling regressions.
- Update the `tests-e2e/README.md` coverage table.

The AVD has a widget picker, so these run on the emulator. Follow the e2e
conventions in CLAUDE.md (no inline selectors, no `time.sleep`, mark tests, use
the workspace-seeding fixtures).

## 7. Files

### New
- `src/.../widget/custom/DanfoClockWidgetPlugin.java` - the `CustomWidgetPlugin`.
- `src/.../widget/custom/DanfoClockView.java` - the responsive clock view.
- `src/.../settings/ClockWidgetFragment.java` - settings sub-screen.
- `res/xml/clock_widget_preferences.xml` - settings hierarchy.
- `res/font/bebas_neue.ttf` - dateline font.
- Preview asset for the widget picker (drawable or preview layout).
- `tests-e2e/smoke/test_clock_widget_basics.py`,
  `tests-e2e/regression/test_clock_widget_prefs.py`, and a visual baseline.

### Modified (AOSP-origin edits need change-doc justification)
- `src/.../widget/custom/CustomWidgetManager.java` - unconditional registration
  of launcher-shipped widgets (5.1).
- `src/com/android/launcher3/LauncherPrefs.kt` - five new `backedUpItem` entries.
- `res/values/arrays.xml` - new `launcher_custom_widgets` array.
- `res/values/strings.xml` - widget label + settings strings.
- Home-screen settings XML/fragment - entry point to the Clock widget sub-screen.

## 8. Decisions made during brainstorming

- Layout: single line (not stacked/accent).
- Dateline font: Bebas Neue.
- Both alignments available as a setting (centered default).
- Time format: follow system by default, with 12h/24h overrides. 24h is
  zero-padded (`09:41`); 12h is not (`9:41`).
- Color: all four controls included (custom picker, Material You, wallpaper-auto,
  separate time/date colors).
- Resizable: minimum 2x2, no maximum, auto font sizing, correct centering.
- Adaptive date abbreviation at narrow widths (never wraps).
- Hosting: in-process custom widget (not RemoteViews).
- Tests: UI + visual, as first-class deliverables.

## 9. Open questions for the plan

- Exact font-size clamps and the time/date ratio (tune against the AVD).
- Material You resource selection across light/dark and contrast against
  wallpaper.
- Widget-picker preview asset: static drawable vs a rendered preview.
- Whether the home-settings entry point is a new row under an existing screen or
  a new top-level section.
