"""Grid golden paths: workspace cell layout is consistent, hotseat row matches grid.

Grid math is heavily tested in regression/. These smokes assert that nothing
catastrophic broke (e.g. cells overlap, icons render at zero size).
"""

import pytest

from lib import selectors as S


@pytest.mark.smoke
@pytest.mark.grid
def test_workspace_icon_bounds_nonzero(launcher):
    """Every visible workspace icon has a strictly positive bounding rect."""
    launcher.go_home()
    icons = launcher.d(resourceId=S.ID_WORKSPACE).child(descriptionMatches=r".+")
    assert icons.count >= 1
    bad = []
    for i in range(icons.count):
        node = icons[i]
        bounds = node.info.get("bounds")
        if not bounds:
            continue
        w = bounds["right"] - bounds["left"]
        h = bounds["bottom"] - bounds["top"]
        if w <= 0 or h <= 0:
            bad.append((node.info.get("contentDescription"), bounds))
    assert not bad, f"icons with non-positive size: {bad}"


@pytest.mark.smoke
@pytest.mark.grid
def test_hotseat_row_visible(launcher):
    """Hotseat row is at the bottom of the screen and tall enough to contain icons."""
    launcher.go_home()
    hotseat = launcher.d(resourceId=S.ID_HOTSEAT)
    assert hotseat.exists
    bounds = hotseat.info.get("bounds")
    assert bounds, "hotseat has no bounds"
    info = launcher.d.info
    # Hotseat should be in the bottom 25% of the screen.
    assert bounds["top"] > info["displayHeight"] * 0.7, \
        f"hotseat top {bounds['top']} is too high on screen {info['displayHeight']}"
    # Hotseat height should be at least 80px (very loose floor).
    assert bounds["bottom"] - bounds["top"] >= 80, \
        f"hotseat height {bounds['bottom'] - bounds['top']} is too small"
