# 016: Universal Search, Spring Overscroll, and Search UX

Adds a multi-source universal search system to the all-apps drawer, replacing AOSP's app-only search. Includes spring-physics overscroll, a floating "Web search" FAB, persistent search mode, and filter chips for result categories.

See [`docs/search-system.md`](../search-system.md) for the full architecture documentation.

## Changes

### Universal Search Algorithm

- New `UniversalSearchAlgorithm` replaces AOSP's `DefaultAppSearchAlgorithm`
- Dispatches to 8 providers in parallel: apps, shortcuts, quick actions, calculator, unit converter, contacts, calendar, files
- Results aggregated into `SearchResult` container, converted to `AdapterItem` list with fixed ordering (filter bar → quick actions → calculator → unit converter → apps → shortcuts → contacts → calendar → files)
- `AtomicInteger` pending counter ensures results delivered only after all providers complete (no layout jumps)
- Provider enable/disable controlled by `LauncherPrefs.SEARCH_*` preferences

### Search Result Types

- New `SearchResultAdapterItem` extends `AdapterItem` with fields for each result type (`resultData`, `sectionTitle`, `filters`, `quickAction`)
- New `UniversalSearchAdapterProvider` creates/binds ViewHolders for 9 view types (filter bar, section header, shortcut, contact, calendar, file, quick action, calculator, unit converter)
- All result card layouts use `MaterialCardView` with `0dp` elevation (tonal surface), `28dp` corner radius, `?attr/colorSecondaryContainer` background
- Material Components require `HomeSettings_Theme` + `DynamicColors` context wrapper since Launcher uses a platform theme

### Search Providers

- `SearchProvider<T>` interface: `search()`, `cancel()`, `category()`, `minQueryLength()`
- `AppSearchProvider` — matches against `AllAppsStore` using `StringMatcherUtility`
- `ShortcutSearchProvider` — queries `LauncherApps.getShortcuts()` for deep links
- `QuickActionProvider` — regex-based pattern detection for phone numbers, emails, URLs; always generates a `WEB_SEARCH` action (now filtered out of results in favor of the FAB)
- `CalculatorProvider` — evaluates math expressions via `javax.script.ScriptEngine`
- `UnitConverterProvider` — parses "10 kg" style queries and produces conversion tables
- `ContactSearchProvider` — searches `ContactsContract` for name/phone/email matches
- `CalendarSearchProvider` — searches upcoming 30 days of `CalendarContract.Events`
- `FileSearchProvider` — searches `MediaStore.Files` by display name

### Search Result Data Classes

- `Launchable` interface with `launch(Context)` method, implemented by all result types
- `QuickAction` — `Type` enum (`CALL`, `EMAIL`, `WEB_SEARCH`, `URL`) + label + icon + intent
- `ShortcutResult` — shortcut info with deferred icon loading
- `ContactResult` — display name, phones, emails, photo URI, contact URI
- `CalendarResult` — event title, start/end time, all-day flag, location, color, content URI
- `FileResult` — file name, path, size, MIME type, content URI
- `CalculatorResult` — expression + result with hex/octal alternate formats
- `UnitConversion` — input value/unit + list of `ConvertedValue` entries

### Filter Chips

- `SearchFilters` tracks active categories via `EnumSet<Category>` with "All" toggle
- Categories: `APPS`, `SHORTCUTS`, `CONTACTS`, `CALENDAR`, `FILES`, `TOOLS`
- Filter changes re-deliver existing `SearchResult` without re-querying providers
- Chips only shown for enabled categories (respects `LauncherPrefs` toggles)
- M3 Filter Chip styling: `colorSecondaryContainer` when checked, transparent when unchecked

### Spring Bounce Overscroll

- New `SpringBounceEdgeEffectFactory` replaces AOSP's stretch `EdgeEffect` with spring-physics `translationY` bounce
- Uses `androidx.dynamicanimation.animation.SpringAnimation` with stiffness 300f, damping ratio 0.9f (M3 `motionSpringSlowSpatial`)
- Max overscroll 36dp with cubic damping curve: `1 - (1 - x)^3`
- `onPull()` → damped translationY, `onRelease()` → spring back to 0, `onAbsorb()` → spring with fling velocity
- `draw()` returns `false` (no canvas rendering)
- Applied to both MAIN and SEARCH RecyclerViews via `AdapterHolder.setup()`
- `SpringRelativeLayout.createSpringBounceEdgeEffectFactory()` added as factory method

