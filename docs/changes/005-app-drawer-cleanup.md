# 005: App Drawer Cleanup

**Date:** 2026-02-14
**Branch:** dev
**Status:** Implemented, builds successfully

## Summary

Comprehensive cleanup of the all-apps (app drawer) UI:

1. **Row gap slider** — Configurable row gap with three notches: 16dp (compact), 24dp (normal), 32dp (spacious). Replaces the earlier hardcoded constant.
2. **Edge padding** — 12dp edge margins matching the home screen, consistent across RecyclerView items and the search bar.
3. **Search bar containment** — Search bar respects 16dp left/right margins so it no longer extends beyond the screen edges.
4. **Pinned tabs** — Work/personal profile tabs stay fixed during scroll instead of scrolling off-screen with the header.
5. **Tab/icon gap** — 12dp visual gap between pinned tabs and the first icon row, with icons clipping at the header bottom.
6. **Search-to-tab gap** — 16dp gap between search bar and the tab strip.
7. **Tab label text** — Tab labels use string resources directly instead of enterprise StringCache (which returns empty strings on consumer devices).
8. **Work paused card text** — Guards against empty StringCache values so paused-state title, description, and button text fall back to XML defaults.
9. **Discovery bounce removed** — Removed the hotseat discovery bounce animation on app resume.

## Part 1: Row Gap Slider Setting

### Preference Wiring

| Layer | File | Change |
|-------|------|--------|
| XML | `launcher_preferences.xml` | Added `SeekBarPreference` key=`pref_allapps_row_gap`, min=0, max=2, default=2 |
| String | `strings.xml` | Added `allapps_row_gap_title` = "App drawer row gap" |
| Pref constant | `LauncherPrefs.kt` | Added `ALLAPPS_ROW_GAP = backedUpItem("pref_allapps_row_gap", 0)` |
| Change listener | `SettingsActivity.java` | Added `"pref_allapps_row_gap"` to `gridPrefKeys` array |
| Summary display | `SettingsActivity.java` | Shows current dp value (e.g., "16dp") as summary text |
| Consumer | `InvariantDeviceProfile.java` | Reads pref index, maps via `ALLAPPS_ROW_GAP_OPTIONS` array |

### Index-to-DP Mapping

The slider stores an index (0/1/2). The mapping array in `InvariantDeviceProfile`:

```java
public static final float[] ALLAPPS_ROW_GAP_OPTIONS = {16f, 24f, 32f};
```

| Index | DP | Description |
|-------|-----|-------------|
| 0 | 16dp | Compact — minimal gap, fits more rows (default) |
| 1 | 24dp | Normal — balanced spacing |
| 2 | 32dp | Spacious — generous gap with room for labels |

The value flows through `allAppsRowSpacingDp` → `DeviceProfile.allAppsBorderSpacePx.y` → baked into `allAppsCellHeightPx`.

### Previous Setting Removed

The earlier `pref_allapps_row_spacing` SeekBarPreference (continuous slider, 0–48dp range) was removed:
- Deleted from `launcher_preferences.xml`, `strings.xml`, `LauncherPrefs.kt`, `SettingsActivity.java`

## Part 2: Edge Padding and Search Bar

### All-Apps Edge Padding (12dp)

**`DeviceProfile.updateAllAppsContainerWidth()`** — Added a square grid branch:

```java
} else if (inv.isSquareGrid) {
    allAppsPadding.left = allAppsPadding.right =
            pxFromDp(InvariantDeviceProfile.SQUARE_GRID_EDGE_GAP_DP, mMetrics);
}
```

This sets RecyclerView side padding to 12dp, matching the home screen edge gap.

### Search Bar Width (16dp margins)

**`AppsSearchContainerLayout`** — Two changes:

1. **`onMeasure()`** — Square grid bypasses the AOSP icon-alignment width calculation:
   ```java
   if (dp.inv.isSquareGrid) {
       super.onMeasure(widthMeasureSpec, heightMeasureSpec);
       return;
   }
   ```

2. **`setInsets()`** — Sets 16dp left/right margins for square grid:
   ```java
   if (dp.inv.isSquareGrid) {
       int margin = (int) (16 * getResources().getDisplayMetrics().density);
       mlp.leftMargin = margin;
       mlp.rightMargin = margin;
   }
   ```

The search bar is then centered by the existing `onLayout()` translationX logic.

## Part 3: Pinned Tabs and Gaps

### Tab Pinning

**`FloatingHeaderView`** — Three changes:

1. **`updateExpectedHeight()`** — Tabs no longer contribute to `mMaxTranslation`:
   ```java
   mMaxTranslation += mFloatingRowsHeight;
   // Tabs stay pinned and don't scroll with the header
   ```

