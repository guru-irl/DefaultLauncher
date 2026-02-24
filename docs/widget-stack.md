# Widget Stack System

How multiple widgets can be stacked in a single grid cell group, allowing users to swipe between them horizontally.

---

## Architecture Overview

The widget stack system has four layers:

```
WidgetStackInfo           (data model, extends CollectionInfo)
        |
        v
WidgetStackView           (FrameLayout with swipe gestures + dot indicators)
        |
        v
AppWidgetHostView[]       (child widget views, one visible at a time)
        |
        v
WidgetDropHighlight       (spring-animated drop feedback)
```

**WidgetStackInfo** is the data model, analogous to `FolderInfo` for folders. It stores an ordered list of `LauncherAppWidgetInfo` children and the currently active index. Persisted to the favorites DB as `ITEM_TYPE_WIDGET_STACK` (value 12).

**WidgetStackView** is the view layer. It extends `FrameLayout`, implements `DraggableView` and `Reorderable`, and manages the swipe gesture, animations, and page indicator dots. Only one child widget is visible at a time.

**WidgetDropHighlight** is a shared spring-animated visual feedback helper used during drag-and-drop hover, both for creating new stacks and adding to existing ones.

---

## Database Schema

Widget stacks reuse the existing `favorites` table with these conventions:

| Column | Stack Row | Child Widget Row |
|--------|-----------|------------------|
| `itemType` | `12` (WIDGET_STACK) | `4` (APPWIDGET) or `5` (CUSTOM_APPWIDGET) |
| `container` | `CONTAINER_DESKTOP` or `CONTAINER_HOTSEAT` | **Stack's `_id`** (positive integer) |
| `screen` | Screen ID | Same as parent stack |
| `cellX`, `cellY` | Grid position | `-1` (no individual position) |
| `spanX`, `spanY` | Stack's span | Individual widget's span |
| `options` | Active widget index | (unused) |
| `rank` | (unused) | Order within stack (0-based) |
| `appWidgetId` | `-1` (not a widget) | Widget's actual appWidgetId |

The `container > 0` convention is how the loader knows a widget belongs to a collection (folder or stack) rather than being placed directly on the workspace. The utility method `LauncherSettings.Favorites.isInsideCollection(int)` encapsulates this check.

---

## Data Model

### WidgetStackInfo

**File:** [`src/com/android/launcher3/model/data/WidgetStackInfo.java`](../src/com/android/launcher3/model/data/WidgetStackInfo.java)

Extends `CollectionInfo` (the shared base for folders, app pairs, and stacks).

| Field / Constant | Description |
|-----------------|-------------|
| `MAX_STACK_SIZE` | Hard limit of 6 widgets per stack |
| `contents` | `ArrayList<ItemInfo>` of child widgets |
| `activeIndex` | Index of the currently visible widget |

**Key methods:**

- `add(ItemInfo)` -- Validates widget type and enforces `MAX_STACK_SIZE`. Throws `RuntimeException` on violation.
- `assignWidget(LauncherAppWidgetInfo, int rank)` -- Sets `container`, `screenId`, `cellX`, `cellY`, and `rank` on the widget, then calls `add()`. Does **not** persist to DB; callers handle persistence.
- `removeMatching(Predicate<ItemInfo>)` -- Removes widgets matching the predicate (e.g. on package uninstall) and clamps `activeIndex`. Returns count removed.
- `sortByRank()` -- Sorts contents by rank for consistent ordering after DB load.
- `getActiveWidget()` -- Returns the active `LauncherAppWidgetInfo` with safe cast, or `null`.
- `willAcceptItemType(int)` -- Static. Returns `true` for `ITEM_TYPE_APPWIDGET` and `ITEM_TYPE_CUSTOM_APPWIDGET`.
- `onAddToDatabase(ContentWriter)` -- Persists `activeIndex` to the `OPTIONS` column.

### Model Filters (ModelUtils.java)

Two predicates control how widget-related items are filtered in model operations:

