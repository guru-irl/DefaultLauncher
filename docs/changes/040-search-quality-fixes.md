# 040: Search Quality Fixes

## Summary

Fixes 1 crash bug, 5 medium-severity issues, and 4 low-severity issues found during code review of the universal search feature. Adds FileProvider declaration to prevent crashes when opening file results, extracts duplicated number formatting, localizes all hardcoded UI strings, fixes shared animation state, moves heavy computation off the main thread, and implements proper DiffUtil content comparison.

## Crash Fix

**FileProvider missing** — `FileResult.launch()` calls `FileProvider.getUriForFile()` with authority `<packageName>.fileprovider`, but no FileProvider was declared in the manifest. Opening any file search result would crash with `IllegalArgumentException`.

- Created `res/xml/file_paths.xml` with all 9 external storage paths matching `FileSearchProvider.SEARCH_DIRS`
- Added `<provider>` declaration in `AndroidManifest-common.xml`

## New Files

| File | Purpose |
|------|---------|
| `res/xml/file_paths.xml` | FileProvider path declarations for file search results |
| `src/.../search/result/NumberFormatUtil.java` | Shared number formatting (integer check + sigfig + strip zeros) |

## Modified Files

### String Localization

- **`res/values/strings.xml`** — Changed `all_apps_search_bar_hint` to "Search", `all_apps_no_search_results` to "No results found for...". Added 5 section header strings (`search_section_*`) and 4 quick action labels (`search_action_*`).
- **85 locale `strings.xml` files** — Removed `all_apps_search_bar_hint` and `all_apps_no_search_results` entries so all locales fall through to the updated English defaults (AOSP translations still said "Search apps" / "No apps found").
- **`UniversalSearchAlgorithm.java`** — Replaced 5 hardcoded section header strings ("Apps", "Shortcuts", etc.) with `R.string.search_section_*` references.
- **`QuickActionProvider.java`** — Replaced 4 hardcoded action labels ("Call ...", "Email ...", etc.) with `R.string.search_action_*` references.

### Code Deduplication

- **`CalculatorResult.java`** — Replaced private `formatResult()` with `NumberFormatUtil.format(result, 10)`.
- **`UnitConversion.java`** — Replaced `formatValue()` body with `NumberFormatUtil.format(value, 6)`.

### Bug Fixes

- **`SpringBounceEdgeEffectFactory.java`** — Moved `mSpringAnimation` and `mCurrentOverscroll` from factory-level fields into each `SpringBounceEdgeEffect` instance. Previously, top and bottom edge effects shared the same animation/state, causing incorrect bounce behavior when both edges were triggered.
- **`UniversalSearchAdapterProvider.java`** — Removed dead WEB_SEARCH best-match tracking in `bindQuickAction()` (WEB_SEARCH items are filtered out in `deliverResults()` and never reach the adapter).
- **`SearchFragment.java`** — Added else branch in `onResume()`: when file access permission is revoked externally, the toggle now unchecks and persists `SEARCH_FILES = false`.

### Performance

- **`CalculatorProvider.java`** — Moved mxparser evaluation to `MODEL_EXECUTOR` background thread. Added `volatile mCancelled` flag checked before posting results. Pre-check (`containsMathContent`) stays on calling thread for fast rejection.
- **`UnitConverterProvider.java`** — Same pattern: regex parsing and conversion logic runs on `MODEL_EXECUTOR` with cancellation support.
- **`SearchResultAdapterItem.java`** — Implemented per-type `isContentSame()` comparisons: contacts by ID, calendar by event ID, files by path+lastModified, shortcuts by ID, calculator by expression+result, unit conversion by inputValue+inputUnit. Filter bar stays `false` (mutable state). Reduces unnecessary rebinding during DiffUtil passes.

### Documentation

- **`docs/search-system.md`** — Fixed calendar search range from "next 30 days" to "next 365 days".
