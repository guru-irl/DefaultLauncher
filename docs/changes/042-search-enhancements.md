# 042: Search Enhancements

## Summary

Major search system improvements: adds timezone conversion provider, AI search FAB with auto-detection, Jaro-Winkler fuzzy scoring for app search, progressive result delivery with debounced input, and animated search result transitions. Also fixes M3 compliance for the web search FAB and the AI icon's light mode visibility in settings.

## New Files

| File | Purpose |
|------|---------|
| `src/.../search/SearchScorer.java` | Jaro-Winkler similarity scoring with prefix/substring bonuses (replaces `StringMatcherUtility` for apps) |
| `src/.../search/providers/TimezoneProvider.java` | Parses timezone queries ("5pm India to Chicago", "time in Tokyo") using java.time APIs |
| `src/.../search/result/TimezoneResult.java` | Data class for timezone conversion/current-time results |
| `src/.../allapps/SearchItemAnimator.java` | Custom `DefaultItemAnimator` for search results: fade+slide-up for adds/moves, instant removes |
| `res/drawable/ic_ai_search.xml` | Material Symbols sparkle icon for the AI search FAB |
| `res/layout/search_result_timezone.xml` | MaterialCardView layout for timezone results |
| `res/layout/search_loading_indicator.xml` | M3 `LoadingIndicator` placeholder (for future loading state) |

## Modified Files

### Search Infrastructure

- **`UniversalSearchAlgorithm.java`** — Progressive delivery: app results are delivered immediately as `INTERMEDIATE` while I/O providers (contacts, calendar, files) continue running. Final delivery waits for all providers. Empty state only shown on `FINAL` results. Added timezone provider registration and category handling.
- **`AllAppsSearchBarController.java`** — 150ms debounce on keystroke input to avoid thrashing providers during fast typing. Immediate dispatch on explicit IME action. Pending debounce cancelled on reset.
- **`SearchCallback.java`** — Added `INTERMEDIATE` and `FINAL` result code constants; added `onSearchResult(query, items, resultCode)` overload.
- **`SearchResult.java`** — Added `timezone` field.
- **`SearchResultAdapterItem.java`** — Added `filterVersion` for DiffUtil content comparison (filter bar only rebinds when state actually changes). Added `TimezoneResult` equality check.
- **`SearchFilters.java`** — Added monotonic `mVersion` counter incremented on each filter change, exposed via `getVersion()`.

### App Search Scoring

- **`AppSearchProvider.java`** — Replaced `StringMatcherUtility` prefix matching with `SearchScorer.score()` (Jaro-Winkler + prefix/substring bonuses). Results are now ranked by score descending, so the best matches appear first.

### Search Animations

- **`SearchItemAnimator.java`** — New: fade-in + 16dp slide-up for content items (200ms, EMPHASIZED_DECELERATE). Structural items (filter bar, section headers) skip animation. Removes and changes are instant. Supports `setAnimationsEnabled(false)` to suppress animations during intermediate result delivery.
- **`SearchTransitionController.java`** — Replaced base class's scaleY "unsqueeze" with translationY slide-up for content items (matching `SearchItemAnimator`). Filter bar retains scaleY unsqueeze. Removed `shouldAnimate()` override (now handled inline).
- **`SearchRecyclerView.java`** — Removed `setItemAnimator(null)` (was disabling all animation). Added `initSearchAnimator()` to install `SearchItemAnimator` after adapter setup. Overrides `onSearchResultsChanged()` to only scroll-to-top when the user has scrolled away from position 0.

### DiffUtil Improvements

- **`BaseAllAppsAdapter.java`** — `isSameAs()` now compares app icons by component name + user, so DiffUtil can track individual apps (not just view type). Added `VIEW_TYPE_SEARCH_TIMEZONE` and `VIEW_TYPE_SEARCH_LOADING` constants.
- **`AlphabeticalAppsList.java`** — Changed DiffUtil `detectMoves` from `false` to `true` for proper move animations.

### FAB System

- **`ActivityAllAppsContainerView.java`** — Added AI search FAB (56dp icon-only `FloatingActionButton` with tertiary container colors) stacked above the web search Extended FAB in a `LinearLayout` container. AI FAB auto-detects installed AI apps (ChatGPT, Claude, Perplexity, Gemini) or reads `SEARCH_AI_APP` preference. M3 fixes: removed `setStateListAnimator(null)` / `setElevation(0f)` from both FABs (restoring M3 default 6dp shadow), changed container gravity from `CENTER_HORIZONTAL` to `END` for right-alignment, uses default Extended FAB style (not Large). `loadAiAppIcon()` re-evaluates on each FAB show (not every keystroke).
- **`AppsSearchContainerLayout.java`** — Renamed `updateSearchOnlineFab` → `updateSearchFabs`.

### Threading

- **`CalendarSearchProvider.java`**, **`ContactSearchProvider.java`**, **`FileSearchProvider.java`**, **`ShortcutSearchProvider.java`** — Moved from `MODEL_EXECUTOR` (single-threaded) to `THREAD_POOL_EXECUTOR` for parallel I/O provider execution.

### AI Search Settings

- **`LauncherPrefs.kt`** — Added `SEARCH_TIMEZONE` and `SEARCH_AI_APP` preference items. Added `AI_APP_PACKAGES` array with auto-detection priority order.
- **`SearchFragment.java`** — Added AI app chooser bottom sheet (reuses web app chooser pattern). Refactored `addWebAppOption()` → generic `addChooserOption()` taking a `Runnable` callback.
- **`search_preferences.xml`** — Added timezone toggle and AI app picker preferences.
- **`strings.xml`** — Added timezone, AI search, clipboard, and loading strings.

### Icon Fix

- **`ic_ai_search.xml`** — Added `android:tint="?android:attr/textColorSecondary"` matching `ic_web_search.xml`, fixing white-on-white invisible icon in light mode settings.

### Localization

- **`UnitConversion.java`** — Replaced hardcoded "Copied conversions" toast with `R.string.search_result_copied`.

## Settings Added

| Setting | Key | Type | Default |
|---------|-----|------|---------|
| World clock | `pref_search_timezone` | `boolean` | `true` |
| AI search app | `pref_search_ai_app` | `String` | `""` (auto-detect) |

## Design Decisions

- **Progressive delivery**: App results appear instantly while I/O providers run in parallel. This avoids the perceived latency of waiting for contacts/calendar/files before showing anything. Animations are disabled for intermediate batches to prevent overlap.
- **Debounce (150ms)**: Prevents thrashing providers on every keystroke. Immediate dispatch on IME action button. Previous search is cancelled immediately on new input (before debounce fires).
- **Jaro-Winkler scoring**: More forgiving than exact prefix matching — handles typos and out-of-order words. Prefix and substring matches get additive bonuses so exact-prefix results still rank highest.
- **AI FAB re-evaluation**: Runs `loadAiAppIcon()` (one `getPackageInfo()` call) when the FAB container transitions from GONE→VISIBLE, not on every keystroke. Covers the install-while-running case since the FAB is hidden between searches.
- **Thread pool for I/O providers**: Moved from `MODEL_EXECUTOR` (single thread) to `THREAD_POOL_EXECUTOR` so contacts, calendar, files, and shortcuts query in parallel instead of sequentially.
