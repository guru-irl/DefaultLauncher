# 013: App Drawer Colors — Bug Fixes

## Summary
Fixes three categories of bugs from the initial App Drawer Colors implementation (012):
padding issues with tab visibility, search transition jank, and color flash during
the drawer close animation.

## Bug 1: First icon row overlapping tabs / wrong padding after search

### Root Causes & Fixes

**1A — `updateRVContainerRules()` reads stale `mUsingTabs`**
`replaceAppsRVContainer()` calls `updateRVContainerRules()` which reads `mUsingTabs`,
but the field isn't updated until after the method returns. Changed to check
`mViewPager != null` instead, which IS set before the call.

- **File**: `ActivityAllAppsContainerView.java` line 803

**1B — Search exit doesn't re-setup header padding**
`setFloatingRowsCollapsed(false)` calls `onHeightUpdated()`, but its internal condition
(`mMaxTranslation != oldMaxHeight || mFloatingRowsCollapsed`) evaluates to false when
`mMaxTranslation` is 0 (no floating rows). `setupHeader()` is never called, so RV
padding stays at the search value. Fixed by always calling `parent.setupHeader()` in
`setFloatingRowsCollapsed()` after the state change.

- **File**: `FloatingHeaderView.java` lines 405–417

**1C — No-tabs padding too small**
When tabs are hidden and no floating rows exist, the padding was
`header_bottom_padding + content_gap` (4dp + 16dp = 20dp). Changed to return
`all_apps_search_bar_bottom_padding` (30dp) directly, matching the search view padding
for a consistent gap.

- **File**: `FloatingHeaderView.java` `getMaxTranslation()` else branch

## Bug 2: Search transition jank (icons jumping into position)

After the `RecyclerViewAnimationController` animation ends, child views retained stale
`translationY` values set by `setY()` during the animation. When the adapter subsequently
updated (new search results or clearing search), RecyclerView repositioned items via
`layout()` but the stale `translationY` was additive, causing items to appear at wrong
positions for a frame before being corrected.

**Fix**: Added `resetChildViewProperties()` method that runs in the animation end callback.
It resets `translationY`, `scaleY`, `alpha`, and decoration fill alpha on all visible
children, and clears the `childAttachedConsumer`.

- **File**: `RecyclerViewAnimationController.java`

## Bug 3: Header color flash during drawer close

### Root Cause
On phones, `drawOnScrimWithScaleAndBottomOffset()` draws a separate "header protection"
rectangle over the search bar area using `mHeaderColor` (derived from theme attr
`allAppsScrimColor`). When a custom drawer color is set, this rect's color differs from
the ScrimView background, creating a visible color band that flashes during the close
animation as both layers fade at different rates.

### Fix
Skip header protection drawing entirely on phones (`!hasBottomSheet` → early return).
The ScrimView background already provides a uniform full-screen backdrop for the drawer,
making the header protection redundant on phones. Tablet (bottom sheet) behavior is
preserved unchanged.

- **File**: `ActivityAllAppsContainerView.java` `drawOnScrimWithScaleAndBottomOffset()`

## Bug 4: ScrimView not initialized with custom color

`refreshCustomColors()` runs during `onFinishInflate()`, but `mScrimView` is null at
that point (set later by `AllAppsTransitionController.setupViews()`). The ScrimView
never received the custom color, so the first open animation interpolated from
`0x00000000` (transparent black) to the custom color, shifting through black.

**Fix**: Call `refreshCustomColors()` in `setScrimView()` so the color is applied
as soon as the ScrimView reference becomes available.

- **File**: `ActivityAllAppsContainerView.java` `setScrimView()`

## Modified Files
| File | Change |
|------|--------|
| `ActivityAllAppsContainerView.java` | `mViewPager != null` in `updateRVContainerRules()`; `refreshCustomColors()` in `setScrimView()`; skip header protection on phones |
| `FloatingHeaderView.java` | Always call `setupHeader()` in `setFloatingRowsCollapsed()`; no-tabs padding = search padding (30dp) |
| `RecyclerViewAnimationController.java` | `resetChildViewProperties()` on animation end |
| `WorkspaceStateTransitionAnimation.java` | Preserve RGB when fading scrim to transparent |
| `AllAppsState.java` | Return custom drawer color for workspace scrim |
