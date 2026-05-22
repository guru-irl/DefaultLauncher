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
    should use the `clean_launcher` fixture instead.

    Seeds the workspace to the canonical two-icon fixture (Settings at (0,2),
    Chrome at (1,2)) via WorkspaceSeedReceiver so every session starts
    deterministically with no accumulated icons or widgets.
    """
    drv = LauncherDriver(device)
    adb_setup.seed_workspace(device)
    drv.go_home()
    yield drv


def _has_seed_icon(d: u2.Device) -> bool:
    """True if the canonical seed icon (Settings) is on the workspace.

    O(1) probe: one resourceId + description selector. Fast path in
    _wake_and_home that avoids the expensive child(descriptionMatches)
    count over a large accessibility tree.
    """
    return d(resourceId=S.ID_WORKSPACE).child(description=S.SEED_ICON_DESC).exists


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
        if _has_seed_icon(d):
            # Always press HOME to fire onNewIntent() → mAppsView.reset(),
            # clearing mSearchState. Without this, a prior test's clear_search()
            # can leave ACTIVE_EMPTY which causes the next drawer open to show
            # an empty search_results_list_view. See docs/changes/076.
            d.press("home")
            time.sleep(0.3)
            yield
            return
        # Seed icon missing (test dragged it away or pm clear ran).
        # Re-seed rather than drag — produces a deterministic workspace.
        adb_setup.seed_workspace(d)
        launcher.go_home()
        d.press("home")
        time.sleep(0.3)
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
    # Final workspace probe — use seed check instead of expensive count query.
    if not _has_seed_icon(d):
        adb_setup.seed_workspace(d)
        launcher.go_home()
    yield


@pytest.fixture
def clean_launcher(launcher: LauncherDriver) -> Iterator[LauncherDriver]:
    """Per-test: wipes launcher state and re-seeds the canonical workspace.

    Use when a test mutates persistent state (drag, settings change,
    folder create). Produces a deterministic starting state (Settings +
    Chrome on the workspace) without drag gestures.
    """
    adb_setup.seed_workspace(launcher.d)
    launcher.go_home()
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
