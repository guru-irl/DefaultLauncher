"""Regression: drawer search-state lifecycle (empty-drawer bug family).

These tests cover the ACTIVE_EMPTY race documented in docs/changes/070,
docs/changes/076, and docs/changes/079.

The core bug: mSearchState can be left as ACTIVE_EMPTY when the drawer
closes, so the next drawer open shows an empty search_results_list_view
instead of the apps grid.

Tests in this file:
  test_drawer_not_empty_after_backspace_and_quick_home
    - Exercises the specific race: type then clear_text(), immediately press
      HOME. HOME fires onNewIntent() → reset() during the 300ms search-exit
      animation. Pre-fix: mKeepKeyboardOnSearchExit stays true → ACTIVE_EMPTY.
      Post-fix: flag cleared unconditionally in reset() → IDLE.
  test_drawer_not_empty_after_repeated_search_and_dismiss
    - Exercises the scenario reported by the user: open drawer, search,
      clear, close, reopen — must show apps grid, not empty search list.
  test_search_results_list_hidden_when_not_searching
    - Direct invariant check: when the drawer is opened fresh from home,
      search_results_list_view must be GONE/invisible.
"""

import time

import pytest

from lib import selectors as S


def _apps_list_visible(launcher) -> bool:
    """True if the main apps grid RV is in the accessibility tree.

    When mSearchState is ACTIVE_EMPTY, updateSearchResultsVisibility() sets
    apps_list_view to GONE — GONE views are not in the accessibility tree at
    all, so .exists correctly returns False for the broken state.
    """
    return launcher.d(resourceId=S.ID_ALL_APPS_RECYCLER).exists


def _search_results_visible(launcher) -> bool:
    """True if the search results list is in the accessibility tree.

    When not searching (IDLE), search_results_list_view is set to GONE so
    .exists returns False. If .exists is True, the bug is active.
    """
    return launcher.d(resourceId=S.ID_SEARCH_RESULTS_LIST).exists


@pytest.mark.regression
@pytest.mark.drawer
@pytest.mark.search
def test_drawer_not_empty_after_backspace_and_quick_home(launcher):
    """Pressing HOME within the search-exit animation window must not leave
    mSearchState=ACTIVE_EMPTY for the next drawer open.

    Repro for the race fixed in docs/changes/079:
      1. Open drawer, type a query (→ SEARCHING state).
      2. Clear text — triggers showAppsWhileSearchActive():
         mKeepKeyboardOnSearchExit=true, animateToSearchState(false) starts
         300ms animation.
      3. Immediately press HOME. HOME fires onNewIntent() → reset() which
         races with the 300ms animation (isRunning() may be True).
         Pre-fix: mKeepKeyboardOnSearchExit NOT cleared → deferred runnable
         sets ACTIVE_EMPTY. Post-fix: flag cleared unconditionally, runnable
         resolves to IDLE.
      4. Re-open drawer — must show apps grid.

    Note: BACK is not the triggering gesture — BACK waits for the drawer-close
    animation to finish before onStateSetEnd() calls reset(), so the search
    animation is already over by then. HOME fires reset() immediately.
    """
    launcher.go_home()
    launcher.open_drawer()

    # Type a query to enter SEARCHING state.
    launcher.type_search("chr")

    search_input = launcher.search_input()
    if not search_input.wait(timeout=S.DEFAULT_WAIT):
        launcher.close_drawer()
        pytest.skip("Search input not available")

    # Clear text then immediately press HOME — this is the race.
    # Don't use go_home() here; it's a no-op when already on launcher.
    # d.press("home") fires onNewIntent() immediately, potentially during
    # the 300ms search-exit animation that clear_text() triggered.
    search_input.clear_text()
    launcher.d.press("home")   # fires onNewIntent() → reset() immediately
    time.sleep(0.5)            # let animation + reset() settle

    # Reopen the drawer — must show the apps grid, not the empty search list.
    launcher.open_drawer()
    # Use .wait() not .exists: the RecyclerView may take a frame to become
    # accessible after the drawer container appears. 5s covers slow emulators.
    grid_visible = launcher.d(resourceId=S.ID_ALL_APPS_RECYCLER).wait(
        timeout=S.DEFAULT_WAIT
    )
    search_empty = _search_results_visible(launcher)

    launcher.close_drawer()

    assert not search_empty, (
        "search_results_list_view is visible on fresh drawer open — "
        "mSearchState was left as ACTIVE_EMPTY. "
        "The reset() fix in docs/changes/079 may have been lost or is incomplete."
    )
    assert grid_visible, (
        "apps_list_view is not visible on fresh drawer open after "
        "clear-text + quick HOME press. "
        "Check ActivityAllAppsContainerView.reset() and updateSearchResultsVisibility()."
    )


