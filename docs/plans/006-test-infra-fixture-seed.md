# Plan 006 — Test Infrastructure: Deterministic Workspace Fixture

Addresses the flakiness and state-accumulation problem described by the user after
Session 5. The test suite currently seeds the workspace by *dragging* a Chrome icon
from the drawer — a gesture that fires once per cold path. Over a long session this
accumulates 15+ duplicate icons, a Calendar widget from an accidental drag, and
increases every accessibility query cost. Tests then depend on "whatever is on screen"
rather than a known, stable state.

**Slot:** T0.5 — executes before T3.1 Phase 2, after T3.1 Phase 1 (already shipped).

## Problem statement

```
Before each test:
  _wake_and_home → _workspace_icon_count → d(ID_WORKSPACE).child(".+").count
                                         ↑ queries whole tree: 15 icons + widget = slow
```

Accumulated state sources:
1. `_ensure_workspace_has_icon` drags one icon per cold-path call.
   Cold path fires every time workspace is empty: after `pm clear`, after force-stop,
   after a test accidentally removes the only icon.
2. `test_launch_app_from_hotseat` leaves the Phone dialer in the activity stack,
   poisoning `app_current()` for the whole session (worked around in Session 5 but
   the root accumulation is unaddressed).
3. No test teardown: side effects leak forward.

Target state after this plan:
- Every test session starts with **exactly** one known icon at a fixed grid cell.
- `_wake_and_home` fast path checks for **that specific icon** (by resource ID +
  description), not "any labelled child". Cost: one selector probe (~50ms).
- No drag gestures in the normal fixture path. Drag tests that intentionally move
  icons still work but restore on teardown.
- Session runtime returns to the ~5 min baseline (no accessibility tree bloat).

## Design

### Seed state

Two shortcuts pinned to workspace cells (0, 2) and (1, 2) — row 2, columns 0-1:

| Field    | Icon 1 (anchor)        | Icon 2 (folder target)       |
|----------|------------------------|------------------------------|
| title    | "Settings"             | "Chrome"                     |
| package  | `com.android.settings` | `com.android.chrome`         |
| cellX    | 0                      | 1                            |
| cellY    | 2                      | 2                            |
| container | CONTAINER_DESKTOP     | CONTAINER_DESKTOP            |
| screen   | 0                      | 0                            |
| itemType | ITEM_TYPE_APPLICATION  | ITEM_TYPE_APPLICATION        |

Two icons are seeded so that folder-creation tests can drag one onto the other
(cells (0,2) and (1,2) are adjacent) without needing additional setup. Tests
that don't need folders use `d(description="Settings")` as the workspace probe.

"Settings" is the primary anchor: always installed, description is exactly the
string "Settings", never in the hotseat.

Cell (0, 2) is the third row, leftmost column: visible on the workspace without
scrolling and clear of the hotseat, search bar, and first two rows where some
skins place widget suggestions.

### Seed mechanism

Use the launcher's existing `LauncherProvider` content-provider endpoint
(`content://com.guru.defaultlauncher.settings/favorites`). The provider is not
exported to third-party apps but is accessible via `adb shell content insert`
running under the launcher's own UID (using `run-as`).

```bash
adb shell run-as com.guru.defaultlauncher \
  content insert \
    --uri "content://com.guru.defaultlauncher.settings/favorites" \
    --bind "title:s:Settings" \
    --bind "intent:s:#Intent;action=android.intent.action.MAIN;category=android.intent.category.LAUNCHER;component=com.android.settings/.Settings;launchFlags=0x10200000;end" \
    --bind "container:i:-100" \
    --bind "screen:i:0" \
    --bind "cellX:i:0" \
    --bind "cellY:i:2" \
    --bind "spanX:i:1" \
    --bind "spanY:i:1" \
    --bind "itemType:i:0" \
    --bind "profileId:i:0" \
    --bind "rank:i:0"
```

If `run-as` + `content insert` fails (environment restriction), fall back to
direct `sqlite3` via `run-as`:

```bash
adb shell run-as com.guru.defaultlauncher sqlite3 \
  /data/data/com.guru.defaultlauncher/databases/launcher.db \
  "INSERT INTO favorites (title, intent, container, screen, cellX, cellY, spanX, spanY, itemType, profileId, rank) \
   VALUES ('Settings', '#Intent;...end', -100, 0, 0, 2, 1, 1, 0, 0, 0);"
```

Both commands are tried in sequence by the fixture; first success wins. The
fixture logs which path was taken so failures are diagnosable.

After the insert, the launcher must be restarted so it loads the seeded row:
```python
d.shell(f"am force-stop {S.PACKAGE}")
time.sleep(0.5)
d.shell(f"am start -n {S.PACKAGE}/{S.LAUNCH_ACTIVITY} -f 0x10200000")
```

### Session fixture (`conftest.py::launcher`)

