# Widget Deletion Prevention — Design Spec

**Date:** 2026-05-25  
**Supersedes:** `docs/plans/005-deletion-safety-v2.md` (the `PackagePresenceVerifier` approach is replaced entirely)  
**Status:** Approved — ready for implementation planning

---

## Problem statement

After exiting a fullscreen landscape game, the launcher's model reloads during a brief period (~9 seconds observed) when AppWidgetService returns 0 providers. During this window, `getLauncherAppWidgetInfo()` returns null for every widget. `ServiceReadiness.isPackageProbablyInstalled()` — the current last-resort guard — also throws `NameNotFoundException` under the same memory pressure, so it returns `false`. Both probes fail simultaneously, and every widget in the DB is permanently deleted.

Additionally, `WorkspaceLayoutManager.addInScreen:140` crashes when `child.getTag()` is null — caused by `attachViewToHostAndGetAttachedView()` returning a new view object that doesn't inherit the original view's tag.

The correct fix is a policy change: **widgets must never be auto-deleted from the database.** Only the user can remove a widget.

---

## Core principle

> A widget is present on the workspace until the user explicitly removes it.

Consequences:
- **Transient service failure** → launcher shows the widget as-is (real view still rendered from the prior frame) or as a pending placeholder. It recovers automatically on the next model bind.
- **Provider package genuinely uninstalled** → launcher shows a dimmed `UnavailableWidgetView` placeholder with an × button. Widget stays in DB. User taps × to permanently remove.
- **Provider package reinstalled** → on next model bind, `TYPE_REAL` is returned, placeholder is replaced with the real widget automatically.

---

## API design

### New constant: `WidgetInflater.TYPE_MISSING = 3`

Indicates the widget provider is confirmed absent. The widget record is kept in the DB; the caller creates a placeholder view.

Full decision table for `WidgetInflater.inflateAppWidget()`:

| `appWidgetInfo` | `ServiceReadiness.isPackageProbablyInstalled()` | Result |
|---|---|---|
| non-null | — | `TYPE_REAL` (unchanged) |
| null | `true` (flaky service, package probably present) | `TYPE_PENDING` (unchanged) |
| null | `false` (package confirmed gone) | **`TYPE_MISSING`** (new) |

`TYPE_DELETE` is **removed** from all widget inflation paths. `WorkspaceItemProcessor`'s `TYPE_DELETE` branch for widgets becomes a defensive log-and-skip guard (dead code path).

### `ServiceReadiness` role change

`isPackageProbablyInstalled()` becomes a **placeholder-type selector** rather than a deletion gate. It is no longer the final step before an irrecoverable DB delete. False positives (transient `NameNotFoundException`) now produce a brief `UnavailableWidgetView` that disappears on the next model bind — acceptable.

---

## New class: `UnavailableWidgetView`

**Package:** `com.android.launcher3.widget`  
**Extends:** `FrameLayout`  
**Layout file:** `res/layout/unavailable_widget.xml`

Visual appearance:
- Background: semi-transparent dark layer (`allAppsScrimColor` @ ~60% alpha) with rounded corners matching the workspace cell radius
- Center: broken-widget/warning icon (`@drawable/ic_broken_widget`, 32dp — create as a VectorDrawable if not present; can reuse `@drawable/ic_warning` or similar if available in the existing drawable set)
- Below icon: `"Widget unavailable"` (body2, medium weight)
- Sub-text: `"App removed — tap × to delete"` (caption, secondary text color)
- Top-right: × `ImageButton` (16dp icon, transparent background, touch target 48×48dp)

Behavior:
- Tag set to `LauncherAppWidgetInfo` via `prepareAppWidget(view, item)` — satisfies `addInScreen` tag contract
- × button: calls `Launcher.removeWorkspaceItem(item)` → `deleteItemFromDatabase(item)` + `deleteAppWidgetId(item.appWidgetId)`
- Long-press: inherits `ItemLongClickListener.INSTANCE_WORKSPACE` (drag-to-remove still works)
- Occupies the same `spanX × spanY` cell span as the original widget

