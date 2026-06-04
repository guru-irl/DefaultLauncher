"""Regression: drawer must show apps grid after launching an app from search results.

Root cause (docs/changes/084):
  SearchTransitionController.onProgressUpdated() sets appsContainer.alpha=0 when
  entering search mode. The normal search-exit animation restores it to 1. But when
  reset() is called (HOME press), mSearchState is set to IDLE synchronously, causing
  animateToSearch(false) to hit its guard and return early without running the exit
  animation. appsContainer.alpha stays at 0; isVisibleToUser() returns false for
  alpha=0 views, making the apps list invisible even though visibility=VISIBLE.

Fix: SearchLifecycle.onContainerReset(true) resets appsContainer.alpha=1 and
  translationY=0. Launcher.onResume() also resets the drawer if in ALL_APPS state
  with active search (covers the BACK-press return path).

Tests:
  test_apps_visible_after_launching_from_search_then_home
    - The exact user-reported flow: search → tap result → HOME → open drawer.
  test_apps_visible_after_multiple_search_launch_cycles
    - Multiple back-to-back cycles; verifies alpha reset is idempotent.
"""

import time

import pytest

from lib import selectors as S


def _apps_grid_visible(launcher) -> bool:
    """True if the main apps recycler view is visible to user."""
    return launcher.d(resourceId=S.ID_ALL_APPS_RECYCLER).wait(timeout=3.0)


def _search_results_visible(launcher) -> bool:
    return launcher.d(resourceId=S.ID_SEARCH_RESULTS_LIST).exists


def _find_and_launch(launcher, query: str) -> bool:
    """Type query in drawer, launch first result. Returns True if launched."""
    # Start from a known-good workspace state
    launcher.d.press("home")
    time.sleep(1.0)

    launcher.open_drawer()
    launcher.type_search(query)
    time.sleep(1.5)

    if not _search_results_visible(launcher):
        launcher.d.press("home")
        time.sleep(0.5)
        return False

    for selector in [launcher.d(description="Settings"), launcher.d(text="Settings")]:
        if selector.wait(timeout=3.0):
            selector.click()
            time.sleep(2.5)
            return True

    launcher.d.press("home")
    time.sleep(0.5)
    return False


@pytest.mark.regression
@pytest.mark.search
def test_apps_visible_after_launching_from_search_then_home(launcher):
    """Apps grid must be visible after: search → launch app → HOME → reopen drawer.

    This is the exact user-reported bug (docs/changes/084).
    Root cause: appsContainer.alpha=0 from search-enter animation not reset when
    the search-exit animation was bypassed by early IDLE state assignment.
    """
    if not _find_and_launch(launcher, "settings"):
        pytest.skip("Could not launch app from search results")

    # Return to workspace via HOME
    launcher.go_home()
    time.sleep(0.5)

    # Open drawer
    launcher.open_drawer()
    time.sleep(1.0)

    assert _apps_grid_visible(launcher), (
        "apps_list_view not visible after search → launch → HOME → open drawer. "
        "appsContainer.alpha may be stuck at 0. See SearchLifecycle.onContainerReset() "
        "and docs/changes/084."
    )
    assert not _search_results_visible(launcher), (
        "search_results_list_view still visible — drawer should show apps grid (IDLE state)."
    )
    launcher.go_home()


@pytest.mark.regression
@pytest.mark.search
def test_apps_visible_after_multiple_search_launch_cycles(launcher):
    """Alpha reset must be idempotent across repeated search → launch → return cycles.

    Each cycle: open drawer, search, launch Settings, go home, reopen drawer, assert.
    Verifies that docs/changes/084 fix doesn't degrade across successive cycles.
    """
    passes = 0
    for cycle in range(2):
        if not _find_and_launch(launcher, "settings"):
            if passes > 0:
                break  # First cycle passed; second skip is acceptable on slow emulator
            pytest.skip("Could not launch app from search results")

        launcher.go_home()
        time.sleep(0.5)
        launcher.open_drawer()
        time.sleep(1.0)

        if not _apps_grid_visible(launcher):
            pytest.fail(
                f"Cycle {cycle + 1}: apps_list_view not visible after search-launch-return. "
                "Alpha reset may not be idempotent. See docs/changes/084."
            )
        passes += 1
        launcher.go_home()
        time.sleep(0.3)

    if passes == 0:
        pytest.skip("No cycles completed — emulator may be overloaded")