Replace `_ensure_workspace_has_icon` call in `launcher` with `_seed_workspace`:

```python
@pytest.fixture(scope="session")
def launcher(device: u2.Device) -> Iterator[LauncherDriver]:
    drv = LauncherDriver(device)
    adb_setup.set_as_default_home()
    adb_setup.seed_workspace(device)   # <-- replaces _ensure_workspace_has_icon
    drv.go_home()
    yield drv
```

`adb_setup.seed_workspace(d)` does:
1. `pm clear {S.PACKAGE}`
2. Wait 1s
3. `am start` launcher
4. Wait 2s (model bind)
5. Insert via content provider (with sqlite3 fallback)
6. `am force-stop` + `am start` to trigger model reload
7. Wait 2s
8. Verify: `d(description="Settings").wait(timeout=10s)` — raises if seed failed

### Per-test fast path (`_wake_and_home`)

Replace the current `_workspace_icon_count(d) >= 1` check with the specific icon:

```python
SEED_ICON_DESC = "Settings"   # in lib/selectors.py

def _has_seed_icon(d: u2.Device) -> bool:
    return d(resourceId=S.ID_WORKSPACE)(description=SEED_ICON_DESC).exists
```

If `_has_seed_icon` returns False mid-session (a test dragged the icon away or
`pm clear` was called), fall back to re-seeding rather than the drag approach:

```python
if not _has_seed_icon(d):
    adb_setup.seed_workspace(d)
    drv.go_home()
```

### Drag suppression

The drag-based `_ensure_workspace_has_icon` / `_scaffold_drag_anchor_to_workspace`
helpers are **deleted**. Tests that intentionally test drag behavior (future stress
tests) use a per-test fixture that seeds and tears down its own drag state.

### `clean_launcher` fixture update

The `clean_launcher` per-test fixture (used by `test_drawer_cold_start`) currently
calls `reset_launcher_data` (pm clear) and then `_ensure_workspace_has_icon`. After
this plan it calls `seed_workspace` instead:

```python
@pytest.fixture
def clean_launcher(launcher: LauncherDriver) -> Iterator[LauncherDriver]:
    adb_setup.seed_workspace(launcher.d)
    launcher.go_home()
    yield launcher
```

## Files changed

| File | Change |
|------|--------|
| `tests-e2e/lib/adb_setup.py` | Add `seed_workspace(d)` — pm clear + content insert + restart |
| `tests-e2e/lib/selectors.py` | Add `SEED_ICON_DESC = "Settings"` |
| `tests-e2e/conftest.py` | Replace `_ensure_workspace_has_icon` with `seed_workspace`; update `_wake_and_home` and `clean_launcher` fixtures; delete drag helpers |

No launcher source changes. This is pure test infrastructure.

## Regression tests

No new tests — this plan is itself a test infrastructure fix. The existing 34-test
suite is the verification: all tests must pass in under 8 minutes on a fresh emulator
session. The specific acceptance criteria:

1. **Determinism**: two consecutive full runs produce identical pass/fail counts.
2. **No accumulation**: after the full suite, the workspace has exactly 1 icon
   (the seed Settings icon), confirmed by `d(description="Settings").count == 1`
   in a post-suite sanity fixture.
3. **Speed**: full suite completes in < 8 minutes (target 6) on a fresh session.
4. **Isolation**: killing and restarting the emulator, then running again, yields
   the same results (no cross-session state dependency).

## Why not emulator snapshot?

Emulator snapshots are fast (< 1s restore) but:
- Require a checked-in binary artifact (snapshot file, ~200MB) or regeneration script
- `emulator -snapshot-load` requires `-no-snapshot-save` in the test run to avoid
  overwriting the baseline — easy to get wrong
- Snapshots are opaque; broken snapshots produce cryptic failures
- Not composable with the existing `pm clear`-based `clean_launcher` fixture

The content-provider seed is 50 lines of Python, fully auditable, and reproducible
on any fresh AVD without a binary artifact.

## Known risk

**Content provider accessibility**: `adb shell run-as ... content insert` may fail
on some Android versions if the provider enforces write permission beyond the UID
check. The sqlite3 fallback bypasses this. If both fail, add a third path: a
`DEBUG`-only `BroadcastReceiver` in the launcher that accepts a seed command and
calls `LauncherModel.resetAndReloadLauncher()` — but this path requires a source
change and adds 30 minutes of implementation time. Try the no-source-change paths
first.

## Implementation order

1. `adb_setup.seed_workspace()` — write and smoke-test manually before wiring fixtures.
2. Update `conftest.py` (session fixture only first, verify full suite still passes).
3. Update `_wake_and_home` fast path.
4. Update `clean_launcher`.
5. Delete drag helpers.
6. Run full suite twice; confirm both pass with identical counts.
7. Write change doc `docs/changes/076-test-infra-fixture-seed.md`.
8. Commit.
