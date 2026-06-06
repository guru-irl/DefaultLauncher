"""Smoke: the Search pill widget is offered in the picker and renders.

Mirrors test_clock_widget_basics.py. The picker-listing test filters the
picker via its search field (the launcher-shipped widget is grouped under a
collapsed app header, so searching is more robust than scrolling), and the
render test uses the debug broadcast seam (WorkspaceSeedReceiver).
"""

import pytest

import lib.selectors as S


@pytest.mark.smoke
@pytest.mark.workspace
def test_search_widget_listed_in_picker(launcher):
    """The launcher-shipped search widget is offered in the widget picker."""
    launcher.go_home()
    launcher.open_widget_picker()
    launcher.search_widget_picker("Search")
    assert launcher.d(text=S.WIDGET_LABEL_SEARCH).wait(timeout=S.DEFAULT_WAIT), \
        "Search widget not found in the widget picker"
    launcher.go_home()


@pytest.mark.smoke
@pytest.mark.workspace
def test_search_widget_renders(clean_launcher):
    """Placing the search widget renders the SEARCH pill view."""
    launcher = clean_launcher
    launcher.place_search_widget()
    assert launcher.search_widget_present(), "search widget did not render"
    launcher.reset_workspace()
