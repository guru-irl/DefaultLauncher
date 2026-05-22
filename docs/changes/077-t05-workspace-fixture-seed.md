# 077 — T0.5: Deterministic workspace fixture seed

Replaces the drag-based workspace scaffold with a `WorkspaceSeedReceiver`
that directly writes to `ModelDbController`, producing a known two-icon
workspace on every test session start. Resolves workspace state accumulation
(15+ Chrome icons, Calendar widget) that degraded test performance and
reliability.

## Problem

`_ensure_workspace_has_icon()` in `conftest.py` dragged one Chrome icon from
the drawer to the workspace each time the workspace was empty. Over a long
session this accumulated:
- 15+ Chrome icons (each cold-path call adds one)
- A Calendar widget from a stray gesture during a drag
- A bloated accessibility tree making `_workspace_icon_count` slow (~5s per query)

Tests were also non-deterministic: any test that navigated away and back could
trigger a new drag, adding another icon.

## Solution

**WorkspaceSeedReceiver** (new, `src/.../testing/WorkspaceSeedReceiver.java`):
- A `DEBUG`-only exported `BroadcastReceiver` that receives `SEED_WORKSPACE` broadcasts
- Calls `ModelDbController.delete()` to remove all existing workspace items
- Calls `ModelDbController.insert()` to add Settings at (0,2) and Chrome at (1,2)
- Calls `model.forceReload()` to refresh the UI

The `ContentProvider` approach was blocked by Android 17's `Same process
should call model directly` restriction; using `ModelDbController` directly
(within the same process, on MODEL_EXECUTOR) bypasses this cleanly.

**adb_setup.seed_workspace()** (new):
```
pm clear com.guru.defaultlauncher
am start launcher
am broadcast -p com.guru.defaultlauncher -a ...SEED_WORKSPACE
verify Settings icon appears on workspace within 10s
```

Note: `pm clear` does not auto-restart the home app on Android 17; explicit
`am start` is required before the broadcast.

## Seed state

| Icon | Cell | Purpose |
|------|------|---------|
| Settings | (0, 2) | Primary anchor — `d(description="Settings").exists` fast path |
| Chrome | (1, 2) | Folder target — adjacent to Settings for folder-creation tests |

Two adjacent icons allow future tests to drag one onto the other to create
a folder without additional setup.

## Files changed

| File | Change |
|------|--------|
| `src/com/android/launcher3/testing/WorkspaceSeedReceiver.java` | New DEBUG-only receiver |
| `AndroidManifest-common.xml` | Register receiver as exported (needed for adb shell) |
| `tests-e2e/lib/adb_setup.py` | Add `seed_workspace()`, add `SEED_ICON_DESC` |
| `tests-e2e/lib/selectors.py` | Add `SEED_ICON_DESC`, `SEED_ICON_2_DESC` |
| `tests-e2e/conftest.py` | Replace drag scaffold with `seed_workspace()`; add `_has_seed_icon()`; delete all drag helpers |

## Verification

- `assembleDebug`: clean.
- `tests-e2e/smoke/ + regression/ + visuals/`: all 34 tests pass.
- Workspace has exactly 2 icons after full suite.
- Two consecutive runs produce identical pass/fail counts.