| Predicate | Matches | Used By |
|-----------|---------|---------|
| `APP_WIDGET_FILTER` | `APPWIDGET`, `CUSTOM_APPWIDGET` | CacheDataUpdatedTask, PackageInstallStateChangedTask, PackageUpdatedTask, LoaderTask (first screen broadcast) |
| `WIDGET_OR_STACK_FILTER` | Above + `WIDGET_STACK` | BaseLauncherBinder (categorizing items for binding) |

`APP_WIDGET_FILTER` is used when iterating over actual widget items that have `appWidgetId`, `providerName`, etc. Widget stacks don't have these fields, so they must be excluded.

`WIDGET_OR_STACK_FILTER` is used when categorizing items into "widgets" vs. "workspace items" for binding -- stacks should be treated like widgets for layout purposes.

---

## Loading Pipeline

Widget stacks are loaded from the database through the standard AOSP loader pipeline with targeted extensions at each stage.

### Stage 1: Cursor Processing (LoaderCursor)

**File:** [`src/com/android/launcher3/model/LoaderCursor.java`](../src/com/android/launcher3/model/LoaderCursor.java)

When child widgets are loaded before their parent stack row (lower `_id`), the loader needs a placeholder. `findOrMakeWidgetStack(int id, BgDataModel)` checks the data model and a pending collection map, creating a blank `WidgetStackInfo` if needed.

This uses the same generic `findOrMakeCollection()` helper as `findOrMakeFolder()`:

```java
private <T extends CollectionInfo> T findOrMakeCollection(int id, BgDataModel dataModel,
        Class<T> type, Supplier<T> factory) {
    ItemInfo info = dataModel.itemsIdMap.get(id);
    if (type.isInstance(info)) return type.cast(info);
    CollectionInfo pending = mPendingCollectionInfo.get(id);
    if (type.isInstance(pending)) return type.cast(pending);
    T created = factory.get();
    created.id = id;
    mPendingCollectionInfo.put(id, created);
    return created;
}
```

When a child widget has `container > 0`, the cursor calls `findOrMakeWidgetStack(container)` and adds the widget to the returned stack.

### Stage 2: Item Processing (WorkspaceItemProcessor)

**File:** [`src/com/android/launcher3/model/WorkspaceItemProcessor.kt`](../src/com/android/launcher3/model/WorkspaceItemProcessor.kt)

The `ITEM_TYPE_WIDGET_STACK` case dispatches to `processWidgetStack()`, which:

1. Calls `findOrMakeWidgetStack(c.id, bgDataModel)` to get the (possibly pre-populated) stack
2. Applies common properties (id, container, screenId, cellX, cellY)
3. Sets spanX, spanY
4. Loads active index from the `OPTIONS` column
5. Adds to `BgDataModel` via `checkAndAddItem()`

Child widgets use `Favorites.isInsideCollection(c.container)` to determine they belong to a collection rather than the workspace directly.

### Stage 3: Post-Processing (LoaderTask)

**File:** [`src/com/android/launcher3/model/LoaderTask.java`](../src/com/android/launcher3/model/LoaderTask.java)

After all items are loaded, `processWidgetStackItems()` iterates over all `WidgetStackInfo` entries and calls `sortByRank()` on each to ensure consistent widget ordering.

### Stage 4: View Inflation (ItemInflater)

**File:** [`src/com/android/launcher3/util/ItemInflater.kt`](../src/com/android/launcher3/util/ItemInflater.kt)

The `ITEM_TYPE_WIDGET_STACK` case calls `inflateWidgetStack(info, writer)`:

1. Sorts contents by rank
2. Creates a new `WidgetStackView` and sets its tag to the `WidgetStackInfo`
3. Iterates through children, inflating each via `widgetInflater.inflateAppWidget()`
4. Handles `TYPE_DELETE` (removes corrupt widgets from DB) and `TYPE_PENDING` (pending widgets)
5. Calls `stackView.addWidgetView()` for each successfully inflated child
6. Sets the active index from `WidgetStackInfo`

Safe casts (`as?`) protect against unexpected child types with diagnostic logging.

---

## View Layer

### WidgetStackView

**File:** [`src/com/android/launcher3/widget/WidgetStackView.java`](../src/com/android/launcher3/widget/WidgetStackView.java)

