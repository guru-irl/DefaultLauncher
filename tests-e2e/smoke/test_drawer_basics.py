"""App drawer golden paths: open via swipe, app icons visible, dismiss."""

import pytest

from lib import selectors as S


@pytest.mark.smoke
@pytest.mark.drawer
def test_open_drawer_with_swipe(launcher):
    launcher.go_home()
    assert not launcher.drawer_open()
    launcher.open_drawer()
    assert launcher.drawer_open()
    launcher.close_drawer()
    assert not launcher.drawer_open()


@pytest.mark.smoke
@pytest.mark.drawer
def test_drawer_has_app_icons(launcher):
    """Drawer shows at least 8 distinct app icons (a tiny floor)."""
    launcher.go_home()
    launcher.open_drawer()
    # icons have a content description equal to the app name.
    icons = launcher.d(resourceId=S.ID_ALL_APPS_RECYCLER).child(descriptionMatches=r".+")
    count = icons.count
    launcher.close_drawer()
    assert count >= 8, f"drawer showed only {count} icons (expected >= 8)"


@pytest.mark.smoke
@pytest.mark.drawer
def test_drawer_dismiss_via_home(launcher):
    """Pressing HOME while drawer is open returns to the workspace."""
    launcher.go_home()
    launcher.open_drawer()
    assert launcher.drawer_open()
    launcher.d.press("home")
    launcher.d.wait_activity(S.LAUNCH_ACTIVITY, timeout=S.DEFAULT_WAIT)
    assert launcher.workspace_visible() and not launcher.drawer_open()


@pytest.mark.smoke
@pytest.mark.drawer
def test_drawer_dismiss_via_back(launcher):
    launcher.go_home()
    launcher.open_drawer()
    launcher.d.press("back")
    # Wait for the apps container to actually leave the view tree.
    gone = launcher.d(resourceId=S.ID_ALL_APPS_CONTAINER).wait_gone(timeout=S.DEFAULT_WAIT)
    assert gone, "drawer container still present after back press"
    assert not launcher.drawer_open()
