"""Regression: live clock-widget color changes repaint the placed widget.

Places the clock widget via the deterministic broadcast seam, samples a
strip of pixels across the time-digit band, switches the color mode to
Custom and picks an explicit non-white swatch in the Time color picker,
then confirms the rendered pixels changed.

Why an explicit swatch (not just "Custom"): the Custom-mode default time
color is white, the same as the default White mode, so selecting Custom
alone does not move any pixels. We pick a saturated tonal swatch so the
repaint is real and detectable. This mirrors how test_folder_visual.py
drives the color picker.
"""

from __future__ import annotations

import time

import pytest

import lib.selectors as S
import lib.visuals as V


# The default AVD wallpaper is a light blue-grey, so white time text is
# low-contrast. We sample a dense strip across the time-digit band and
# require ANY sample to move once the time color becomes a saturated tone.
_TIME_BAND_YF = 0.45           # fraction of widget height: center of time-digit band
_TIME_BAND_XF = (0.20, 0.30, 0.40, 0.50, 0.60, 0.70, 0.80)
_MIN_CHANNEL_DISTANCE = 16     # per-sample threshold for "this pixel changed"


def _time_region_pixels(launcher) -> list[V.Pixel]:
    """Sample a horizontal strip across the clock widget's time-digit band."""
    el = launcher.d(description=S.DESC_CLOCK_WIDGET)
    assert el.wait(timeout=S.DEFAULT_WAIT), "clock widget missing"
    b = el.info["bounds"]
    cy = b["top"] + int((b["bottom"] - b["top"]) * _TIME_BAND_YF)
    xs = [b["left"] + int((b["right"] - b["left"]) * f) for f in _TIME_BAND_XF]
    return [V.sample_screen_pixel(launcher.d, x, cy) for x in xs]


def _pick_custom_time_color(launcher) -> None:
    """Settings > Clock widget > Color = Custom, then pick a saturated swatch.

    The Time color picker only appears once the mode is Custom. Its swatches
    are unlabeled tonal circles grouped under palette headers (PRIMARY,
    SECONDARY, ...), so we tap by coordinate: the darkest swatch in the
    PRIMARY row, which is the most distinct from the white default text.
    """
    launcher.open_launcher_settings()

    clock = launcher.d(text="Clock widget")
    assert clock.wait(timeout=S.DEFAULT_WAIT), "Clock widget settings row missing"
    clock.click()

    mode = launcher.d(text="Color")
    assert mode.wait(timeout=S.DEFAULT_WAIT), "Color row missing"
    mode.click()
    custom = launcher.d(text="Custom")
    assert custom.wait(timeout=S.DEFAULT_WAIT), "Custom option missing"
    custom.click()

    time_color = launcher.d(text="Time color")
    assert time_color.wait(timeout=S.DEFAULT_WAIT), "Time color row did not appear"
    time_color.click()

    header = launcher.d(text="PRIMARY")
    assert header.wait(timeout=S.DEFAULT_WAIT), "PRIMARY palette header missing"
    pb = header.info["bounds"]
    # Swatch row sits just below the header; the rightmost swatches are the
    # darkest (most saturated) tones. Tap the last full swatch column.
    sx = pb["left"] + int((pb["right"] - pb["left"]) * 0.91)
    sy = pb["bottom"] + (pb["bottom"] - pb["top"]) // 2
    launcher.d.click(sx, sy)
    time.sleep(S.ANIMATION_WAIT)


@pytest.mark.regression
@pytest.mark.prefs
def test_clock_widget_color_mode_repaints(clean_launcher):
    launcher = clean_launcher
    launcher.place_clock_widget()
    before = _time_region_pixels(launcher)

    _pick_custom_time_color(launcher)

    launcher.go_home()
    after = _time_region_pixels(launcher)

    changed = any(
        b.channel_distance(a) >= _MIN_CHANNEL_DISTANCE
        for b, a in zip(before, after)
    )
    assert changed, (
        "clock pixels did not change after switching time color to a custom "
        f"swatch.\n  before: {[str(p) for p in before]}\n"
        f"  after:  {[str(p) for p in after]}"
    )

    launcher.reset_workspace()
