"""Visual: the placed clock widget actually paints glyphs (not a blank view).

Scans a 2D grid over the time half of the widget and asserts the brightest
sampled pixel is clearly brighter than the dimmest. The brightest pixels are
the white time glyphs; the dimmest are the wallpaper showing through. A blank
widget would yield a flat grid.

A grid scan (rather than a single fixed strip) is used because the time
digits shift position with the clock value and the widget's centering, so a
fixed strip can miss every glyph. Absolute brightness alone is unreliable
because the default AVD wallpaper is light, so we assert a spread above the
background floor.
"""

from __future__ import annotations

import pytest

import lib.selectors as S
import lib.visuals as V


# Time text occupies the upper portion of the 2x2 widget; scan that band.
_TIME_BAND_TOP_F = 0.12
_TIME_BAND_BOTTOM_F = 0.55
_MIN_GLYPH_SPREAD = 40   # bright glyph sum minus background-floor sum


@pytest.mark.visuals
@pytest.mark.workspace
def test_clock_widget_paints_text(clean_launcher):
    launcher = clean_launcher
    launcher.place_clock_widget()

    el = launcher.d(description=S.DESC_CLOCK_WIDGET)
    assert el.wait(timeout=S.DEFAULT_WAIT), "clock widget missing"
    b = el.info["bounds"]
    h = b["bottom"] - b["top"]
    band_top = b["top"] + int(h * _TIME_BAND_TOP_F)
    band_bottom = b["top"] + int(h * _TIME_BAND_BOTTOM_F)

    grid = V.sample_region_grid(
        launcher.d, b["left"], band_top, b["right"], band_bottom,
        cols=32, rows=20,
    )
    sums = [p.r + p.g + p.b for p in grid]
    brightest = max(sums)
    dimmest = min(sums)
    spread = brightest - dimmest

    # Painted glyphs are clearly brighter than the wallpaper showing through.
    assert spread >= _MIN_GLYPH_SPREAD, (
        f"no painted time text detected: time band is nearly flat "
        f"(brightest sum {brightest}, dimmest {dimmest}, spread {spread} < "
        f"{_MIN_GLYPH_SPREAD}). The widget may be rendering blank."
    )
    # Sanity floor: at least one near-white glyph pixel.
    assert brightest >= 600, f"no bright text pixel found (max sum {brightest})"

    launcher.reset_workspace()