### "Web Search" FAB

- `ExtendedFloatingActionButton` created programmatically in `ActivityAllAppsContainerView.initContent()`
- Themed with `HomeSettings_Theme` + `DynamicColors` (same as search result cards)
- Elevation `0dp` (M3 tonal elevation, no shadow)
- Positioned `ALIGN_PARENT_BOTTOM | ALIGN_PARENT_END` with 16dp margins
- Shows with scale-in animation when query is non-empty and search is active
- Hides with scale-out animation when query is empty or search exits
- Positioned above keyboard via `dispatchApplyWindowInsets()` reading `WindowInsets.Type.ime()` bottom
- `launchWebSearch()` fires `ACTION_WEB_SEARCH` intent with `SearchManager.QUERY` extra, respects `LauncherPrefs.SEARCH_WEB_APP` preference
- Inline `WEB_SEARCH` quick action removed from `deliverResults()` loop (replaced by FAB)

### Persistent Search Mode

- `AllAppsSearchBarController.afterTextChanged("")` sends `onSearchResult("", null)` instead of `clearSearchResult()`
- `AppsSearchContainerLayout.onSearchResult()` detects empty query → calls `showAppsWhileSearchActive()`
- `ActivityAllAppsContainerView.showAppsWhileSearchActive()` directly toggles visibility (search RV → GONE, apps container/header → VISIBLE) without animation, sets `mIsSearching = false`, keeps keyboard up and search bar focused
- Scroll-to-dismiss: `AllAppsRecyclerView.onScrollStateChanged()` checks if search bar is focused with empty text → calls `resetSearch()`; same logic in `SearchRecyclerView.onScrollStateChanged()`
- Back key dismissal unchanged: `AllAppsSearchBarController.onBackKey()` → `reset()` → full exit
- Typing again triggers `animateToSearchState(true)` crossfade back to search results

### Search Transition Animation Fix

- `SearchTransitionController.shouldAnimate()` overridden to return `true` for all views (not just app icons), ensuring all search result cards participate in the crossfade animation

### Search Settings Page

- New `SearchFragment` with `search_preferences.xml` hierarchy
- Two categories: "Search categories" (provider toggles) and "Search behavior" (web app chooser)
- Web app chooser shows installed browser/search apps via `PackageManager.queryIntentActivities()`

## Files Changed

