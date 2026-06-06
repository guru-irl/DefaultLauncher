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
# low-contrast. The centered time+date block shifts vertically with the
# widget size and the date gap, so rather than a fixed strip we sample a
# dense grid over the whole widget (one screenshot) and require ANY cell to
# move once the time color becomes a saturated tone.
_GRID_COLS = 20
_GRID_ROWS = 24
_MIN_CHANNEL_DISTANCE = 16     # per-sample threshold for "this pixel changed"


def _time_region_pixels(launcher) -> list[V.Pixel]:
    """Sample a dense grid over the clock widget, robust to glyph position."""
    el = launcher.d(description=S.DESC_CLOCK_WIDGET)
    assert el.wait(timeout=S.DEFAULT_WAIT), "clock widget missing"
    b = el.info["bounds"]
    return V.sample_region_grid(
        launcher.d, b["left"], b["top"], b["right"], b["bottom"],
        cols=_GRID_COLS, rows=_GRID_ROWS,
    )


def _pick_custom_time_color(launcher) -> None:
    """Settings > Widgets > Clock widget > Color = Custom, then pick a swatch.

    Navigation reflects the Widgets settings submenu: the clock settings now
    live under Settings > Widgets > Clock widget. The three choice rows
    (Alignment, Time format, Color) open an M3 single-choice bottom sheet with
    one card per option (no androidx radio dialog), so we tap the option by its
    visible card text.

    The Time color picker only appears once the mode is Custom. Its swatches
    are unlabeled tonal circles grouped under palette headers (PRIMARY,
    SECONDARY, ...), so we tap by coordinate: the darkest swatch in the
    PRIMARY row, which is the most distinct from the white default text.
    """
    launcher.open_launcher_settings()

    widgets = launcher.d(text="Widgets")
    assert widgets.wait(timeout=S.DEFAULT_WAIT), "Widgets settings row missing"
    widgets.click()

    clock = launcher.d(text="Clock widget")
    assert clock.wait(timeout=S.DEFAULT_WAIT), "Clock widget settings row missing"
    clock.click()

    # Open the Color choice row; an M3 bottom sheet of option cards appears.
    mode = launcher.d(text="Color")
    assert mode.wait(timeout=S.DEFAULT_WAIT), "Color row missing"
    mode.click()
    # Tap the "Custom" card; the Time color picker row appears once it takes.
    launcher.tap_choice_card("Custom", confirm_text="Time color")

    time_color = launcher.d(text="Time color")
    assert time_color.wait(timeout=S.DEFAULT_WAIT), "Time color row did not appear"
    time_color.click()

    header = launcher.d(text="PRIMARY")
    assert header.wait(timeout=S.DEFAULT_WAIT), "PRIMARY palette header missing"
    # The picker is a bottom sheet that slides up: reading header bounds while it
    # animates gives a too-high y, so the swatch tap misses. Tap the rightmost
    # (most saturated) PRIMARY swatch and retry until the Time color summary
    # leaves its "Default" state, proving a real color was applied.
    # Tap the rightmost (most saturated) swatch below the PRIMARY header; any
    # saturated tone repaints the white default text. Picking a swatch closes
    # the picker and sets the Time color summary to a "<Palette> 500" tone
    # (the exact palette depends on where the settled row lands). Retry until a
    # "... 500" summary appears, since the sliding sheet can shift an early tap.
    swatch_applied = launcher.d(textMatches=r".* 500")
    deadline = time.time() + S.DEFAULT_WAIT
    while time.time() < deadline and not swatch_applied.exists:
        header = launcher.d(text="PRIMARY")
        if not header.exists:
            break  # picker already dismissed by a prior successful tap
        pb = header.info["bounds"]
        sx = pb["left"] + int((pb["right"] - pb["left"]) * 0.91)
        sy = pb["bottom"] + (pb["bottom"] - pb["top"]) // 2
        launcher.d.click(sx, sy)
        swatch_applied.wait(timeout=S.ANIMATION_WAIT)
    assert launcher.d(textMatches=r".* 500").wait(timeout=S.DEFAULT_WAIT), \
        "Time color swatch did not apply (no saturated tone summary)"


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