A `FrameLayout` that manages multiple `AppWidgetHostView` children. Implements:

- **`DraggableView`** -- So the entire stack can be long-press dragged as a unit
- **`Reorderable`** -- So workspace reorder animations work correctly

#### Child Management

Children are stored in `mWidgetViews` (ArrayList). Only the active widget is `VISIBLE`; all others are `INVISIBLE`. Individual widget long-press is disabled (`setLongClickable(false)`) so the stack handles drag as a unit.

`addWidgetView(AppWidgetHostView, LauncherAppWidgetInfo)` adds a child, resetting any stale transform state and updating visibility.

`rebuildFromStackInfo()` synchronizes the view list with the data model after mutations (e.g. package uninstall), removing stale child views and clamping the active index.

`attachChildWidgetsToHost(LauncherWidgetHolder)` reattaches children after async workspace inflation so they receive remote view updates.

#### Swipe Gesture

The horizontal swipe system uses a three-phase approach:

1. **Intercept** (`onInterceptTouchEvent`) -- Detects horizontal vs. vertical gesture using `mTouchSlop`. Claims parent touch for horizontal swipes (`requestDisallowInterceptTouchEvent`). Releases for vertical gestures so Workspace page scrolling works.

2. **Track** (`onTouchEvent`) -- Tracks `mSwipeOffset` and applies live translation/scale to the current and peek widgets via `applySwipeTranslation()`.

3. **Resolve** -- On `ACTION_UP`, checks fling velocity (threshold: 500 dp/s) and drag fraction (threshold: 50% of width). Calls `snapToIndex()` for successful swipes or `snapBack()` for failed ones.

#### Swipe Animation

During a live swipe, two widgets are visible simultaneously:

- **Current widget:** Scales from 1.0 down to 0.92, translates with finger
- **Peek widget:** Scales from 0.92 up to 1.0, slides in from the edge

Both `snapToIndex()` and `snapBack()` delegate to a shared `animateSnap()` helper:

```java
private void animateSnap(Interpolator interpolator,
        SnapUpdateCallback updateCallback, Runnable endAction)
```

| Animation | Interpolator | Duration |
|-----------|-------------|----------|
| Snap to next | `Interpolators.EMPHASIZED` | `M3Durations.LONG_2` (450ms) |
| Snap back | `Interpolators.EMPHASIZED_DECELERATE` | `M3Durations.LONG_2` (450ms) |

#### Page Indicator Dots

Dots are drawn in `dispatchDraw()` as a canvas overlay (not child views):

| Property | Value |
|----------|-------|
| Radius | 3dp |
| Spacing | 8dp |
| Bottom margin | 8dp |
| Active color | `materialColorPrimary` at 100% alpha |
| Inactive color | `materialColorOutline` at 40% alpha |
| Show animation | `M3Durations.SHORT_3` (100ms), `Interpolators.STANDARD` |
| Hide delay | 1500ms after swipe ends |
| Hide animation | `M3Durations.SHORT_4` (150ms), `Interpolators.STANDARD` |

Dots auto-show when a swipe begins and auto-hide after the swipe animation completes.

#### Resize Support

Five provider-info methods use a shared `accumulateFromProviderInfo()` helper to iterate over all child widgets:

| Method | Identity | Accumulator | Meaning |
|--------|----------|-------------|---------|
| `getMinSpanX` | 1 | `Math.max` | Most restrictive minimum |
| `getMinSpanY` | 1 | `Math.max` | Most restrictive minimum |
| `getMaxSpanX` | `MAX_VALUE` | `Math.min` | Most restrictive maximum |
| `getMaxSpanY` | `MAX_VALUE` | `Math.min` | Most restrictive maximum |
| `getResizeMode` | `BOTH` | `&` (AND) | Intersection of capabilities |

`expandSpanIfNeeded()` grows the stack's span to accommodate a new widget, clamped to `InvariantDeviceProfile.numColumns`/`numRows`.

`updateChildWidgetSizes()` propagates span changes to all children with an optional `ModelWriter` for DB persistence.