| File | Change |
|------|--------|
| **NEW** `src/.../search/UniversalSearchAlgorithm.java` | Multi-provider search orchestrator |
| **NEW** `src/.../search/UniversalSearchAdapterProvider.java` | ViewHolder creation/binding for all result types |
| **NEW** `src/.../search/SearchFilters.java` | Filter chip state management |
| **NEW** `src/.../search/SearchResult.java` | Aggregated result container |
| **NEW** `src/.../search/SearchResultAdapterItem.java` | Extended AdapterItem for search results |
| **NEW** `src/.../search/providers/SearchProvider.java` | Provider interface |
| **NEW** `src/.../search/providers/AppSearchProvider.java` | App name/package matching |
| **NEW** `src/.../search/providers/ShortcutSearchProvider.java` | LauncherApps shortcut search |
| **NEW** `src/.../search/providers/QuickActionProvider.java` | Pattern detection (phone, email, URL, web) |
| **NEW** `src/.../search/providers/CalculatorProvider.java` | Math expression evaluation |
| **NEW** `src/.../search/providers/UnitConverterProvider.java` | Unit conversion |
| **NEW** `src/.../search/providers/ContactSearchProvider.java` | ContactsContract search |
| **NEW** `src/.../search/providers/CalendarSearchProvider.java` | CalendarContract search |
| **NEW** `src/.../search/providers/FileSearchProvider.java` | MediaStore file search |
| **NEW** `src/.../search/result/Launchable.java` | Launch interface |
| **NEW** `src/.../search/result/QuickAction.java` | Quick action data class |
| **NEW** `src/.../search/result/ShortcutResult.java` | Shortcut result data |
| **NEW** `src/.../search/result/ContactResult.java` | Contact result data |
| **NEW** `src/.../search/result/CalendarResult.java` | Calendar event data |
| **NEW** `src/.../search/result/FileResult.java` | File result data |
| **NEW** `src/.../search/result/CalculatorResult.java` | Calculator result data |
| **NEW** `src/.../search/result/UnitConversion.java` | Unit conversion data |
| **NEW** `src/.../views/SpringBounceEdgeEffectFactory.java` | Spring bounce overscroll effect |
| **NEW** `src/.../settings/SearchFragment.java` | Search settings page |
| **NEW** `res/xml/search_preferences.xml` | Search settings preference hierarchy |
| **NEW** `res/layout/search_filter_bar.xml` | Filter chip bar |
| **NEW** `res/layout/search_result_shortcut.xml` | Shortcut result card |
| **NEW** `res/layout/search_result_contact.xml` | Contact result card |
| **NEW** `res/layout/search_result_calendar.xml` | Calendar event card |
| **NEW** `res/layout/search_result_file.xml` | File result card |
| **NEW** `res/layout/search_result_quick_action.xml` | Quick action pill |
| **NEW** `res/layout/search_result_calculator.xml` | Calculator card |
| **NEW** `res/layout/search_result_unit_converter.xml` | Unit converter card |
| **NEW** `res/layout/search_section_header.xml` | Section header |
| **NEW** `res/layout/dialog_web_search_chooser.xml` | Web app chooser dialog |
| **NEW** `res/layout/item_web_search_app.xml` | Web app list item |
| **NEW** `res/drawable/ic_web_search.xml` | Web search icon |
| **NEW** `res/drawable/ic_calculate.xml` | Calculator icon |
| **NEW** `res/drawable/ic_calendar.xml` | Calendar icon |
| **NEW** `res/drawable/ic_call.xml` | Call icon |
| **NEW** `res/drawable/ic_contact.xml` | Contact icon |
| **NEW** `res/drawable/ic_email.xml` | Email icon |
| **NEW** `res/drawable/ic_file.xml` | File icon |
| **NEW** `res/drawable/ic_link.xml` | Link/URL icon |
| **NEW** `res/drawable/ic_message.xml` | Message icon |
| **NEW** `res/drawable/ic_settings_search.xml` | Search settings icon |
| **NEW** `res/drawable/ic_shortcut.xml` | Shortcut icon |
| **NEW** `res/drawable/circle_dot.xml` | Calendar color dot |
| **NEW** `res/color/chip_bg_filter.xml` | Filter chip background color state |
| `src/.../allapps/ActivityAllAppsContainerView.java` | Added FAB, `showAppsWhileSearchActive()`, spring overscroll factory |
| `src/.../allapps/BaseAllAppsAdapter.java` | Added `VIEW_TYPE_SEARCH_*` constants |
| `src/.../allapps/FloatingHeaderView.java` | Minor search-related adjustments |
| `src/.../allapps/SearchRecyclerView.java` | Added scroll-to-dismiss for empty search, disabled item animator |
| `src/.../allapps/AllAppsRecyclerView.java` | Added scroll-to-dismiss when search bar focused with empty text |
| `src/.../allapps/RecyclerViewAnimationController.java` | Animation fix for newly attached children during search transition |
| `src/.../allapps/SearchTransitionController.java` | Override `shouldAnimate()` for all result types |
| `src/.../allapps/search/AllAppsSearchBarController.java` | Persistent search: empty text sends `onSearchResult("", null)` |
| `src/.../allapps/search/AppsSearchContainerLayout.java` | Routes empty query to `showAppsWhileSearchActive()`, calls `updateSearchOnlineFab()` |
| `src/.../allapps/search/AllAppsSearchUiDelegate.java` | Returns `UniversalSearchAdapterProvider` |
| `src/.../views/SpringRelativeLayout.java` | Added `createSpringBounceEdgeEffectFactory()` method |
| `src/.../LauncherPrefs.kt` | Added `SEARCH_*` preference keys |
| `res/values/strings.xml` | Added search-related strings |
| `res/xml/launcher_preferences.xml` | Added search settings entry |
| `build.gradle` | (no new deps — `androidx.dynamicanimation` already present) |
| `AndroidManifest-common.xml` | Registered `SearchFragment` permissions |
