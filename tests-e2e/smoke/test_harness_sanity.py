"""Smoke-of-smokes: validates the harness itself works.

If any of these fail, every other test is suspect."""

import pytest


@pytest.mark.smoke
def test_device_reachable(device):
    """uiautomator2 connected to the AVD."""
    assert device.serial
    assert device.info["sdkInt"] >= 30


@pytest.mark.smoke
def test_launcher_is_default_home(launcher):
    """DefaultLauncher is the active app after pressing HOME."""
    launcher.go_home()
    assert launcher.is_home(), "DefaultLauncher is not the default home"


@pytest.mark.smoke
def test_workspace_visible(launcher):
    """Workspace + hotseat render after launching home."""
    launcher.go_home()
    assert launcher.workspace_visible()
    assert launcher.hotseat_visible()