#### Accessibility

- Content description: "Widget %d of %d" (`widget_stack_page_description`)
- Scroll actions: `ACTION_SCROLL_FORWARD` / `ACTION_SCROLL_BACKWARD`
- Both actions trigger `snapToIndex()` for animated navigation

#### Lifecycle

`onDetachedFromWindow()` cancels all pending operations: long-press timer, snap animator, velocity tracker, dot hide runnable, dot fade animator, and drop highlight.

---

### WidgetDropHighlight

**File:** [`src/com/android/launcher3/widget/WidgetDropHighlight.java`](../src/com/android/launcher3/widget/WidgetDropHighlight.java)

Spring-animated visual feedback for drag hover over a widget or stack.

| Property | Value |
|----------|-------|
| Child scale at full highlight | 0.85 (15% reduction) |
| Background color | `materialColorPrimaryContainer` |
| Background alpha at full highlight | 230/255 (~90%) |
| Background corner radius | `RoundedCornerEnforcement.computeEnforcedRadius()` |
| Show spring | M3 bounce gentle (damping/stiffness from `R.dimen.m3_bounce_gentle_*`) |
| Clear spring | M3 bounce smooth (damping/stiffness from `R.dimen.m3_bounce_smooth_*`) |

Uses `SpringAnimationBuilder` with a `FloatProperty<WidgetDropHighlight>` for progress (0.0 = rest, 1.0 = full highlight). The progress drives both child scale and background alpha.

Used by:
- `WidgetStackView.showDropHighlight()` -- When hovering a widget over an existing stack
- `Workspace.onDragOver()` -- When hovering a widget over another widget to create a stack

---

## Drag-and-Drop

### Creating a New Stack

**Trigger:** Dragging a widget over another standalone widget on the workspace.

**Flow:**

1. **Validation** (`Workspace.willCreateWidgetStack()`):
   - Both items must be widget types (`willAcceptItemType()`)
   - Can't stack a widget onto itself
   - Computes union span (`max(source.spanX, dest.spanX)`, same for Y)
   - Both widgets must support the union span (checked via provider info min/max span)

2. **Hover feedback** (`Workspace.onDragOver()`):
   - Creates a `WidgetDropHighlight` on the target widget's `ViewGroup`
   - Calls `show()` for spring animation
   - Sets drag mode to `DRAG_MODE_CREATE_WIDGET_STACK`

3. **Drop** (`Workspace.createWidgetStackIfNecessary()`):
   - Detaches source widget from DragView, removes from workspace
   - Removes target widget from workspace
   - Calls `Launcher.addWidgetStack()` to create `WidgetStackView` + `WidgetStackInfo` at target's cell position
   - Assigns target widget at rank 0, source widget at rank 1
   - Persists via `ModelWriter.modifyItemInDatabase()` (existing widget) and `addItemToDatabase()` (stack itself)
   - Adds both host views to the stack
   - Sets active index to the newly added widget
   - Animates drag view into stack position

### Adding to an Existing Stack

**Trigger:** Dragging a widget over an existing `WidgetStackView`.

**Flow:**

1. **Validation** (`Workspace.willAddToExistingWidgetStack()`):
   - Target must be `WidgetStackView`
   - Dragged item must be widget type
   - Stack must not be full (`< MAX_STACK_SIZE`)

2. **Hover feedback** (`Workspace.onDragOver()`):
   - Calls `stackView.showDropHighlight()` (delegates to `WidgetDropHighlight`)
   - Sets drag mode to `DRAG_MODE_ADD_TO_WIDGET_STACK`

3. **Drop** (`Workspace.addToExistingWidgetStackIfNecessary()`):
   - Detaches source widget from DragView
   - Assigns widget to stack at next rank
   - Calls `expandSpanIfNeeded()` to accommodate larger widget
   - Adds host view to stack
   - Sets active index to newly added widget

### External Picker Flow

When a widget is dragged from the widget picker onto the workspace over an existing widget or stack:

1. **Drag exit** (`Workspace.onDropExternal()`):
   - Stores the target in `mPendingExternalStackTarget` (for existing stacks) or `mPendingExternalStackCreationTarget` (for new stacks)
   - Widget binding proceeds normally

2. **Bind completion** (`Launcher.completeAddAppWidget()`):
   - Checks `consumePendingExternalStackTarget()` -- if set, calls `addWidgetToExistingStack()`
   - Checks `consumePendingExternalStackCreationTarget()` -- if set, calls `createStackFromExternalDrop()`
   - Both methods use `assignWidget()` for field mutation and explicit `ModelWriter` calls for persistence

---

## Resize Frame Integration

**File:** [`src/com/android/launcher3/AppWidgetResizeFrame.java`](../src/com/android/launcher3/AppWidgetResizeFrame.java)

`showForWidgetStack(WidgetStackView, CellLayout)` creates a resize frame for the stack:

- Uses `setupForWidgetStack()` instead of the standard widget setup
- Queries min/max spans and resize mode from the stack (composite of all children)
- On resize commit, calls `wsv.updateChildWidgetSizes()` to propagate new spans to all children
- Sets content description: "Widget stack with %d widgets"

---

## Package Uninstall Handling

When a package is uninstalled, `Workspace.removeItemsByMatcher()` handles widget stacks alongside folders and app pairs:

```java
} else if (child instanceof WidgetStackView wsv) {
    WidgetStackInfo stackInfo = wsv.getStackInfo();
    if (stackInfo != null) {
        int removed = stackInfo.removeMatching(matcher);
        if (removed > 0) {
            if (stackInfo.getContents().isEmpty()) {
                layout.removeViewInLayout(child);  // Empty stack -- remove entirely
            } else {
                wsv.rebuildFromStackInfo();  // Rebuild child views
            }
        }
    }
}
```

`WidgetStackInfo.removeMatching()` iterates backwards through contents, removes matched items, and clamps the active index.

`WidgetStackView.rebuildFromStackInfo()` builds a set of valid IDs from the stack info, removes stale child views, and updates visibility.

---

## Drag Preview

**File:** [`src/com/android/launcher3/graphics/DragPreviewProvider.java`](../src/com/android/launcher3/graphics/DragPreviewProvider.java)

`WidgetStackView` is treated like `LauncherAppWidgetHostView` for drag preview generation:
- `createDrawable()` returns `null` (uses content view instead)
- `getContentView()` returns the stack view itself
- `prepareDrawDragView()` hides non-active children and cancels animations for a clean snapshot

---

## Widget Stack Editor

A bottom sheet for reordering, removing, and adding widgets within a stack.

### Architecture

The editor extends `AbstractSlideInView<Launcher>` (the same base class as `WidgetsBottomSheet`) and implements `Insettable` for system bar inset handling.

```
WidgetStackEditorView          (AbstractSlideInView + Insettable)
        |
        v
WidgetStackEditorAdapter       (RecyclerView.Adapter with 2 view types)
        |
        v
WidgetStackEditorItemDecoration (position-aware rounded card backgrounds)
```

**Entry point:** Long-press a widget stack on the workspace, then tap "Edit stack" in the popup menu. This calls `WidgetStackEditorView.show(Launcher, WidgetStackView)`.

### Layout

The editor is a standard M3 modal bottom sheet:
- Drag handle (32x4dp, `outlineVariant`)
- Centered title (`MaterialToolbar` with `titleCentered="true"`)
- `RecyclerView` with card-grouped items
- Last item is an "Add widget" action row (not a separate button)

### Adapter View Types

| Type | Layout | Description |
|------|--------|-------------|
| `TYPE_WIDGET` (0) | `widget_stack_editor_item` | Widget row: 88dp min height, 64dp preview, drag handle, remove button |
| `TYPE_ADD` (1) | Same layout, reconfigured | Action row: 56dp min height, 40dp `+` icon, no drag/remove |

The add row is hidden when the stack is full (`MAX_STACK_SIZE`).

### Item Decoration

`WidgetStackEditorItemDecoration` draws position-aware rounded-rect card backgrounds behind each item:

