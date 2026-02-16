# Search System

Universal multi-source search for the all-apps drawer, inspired by [Kvaesitso](https://github.com/MM2-0/Kvaesitso)'s multi-source architecture.

## Overview

The search system replaces AOSP's app-only search with a universal search that queries multiple providers in parallel (apps, shortcuts, contacts, calendar, files, calculator, unit converter) and displays categorized results in a single scrollable list with filter chips. A floating "Web search" FAB appears above the keyboard for quick web lookups.

The search UX has three distinct visual states:

1. **Idle** — A-Z app list visible, search bar unfocused
2. **Active with empty text** — A-Z app list visible, search bar focused, keyboard up
3. **Active with results** — Search results RV visible, filter bar + categorized results, "Web search" FAB shown

---

## Architecture

### Data Flow

```
User types → AllAppsSearchBarController.afterTextChanged()
           → UniversalSearchAlgorithm.doSearch()
           → dispatches to N providers in parallel
           → each provider calls back with results
           → onProviderComplete() decrements counter
           → when all providers done → deliverResults()
           → converts SearchResult → ArrayList<AdapterItem>
           → SearchCallback.onSearchResult(query, items)
           → AppsSearchContainerLayout.onSearchResult()
           → ActivityAllAppsContainerView.setSearchResults()
           → animateToSearchState(true) [crossfade]
```

### Package Structure

```
com.android.launcher3.search/
├── SearchAlgorithm.java          # Interface: doSearch(), cancel(), destroy()
├── SearchCallback.java           # Interface: onSearchResult(), clearSearchResult()
├── SearchFilters.java            # Filter chips state (EnumSet<Category>)
├── SearchResult.java             # Aggregated container for all result categories
├── SearchResultAdapterItem.java  # Extended AdapterItem for non-app results
├── StringMatcherUtility.java     # AOSP fuzzy string matching
├── UniversalSearchAlgorithm.java # Orchestrator: dispatches to providers, aggregates
├── UniversalSearchAdapterProvider.java  # Creates/binds ViewHolders for result types
├── providers/
│   ├── SearchProvider.java       # Interface: search(), cancel(), category()
│   ├── AppSearchProvider.java    # App name/package matching
│   ├── ShortcutSearchProvider.java    # LauncherApps shortcut search
│   ├── QuickActionProvider.java       # Pattern detection (phone, email, URL, web search)
│   ├── CalculatorProvider.java        # Math expression evaluation
│   ├── UnitConverterProvider.java     # Unit conversion (length, weight, temp, etc.)
│   ├── ContactSearchProvider.java     # ContactsContract lookup
│   ├── CalendarSearchProvider.java    # CalendarContract event search
│   └── FileSearchProvider.java        # MediaStore file search
└── result/
    ├── Launchable.java           # Interface: launch(Context) → boolean
    ├── QuickAction.java          # Type enum + label + icon + intent
    ├── ShortcutResult.java       # Shortcut info + icon loading
    ├── ContactResult.java        # Contact data + photo URI
    ├── CalendarResult.java       # Event title + time + location + color
    ├── FileResult.java           # File name + path + size + MIME
    ├── CalculatorResult.java     # Expression + result + hex/oct formats
    └── UnitConversion.java       # Input value/unit + list of conversions
```

### AOSP Integration Points

The search system hooks into AOSP's existing search infrastructure at these points:

| AOSP Class | Role | Our Changes |
|------------|------|-------------|
| [`AllAppsSearchBarController`](../src/com/android/launcher3/allapps/search/AllAppsSearchBarController.java) | Text watcher, dispatches to `SearchAlgorithm` | Modified `afterTextChanged()` for persistent search mode |
| [`AppsSearchContainerLayout`](../src/com/android/launcher3/allapps/search/AppsSearchContainerLayout.java) | `SearchUiManager` + `SearchCallback` impl | Routes results to container; handles empty-query → apps |
| [`ActivityAllAppsContainerView`](../src/com/android/launcher3/allapps/ActivityAllAppsContainerView.java) | Container managing search/apps RV visibility | Added FAB, `showAppsWhileSearchActive()`, spring overscroll |
| [`SearchTransitionController`](../src/com/android/launcher3/allapps/SearchTransitionController.java) | Crossfade animation between A-Z and search | Overrides `shouldAnimate()` to animate all items (not just app icons) |
| [`BaseAllAppsAdapter`](../src/com/android/launcher3/allapps/BaseAllAppsAdapter.java) | View type constants | Added `VIEW_TYPE_SEARCH_*` constants for all result types |
| [`AllAppsSearchUiDelegate`](../src/com/android/launcher3/allapps/search/AllAppsSearchUiDelegate.java) | Factory for search adapter provider | Returns `UniversalSearchAdapterProvider` |

---

## Search Providers

### Interface

```java
public interface SearchProvider<T> {
    void search(String query, Consumer<List<T>> callback);
    void cancel();
    String category();
    default int minQueryLength() { return 1; }
}
```

Each provider runs its search on a background thread (or synchronously if cheap) and delivers results via the callback on the main thread. The `category()` string maps to both the `SearchFilters.Category` enum (for filter chips) and the `LauncherPrefs.SEARCH_*` preference keys (for settings toggles).

### Built-in Providers

| Provider | Category | Min Query | Result Type | Data Source |
|----------|----------|-----------|-------------|-------------|
| `AppSearchProvider` | apps | 1 | `AppInfo` | `AllAppsStore` via `StringMatcherUtility` |
| `ShortcutSearchProvider` | shortcuts | 2 | `ShortcutResult` | `LauncherApps.getShortcuts()` |
| `QuickActionProvider` | quick_actions | 1 | `QuickAction` | Pattern detection (regex for phone/email/URL) |
| `CalculatorProvider` | calculator | 1 | `CalculatorResult` | `javax.script.ScriptEngine` (Rhino) |
| `UnitConverterProvider` | unit_converter | 2 | `UnitConversion` | Regex parsing + conversion tables |
| `ContactSearchProvider` | contacts | 2 | `ContactResult` | `ContactsContract.Contacts` |
| `CalendarSearchProvider` | calendar | 2 | `CalendarResult` | `CalendarContract.Events` (next 30 days) |
| `FileSearchProvider` | files | 3 | `FileResult` | `MediaStore.Files` |

### Provider Enable/Disable

Each provider (except `quick_actions`) can be toggled on/off from the Search settings page (`SearchFragment` → `search_preferences.xml`). The preference keys map directly:

| Provider | Preference Key | Default |
|----------|---------------|---------|
| Apps | `pref_search_apps` | `true` |
| Shortcuts | `pref_search_shortcuts` | `true` |
| Contacts | `pref_search_contacts` | `false` |
| Calendar | `pref_search_calendar` | `false` |
| Files | `pref_search_files` | `false` |
| Calculator | `pref_search_calculator` | `true` |
| Unit Converter | `pref_search_unit_converter` | `true` |

Contacts, Calendar, and Files default to `false` because they require runtime permissions.

---

## Result Aggregation (`UniversalSearchAlgorithm`)

**File:** [`UniversalSearchAlgorithm.java`](../src/com/android/launcher3/search/UniversalSearchAlgorithm.java)

The algorithm:

1. Cancels any previous search
2. Creates a fresh `SearchResult` container
3. Counts how many providers will run (respecting `minQueryLength` + pref toggles)
4. Sets `mPendingProviders` to that count
5. Dispatches all enabled providers in parallel
6. Each provider callback decrements `mPendingProviders`
7. When `mPendingProviders` reaches 0 → `deliverResults()`

### `deliverResults()` — Result Ordering

Results are converted to `AdapterItem` list in this fixed order:

1. **Filter bar** (always first)
2. **Quick actions** (call, email, URL — but NOT web search, which is now the FAB)
3. **Calculator** (if `TOOLS` filter active)
4. **Unit converter** (if `TOOLS` filter active)
5. **Apps** with "Apps" section header (if `APPS` filter active)
6. **Shortcuts** with "Shortcuts" section header (if `SHORTCUTS` filter active)
7. **Contacts** with "Contacts" section header (if `CONTACTS` filter active)
8. **Calendar** with "Calendar" section header (if `CALENDAR` filter active)
9. **Files** with "Files" section header (if `FILES` filter active)
10. **Empty state** (if only filter bar present — i.e., no results)

The `WEB_SEARCH` quick action type is skipped in the delivery loop because it's replaced by the floating "Web search" FAB (see below).

### Filter Chips (`SearchFilters`)

**File:** [`SearchFilters.java`](../src/com/android/launcher3/search/SearchFilters.java)

The filter bar uses M3 Filter Chips in a horizontal scroll. State is tracked by `SearchFilters`:

- `mShowAll = true` → all categories visible ("All" chip selected)
- `mShowAll = false` → only `mSelected` categories visible
- Toggling a category adds/removes it from the set
- When the set empties → reverts to "All"
- Filter changes call `OnFilterChangedListener` → `deliverResults()` re-runs with current `SearchResult` (no new provider queries)

---

## Search Transition Animation

**File:** [`SearchTransitionController.java`](../src/com/android/launcher3/allapps/SearchTransitionController.java) (extends [`RecyclerViewAnimationController`](../src/com/android/launcher3/allapps/RecyclerViewAnimationController.java))

The transition between A-Z apps and search results is a crossfade animation:

- **Entering search** (`goingToSearch = true`): Search RV items scale from 0 to 1 (height), A-Z container translates down and fades out
- **Exiting search** (`goingToSearch = false`): Reverse — search items collapse, A-Z slides back up

Key override: `shouldAnimate()` returns `true` for all views (not just app icons as in AOSP), so all search result types (cards, headers, filter bar) participate in the animation.

The animation uses `DECELERATE_1_7` interpolator when already in all-apps, or `INSTANT` when transitioning to all-apps simultaneously. Duration is 300ms.

### Transition State Machine

```
                    ┌──────────────────────┐
                    │   IDLE (A-Z visible)  │
                    │   mIsSearching=false  │
                    └──────┬───────────────┘
                           │ setSearchResults(items)
                           │ → animateToSearchState(true)
                           ▼
                    ┌──────────────────────┐
                    │  SEARCHING (results)  │
                    │   mIsSearching=true   │
                    └──────┬───────────────┘
                           │ backspace to empty
                           │ → showAppsWhileSearchActive()
                           ▼
                    ┌──────────────────────┐
                    │ ACTIVE-EMPTY (A-Z     │
                    │  visible, keyboard    │
                    │  up, search focused)  │
                    │  mIsSearching=false   │
                    └──────┬───────┬───────┘
                           │       │ type text
                           │       │ → doSearch() → setSearchResults()
                           │       │ → animateToSearchState(true)
                           │       └──────► back to SEARCHING
                           │ back key / scroll
                           │ → resetSearch()
                           ▼
                    ┌──────────────────────┐
                    │   IDLE (A-Z visible)  │
                    └──────────────────────┘
```

---

## Persistent Search Mode

When the user backspaces to empty text, the search bar stays active instead of dismissing. This allows quick query refinement without re-tapping the search bar.

### How It Works

1. **`AllAppsSearchBarController.afterTextChanged("")`** — Cancels search, calls `mCallback.onSearchResult("", null)` (null items = signal for "show apps")

2. **`AppsSearchContainerLayout.onSearchResult("", null)`** — Detects empty query and calls `mAppsView.showAppsWhileSearchActive()` instead of `setSearchResults()`

3. **`ActivityAllAppsContainerView.showAppsWhileSearchActive()`** — Directly toggles visibility without animation:
   - Search RV → `GONE`
   - Apps container → `VISIBLE` (with `translationY=0`, `alpha=1`)
   - Header → `VISIBLE` (with `translationY=0`, `alpha=1`)
   - Fast scroller → `VISIBLE`
   - `mIsSearching = false`
   - Does NOT call `animateToSearchState()` (which would trigger full exit, resetting search bar)

4. **Dismissing search** from this state:
   - **Back key**: `AllAppsSearchBarController.onBackKey()` → `reset()` → `clearSearchResult()` → full exit
   - **Scrolling A-Z list**: `AllAppsRecyclerView.onScrollStateChanged(SCROLL_STATE_DRAGGING)` detects focused search bar with empty text → calls `resetSearch()`
   - **Scrolling search RV**: Same logic in `SearchRecyclerView.onScrollStateChanged()`

5. **Typing again**: `afterTextChanged("a")` → `doSearch()` → providers return results → `onSearchResult(query, items)` → `setSearchResults(items)` → `animateToSearchState(true)` — the full crossfade animation runs because `mIsSearching` is `false`

---

## "Web Search" FAB

A floating `ExtendedFloatingActionButton` that appears above the keyboard when search has a non-empty query.

### Creation

Created programmatically in `ActivityAllAppsContainerView.initContent()`:

- **Theme**: Uses `ContextThemeWrapper(context, R.style.HomeSettings_Theme)` + `DynamicColors.wrapContextIfAvailable()` — same M3 themed context as search result cards in `UniversalSearchAdapterProvider`
- **Elevation**: `0dp` (M3 tonal elevation — the container color provides the elevation cue, no shadow)
- **Position**: `ALIGN_PARENT_BOTTOM | ALIGN_PARENT_END` with 16dp margins
- **Icon**: `R.drawable.ic_web_search`
- **Text**: "Web search" (`R.string.search_online`)

### Show/Hide Logic (`updateSearchOnlineFab`)

```
show = query != null && !query.isEmpty()
       && (isSearching() || searchTransitionController.isRunning())
```

- **Show**: Scale-in animation (scaleX/Y 0→1, 200ms)
- **Hide**: Scale-out animation (scaleX/Y 1→0, 150ms, then `GONE`)
- Called from `AppsSearchContainerLayout.onSearchResult()` with the current query
- Called with `null` from `onClearSearchResult()` (full search exit)

### Keyboard Positioning

In `ActivityAllAppsContainerView.dispatchApplyWindowInsets()`:

```java
int imeBottom = insets.getInsets(WindowInsets.Type.ime()).bottom;
int navBottom = insets.getInsets(WindowInsets.Type.navigationBars()).bottom;
fabLp.bottomMargin = Math.max(imeBottom, navBottom) + 16dp;
```

The FAB repositions automatically when the keyboard appears/disappears.

### Web Search Intent

`launchWebSearch()` reads `LauncherPrefs.SEARCH_WEB_APP` preference:
- `"default"` → system default `ACTION_WEB_SEARCH` handler
- Component string → sets explicit component on the intent

The intent uses `SearchManager.QUERY` extra with the current search text. This is the same logic as `QuickActionProvider.buildWebSearchIntent()` — the inline web search card was removed from results (the `WEB_SEARCH` type is skipped in `deliverResults()`).

---

## Spring Bounce Overscroll

Replaces the default platform stretch overscroll effect on the all-apps RecyclerViews with M3 Expressive spring-physics bounce.

### Implementation

**File:** [`SpringBounceEdgeEffectFactory.java`](../src/com/android/launcher3/views/SpringBounceEdgeEffectFactory.java)

A custom `RecyclerView.EdgeEffectFactory` that creates `SpringBounceEdgeEffect` instances for both top and bottom directions. The effect uses `translationY` on the RecyclerView instead of canvas-based drawing.

### Spring Parameters

| Parameter | Value | M3 Reference |
|-----------|-------|--------------|
| Stiffness | 300f | `motionSpringSlowSpatial` |
| Damping ratio | 0.9f | Under-damped (slight oscillation) |
| Max overscroll | 36dp | Capped with cubic damping |

### How It Works

- **`onPull(deltaDistance)`** — Cancels any running spring, accumulates pull distance, applies cubic-damped `translationY` to the RecyclerView
- **`onRelease()`** — Starts `SpringAnimation` from current offset back to 0
- **`onAbsorb(velocity)`** — Starts `SpringAnimation` with the fling velocity (converts to spring initial velocity)
- **`draw()`** — Returns `false` (no canvas rendering — all visual feedback is via `translationY`)

### Cubic Damping Curve

```java
float damped = 1 - (1 - clamped)^3;  // clamped ∈ [0, 1]
return damped * maxOverscrollPx * direction;
```

This ensures large pulls have diminishing returns — the first 50% of pull gives ~87.5% of the max offset, making the overscroll feel physically natural.

### Integration

`SpringRelativeLayout.createSpringBounceEdgeEffectFactory(RecyclerView)` creates the factory. Called in `AdapterHolder.setup()` for both MAIN and SEARCH RecyclerViews, replacing the previous `createEdgeEffectFactory()` which used canvas-based `EdgeEffect` proxied to the parent.

The previous `createEdgeEffectFactory()` (AOSP's `ProxyEdgeEffectFactory`) is preserved for backward compatibility but no longer used by the all-apps RVs.

---

## View Type Constants

All search-specific view types are defined in `BaseAllAppsAdapter`:

| Constant | Value | Layout | Description |
|----------|-------|--------|-------------|
| `VIEW_TYPE_SEARCH_FILTER_BAR` | 200 | `search_filter_bar.xml` | Horizontal chip bar (All, Apps, Shortcuts, etc.) |
| `VIEW_TYPE_SEARCH_SECTION_HEADER` | 201 | `search_section_header.xml` | Category title ("Apps", "Contacts", etc.) |
| `VIEW_TYPE_SEARCH_SHORTCUT` | 202 | `search_result_shortcut.xml` | Shortcut card with icon + app name |
| `VIEW_TYPE_SEARCH_CONTACT` | 203 | `search_result_contact.xml` | Contact card with avatar + call/message buttons |
| `VIEW_TYPE_SEARCH_CALENDAR` | 204 | `search_result_calendar.xml` | Calendar event with color dot + time + location |
| `VIEW_TYPE_SEARCH_FILE` | 205 | `search_result_file.xml` | File with icon + size + path |
| `VIEW_TYPE_SEARCH_QUICK_ACTION` | 206 | `search_result_quick_action.xml` | Quick action pill (call, email, open URL) |
| `VIEW_TYPE_SEARCH_CALCULATOR` | 207 | `search_result_calculator.xml` | Calculator with expression + result + alt formats |
| `VIEW_TYPE_SEARCH_UNIT_CONVERTER` | 208 | `search_result_unit_converter.xml` | Unit conversion with input + output values |

---

## Theming

The Launcher activity uses a platform theme (not AppCompat/MaterialComponents). All Material Components used in search results (Chips, MaterialCardView) require wrapping the context:

```java
Context themed = new ContextThemeWrapper(context, R.style.HomeSettings_Theme);
themed = DynamicColors.wrapContextIfAvailable(themed);
```

This is done in:
- `UniversalSearchAdapterProvider.onCreateViewHolder()` for all search result card layouts
- `ActivityAllAppsContainerView.initContent()` for the "Web search" FAB

Search result cards use `?attr/colorSecondaryContainer` / `?attr/colorOnSecondaryContainer` for M3 tonal surfaces. Card elevation is `0dp` (tonal elevation only).

---

## Settings Integration

**Settings page:** `SearchFragment` → `search_preferences.xml`

**Preference keys (in `LauncherPrefs.kt`):**

| Key | Type | Default | Purpose |
|-----|------|---------|---------|
| `pref_search_apps` | `boolean` | `true` | Enable app search |
| `pref_search_shortcuts` | `boolean` | `true` | Enable shortcut search |
| `pref_search_contacts` | `boolean` | `false` | Enable contact search |
| `pref_search_calendar` | `boolean` | `false` | Enable calendar search |
| `pref_search_files` | `boolean` | `false` | Enable file search |
| `pref_search_calculator` | `boolean` | `true` | Enable calculator |
| `pref_search_unit_converter` | `boolean` | `true` | Enable unit converter |
| `pref_search_web_app` | `String` | `"default"` | Preferred web search app component |
| `pref_drawer_search_bg_color` | `String` | `""` | Custom search bar background color |

---

## Key Files Reference

| File | Purpose |
|------|---------|
| `src/.../search/UniversalSearchAlgorithm.java` | Orchestrates parallel provider dispatch and result aggregation |
| `src/.../search/UniversalSearchAdapterProvider.java` | ViewHolder creation/binding for all search result types |
| `src/.../search/SearchFilters.java` | Filter chip state management |
| `src/.../search/SearchResult.java` | Aggregated result container |
| `src/.../search/SearchResultAdapterItem.java` | Extended `AdapterItem` for search results |
| `src/.../search/providers/SearchProvider.java` | Provider interface |
| `src/.../search/providers/QuickActionProvider.java` | Pattern detection (phone, email, URL, web search) |
| `src/.../search/result/QuickAction.java` | Quick action data class with Type enum |
| `src/.../allapps/search/AllAppsSearchBarController.java` | Text watcher, drives persistent search mode |
| `src/.../allapps/search/AppsSearchContainerLayout.java` | Search UI manager, routes results to container |
| `src/.../allapps/ActivityAllAppsContainerView.java` | Container: FAB, `showAppsWhileSearchActive()`, overscroll |
| `src/.../allapps/SearchTransitionController.java` | A-Z ↔ search crossfade animation |
| `src/.../allapps/SearchRecyclerView.java` | Search RV with scroll-to-dismiss |
| `src/.../allapps/AllAppsRecyclerView.java` | A-Z RV with scroll-to-dismiss (when search active) |
| `src/.../views/SpringBounceEdgeEffectFactory.java` | M3 spring bounce overscroll effect |
| `src/.../views/SpringRelativeLayout.java` | Factory method for spring bounce |
| `src/.../settings/SearchFragment.java` | Search settings page |
| `res/xml/search_preferences.xml` | Search settings preference hierarchy |
| `res/layout/search_filter_bar.xml` | Filter chip bar layout |
| `res/layout/search_result_*.xml` | Result type layouts |
