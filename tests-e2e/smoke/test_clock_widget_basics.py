"""Smoke: the Danfo clock widget is offered in the picker and renders.

Both checks are deterministic: the picker-listing test filters the picker
via its search field (the launcher-shipped widget is grouped under a
collapsed app header, so searching is more robust than scrolling), and the
render test uses the debug broadcast seam (WorkspaceSeedReceiver).
"""

import pytest

import lib.selectors as S


@pytest.mark.smoke
@pytest.mark.workspace
def test_clock_widget_listed_in_picker(launcher):
    """The launcher-shipped clock widget is offered in the widget picker."""
    launcher.go_home()
    launcher.open_widget_picker()
    launcher.search_widget_picker("Danfo")
    assert launcher.d(text=S.WIDGET_LABEL_CLOCK).wait(timeout=S.DEFAULT_WAIT), \
        "Danfo Clock not found in the widget picker"
    launcher.go_home()


@pytest.mark.smoke
@pytest.mark.workspace
def test_clock_widget_renders(clean_launcher):
    """Placing the clock widget renders a ticking clock view."""
    launcher = clean_launcher
    launcher.place_clock_widget()
    assert launcher.clock_widget_present(), "clock widget did not render"
    launcher.reset_workspace()
