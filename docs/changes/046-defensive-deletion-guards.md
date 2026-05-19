# 046: Defensive Deletion Guards Against Transient Service Failures

## Summary

Prevents permanent loss of workspace items (apps, folders, widgets, and widget
stacks) when system services return spurious null/false answers under memory
pressure — typically observed after exiting fullscreen games or videos. Adds a
package-presence double-check at every "package not installed" deletion site
and a circuit breaker that aborts mass deletions in a single load pass.

## Background

Several user reports described "missing pages and widgets after exiting an app
from fullscreen." Analysis of the loader pipeline identified two parallel kill
paths that share the same flaw: a system service returning "not present" /
"no info" was treated as proof of permanent removal, rather than as
"transiently unavailable."

### Kill path A — apps & folders

`WorkspaceItemProcessor.processAppOrDeepShortcut` deletes a shortcut row when
`ApplicationInfoWrapper.isInstalled()` is false. The wrapper calls
`LauncherApps.getApplicationInfo` and returns null on `NameNotFoundException`.
Under memory pressure following a fullscreen-app exit, this AIDL path can
transiently fail for packages that are actually installed. The deletion runs,
the icon is gone, and if all of a folder's children are deleted,
`sanitizeFolders` removes the folder too.

### Kill path B — widgets & widget stacks

`WidgetInflater.inflateAppWidget` returns `TYPE_DELETE` when
`AppWidgetManager.getAppWidgetInfo()` returns null and the widget's restore
status is `RESTORE_COMPLETED`. The same transient pattern applies — a brief
window where the launcher's `AppWidgetHost` is rebinding causes every widget
on the workspace to be deleted in one bind. Widget stacks compound this
because their children inflate sequentially inside a single call; if the
failure window covers that call, every child of the stack dies and the empty
stack is sanitized by the next eligible load.

## Cascade to missing pages

`BgDataModel.collectWorkspaceScreens()` derives the workspace page set from
`itemsIdMap`. When every item on a screen is deleted, no row references that
screen, the page disappears from the binder's screen order, and any
`CellLayout` for it is stripped by `Workspace.stripEmptyScreens()`.

## Fixes

### 1. New: `ServiceReadiness`

`src/com/android/launcher3/util/ServiceReadiness.java`

Two utilities used at every deletion site:

- `isPackageProbablyInstalled(Context, String pkg, UserHandle user)` —
  performs a secondary lookup via `PackageManager.getPackageInfo` with
  `MATCH_UNINSTALLED_PACKAGES | MATCH_DISABLED_COMPONENTS`. This is a
  different IPC path than `LauncherApps.getApplicationInfo` used by
  `ApplicationInfoWrapper`. Returns false **only** on `NameNotFoundException`;
  any other exception (`RemoteException`, `DeadObjectException`) is treated as
  "probably installed" so the caller defers deletion rather than commits it.
- `snapshot(Context)` — compact state string (currently `boot=<bool>`)
  attached to every deletion log line for forensics.

### 2. WidgetInflater guard

`src/com/android/launcher3/widget/WidgetInflater.kt`

Both `TYPE_DELETE` branches (the in-restore path at the first null check and
the `RESTORE_COMPLETED` path at the second) now run the
`ServiceReadiness.isPackageProbablyInstalled` check against the widget's
provider package. If the package is still installed, the inflater returns
`TYPE_PENDING` instead — the widget renders as a pending placeholder this
bind and re-inflates on the next bind, when the host has recovered.

### 3. WorkspaceItemProcessor guard

`src/com/android/launcher3/model/WorkspaceItemProcessor.kt`

Both deletion branches in `processAppOrDeepShortcut`
(`APP_NOT_RESTORED_OR_INSTALLING` and `APP_NOT_INSTALLED_EXTERNAL_MEDIA`) now
double-check via `ServiceReadiness` before calling `c.markDeleted(...)`. If
the secondary probe confirms the package, the row is kept untouched; on the
next bind, when `LauncherApps` is back, the shortcut binds normally.

### 4. Circuit breaker in `LoaderCursor.commitDeleted()`

`src/com/android/launcher3/model/LoaderCursor.java`

A single-load circuit breaker that aborts deletions when the count exceeds a
small absolute floor **and** a fraction of the total cursor rows:

- `MASS_DELETE_FLOOR = 3`
- `MASS_DELETE_RATIO_DIVISOR = 4` (25%)

If both thresholds are exceeded, the cursor logs an error with the service
snapshot, clears the pending-delete list, and returns false from
`commitDeleted()`. The rows remain in the DB and the loader retries on the
next bind. Single-item deletions (the common legitimate case — one uninstall
at a time) are unaffected.

This is the global safety net: even if a future failure mode bypasses guards
2 and 3, mass deletion in a single pass is prevented.

## Why this is correct on the healthy path

All four changes silently no-op when services are healthy:

- `isPackageProbablyInstalled` returns true ⇒ deletion deferred only if the
  package is actually present. Genuinely uninstalled packages still throw
  `NameNotFoundException` from `getPackageInfo`, so legitimate cleanups still
  run.
- The circuit breaker requires both a minimum absolute count (3+) and a
  minimum ratio (25%). Typical loads delete 0–1 items.

Validated on emulator: all 5 stress variants (force-stop + cold home, 5×
rapid restart, rotate + kill + home, widget-host clear, density change) keep
all 8 workspace rows intact with the fixes installed. None of the deferral
log lines fired, confirming no false positives.

## Diagnostic logging

Every deletion site now emits `ServiceReadiness.snapshot(...)` inline with
the deletion reason. The next time a user reports missing items, `logcat`
will include the boot-completed state and (in future revisions) any other
service-readiness flags we add, making root-cause analysis unambiguous.

## Modified Files

| File | Change |
|------|--------|
| `src/com/android/launcher3/util/ServiceReadiness.java` | **new** — `isPackageProbablyInstalled` + `snapshot` |
| `src/com/android/launcher3/widget/WidgetInflater.kt` | Guard both `TYPE_DELETE` returns with package double-check |
| `src/com/android/launcher3/model/WorkspaceItemProcessor.kt` | Guard both app-not-installed `markDeleted` calls with package double-check |
| `src/com/android/launcher3/model/LoaderCursor.java` | Circuit breaker in `commitDeleted()` (floor 3, ratio 1/4) |

## Design Decisions

**Why a separate `getPackageInfo` probe rather than reusing
`ApplicationInfoWrapper`?** Both `ApplicationInfoWrapper` and the existing
`LauncherApps.isPackageEnabled` go through the same `LauncherApps` AIDL. When
that path fails transiently, both report "not installed" together — they're
correlated failures. `PackageManager.getPackageInfo` goes through a distinct
binder transaction with `system_server`, so it succeeds in cases where
`LauncherApps` is briefly mid-rebind.

**Why `TYPE_PENDING` rather than `TYPE_REAL` placeholder?** Returning
`TYPE_PENDING` keeps the existing pending-widget machinery responsible for
recovery — `PendingAppWidgetHostView` shows the widget as inflating, and the
next bind retries. No new state machine.

**Why an absolute floor *and* a ratio for the circuit breaker?** A pure
absolute threshold would block legitimate uninstall cleanups when users
remove a handful of apps. A pure ratio would always trip on workspaces with
very few items. The conjunction targets only the "many items lost at once"
pattern that characterizes service failure.

**Why not retry inside the loader?** Retry-with-backoff inside a single
`LoaderTask` would block the model executor and delay the visible
workspace. The chosen approach lets the bind proceed (showing pending
widgets / preserved icons) and relies on the next natural rebind for
recovery, which is bounded by normal config-change cadence.
