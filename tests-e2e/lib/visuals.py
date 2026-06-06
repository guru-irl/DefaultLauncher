"""Pixel-sampling helpers for visual regression tests.

The point of this module: catch regressions where a paint-only preference
change stops propagating, or where a refactor accidentally swaps the color
of a view tier. Tests sample pixels at known display coordinates (relative
to the bounds reported by uiautomator) and assert RGB-channel equality or
proximity.

Why not golden-image comparison: AVDs vary subtly across kernel/skin/dpi
even with the same image. Pixel-level diffs at known coordinates are
deterministic; golden images flake.

Coordinates are taken relative to a view's bounds, not absolute screen
coordinates, so the tests survive grid-config changes that move the view.
"""

from __future__ import annotations

import time
from dataclasses import dataclass
from pathlib import Path
from typing import Optional, Tuple

import uiautomator2 as u2


@dataclass(frozen=True)
class Pixel:
    """A sampled pixel: 8-bit per channel, alpha included for completeness."""
    r: int
    g: int
    b: int
    a: int = 255

    def rgb(self) -> Tuple[int, int, int]:
        return (self.r, self.g, self.b)

    def channel_distance(self, other: "Pixel") -> int:
        """Sum of |channel diff| across R, G, B. 0 = identical."""
        return (abs(self.r - other.r) + abs(self.g - other.g)
                + abs(self.b - other.b))

    def __str__(self) -> str:
        return f"#{self.r:02x}{self.g:02x}{self.b:02x} (rgb={self.r},{self.g},{self.b})"


def sample_screen_pixel(d: u2.Device, x: int, y: int,
                        scratch: Optional[Path] = None) -> Pixel:
    """Capture a screenshot, return the pixel at (x, y) in screen coords.

    `scratch` is the temporary PNG path on the host; defaults to a unique
    file under /tmp. The screenshot is removed after sampling.
    """
    from PIL import Image
    if scratch is None:
        scratch = Path(f"/tmp/vislib_{int(time.time() * 1000)}.png")
    d.screenshot(str(scratch))
    try:
        with Image.open(str(scratch)) as img:
            rgba = img.convert("RGBA")
            r, g, b, a = rgba.getpixel((x, y))
            return Pixel(r=r, g=g, b=b, a=a)
    finally:
        try:
            scratch.unlink()
        except FileNotFoundError:
            pass


def sample_region_grid(d: u2.Device, left: int, top: int, right: int,
                       bottom: int, cols: int = 24, rows: int = 16,
                       scratch: Optional[Path] = None) -> list[Pixel]:
    """Sample a `cols` x `rows` grid of pixels over a screen rectangle.

    Takes a single screenshot (cheap relative to one-per-pixel sampling) and
    returns the flattened list of sampled `Pixel`s. Useful for detecting
    painted glyphs anywhere inside a view whose exact text position is not
    known ahead of time (e.g. a clock whose digits shift with the time).
    """
    from PIL import Image
    if scratch is None:
        scratch = Path(f"/tmp/vislib_{int(time.time() * 1000)}.png")
    d.screenshot(str(scratch))
    try:
        with Image.open(str(scratch)) as img:
            rgb = img.convert("RGB")
            w, h = rgb.size
            out: list[Pixel] = []
            for j in range(rows):
                fy = (j + 0.5) / rows
                y = min(h - 1, max(0, top + int((bottom - top) * fy)))
                for i in range(cols):
                    fx = (i + 0.5) / cols
                    x = min(w - 1, max(0, left + int((right - left) * fx)))
                    r, g, b = rgb.getpixel((x, y))
                    out.append(Pixel(r=r, g=g, b=b))
            return out
    finally:
        try:
            scratch.unlink()
        except FileNotFoundError:
            pass


def sample_view_center(d: u2.Device, resource_id: str,
                       offset_x: int = 0, offset_y: int = 0) -> Pixel:
    """Sample the pixel at the center of the view with `resource_id`.

    `offset_x`/`offset_y` shift the sample from the view's center by the
    given pixel count, useful for sampling near an edge (e.g., the
    scrollbar's track vs. its thumb).
    """
    view = d(resourceId=resource_id)
    if not view.wait(timeout=2.0):
        raise AssertionError(
            f"sample_view_center: view {resource_id} not visible"
        )
    b = view.info["bounds"]
    cx = (b["left"] + b["right"]) // 2 + offset_x
    cy = (b["top"] + b["bottom"]) // 2 + offset_y
    return sample_screen_pixel(d, cx, cy)


def assert_pixel_close(actual: Pixel, expected: Pixel,
                       tolerance: int = 6) -> None:
    """Asserts the sampled pixel matches `expected` within `tolerance`.

    `tolerance` is the maximum allowed sum of per-channel absolute
    differences. 6 covers the small dither variation AVDs introduce on
    surface composition without permitting genuine color changes.
    """
    dist = actual.channel_distance(expected)
    if dist > tolerance:
        raise AssertionError(
            f"pixel {actual} differs from expected {expected} by {dist} "
            f"(tolerance {tolerance})"
        )


def assert_pixel_changed(before: Pixel, after: Pixel,
                         min_distance: int = 16) -> None:
    """Assert that `after` differs from `before` by at least `min_distance`.

    Used to catch "I changed a color preference but nothing on screen
    moved" regressions — the inverse of `assert_pixel_close`.
    """
    dist = before.channel_distance(after)
    if dist < min_distance:
        raise AssertionError(
            f"pixel barely moved: {before} → {after} (distance {dist}, "
            f"expected >= {min_distance})"
        )