Widget stack integration: each missing child in a `WidgetStackInfo` shows an `UnavailableWidgetView` in its swipe slot. The stack container itself remains visible. The user can swipe to the missing slot and tap × to remove that child.

---

## File changes

### `WidgetInflater.kt`

1. Add `const val TYPE_MISSING = 3`
2. In `inflateAppWidget()` (RESTORE_COMPLETED branch, line ~194-216): change the `ServiceReadiness.isPackageProbablyInstalled` false branch from `return InflationResult(TYPE_DELETE, ...)` to `return InflationResult(TYPE_MISSING, reason=removalReason, ...)`
3. In `inflateAppWidget()` (restore-pending branch, line ~99-113): same — `TYPE_DELETE` → `TYPE_MISSING`
4. Javadoc update: document `TYPE_MISSING` alongside `TYPE_PENDING` and `TYPE_REAL`

No other changes to `WidgetInflater.kt`.

### `ItemInflater.kt`

**`inflateAppWidget()` method:**
- Remove lines: `if (type == WidgetInflater.TYPE_DELETE) { writer.deleteItemFromDatabase(item, reason); return null }`
- Add: `TYPE_MISSING -> UnavailableWidgetView(context, item)` branch in the view-creation expression (before calling `prepareAppWidget`)
- `prepareAppWidget(view, item)` is called for `TYPE_MISSING` as for all other types — sets the tag

**`inflateWidgetStack()` method:**
- Remove lines: `if (type == WidgetInflater.TYPE_DELETE) { writer.deleteItemFromDatabase(widgetInfo, reason); continue }`
- Add `TYPE_MISSING` → `UnavailableWidgetView` for that stack slot; add `widgetInfo.id` to `inflatedIds` (so it is not removed from the model by line 172)
- Line 172 (`removeIf`) is kept as-is; since all valid widget children are now in `inflatedIds`, it only removes genuinely non-widget-type content errors

### `WorkspaceItemProcessor.kt`

Line 571, `WidgetInflater.TYPE_DELETE` branch:
```kotlin
WidgetInflater.TYPE_DELETE -> {
    // This branch should not be reached — WidgetInflater no longer returns TYPE_DELETE.
    // Treat as TYPE_MISSING: do not mark deleted; log the unexpected state.
    Log.e(TAG, "TYPE_DELETE from WidgetInflater for widget id=${item.appWidgetId} " +
                "— treating as TYPE_MISSING. See docs/changes/080.")
}
```

### `Launcher.java` — NPE fix in `bindInflatedItems`

Around line 2422, in the `enableWorkspaceInflation()` branch:
```java
// Before:
view = getAppWidgetHolder().attachViewToHostAndGetAttachedView(lv);

// After:
View attached = getAppWidgetHolder().attachViewToHostAndGetAttachedView(lv);
if (attached == null) attached = lv;                        // null-safe fallback
if (attached.getTag() == null) attached.setTag(lv.getTag()); // preserve tag
view = attached;
```

### `WorkspaceLayoutManager.java` — defensive NPE guard at line 140

```java
ItemInfo info = (ItemInfo) child.getTag();
if (info == null) {
    Log.e(TAG, "addInScreen: child " + child.getClass().getSimpleName() + " has null tag — skipping");
    return;  // belt-and-braces; see docs/changes/080
}
int childId = info.getViewId();
```

### `res/layout/unavailable_widget.xml` (new file)

FrameLayout container with:
- Background drawable: `@drawable/widget_unavailable_background` (rounded rect, `allAppsScrimColor` 60% alpha)
- `LinearLayout` (vertical, center-gravity) containing icon + two TextViews
- `ImageButton` id=`remove_unavailable_widget`, positioned top-right