| Position | Top corners | Bottom corners |
|----------|-------------|----------------|
| First (solo) | 28dp | 28dp |
| First | 28dp | 4dp |
| Middle | 4dp | 4dp |
| Last (add row) | 4dp | 28dp |

Items being dragged are skipped by the decoration -- the drag animation draws their background instead.

Ripple drawables with matching corner radii are applied per-item for touch feedback.

### Drag Reorder

Uses `ItemTouchHelper` with guards to exclude the add row:
- `getMovementFlags()` returns 0 for the add row
- `canDropOver()` returns false for the add row
- `onMove()` swaps items in `WidgetStackInfo.contents` and calls `notifyItemMoved()`

### Drag Animations

Animated transitions on drag pickup and drop using `AnimatorSet`:

| Property | Pickup | Drop |
|----------|--------|------|
| Corner radii | Position-aware -> all 28dp | All 28dp -> position-aware |
| Elevation (translationZ) | 0 -> 8dp | 8dp -> 0 |
| Scale | 1.0 -> 1.02 | 1.02 -> 1.0 |
| Background color | `colorSurfaceContainerHighest` | (animated back, cleared on end) |
| Duration | `M3Durations.SHORT_2` (100ms) | `M3Durations.SHORT_2` (100ms) |
| Interpolator | `EMPHASIZED_DECELERATE` | `EMPHASIZED_ACCELERATE` |

### Color Hierarchy (M3 Spec)

Both the Widgets bottom sheet and the Widget Stack Editor resolve colors from `WidgetContainerTheme` for consistency:

| Layer | Theme attr | Token |
|-------|-----------|-------|
| Sheet background | `widgetPickerPrimarySurfaceColor` | `colorSurfaceContainerLow` |
| Item card backgrounds | `widgetPickerSecondarySurfaceColor` | `colorSurfaceContainer` |
| Dragged item | `colorSurfaceContainerHighest` | (M3 attr, resolved from inflate context) |
| Title text | -- | `materialColorOnSurface` |
| Supporting text / icons | -- | `materialColorOnSurfaceVariant` |
| Add row icon | -- | `materialColorPrimary` |

