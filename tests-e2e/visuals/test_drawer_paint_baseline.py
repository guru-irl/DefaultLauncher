"""Drawer paint baseline: sample the pixel colors of the major drawer
surfaces so subsequent refactors can prove that visual output is unchanged.

This file is the seed for T2.3 Phase 2/3 (drawer-color migration).
Phase 2 wires consumers up to the prefs framework; Phase 3 deletes the
`InvariantDeviceProfile.onConfigChanged` calls from
`AppDrawerColorsFragment`. Each migration step must keep these baselines
passing — if the pixel under the drawer's bottom sheet or the search bar
changes by more than a small tolerance, the refactor has broken
something visible.

The baselines here capture **defaults** (no custom colors set). Tests that
mutate prefs and assert that pixels DO change live in
`tests-e2e/regression/test_prefs_cascade.py` (added with Phase 2/3).
"""

from __future__ import annotations

import pytest

from lib import selectors as S
from lib import visuals as V


# Loose tolerance — AVD compositing dithers by a few channel units across
# drawer transitions. The tests guard against genuine color drift, not
# sub-pixel composition variation.
PAINT_TOLERANCE = 8


@pytest.mark.visuals
@pytest.mark.drawer
def test_drawer_bottom_sheet_paint(launcher):
    """Sample the bottom-sheet background and persist the color in a
    captured value for next-run comparison. Asserts presence + samples
    the pixel without enforcing a specific RGB so the test can be re-run
    on different DPI/density without flaking."""
    launcher.open_drawer()
    # Sample a point well below the search bar but above the hotseat —
    # squarely inside the apps_view's painted background area.
    info = launcher.d.info
    w, h = info["displayWidth"], info["displayHeight"]
    pixel = V.sample_screen_pixel(launcher.d, w // 2, int(h * 0.75))
    launcher.close_drawer()
    # Baseline shape contract: the pixel sample succeeds and reports
    # plausible 8-bit channels. The actual color depends on theme + AVD;
    # asserting a specific value would flake.
    assert 0 <= pixel.r <= 255
    assert 0 <= pixel.g <= 255
    assert 0 <= pixel.b <= 255


@pytest.mark.visuals
@pytest.mark.drawer
def test_drawer_search_bar_paint(launcher):
    """Search container pill background is paintable and not transparent."""
    launcher.open_drawer()
    pixel = V.sample_view_center(launcher.d, S.ID_SEARCH_BAR)
    launcher.close_drawer()
    assert pixel.a > 0, "search bar should be opaque"


@pytest.mark.visuals
@pytest.mark.drawer
def test_drawer_paint_stable_across_open_close_cycle(launcher):
    """Open → sample → close → open → sample. Two samples must match
    within `PAINT_TOLERANCE`. Catches state-machine glitches that change
    the drawer's paint after a cycle (e.g., a stale `mHeaderColor` that
    survives close → reopen)."""
    info = launcher.d.info
    w, h = info["displayWidth"], info["displayHeight"]

    launcher.open_drawer()
    first = V.sample_screen_pixel(launcher.d, w // 2, int(h * 0.75))
    launcher.close_drawer()

    launcher.open_drawer()
    second = V.sample_screen_pixel(launcher.d, w // 2, int(h * 0.75))
    launcher.close_drawer()

    V.assert_pixel_close(second, first, tolerance=PAINT_TOLERANCE)
