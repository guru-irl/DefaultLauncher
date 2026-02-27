# 043: Timezone Date Clarity and Timed-Place Queries

## Summary

Fixes confusing date display on timezone cards and adds support for timed-place queries.
Previously, dates were only shown when they differed from today, making cross-midnight
conversions ambiguous. Now the source side always shows an absolute date and the target
side shows a relative label ("Same day" / "Next day" / "Previous day"). Also adds a new
query pattern like "4pm chicago time tuesday to tokyo" for converting a specific time at
a place with optional day-of-week and target.

Additionally expands the debug color swatches from 16 to 45 tokens organized into 6 M3
categories, and fixes light mode search result card contrast by switching to a semantic
`searchResultCardBackground` color.

## Date Display Changes

| Query | LHS (source) | RHS (target) |
|-------|-------------|--------------|
| `5pm chicago to india` | 5:00 PM / Chicago / Thu, Feb 27 | 3:30 AM / Kolkata / Next day |
| `chicago time` | 10:30 AM / Kolkata / Thu, Feb 27 | 9:00 AM / Chicago / Same day |
| `4pm chicago time` | 4:00 PM / Chicago / Thu, Feb 27 | 2:30 AM / Kolkata / Next day |
| `4pm chicago time tuesday` | 4:00 PM / Chicago / Tue, Mar 4 | 2:30 AM / Kolkata / Next day |
| `4pm chicago time tue to tokyo` | 4:00 PM / Chicago / Tue, Mar 4 | 6:00 AM / Tokyo / Next day |

Clipboard always uses absolute dates on both sides:
`5:00 PM Chicago (Thu, Feb 27) = 3:30 AM Kolkata (Fri, Feb 28)`

## New Query Pattern

`TIMED_PLACE_PATTERN` matches "4pm chicago time", "4pm chicago time tuesday",
"4pm chicago time tue to tokyo":
- Group 1: time ("4pm")
- Group 2: source place ("chicago")
- Group 3: optional day-of-week ("tuesday") — resolved via `nextOrSame()`
- Group 4: optional target place ("tokyo") — defaults to device local timezone

Pattern match order: tryConvert → **tryTimedPlace** → tryCurrentTime.

## Search Result Card Background

New semantic color `searchResultCardBackground`:
- Light mode: `materialColorSurfaceContainerLowest` (#FFFFFF) — white cards on tinted drawer
- Dark mode: `materialColorSurfaceContainerHigh` (#282A2F) — unchanged

## Debug Color Swatches

Expanded from 16 flat colors to all 45 `materialColor*` tokens organized into 6 M3
categories: Primary (9), Secondary (8), Tertiary (8), Error (4), Surface (14), Outline (2).
Each category has a header styled with primary color and Label Medium typography.

## Modified Files

**Timezone provider & result:**
- `TimezoneProvider.java` — Added `TIMED_PLACE_PATTERN`, `DAY_OF_WEEK_MAP`, `computeRelativeDay()`, `tryTimedPlace()`; updated `tryConvert()` and `tryCurrentTime()` to always populate absolute source date and relative target label; delegated zone resolution to `TimezoneResolver`
- `TimezoneResult.java` — Added `targetDateAbsolute` field (8th constructor param); clipboard now always shows absolute dates on both sides
- `SearchResultAdapterItem.java` — Added `targetDateAbsolute` to TimezoneResult equality check

**Timezone layout & binding:**
- `search_result_timezone.xml` — Redesigned from vertical to horizontal dual-clock layout (LHS source + arrow + RHS target), added `tz_source_date` view
- `UniversalSearchAdapterProvider.java` — Updated `bindTimezone()` to bind source date and always show both sides

**Search result card background:**
- `res/values/colors.xml` — Added `searchResultCardBackground` → `materialColorSurfaceContainerLowest`
- `res/values-night/colors.xml` — Added `searchResultCardBackground` → `materialColorSurfaceContainerHigh`
- 7 layout files (`search_result_*.xml`) — Changed `cardBackgroundColor` to `@color/searchResultCardBackground`

**Debug color swatches:**
- `ColorDebugPreference.java` — Expanded to 45 colors in 6 categorized sections with headers

**Search input:**
- `search_container_all_apps.xml` — Removed `textCapWords` from input type (prevents auto-capitalize interfering with timezone queries)