The `WidgetContainerTheme` tokens were updated from `surfaceContainerHigh` to `surfaceContainerLow` for the primary surface to match the [M3 bottom sheet spec](https://m3.material.io/components/bottom-sheets/specs).

### Theme Context Chain

The editor is inflated with `HomeSettings_Theme + DynamicColors` (needed for `MaterialToolbar` and `MaterialButton`), but colors are resolved from a separate `WidgetContainerTheme` context via `getWidgetThemeContext()`. This ensures both sheets use the same `widgetPicker*` attrs regardless of their inflation context.

### Nav Bar Scrim

Matches `BaseWidgetSheet` pattern:
- `Insettable` interface receives insets from `DragLayer`
- `setupNavBarColor()` uses `mActivityContext` (Launcher) to read `isMainColorDark`
- Nav bar scrim drawn in `dispatchDraw()` as a filled rect
- RecyclerView bottom padding accounts for nav bar height

### Popup Integration

`WidgetStackPopupHelper` adds "Edit stack" and "Remove stack" shortcuts to the long-press popup via `PopupContainerWithArrow`. These are system shortcuts that appear alongside the standard widget actions.

---

## File Manifest

### New Files (8)

| File | Purpose |
|------|---------|
| `src/.../model/data/WidgetStackInfo.java` | Data model (extends CollectionInfo) |
| `src/.../widget/WidgetStackView.java` | View layer (FrameLayout + swipe + dots) |
| `src/.../widget/WidgetDropHighlight.java` | Spring-animated drop feedback |
| `src/.../widget/WidgetStackEditorView.java` | Bottom sheet editor (AbstractSlideInView) |
| `src/.../widget/WidgetStackEditorAdapter.java` | RecyclerView adapter (widget rows + add row) |
| `src/.../widget/WidgetStackEditorItemDecoration.java` | Position-aware card backgrounds |
| `src/.../widget/WidgetStackPopupHelper.java` | Popup menu shortcuts (edit/remove stack) |
| `res/layout/widget_stack_editor.xml` | Editor bottom sheet layout |

### Modified Files (11)

| File | Changes |
|------|---------|
| `LauncherSettings.java` | `ITEM_TYPE_WIDGET_STACK = 12`, `isInsideCollection()`, `itemTypeToString()` |
| `Launcher.java` | `addWidgetStack()`, `addWidgetToExistingStack()`, `createStackFromExternalDrop()`, picker completion |
| `Workspace.java` | Stack creation/addition drop logic, hover feedback, package uninstall, external picker targets |
| `AppWidgetResizeFrame.java` | `showForWidgetStack()`, `setupForWidgetStack()`, stack resize handling |
| `DragPreviewProvider.java` | WidgetStackView recognized for content-view drag preview |
| `ModelUtils.java` | `APP_WIDGET_FILTER`, `WIDGET_OR_STACK_FILTER` predicates |
| `LoaderCursor.java` | `findOrMakeWidgetStack()`, generic `findOrMakeCollection()` |
| `WorkspaceItemProcessor.kt` | `processWidgetStack()`, `isInsideCollection` check for child widgets |
| `LoaderTask.java` | `processWidgetStackItems()` post-processing, `APP_WIDGET_FILTER` usage |
| `ItemInflater.kt` | `inflateWidgetStack()` with safe casts |
| `BaseLauncherBinder.java` | Uses `WIDGET_OR_STACK_FILTER` for item categorization |

### Modified Model Tasks (3)

| File | Change |
|------|--------|
| `CacheDataUpdatedTask.java` | Uses `APP_WIDGET_FILTER` (no redundant instanceof) |
| `PackageInstallStateChangedTask.java` | Uses `APP_WIDGET_FILTER` (no redundant instanceof) |
| `PackageUpdatedTask.java` | Uses `APP_WIDGET_FILTER` (no redundant instanceof) |

### Resources

| Resource | Purpose |
|----------|---------|
| `res/values/strings.xml` | `widget_stack_description`, `widget_stack_page_description`, `widget_stack_editor_title`, `widget_stack_add_widget`, `widget_stack_remove_widget`, `widget_stack_drag_handle` |
| `res/layout/widget_stack_editor.xml` | Editor bottom sheet layout |
| `res/layout/widget_stack_editor_item.xml` | Editor list item (shared by widget rows and add row) |
| `res/drawable/ic_add_widget.xml` | 24dp plus icon for add row |
| `res/drawable/ic_drag_handle.xml` | Drag handle icon |
| `res/drawable/ic_edit.xml` | Edit icon for popup shortcut |
| `res/drawable/bg_preview_rounded.xml` | Rounded preview thumbnail background |
| `res/drawable/bg_icon_badge.xml` | App icon badge background |
| `res/values/ids.xml` | `editor_drag_state_tag`, `editor_ripple_tag` |

---

## Design Decisions

### Why CollectionInfo?

Widget stacks follow the same "parent container with child items" pattern as folders. By extending `CollectionInfo`, stacks inherit the `_id`-based containment model, `BgDataModel` integration, and the `mPendingCollectionInfo` placeholder system from the loader.

### Why not a container constant?

Unlike `CONTAINER_DESKTOP` (-100) or `CONTAINER_HOTSEAT` (-101), widget stacks use their **row ID** as the container value for children. This matches the existing folder convention: `container > 0` means "inside the item with `_id` = container". The `isInsideCollection()` utility method documents this convention.

### Why separate data mutation from DB persistence?

`assignWidget()` only mutates fields. The three callers (`Workspace.addWidgetToStack`, `Launcher.addWidgetToExistingStack`, `Launcher.createStackFromExternalDrop`) each need different persistence patterns -- INSERT for new widgets from the picker, UPDATE for existing widgets being moved. Separating mutation from persistence makes this explicit at each call site.

### Why MAX_STACK_SIZE = 6?

Six widgets is enough to be useful without making the dot indicator illegible or the swipe navigation tedious. The limit is enforced at both the data level (`WidgetStackInfo.add()`) and the UI level (`willAddToExistingWidgetStack()` rejects drops when full).