@pytest.mark.regression
@pytest.mark.drawer
@pytest.mark.search
def test_drawer_not_empty_after_repeated_search_and_dismiss(launcher):
    """User-reported scenario: search, clear, close, reopen — must show apps grid.

    Exercises the ACTIVE_EMPTY state leak via the normal usage path.
    After each cycle, press HOME explicitly (not go_home() which is a no-op
    when already on the launcher) to ensure onNewIntent() fires and reset()
    runs before the next drawer open.
    """
    # Cycle through search 3 times using explicit HOME to reset state.
    for _ in range(3):
        launcher.open_drawer()
        launcher.type_search("settings")
        launcher.clear_search()
        launcher.close_drawer()
        # Must use d.press("home") not go_home() — go_home() is a no-op
        # when already on the launcher, so onNewIntent() never fires and
        # mSearchState can stay as ACTIVE_EMPTY.
        launcher.d.press("home")
        time.sleep(0.4)

    # Final open must show apps grid.
    launcher.open_drawer()
    grid_visible = launcher.d(resourceId=S.ID_ALL_APPS_RECYCLER).wait(
        timeout=S.DEFAULT_WAIT
    )
    search_empty = _search_results_visible(launcher)

    launcher.close_drawer()

    assert not search_empty, (
        "search_results_list_view visible after repeated search-clear-dismiss cycle. "
        "mSearchState == ACTIVE_EMPTY — docs/changes/070/076/079 regression."
    )
    assert grid_visible, (
        "apps_list_view not visible after repeated search-clear-dismiss. "
        "Check mSearchState reset path in ActivityAllAppsContainerView."
    )


@pytest.mark.regression
@pytest.mark.drawer
def test_search_results_list_hidden_when_not_searching(launcher):
    """On a fresh drawer open after a HOME press, the apps grid must be visible.

    This is the fundamental invariant: isSearching() must be false when the
    drawer opens from an IDLE state. A visible search_results_list_view here
    means mSearchState != IDLE.

    Uses d.press("home") explicitly — go_home() is a no-op when already on
    the launcher, so onNewIntent() would not fire without the explicit press.
    """
    # Explicit HOME press to guarantee onNewIntent() → reset(exitSearch=true).
    launcher.d.press("home")
    time.sleep(0.4)

    launcher.open_drawer()
    grid_visible = launcher.d(resourceId=S.ID_ALL_APPS_RECYCLER).wait(
        timeout=S.DEFAULT_WAIT
    )
    search_empty = _search_results_visible(launcher)

    launcher.close_drawer()

    assert not search_empty, (
        "search_results_list_view is visible on a fresh drawer open from HOME. "
        "mSearchState is not IDLE — state machine was not reset by onNewIntent. "
        "See docs/changes/070 and docs/changes/076."
    )
    assert grid_visible, (
        "apps_list_view is not visible on a fresh drawer open from HOME. "
        "Drawer appears empty — likely an ACTIVE_EMPTY or SEARCHING state bleed."
    )
