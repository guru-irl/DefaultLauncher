# 080 — Widget deletion prevention: TYPE_MISSING + UnavailableWidgetView + NPE fix

Fixes permanent widget loss after AppWidgetService or PackageManager is
transiently unavailable (e.g., after exiting a fullscreen landscape game).
Also fixes the recurring `NullPointerException` at
`WorkspaceLayoutManager.addInScreen:140`.

## Root cause (confirmed 2026-05-25 from device logs)

AppWidgetService returned **0 providers for ~9 seconds** after a Samsung game
exited (observed as `getInstalledProvidersForProfiles t:0 r:0` in the
AppWidget service history at 15:45:57). During this window the launcher's
`LoaderTask` ran `WidgetInflater`, which returned `TYPE_DELETE` for every
widget whose `AppWidgetInfo` resolved to null. `ServiceReadiness.isPackage-
ProbablyInstalled()` also returned `false` under the same memory pressure
(caught `NameNotFoundException` instead of the package info). Both guards
failed simultaneously → permanent DB deletion → widget stack gone.

## Policy change: never auto-delete widgets

Widgets are kept in the database until the user explicitly removes them via
the × button on the placeholder. The transient-failure case (today's incident)
recovers automatically on the next model bind.

### `WidgetInflater.TYPE_MISSING = 3`

New constant replacing all `TYPE_DELETE` exits in the null-`appWidgetInfo`
paths:
- Restore-pending branch (line ~114): package absent → `TYPE_MISSING`
- `RESTORE_COMPLETED` branch (line ~216): `appWidgetInfo` null → `TYPE_MISSING`
- Search-widget branch (line ~45): provider not found → `TYPE_MISSING`

`TYPE_DELETE` is kept in the constants for backward-compatibility but is no
longer returned by `WidgetInflater`.

### `UnavailableWidgetView` (new)

Dimmed placeholder (`unavailable_widget_bg.xml` 70% black rounded-rect) with:
- `@drawable/ic_warning` icon
- "Widget unavailable" text
- × `ImageButton` (`@id/remove_unavailable_widget`) that calls
  `Launcher.removeItem(view, item, deleteFromDb=true)` — the sole deletion path

Tag set to `LauncherAppWidgetInfo` via `prepareAppWidget()` to satisfy the
`WorkspaceLayoutManager.addInScreen` tag contract.

### `ItemInflater` changes

- `inflateAppWidget()`: `TYPE_MISSING` / `TYPE_DELETE` → `UnavailableWidgetView`
  (no `deleteItemFromDatabase` call)
- `inflateWidgetStack()`: each missing child → `UnavailableWidgetView` slot
  with a callback that removes the child and, if the stack is then empty,
  removes the parent `WidgetStackInfo` too
- `prepareAppWidget()` signature widened from `AppWidgetHostView` to `View`
- `WidgetStackView.addWidgetView()` already accepted `View`

### `WorkspaceItemProcessor` changes

- `TYPE_DELETE` branch: `markDeleted` → log-and-fall-through (dead code guard)
- Unrestored-pending branch: `markDeleted` + `return` → `FileLog.w` + fall-
  through, so the item stays in the model and binds as `UnavailableWidgetView`

## NPE fix (`WorkspaceLayoutManager.addInScreen:140`)

**Crash (recurring since 2026-05-23):**
```
NullPointerException: ItemInfo.getViewId() on null reference
    at WorkspaceLayoutManager.addInScreen(:140)   ← child.getTag() is null
    at Launcher.bindInflatedItems(:2422)
    at BaseLauncherBinder.inflateAsyncAndBind(:378)
```

**Root cause:** `Launcher.bindInflatedItems` called
`attachViewToHostAndGetAttachedView(lv)` which returned a new view object
that did not inherit the tag set by `prepareAppWidget`.

**Fix:** After `attachViewToHostAndGetAttachedView`, copy the tag from the
original view; fall back to the original if the method returns null. Added
a defensive null guard in `addInScreen` as belt-and-braces.

## Debug test seam

`WidgetInflater.sSimulateNullProvider` (debug builds only) forces
`getLauncherAppWidgetInfo` to return null for every widget. Toggled via:

```
adb shell am broadcast -p com.guru.defaultlauncher \
    -a com.guru.defaultlauncher.test.SIMULATE_NULL_PROVIDER
# clear: add --ez disable true
```

Wired through `WorkspaceSeedReceiver`.

## Files changed

| File | Change |
|---|---|
| `WidgetInflater.kt` | `TYPE_MISSING`, `sSimulateNullProvider`, `TYPE_DELETE` exits → `TYPE_MISSING` |
| `UnavailableWidgetView.java` | new placeholder class |
| `res/layout/unavailable_widget.xml` | placeholder layout |
| `res/drawable/unavailable_widget_bg.xml` | rounded-rect background |
| `res/values/strings.xml` | `unavailable_widget_title`, `unavailable_widget_remove_desc` |
| `ItemInflater.kt` | consume `TYPE_MISSING`, no `deleteItemFromDatabase` on widgets |
| `WidgetStackView.java` | `addWidgetView(View, ...)` already present; `removeChildWidget(int)` already present |
| `WorkspaceItemProcessor.kt` | `TYPE_DELETE` + unrestored-pending → log-and-skip |
| `Launcher.java` | tag preservation after `attachViewToHostAndGetAttachedView` |
| `WorkspaceLayoutManager.java` | null-tag guard in `addInScreen` |
| `WorkspaceSeedReceiver.java` | `ACTION_SIMULATE_NULL_PROVIDER` handler |
| `tests-e2e/regression/test_deletion_safety.py` | 3 regression tests |

Supersedes `docs/plans/005-deletion-safety-v2.md` (`PackagePresenceVerifier`
approach abandoned — the no-auto-delete policy is simpler and more robust).

## Verification

- `assembleDebug`: clean.
- Full test suite (47 tests): 39 passed, 0 failed, 2 xfailed, 6 skipped. Exit 0 in 7 min.
