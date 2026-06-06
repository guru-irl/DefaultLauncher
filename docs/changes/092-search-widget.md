# 092: Search Widget

## Summary

Adds a second launcher-native in-process custom widget: a fixed-size "SEARCH"
pill that opens the user's configured web-search provider. It reuses all of the
clock widget's infrastructure (custom-widget registration, inherited-field
normalization, `providerInfo` resource rooting, the Widgets settings sub-screen,
the tight preference row, the deterministic placement seam). Built on
`feature/search-widget`; see also change 091 (the clock widget).

## New Files

- `src/.../widget/custom/SearchWidgetView.java` - the view: a rounded pill
  (`GradientDrawable`) filling the cell, with "SEARCH" centered in Bebas Neue.
  The label uses tight (-0.05) letter spacing, auto-sizes to the pill height,
  and is ink-centered (the Bebas descent is empty for an all-caps word, so the
  line box is shifted to center the visible glyphs, not the metrics box). Tapping
  opens the configured web-search provider (`LauncherPrefs.SEARCH_WEB_APP`);
  "default" or unset falls back to the system web search. Two color prefs are
  read live via the prefs framework.
- `src/.../widget/custom/SearchWidgetPlugin.java` - the `CustomWidgetPlugin`.
  Fixed 2x1, NOT resizable (`resizeMode = RESIZE_NONE`, equal min/max spans).
  Copies the clock plugin's inherited-field normalization (widgetCategory =
  HOME_SCREEN, widgetFeatures/generatedPreviewCategories/previewLayout/
  descriptionRes cleared, configure null) so the picker shows it correctly.
- `src/.../settings/SearchWidgetFragment.java` + `res/xml/search_widget_preferences.xml`
  - Background color + Text color pickers (default M3 Surface / OnSurface).
- `res/drawable-nodpi/widget_preview_search.png` - the picker preview, generated
  as a crop of the ACTUAL rendered widget so the preview matches the placed
  widget exactly.
- `tests-e2e/smoke/test_search_widget_basics.py` - picker listing + render smoke.

## Modified Files

- `LauncherPrefs.kt` - `SEARCH_WIDGET_BG_COLOR`, `SEARCH_WIDGET_TEXT_COLOR`.
- `res/values/config.xml` - the plugin added to `launcher_custom_widgets`.
- `res/values/strings.xml`, `res/xml/widgets_preferences.xml` - the Search widget
  row under the Widgets sub-screen (tight `preference_widget_entry` layout,
  `ic_settings_search` icon).
- `src/.../testing/WorkspaceSeedReceiver.java`, `AndroidManifest-common.xml` -
  the placement seam generalized to a shared `placeCustomWidget` with a
  `PLACE_SEARCH_WIDGET` action and a `--es provider` override (debug only).
- `tests-e2e/lib/launcher.py`, `lib/selectors.py` - search widget driver helpers.
- `res/drawable-nodpi/widget_preview_danfo_clock.png` - the clock preview
  regenerated to the live 0.20 date ratio (was baked at the old 0.32), so the
  clock preview also matches its actual render.

## Settings Added

| Setting | Pref key | Type | Default |
|---|---|---|---|
| Background color | `pref_search_widget_bg_color` | color | M3 Surface |
| Text color | `pref_search_widget_text_color` | color | M3 OnSurface |

## Design Decisions

- **Fixed 2x1, not resizable** - matches the compact dock pill the design targets.
- **Tap opens the configured web-search provider** - mirrors
  `SearchFabController.launchWebSearch` (without a query), reading
  `SEARCH_WEB_APP` from the launcher's search settings.
- **Preview is a crop of the live render**, not a hand-drawn asset, so it cannot
  drift from the actual widget.

## Known Limitations

- Colors are global to all search-widget instances (as with the clock).
- The preview bakes in the default Surface/OnSurface colors; it does not reflect
  a user's custom color choice.
