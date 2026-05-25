"""Regression: widgets must survive AppWidgetService transient blackout.

Root cause documented in docs/changes/080: AppWidgetService returned 0 providers
for ~9 seconds after a Samsung game exited. During this window, WidgetInflater
returned TYPE_DELETE for every widget, permanently deleting them from the DB.

Fix: TYPE_MISSING replaces TYPE_DELETE. Widgets stay in DB. An UnavailableWidgetView
placeholder is shown. Widget recovers automatically when provider app is back.

Tests use the sSimulateNullProvider debug seam to reproduce the failure on the AVD:
    adb shell am broadcast -p com.guru.defaultlauncher \\
        -a com.guru.defaultlauncher.test.SIMULATE_NULL_PROVIDER
"""

import os
import subprocess
import time

import pytest

from lib import selectors as S

PACKAGE = S.PACKAGE
SERIAL = os.environ.get("ANDROID_SERIAL", "emulator-5554")

ACTION_SIMULATE = "com.guru.defaultlauncher.test.SIMULATE_NULL_PROVIDER"

# Resource ID set on the FrameLayout root of unavailable_widget.xml
ID_UNAVAIL_CONTAINER = f"{PACKAGE}:id/unavailable_widget_container"
ID_UNAVAIL_REMOVE = f"{PACKAGE}:id/remove_unavailable_widget"


def _adb(*args: str) -> str:
    cmd = ["adb", "-s", SERIAL] + list(args)
    result = subprocess.run(cmd, capture_output=True, text=True)
    return result.stdout


def _enable_null_provider(enable: bool) -> None:
    """Toggle WidgetInflater.sSimulateNullProvider via WorkspaceSeedReceiver."""
    cmd = ["shell", "am", "broadcast", "-p", PACKAGE, "-a", ACTION_SIMULATE]
    if not enable:
        cmd += ["--ez", "disable", "true"]
    _adb(*cmd)


def _force_reload(launcher) -> None:
    """Force-stop and restart the launcher to trigger a full model reload."""
    launcher.d.shell(f"am force-stop {PACKAGE}")
    time.sleep(1.5)
    launcher.d.shell(
        f"am start -n {PACKAGE}/com.android.launcher3.Launcher -f 0x10200000"
    )
    time.sleep(3.0)
    launcher.go_home()


def _has_any_widget(launcher) -> bool:
    """True if any AppWidget host view is currently on the workspace."""
    return launcher.d(className="com.android.launcher3.widget.ListenableHostView").exists


@pytest.mark.regression
@pytest.mark.xfail(
    strict=True,
    reason="Widget deletion bug not yet fixed — expect FAIL before TYPE_MISSING lands",
)
def test_widget_survives_null_provider_blackout(launcher):
    """A widget on the workspace must not be deleted when AppWidgetService
    temporarily returns null for its provider (root cause of today's incident).

    Pre-fix: widget deleted from DB → cell empty after reload.
    Post-fix: UnavailableWidgetView placeholder shown; DB row intact.
    """
    launcher.go_home()

    if not _has_any_widget(launcher):
        pytest.skip("No AppWidget on workspace — add one to the emulator first")

    _enable_null_provider(True)
    try:
        _force_reload(launcher)

        # Either the real widget OR its UnavailableWidgetView placeholder must be present.
        widget_or_placeholder = (
            launcher.d(className="com.android.launcher3.widget.ListenableHostView").exists
            or launcher.d(resourceId=ID_UNAVAIL_CONTAINER).exists
        )

        assert widget_or_placeholder, (
            "Widget cell is empty after null-provider reload — the widget was deleted. "
            "TYPE_MISSING fix (docs/changes/080) not applied or ineffective."
        )
    finally:
        _enable_null_provider(False)
        _force_reload(launcher)


@pytest.mark.regression
def test_widget_recovers_after_seam_cleared(launcher):
    """After the null-provider seam is cleared and the launcher reloads, the
    real widget must reappear — confirming 'restart and it works' behaviour.
    """
    launcher.go_home()
    if not _has_any_widget(launcher):
        pytest.skip("No AppWidget on workspace")

    # Phase 1: simulate blackout → placeholder shown
    _enable_null_provider(True)
    _force_reload(launcher)

    # Phase 2: clear seam → real widget returns
    _enable_null_provider(False)
    _force_reload(launcher)

    real_widget = launcher.d(
        className="com.android.launcher3.widget.ListenableHostView"
    ).wait(timeout=S.DEFAULT_WAIT)
    assert real_widget, (
        "Real widget did not return after seam was cleared and launcher reloaded. "
        "Recovery path broken."
    )


@pytest.mark.regression
def test_unavailable_widget_remove_button_clears_cell(launcher):
    """Tapping × on an UnavailableWidgetView removes it from the workspace.
    Verifies the user-initiated removal path works end-to-end.
    """
    launcher.go_home()
    if not _has_any_widget(launcher):
        pytest.skip("No AppWidget on workspace")

    _enable_null_provider(True)
    _force_reload(launcher)

    remove_btn = launcher.d(resourceId=ID_UNAVAIL_REMOVE)
    if not remove_btn.wait(timeout=S.DEFAULT_WAIT):
        _enable_null_provider(False)
        _force_reload(launcher)
        pytest.skip("UnavailableWidgetView not shown — fix not yet applied")

    remove_btn.click()
    time.sleep(S.ANIMATION_WAIT)

    still_showing = launcher.d(resourceId=ID_UNAVAIL_CONTAINER).exists
    assert not still_showing, (
        "UnavailableWidgetView still visible after × tapped. "
        "Remove listener not wired or not working."
    )
