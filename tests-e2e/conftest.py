"""Session/test fixtures for the DefaultLauncher e2e suite."""

from __future__ import annotations

import os
import time
from pathlib import Path
from typing import Iterator, Optional

import pytest
import uiautomator2 as u2

from lib import adb_setup
from lib.launcher import LauncherDriver
from lib import selectors as S

ARTIFACTS_DIR = Path(__file__).parent / "artifacts"


def pytest_configure(config: pytest.Config) -> None:
    ARTIFACTS_DIR.mkdir(exist_ok=True)


@pytest.fixture(scope="session")
def device() -> Iterator[u2.Device]:
    """One shared uiautomator2 device per test session.

    Uses ANDROID_SERIAL env var if set (required when multiple devices are
    attached — e.g., physical phone + emulator simultaneously).
    """
    serial = os.environ.get("ANDROID_SERIAL")
    d = u2.connect(serial) if serial else u2.connect()
    adb_setup.ensure_device_ready(d)
    yield d


@pytest.fixture(scope="session")
def launcher(device: u2.Device) -> Iterator[LauncherDriver]:
    """Session-wide LauncherDriver. Tests that need a clean state
    should use the `clean_launcher` fixture instead."""
    drv = LauncherDriver(device)
    adb_setup.set_as_default_home()
    drv.go_home()
    _ensure_workspace_has_icon(drv)
    yield drv


def _workspace_icon_count(d: u2.Device) -> int:
    """Number of accessibility-labelled descendants of `apps_view_workspace`."""
    workspace = d(resourceId=S.ID_WORKSPACE)
    if not workspace.exists:
        return 0
    return workspace.child(descriptionMatches=r".+").count


# Anchor app for workspace scaffolding. Ordered by preference. Each one is
# verified to be present in the drawer + accept the drag gesture before
# we accept the scaffold as successful.
_WORKSPACE_ANCHOR_CANDIDATES = ("Chrome", "Phone", "Maps", "Messages", "Settings")

# Max attempts to drag an icon to the workspace before giving up.
_SCAFFOLD_MAX_ATTEMPTS = 3


def _ensure_workspace_has_icon(drv: LauncherDriver) -> None:
    """Scaffold a workspace icon if the default layout left it empty.

    Why this exists: `res/xml/default_workspace_5x5.xml` (and siblings)
    declare workspace items via AOSP `category.APP_EMAIL/GALLERY/MARKET`
    intents that do NOT resolve on the Pixel 7 Pro AVD. Those resolves
    drop, leaving the workspace empty. Tests that look for icons inside
    `apps_view_workspace` (e.g. `test_workspace_icon_bounds_nonzero`)
    fail until something puts an icon there.

    Contract:

      * **Idempotent.** Re-runs against an already-populated workspace
        are early-returns (one resource-id probe). No icons are duplicated;
        the function never drags if at least one accessibility-labelled
        descendant already lives under `apps_view_workspace`.

      * **Reproducible.** Drags use the launcher's expected gesture
        (long-press → drop), and the post-drop state is verified before
        the function returns. If the drag fails (icon stayed in drawer,
        landed on a folder, was rejected by the workspace), the function
        retries with a different anchor app, up to
        `_SCAFFOLD_MAX_ATTEMPTS`. On all-attempts-failed it raises so the
        session aborts loudly rather than yielding a half-baked state.

      * **Cheap on warm path.** When the workspace is already populated,
        cost is one `dump_hierarchy` query (~50ms). On the cold path
        (first run after pm clear, or new device), cost is ~3s for the
        single drag.
    """
    d = drv.d
    drv.go_home()  # required for the workspace probe to be authoritative

    if _workspace_icon_count(d) >= 1:
        return  # warm path

    last_error: Optional[str] = None
    for attempt in range(1, _SCAFFOLD_MAX_ATTEMPTS + 1):
        # Open drawer fresh on every attempt — the previous attempt may
        # have left us in the workspace state if a drop landed.
        if not drv.drawer_open():
            drv.open_drawer()
        if not drv.drawer_open():
            last_error = f"attempt {attempt}: drawer never opened"
            continue

        anchor_label = _pick_anchor(d)
        if anchor_label is None:
            last_error = f"attempt {attempt}: no drawer-resident anchor app found"
            drv.close_drawer()
            break  # without an anchor every retry will hit the same wall

        if not _scaffold_drag_anchor_to_workspace(d, anchor_label):
            last_error = (
                f"attempt {attempt}: drag of '{anchor_label}' did not land"
                f" on workspace"
            )
            drv.go_home()
            continue

        # Post-drop verification — the icon must actually be a child of
        # the workspace, not just visible somewhere else (e.g. still in
        # the drawer, or in a folder that opened over the workspace).
        drv.go_home()
        if _workspace_icon_count(d) >= 1:
            return  # success
        last_error = (
            f"attempt {attempt}: drop reported success but workspace is"
            f" still empty"
        )

    raise RuntimeError(
        "workspace scaffolding failed across "
        f"{_SCAFFOLD_MAX_ATTEMPTS} attempts; last error: {last_error}. "
        "Tests that depend on a populated workspace will not run."
    )