2. **`getMaxTranslation()`** — New branch for visible tabs returns padding that accounts for tab height + gap:
   ```java
   } else if (!mTabsHidden) {
       int tabPadding = getDimensionPixelSize(R.dimen.all_apps_tabs_margin_top)
               + 2 * mTabsAdditionalPaddingBottom;
       return mMaxTranslation + tabPadding;
   }
   ```

3. **`applyVerticalMove()`** — Tab layout pinned at y=0, RV clips at header bottom:
   ```java
   mTabLayout.setTranslationY(0);
   // ...
   if (mTabsHidden || mFloatingRowsCollapsed) {
       mRVClip.top = clipTop;
   } else {
       mRVClip.top = getMaxTranslation();
   }
   ```

### Search-to-Tab Gap

**`res/values/dimens.xml`** — Changed `all_apps_header_top_margin` from 33dp to 44dp (+11dp). This is the margin between the search bar container and the paged header view, producing approximately 16dp visual gap between search bar bottom and tab top.

## Part 4: Text Fixes

### Tab Labels

**`ActivityAllAppsContainerView.setDeviceManagementResources()`** — Replaced StringCache-based text setting with direct string resources:

```java
personalTab.setText(R.string.all_apps_personal_tab);
workTab.setText(R.string.all_apps_work_tab);
```

**Problem**: `DevicePolicyManager.getResources().getString()` returns empty strings on consumer devices (no MDM policy). The StringCache propagated these empty strings, wiping the XML defaults.

### Work Paused Card

**`WorkPausedCard.setWorkProfilePausedResources()`** — Added null/empty guards on all three StringCache fields:

```java
if (cache.workProfilePausedTitle != null && !cache.workProfilePausedTitle.isEmpty()) {
    title.setText(cache.workProfilePausedTitle);
}
// Same pattern for description and button
```

**`WorkEduCard.updateStringFromCache()`** — Same guard pattern applied.

## Part 5: Discovery Bounce

**`Launcher.java`** — Removed `DiscoveryBounce.showForHomeIfNeeded(this)` call and its import. The bounce animation played on every app resume, which is disruptive on a custom launcher.

## Part 6: Row Gap Calculation Fix

**`DeviceProfile.java`** square grid all-apps block — Fixed ordering and cell height calculation:

**Before (broken)**:
```java
allAppsCellHeightPx = allAppsContentHeight;  // missing border space
allAppsBorderSpacePx = new Point(...);       // set too late
```

**After (fixed)**:
```java
// 1. Set border space first
allAppsBorderSpacePx = new Point(cellLayoutBorderSpacePx.x, allAppsRowSpacingPx);
// 2. Bake border space into cell height (matching AOSP pattern)
allAppsCellHeightPx = allAppsContentHeight + allAppsBorderSpacePx.y;
```

AOSP's `AllAppsGridAdapter` positions items at `cellHeight` intervals. If border space isn't included, all rows render with zero visual gap.

## Files Modified

| File | Changes |
|------|---------|
| `res/xml/launcher_preferences.xml` | Removed old row spacing slider; added new 3-notch row gap slider |
| `res/values/strings.xml` | Removed `allapps_row_spacing_title`; added `allapps_row_gap_title` |
| `res/values/dimens.xml` | `all_apps_header_top_margin` 33dp → 44dp |
| `LauncherPrefs.kt` | Removed `ALLAPPS_ROW_SPACING`; added `ALLAPPS_ROW_GAP` |
| `InvariantDeviceProfile.java` | Replaced `SQUARE_GRID_ALLAPPS_ROW_GAP_DP` constant with `ALLAPPS_ROW_GAP_OPTIONS` array; reads pref index |
| `DeviceProfile.java` | Fixed square grid all-apps cell height (bake in border space); added square grid branch in `updateAllAppsContainerWidth()` |
| `AppsSearchContainerLayout.java` | Square grid branch in `onMeasure()` and 16dp margins in `setInsets()` |
| `FloatingHeaderView.java` | Pinned tabs: modified `updateExpectedHeight()`, `getMaxTranslation()`, `applyVerticalMove()` |
| `ActivityAllAppsContainerView.java` | `setDeviceManagementResources()` uses string resources directly |
| `WorkPausedCard.java` | Guarded `setWorkProfilePausedResources()` against empty StringCache |
| `WorkEduCard.java` | Guarded `updateStringFromCache()` against empty StringCache |
| `Launcher.java` | Removed `DiscoveryBounce.showForHomeIfNeeded()` call and import |
| `SettingsActivity.java` | Updated `gridPrefKeys` array (removed old key, added new key) |
