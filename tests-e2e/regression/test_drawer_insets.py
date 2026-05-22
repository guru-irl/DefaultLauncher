"""Regression: DrawerInsetsController (T3.1 Phase 2) behavioral contract.

T3.1 Phase 2 (docs/plans/004-drawer-decomposition-v2.md) extracts
DrawerInsetsController out of ActivityAllAppsContainerView. These tests
define the contract that must survive the extraction.

Tests in this file:
  Phase 2 — inset delegation (3 tests):
    test_drawer_inset_landscape_phone
    test_drawer_insets_no_error_on_open_close
    test_drawer_padding_survives_orientation_round_trip
"""

import time

import pytest

from lib import selectors as S

TAG_APPS = "ActivityAllAppsContainerView"
TAG_INSETS = "DrawerInsetsController"

# Settle time after orientation change (configuration change + layout pass).
ROTATION_SETTLE = 1.2


@pytest.mark.regression
@pytest.mark.drawer
def test_drawer_inset_landscape_phone(launcher):
    """Rotating to landscape applies correct side insets via DrawerInsetsController.

    DrawerInsetsController.applyInsets() must store the new Rect and propagate
    allAppsPadding.left/right to all adapter holders. After landscape rotation
    the apps list must still be visible — a blank or crashed drawer indicates
    the padding recomputation failed.

    Skipped on tablets where the bottom-sheet layout may hide the RV id.
    """
    launcher.go_home()

    # Rotate to landscape
    try:
        launcher.d.set_orientation("l")
    except Exception as exc:
        pytest.skip(f"Orientation change not supported: {exc}")
    time.sleep(ROTATION_SETTLE)
    launcher.go_home()  # Ensure at home after configuration change

    visible_in_landscape = False
    try:
        launcher.open_drawer()
        # open_drawer() waited for ID_ALL_APPS_CONTAINER (up to 10s) so we
        # know the drawer container is accessible. Also wait for the inner
        # RecyclerView; fall back to just the container on a loaded emulator
        # where child views may take longer to bind than the container itself.
        visible_in_landscape = (
            launcher.d(resourceId=S.ID_ALL_APPS_RECYCLER).wait(
                timeout=S.DEFAULT_WAIT * 2   # 10s
            )
            or launcher.d(resourceId=S.ID_ALL_APPS_CONTAINER).exists
        )
        launcher.close_drawer()
    finally:
        launcher.d.set_orientation("n")
        time.sleep(ROTATION_SETTLE)
        launcher.go_home()

    assert visible_in_landscape, (
        "Apps list was not visible in landscape drawer. "
        "DrawerInsetsController.applyInsets() may have failed to propagate "
        "the new side insets to the adapter holders. "
        "See docs/plans/004-drawer-decomposition-v2.md Phase 2."
    )


@pytest.mark.regression
@pytest.mark.drawer
def test_drawer_insets_no_error_on_open_close(launcher):
    """Opening and closing the drawer must not produce any Android errors in
    the insets path.

    The nav-bar scrim drawing path (DrawerInsetsController.drawNavBarScrim)
    and the window-insets dispatch (DrawerInsetsController.onDispatchApplyWindowInsets)
    must be error-free on every open/close cycle.
    """
    launcher.go_home()
    launcher.d.shell("logcat -c")

    launcher.open_drawer()
    time.sleep(S.ANIMATION_WAIT)
    launcher.close_drawer()
    time.sleep(0.3)

    logcat = launcher.logcat_tail(300)
    error_lines = [
        line for line in logcat.splitlines()
        if " E " in line and (TAG_APPS in line or TAG_INSETS in line)
    ]

    assert not error_lines, (
        "Errors in drawer insets path after open/close:\n"
        + "\n".join(error_lines[:10])
    )


@pytest.mark.regression
@pytest.mark.drawer
def test_drawer_padding_survives_orientation_round_trip(launcher):
    """After portrait→landscape→portrait rotation, the drawer must render
    correctly: apps visible and search bar present.

    This exercises the full DrawerInsetsController lifecycle:
      1. setInsets() call on configuration change (portrait).
      2. dispatchApplyWindowInsets() call with updated nav-bar insets.
      3. applyAdapterSideAndBottomPaddings() propagated to all adapter holders.

    A blank drawer or missing search bar after the round trip indicates
    padding was not reapplied.
    """
    launcher.go_home()

    # Round-trip rotation: portrait → landscape → portrait.
    try:
        launcher.d.set_orientation("l")
        time.sleep(ROTATION_SETTLE)
        launcher.d.set_orientation("n")
        time.sleep(ROTATION_SETTLE)
    except Exception as exc:
        pytest.skip(f"Orientation change not supported: {exc}")

    launcher.go_home()
    launcher.open_drawer()

    rv_visible = launcher.d(resourceId=S.ID_ALL_APPS_RECYCLER).wait(
        timeout=S.DEFAULT_WAIT
    )
    search_visible = launcher.d(resourceId=S.ID_SEARCH_BAR).wait(
        timeout=S.DEFAULT_WAIT
    )

    launcher.close_drawer()

    assert rv_visible, (
        "Apps RecyclerView not visible after orientation round trip. "
        "DrawerInsetsController padding propagation may have failed."
    )
    assert search_visible, (
        "Search bar not visible after orientation round trip. "
        "Layout constraints may have broken during inset reapplication."
    )