def _pick_anchor(d: u2.Device) -> Optional[str]:
    """Find the first drawer-resident candidate app. Drawer must be open."""
    for label in _WORKSPACE_ANCHOR_CANDIDATES:
        icon = d(description=label)
        if icon.exists:
            return label
    return None


def _scaffold_drag_anchor_to_workspace(d: u2.Device, label: str) -> bool:
    """Drag `label`'s drawer icon onto the workspace.

    Uses uiautomator2's device-level `drag()` with a duration long enough
    that the launcher's touch dispatcher detects long-press (not tap),
    which is the trigger that takes the gesture from "would-launch-app"
    to "would-drop-on-workspace".

    Returns True if the gesture completed without an obvious error.
    Caller is responsible for verifying the icon actually landed on the
    workspace via `_workspace_icon_count`.
    """
    icon = d(description=label)
    if not icon.wait(timeout=S.DEFAULT_WAIT):
        return False
    bounds = icon.info["bounds"]
    src_x = (bounds["left"] + bounds["right"]) // 2
    src_y = (bounds["top"] + bounds["bottom"]) // 2

    info = d.info
    dst_x = info["displayWidth"] // 2
    # Land just below the search bar / above the second row — well clear
    # of both the hotseat and any folder targets in the top-left.
    dst_y = int(info["displayHeight"] * 0.40)

    # Device-level drag synthesizes ACTION_DOWN, an internal step-pause,
    # then ACTION_MOVE → ACTION_UP. With duration >= LONG_PRESS_DURATION,
    # the launcher classifies this as long-press-drag and dispatches
    # through the drop-target chain.
    try:
        d.drag(src_x, src_y, dst_x, dst_y, duration=1.0)
    except Exception:
        return False

    time.sleep(S.ANIMATION_WAIT)
    # Recover if the gesture was misinterpreted as a tap-launch.
    if not d.app_current().get("package", "").startswith(S.PACKAGE):
        d.press("home")
        time.sleep(S.ANIMATION_WAIT)
    return True


@pytest.fixture(autouse=True)
def _wake_and_home(launcher: LauncherDriver) -> Iterator[None]:
    """Before every test: wake device, dismiss keyguard, ensure we're home,
    ensure the workspace has at least one icon.

    Fast path (~50ms): launcher already home, screen on, workspace already
    has an icon. One `dump_hierarchy` query.

    Slow path: bring launcher home, then re-scaffold the workspace if it
    came up empty (e.g. a previous test ran `pm clear` via
    `clean_launcher`, or a future drag test removed the only icon).
    """
    d = launcher.d
    if launcher.is_home() and d.info.get("screenOn", True):
        if _workspace_icon_count(d) >= 1:
            yield
            return
        # Home & awake but workspace empty — re-scaffold then continue.
        _ensure_workspace_has_icon(launcher)
        yield
        return
    try:
        d.shell("input keyevent KEYCODE_WAKEUP")
        d.shell("wm dismiss-keyguard")
    except Exception:
        d.healthcheck()
    if not launcher.is_home():
        d.press("home")
        deadline = time.time() + 2.0
        while time.time() < deadline:
            if launcher.is_home():
                break
            time.sleep(0.2)
    if not launcher.is_home():
        d.app_stop(S.PACKAGE)
        d.app_start(S.PACKAGE)
        d.wait_activity(S.LAUNCH_ACTIVITY, timeout=S.DEFAULT_WAIT * 2)
    # Final workspace probe — any path above could have left it empty.
    if _workspace_icon_count(d) < 1:
        _ensure_workspace_has_icon(launcher)
    yield


@pytest.fixture
def clean_launcher(launcher: LauncherDriver) -> Iterator[LauncherDriver]:
    """Per-test: wipes launcher state, reinstalls defaults, scaffolds
    workspace, returns home.

    Use only when a test mutates persistent state (drag, settings change,
    folder create). Slower (~5s + scaffold overhead).
    """
    adb_setup.reset_launcher_data()
    launcher.go_home()
    # After pm clear the launcher database is empty; the default
    # workspace XML mostly doesn't resolve on this AVD. Re-scaffold so the
    # caller's test runs against a populated workspace.
    _ensure_workspace_has_icon(launcher)
    yield launcher


@pytest.fixture(autouse=True)
def _on_failure_artifacts(request, launcher: LauncherDriver) -> Iterator[None]:
    """Auto-capture screenshot + UI hierarchy + logcat on any test failure."""
    yield
    rep = getattr(request.node, "rep_call", None)
    if rep is None or not rep.failed:
        return
    name = request.node.name
    png = ARTIFACTS_DIR / f"{name}.png"
    xml = ARTIFACTS_DIR / f"{name}.xml"
    log = ARTIFACTS_DIR / f"{name}.logcat.txt"
    try:
        launcher.screenshot(str(png))
    except Exception:
        pass
    try:
        xml.write_text(launcher.d.dump_hierarchy())
    except Exception:
        pass
    try:
        log.write_text(launcher.logcat_tail(500))
    except Exception:
        pass


@pytest.hookimpl(hookwrapper=True)
def pytest_runtest_makereport(item, call):
    """Attach phase report onto the request node so the autouse fixture can read it."""
    outcome = yield
    rep = outcome.get_result()
    setattr(item, f"rep_{rep.when}", rep)