### `res/drawable/widget_unavailable_background.xml` (new file)

Shape drawable reusing the existing `@color/all_apps_scrim_color` at 60% alpha with corner radius matching `@dimen/dynamic_grid_cell_border_spacing`.

---

## Test changes

File: `tests-e2e/regression/test_deletion_safety.py` (new file, supersedes the plan 005 test spec)

### `test_widget_unavailable_view_shown_on_missing_provider`
- Setup: seed workspace with KWGT widget via `WorkspaceSeedReceiver` (or direct DB insert for the canary widget)
- Stimulus: `adb shell am broadcast -p com.guru.defaultlauncher -a com.guru.defaultlauncher.test.SIMULATE_NULL_PROVIDER` → triggers `WidgetInflater.sSimulateNullProvider = true` → force reload
- Assert: workspace cell shows a view with `id/remove_unavailable_widget` visible; DB row still present; no `markDeleted` in logcat
- Teardown: `SIMULATE_NULL_PROVIDER` clear broadcast, force reload; verify real widget returns

### `test_user_remove_placeholder_deletes_db_row`
- Setup: same as above (placeholder showing)
- Stimulus: tap `id/remove_unavailable_widget`
- Assert: cell empty; DB row gone; widget host ID unregistered from AppWidgetService

### `test_widget_survives_service_restart_and_recovers`
- Setup: install and bind a test widget
- Stimulus: `adb shell am force-stop com.guru.defaultlauncher` then restart; during this restart the test momentarily sets `SIMULATE_NULL_PROVIDER`, clears it, and restarts once more
- Assert: after the second restart, the real widget is visible (TYPE_REAL was returned); no DB deletion occurred

### Debug seam: `WidgetInflater.sSimulateNullProvider`
```kotlin
// DEBUG only — never shipped in release builds
@VisibleForTesting
@JvmStatic
var sSimulateNullProvider: Boolean = false
```
Wired via the existing `WorkspaceSeedReceiver` (which is already a DEBUG-only BroadcastReceiver) — add a new action `com.guru.defaultlauncher.test.SIMULATE_NULL_PROVIDER` to its `onReceive` switch, toggling `WidgetInflater.sSimulateNullProvider`.

---

## What is NOT changed

- `ServiceReadiness.isPackageProbablyInstalled()` — kept as-is, role changes from deletion gate to placeholder-type selector
- `LoaderCursor.markDeleted` / `commitDeleted` circuit-breaker — kept for non-widget items (shortcuts, etc.) which keep their existing deletion behavior
- `PendingAppWidgetHostView` — kept unchanged; `TYPE_PENDING` path is unaffected
- Widget stack `WidgetStackInfo` / `WidgetStackView` — structure unchanged; only the inflation handling of children changes
- `docs/plans/005-deletion-safety-v2.md` — marked superseded in the master plan; `PackagePresenceVerifier` is NOT built

---

## Change doc number

`docs/changes/080-widget-deletion-prevention.md` (next in sequence per superplan)

---

## Risk flags

- **`UnavailableWidgetView` × button click-target overlap with long-press drag**: the × is 48×48dp touch target and long-press is on the parent view. Mitigate: × button consumes touch events (`clickable=true`) so long-press doesn't fire on it; everywhere else long-press still works.
- **Widget stack with 0 children**: if all children are unavailable and user removes them all via ×, the `WidgetStackInfo` remains in DB with empty contents. A subsequent model load would try to inflate an empty stack. `inflateWidgetStack` should return a zero-child `WidgetStackView` (which takes no space), or delete the parent container when it detects 0 remaining children after user × removal. Recommend: when × on the last child is pressed, also delete the parent `WidgetStackInfo` row.
- **`attachViewToHostAndGetAttachedView` returning null**: the null-safe fallback to `lv` means the original pre-attach view is used. This may not be perfectly bound to the host, but it prevents the crash and is recoverable on next bind.
