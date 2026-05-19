"""Search golden paths: text input is reachable, results appear, dismiss works."""

import pytest

from lib import selectors as S


@pytest.mark.smoke
@pytest.mark.search
def test_search_input_accessible(launcher):
    """Search field is visible after opening drawer and can be focused."""
    launcher.go_home()
    launcher.open_drawer()
    edit = launcher.search_input()
    assert edit.exists, "no search input visible inside drawer"


@pytest.mark.smoke
@pytest.mark.search
def test_search_text_renders_results(launcher):
    """Typing a stable query produces visible search results within 2s."""
    launcher.go_home()
    launcher.open_drawer()
    launcher.type_search("chr")  # likely matches Chrome
    # When search is active, the apps recycler is replaced by the search
    # results recycler.
    results_visible = launcher.d(resourceId=S.ID_SEARCH_RESULTS_LIST).wait(timeout=2.0)
    assert results_visible, "search results list did not appear after typing"
    launcher.clear_search()
    launcher.close_drawer()


@pytest.mark.smoke
@pytest.mark.search
def test_search_clear_returns_to_apps(launcher):
    """After clearing the query, the A-Z app list is visible again."""
    launcher.go_home()
    launcher.open_drawer()
    launcher.type_search("xyzzy_unlikely_query_token")
    launcher.clear_search()
    # After clearing, the apps recycler should be back (search list gone).
    apps_back = launcher.d(resourceId=S.ID_ALL_APPS_RECYCLER).wait(timeout=2.0)
    assert apps_back, "apps_list_view did not return after clearing search"
    launcher.close_drawer()


@pytest.mark.smoke
@pytest.mark.search
def test_search_dismiss_via_back(launcher):
    launcher.go_home()
    launcher.open_drawer()
    launcher.type_search("ab")
    launcher.d.press("back")  # collapses keyboard or exits search
    launcher.d.press("back")  # second back exits search if first was kbd
    # We should be either back at the drawer or at home.
    assert launcher.is_home() or launcher.drawer_open()
    if launcher.drawer_open():
        launcher.close_drawer()
