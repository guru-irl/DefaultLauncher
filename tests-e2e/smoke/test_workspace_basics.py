"""Workspace golden paths: app icons present, launch via tap, hotseat present.

These tests assert the basic shape of the home screen — nothing about layout
math, drag, or folders. Those belong in regression/test_workspace_state.py.
"""

import pytest

from lib import selectors as S


@pytest.mark.smoke
@pytest.mark.workspace
def test_workspace_has_visible_icons(launcher):
    """At least one app icon is visible on the home screen."""
    launcher.go_home()
    # Pick the first known app that exists on the AVD; we accept any of them.
    found = False
    for label in S.KNOWN_APPS.values():
        if launcher.d(description=label).exists:
            found = True
            break
    assert found, "no known app icon visible on workspace or hotseat"


@pytest.mark.smoke
@pytest.mark.workspace
def test_hotseat_has_items(launcher):
    """Hotseat contains at least one launchable item."""
    launcher.go_home()
    hotseat = launcher.d(resourceId=S.ID_HOTSEAT)
    assert hotseat.exists, "hotseat container not present"
    # The hotseat in AOSP launcher contains TextView icons (with desc) and
    # possibly a search QSB. Count items with a content description.
    children = hotseat.child(descriptionMatches=r".+")
    assert children.count >= 1, "hotseat appears empty"


@pytest.mark.smoke
@pytest.mark.workspace
def test_launch_app_from_hotseat(launcher):
    """Tapping a hotseat icon launches that app, returning home recovers."""
    launcher.go_home()
    # Pick a stable AOSP app that's always on the hotseat by default.
    target = None
    for label in ("Phone", "Chrome", "Messages"):
        if launcher.d(description=label).exists:
            target = label
            break
    if target is None:
        pytest.skip("no stable hotseat app present on this image")

    pkg = launcher.launch_app(target)
    assert pkg != S.PACKAGE, f"tapping {target} did not leave the launcher"
    launcher.go_home()
    assert launcher.is_home()


@pytest.mark.smoke
@pytest.mark.workspace
def test_workspace_pages_navigable(launcher):
    """Swiping between pages does not crash and returns to a valid state."""
    launcher.go_home()
    info = launcher.d.info
    w, h = info["displayWidth"], info["displayHeight"]
    # swipe left → right (next page)
    launcher.d.swipe(int(w * 0.8), h // 2, int(w * 0.2), h // 2, duration=0.2)
    assert launcher.is_home()
    # swipe back
    launcher.d.swipe(int(w * 0.2), h // 2, int(w * 0.8), h // 2, duration=0.2)
    assert launcher.is_home()
    assert launcher.workspace_visible()
