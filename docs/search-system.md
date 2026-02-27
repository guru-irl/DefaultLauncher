# Search System

Universal multi-source search for the all-apps drawer, inspired by [Kvaesitso](https://github.com/MM2-0/Kvaesitso)'s multi-source architecture.

## Overview

The search system replaces AOSP's app-only search with a universal search that queries multiple providers in parallel (apps, shortcuts, contacts, calendar, files, calculator, unit converter, timezone) and displays categorized results in a single scrollable list with filter chips. Two floating FABs appear above the keyboard: an AI search FAB (icon-only, sends query to ChatGPT/Claude/etc.) and a web search Extended FAB for quick web lookups.

The search UX has three distinct visual states:

1. **Idle** — A-Z app list visible, search bar unfocused
2. **Active with empty text** — A-Z app list visible, search bar focused, keyboard up
3. **Active with results** — Search results RV visible, filter bar + categorized results, "Web search" FAB shown

---

## Architecture

### Data Flow

```
User types → AllAppsSearchBarController.afterTextChanged()
           → 150ms debounce (cancelled on new input)
           → UniversalSearchAlgorithm.doSearch()
           → dispatches apps provider + N I/O providers in parallel
           → app provider completes first → deliverResults(INTERMEDIATE)
           → remaining providers complete → deliverResults(FINAL)
           → converts SearchResult → ArrayList<AdapterItem>
           → SearchCallback.onSearchResult(query, items, resultCode)
           → AppsSearchContainerLayout.onSearchResult()
           → ActivityAllAppsContainerView.setSearchResults()
           → SearchItemAnimator animates new items (fade+slide-up)
           → animateToSearchState(true) [crossfade]
```

### Package Structure

```
com.android.launcher3.search/
├── SearchAlgorithm.java          # Interface: doSearch(), cancel(), destroy()
├── SearchCallback.java           # Interface: onSearchResult(), clearSearchResult()
├── SearchFilters.java            # Filter chips state (EnumSet<Category>) + version counter
├── SearchResult.java             # Aggregated container for all result categories
├── SearchResultAdapterItem.java  # Extended AdapterItem for non-app results
├── SearchScorer.java             # Jaro-Winkler scoring with prefix/substring bonuses
├── StringMatcherUtility.java     # AOSP fuzzy string matching (legacy)
├── UniversalSearchAlgorithm.java # Orchestrator: dispatches to providers, aggregates
├── UniversalSearchAdapterProvider.java  # Creates/binds ViewHolders for result types
├── providers/
│   ├── SearchProvider.java       # Interface: search(), cancel(), category()
│   ├── AppSearchProvider.java    # App name matching (Jaro-Winkler scored)
│   ├── ShortcutSearchProvider.java    # LauncherApps shortcut search
│   ├── QuickActionProvider.java       # Pattern detection (phone, email, URL, web search)
│   ├── CalculatorProvider.java        # Math expression evaluation
│   ├── UnitConverterProvider.java     # Unit conversion (length, weight, temp, etc.)
│   ├── TimezoneProvider.java          # Timezone conversion ("5pm India to Chicago", "4pm chicago time tue")
│   ├── TimezoneResolver.java          # Auto-generated ~650+ zone lookups (IANA/ICU/Locale)
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
    ├── UnitConversion.java       # Input value/unit + list of conversions
    └── TimezoneResult.java       # Source/target time + zone names + date
```

### AOSP Integration Points

The search system hooks into AOSP's existing search infrastructure at these points:

| AOSP Class | Role | Our Changes |
|------------|------|-------------|
| [`AllAppsSearchBarController`](../src/com/android/launcher3/allapps/search/AllAppsSearchBarController.java) | Text watcher, dispatches to `SearchAlgorithm` | 150ms debounce, persistent search mode, immediate cancel on new input |
| [`AppsSearchContainerLayout`](../src/com/android/launcher3/allapps/search/AppsSearchContainerLayout.java) | `SearchUiManager` + `SearchCallback` impl | Routes results to container; handles empty-query → apps |
| [`ActivityAllAppsContainerView`](../src/com/android/launcher3/allapps/ActivityAllAppsContainerView.java) | Container managing search/apps RV visibility | AI FAB + web FAB, `showAppsWhileSearchActive()`, spring overscroll |
| [`SearchTransitionController`](../src/com/android/launcher3/allapps/SearchTransitionController.java) | Transition animation between A-Z and search | Slide-up for content items, scaleY for filter bar |
| [`BaseAllAppsAdapter`](../src/com/android/launcher3/allapps/BaseAllAppsAdapter.java) | View type constants + DiffUtil identity | Added `VIEW_TYPE_SEARCH_*` constants; `isSameAs()` compares by component for apps |
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
| `AppSearchProvider` | apps | 1 | `AppInfo` | `AllAppsStore` via `SearchScorer` (Jaro-Winkler) |
| `ShortcutSearchProvider` | shortcuts | 2 | `ShortcutResult` | `LauncherApps.getShortcuts()` |
| `QuickActionProvider` | quick_actions | 1 | `QuickAction` | Pattern detection (regex for phone/email/URL) |
| `CalculatorProvider` | calculator | 1 | `CalculatorResult` | `javax.script.ScriptEngine` (Rhino) |
| `UnitConverterProvider` | unit_converter | 2 | `UnitConversion` | Regex parsing + conversion tables |
| `TimezoneProvider` | timezone | 6 | `TimezoneResult` | Regex parsing + `java.time` APIs |
| `ContactSearchProvider` | contacts | 2 | `ContactResult` | `ContactsContract.Contacts` |
| `CalendarSearchProvider` | calendar | 2 | `CalendarResult` | `CalendarContract.Events` (next 365 days) |
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
| Timezone | `pref_search_timezone` | `true` |

Contacts, Calendar, and Files default to `false` because they require runtime permissions.

---

## Result Aggregation (`UniversalSearchAlgorithm`)

**File:** [`UniversalSearchAlgorithm.java`](../src/com/android/launcher3/search/UniversalSearchAlgorithm.java)

The algorithm:

1. Cancels any previous search
2. Creates a fresh `SearchResult` container
3. Counts how many providers will run (respecting `minQueryLength` + pref toggles)
4. Sets `mPendingProviders` to that count
5. Dispatches app provider and I/O providers in parallel
6. App provider completes → `deliverResults(INTERMEDIATE)` (users see apps immediately)
7. Each I/O provider callback decrements `mPendingProviders`
8. When `mPendingProviders` reaches 0 → `deliverResults(FINAL)`

Progressive delivery ensures users see app results instantly while I/O-bound providers (contacts, calendar, files, shortcuts) run in parallel on `THREAD_POOL_EXECUTOR`.

### `deliverResults()` — Result Ordering

Results are converted to `AdapterItem` list in this fixed order:

1. **Filter bar** (always first)
2. **Quick actions** (call, email, URL — but NOT web search, which is now the FAB)
3. **Calculator** (if `TOOLS` filter active)
4. **Unit converter** (if `TOOLS` filter active)
5. **Timezone** (if `TOOLS` filter active)
6. **Apps** with "Apps" section header (if `APPS` filter active)
7. **Shortcuts** with "Shortcuts" section header (if `SHORTCUTS` filter active)
8. **Contacts** with "Contacts" section header (if `CONTACTS` filter active)
9. **Calendar** with "Calendar" section header (if `CALENDAR` filter active)
10. **Files** with "Files" section header (if `FILES` filter active)
11. **Empty state** (only on `FINAL` delivery if no results)

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

The transition between A-Z apps and search results uses two different animations depending on the item type:

- **Filter bar**: scaleY "unsqueeze from top" (pivotY=0), matching the base class behavior
- **Content items**: translationY slide-up + staggered fade, matching `SearchItemAnimator`'s visual so the entry and exit animations are consistent

Key behaviors:
- **Entering search** (`goingToSearch = true`): Content items slide up from 16dp offset while fading in, A-Z container translates down and fades out
- **Exiting search** (`goingToSearch = false`): Reverse — content slides down while fading out, A-Z slides back up
- Uses `EMPHASIZED` interpolator when already in all-apps, or `INSTANT` when transitioning to all-apps simultaneously. Duration is 300ms.

### Search Item Animator

**File:** [`SearchItemAnimator.java`](../src/com/android/launcher3/allapps/SearchItemAnimator.java)

Custom `DefaultItemAnimator` for the search RecyclerView. Handles DiffUtil-dispatched animations for progressive result delivery:

| Operation | Behavior | Duration |
|-----------|----------|----------|
| ADD | Fade-in + 16dp slide-up | 200ms, EMPHASIZED_DECELERATE |
| MOVE | Fade-in + 16dp slide-up (same as add) | 200ms, EMPHASIZED_DECELERATE |
| REMOVE | Instant | 0ms |
| CHANGE | Instant | 0ms |

Structural items (filter bar, section headers) never animate. The `setAnimationsEnabled(false)` method suppresses add animations during `INTERMEDIATE` result delivery to prevent overlap, then re-enables for `FINAL` delivery.

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

## Search FABs

Two floating action buttons appear above the keyboard when search has a non-empty query, stacked vertically in a `LinearLayout` container (AI FAB above, web search Extended FAB below).

### FAB Container

Created programmatically in `ActivityAllAppsContainerView.initContent()`:

- **Theme**: Uses `ContextThemeWrapper(context, R.style.HomeSettings_Theme)` + `DynamicColors.wrapContextIfAvailable()`
- **Position**: `ALIGN_PARENT_BOTTOM | ALIGN_PARENT_END` with 16dp margins
- **Gravity**: `END | BOTTOM` (AI FAB right-aligned with Extended FAB's right edge)
- **Spacing**: 16dp between FABs
- **Elevation**: M3 default 6dp with state animations (pressed/hovered)

### AI Search FAB

A 56dp icon-only `FloatingActionButton` with M3 tertiary container colors:

- **Icon**: `R.drawable.ic_ai_search` (Material Symbols sparkle)
- **Colors**: `colorTertiaryContainer` background, `colorOnTertiaryContainer` icon tint
- **Visibility**: Shown only when an AI app is installed (auto-detected or configured in settings)
- **Auto-detection**: Checks `LauncherPrefs.SEARCH_AI_APP` preference, then falls through to `AI_APP_PACKAGES` array (ChatGPT, Claude, Perplexity, Gemini) in priority order
- **Re-evaluation**: `loadAiAppIcon()` runs when the FAB container transitions from GONE→VISIBLE, covering app install/uninstall between search sessions
- **Launch**: Sends `ACTION_SEND` with `text/plain` EXTRA_TEXT to the AI app; falls back to the app's main activity if no send handler

### Web Search Extended FAB

A standard `ExtendedFloatingActionButton` (56dp height, text+icon):

- **Icon**: `R.drawable.ic_web_search`
- **Text**: "Web search" (`R.string.search_online`)
- **Shape**: M3 default rounded rectangle (not pill)

### Show/Hide Logic (`updateSearchFabs`)

```
show = query != null && !query.isEmpty()
       && (isSearching() || searchTransitionController.isRunning())
```

- **Show**: Scale-in animation (scaleX/Y 0→1, 200ms, EMPHASIZED_DECELERATE)
- **Hide**: Scale-out animation (scaleX/Y 1→0, 200ms, EMPHASIZED_ACCELERATE, then `GONE`)
- Called from `AppsSearchContainerLayout.onSearchResult()` with the current query
- Called with `null` from `onClearSearchResult()` (full search exit)

### Keyboard Positioning

In `ActivityAllAppsContainerView.dispatchApplyWindowInsets()`:

```java
int imeBottom = insets.getInsets(WindowInsets.Type.ime()).bottom;
int navBottom = insets.getInsets(WindowInsets.Type.navigationBars()).bottom;
fabLp.bottomMargin = Math.max(imeBottom, navBottom) + 16dp;
```

The FAB container repositions automatically when the keyboard appears/disappears.

### Web Search Intent

`launchWebSearch()` reads `LauncherPrefs.SEARCH_WEB_APP` preference:
- `"default"` → system default `ACTION_WEB_SEARCH` handler
- Component string → sets explicit component on the intent

The intent uses `SearchManager.QUERY` extra with the current search text.

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
| `VIEW_TYPE_SEARCH_TIMEZONE` | 209 | `search_result_timezone.xml` | Timezone conversion with source/target times |
| `VIEW_TYPE_SEARCH_LOADING` | 210 | `search_loading_indicator.xml` | M3 loading indicator (reserved) |

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
| `pref_search_timezone` | `boolean` | `true` | Enable timezone conversion |
| `pref_search_web_app` | `String` | `"default"` | Preferred web search app component |
| `pref_search_ai_app` | `String` | `""` | AI app package (empty=auto-detect, "none"=disabled) |
| `pref_drawer_search_bg_color` | `String` | `""` | Custom search bar background color |

---

## Key Files Reference

| File | Purpose |
|------|---------|
| `src/.../search/UniversalSearchAlgorithm.java` | Orchestrates parallel provider dispatch and progressive result delivery |
| `src/.../search/UniversalSearchAdapterProvider.java` | ViewHolder creation/binding for all search result types |
| `src/.../search/SearchFilters.java` | Filter chip state management with version counter |
| `src/.../search/SearchResult.java` | Aggregated result container |
| `src/.../search/SearchResultAdapterItem.java` | Extended `AdapterItem` for search results |
| `src/.../search/SearchScorer.java` | Jaro-Winkler scoring with prefix/substring bonuses |
| `src/.../search/providers/SearchProvider.java` | Provider interface |
| `src/.../search/providers/QuickActionProvider.java` | Pattern detection (phone, email, URL, web search) |
| `src/.../search/providers/TimezoneProvider.java` | Timezone conversion, timed-place, and current-time queries |
| `src/.../search/providers/TimezoneResolver.java` | Auto-generated ~650+ zone lookups from IANA/ICU/Locale APIs |
| `src/.../search/result/QuickAction.java` | Quick action data class with Type enum |
| `src/.../search/result/TimezoneResult.java` | Timezone result data class |
| `src/.../allapps/search/AllAppsSearchBarController.java` | Text watcher with 150ms debounce, drives persistent search mode |
| `src/.../allapps/search/AppsSearchContainerLayout.java` | Search UI manager, routes results to container |
| `src/.../allapps/ActivityAllAppsContainerView.java` | Container: AI FAB + web FAB, `showAppsWhileSearchActive()`, overscroll |
| `src/.../allapps/SearchItemAnimator.java` | Custom item animator (fade+slide for adds/moves, instant removes) |
| `src/.../allapps/SearchTransitionController.java` | A-Z ↔ search transition (slide-up for content, scaleY for filter bar) |
| `src/.../allapps/SearchRecyclerView.java` | Search RV with scroll-to-dismiss and search animator |
| `src/.../allapps/AllAppsRecyclerView.java` | A-Z RV with scroll-to-dismiss (when search active) |
| `src/.../views/SpringBounceEdgeEffectFactory.java` | M3 spring bounce overscroll effect |
| `src/.../views/SpringRelativeLayout.java` | Factory method for spring bounce |
| `src/.../settings/SearchFragment.java` | Search settings page (web app + AI app choosers) |
| `res/xml/search_preferences.xml` | Search settings preference hierarchy |
| `res/layout/search_filter_bar.xml` | Filter chip bar layout |
| `res/layout/search_result_*.xml` | Result type layouts |
