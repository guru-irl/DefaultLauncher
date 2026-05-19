"""Session/test fixtures for the DefaultLauncher e2e suite."""

from __future__ import annotations

import os
import time
from pathlib import Path
from typing import Iterator

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
    """One shared uiautomator2 device per test session."""
    d = u2.connect()
    adb_setup.ensure_device_ready(d)
    yield d


@pytest.fixture(scope="session")
def launcher(device: u2.Device) -> Iterator[LauncherDriver]:
    """Session-wide LauncherDriver. Tests that need a clean state
    should use the `clean_launcher` fixture instead."""
    drv = LauncherDriver(device)
    adb_setup.set_as_default_home()
    drv.go_home()
    yield drv


@pytest.fixture(autouse=True)
def _wake_and_home(launcher: LauncherDriver) -> Iterator[None]:
    """Before every test: wake device, dismiss keyguard, ensure we're home.
    Fast path: if launcher is already foreground and screen is on, do
    nothing. Slow path: HOME key, then hard-restart if still not home."""
    d = launcher.d
    # Fast path: already home and screen on? skip the rest.
    if launcher.is_home() and d.info.get("screenOn", True):
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
    yield


@pytest.fixture
def clean_launcher(launcher: LauncherDriver) -> Iterator[LauncherDriver]:
    """Per-test: wipes launcher state, reinstalls defaults, returns home.

    Use only when a test mutates persistent state (drag, settings change,
    folder create). Slower (~5s overhead)."""
    adb_setup.reset_launcher_data()
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
