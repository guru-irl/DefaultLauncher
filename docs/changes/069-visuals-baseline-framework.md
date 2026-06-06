# 069 — Visuals baseline framework (pixel sampling)

## What ships

A pixel-sampling test framework that the drawer-color migration (T2.3
Phase 2/3) needs in order to ship safely. Without it, a refactor that
quietly stops invalidating views — or worse, swaps the color of the
wrong surface — would pass smoke tests because the smoke tests only
check structure, not paint.

### `tests-e2e/lib/visuals.py`

Helpers:

- `Pixel(r, g, b, a)` — 8-bit-per-channel sampled pixel.
- `sample_screen_pixel(d, x, y)` — capture, sample at coord, cleanup.
- `sample_view_center(d, resource_id, offset_x, offset_y)` — sample the
  center of a uiautomator-located view (so coordinates survive grid
  reconfig).
- `assert_pixel_close(actual, expected, tolerance=6)` — channel-sum
  difference threshold. 6 covers AVD composition dither.
- `assert_pixel_changed(before, after, min_distance=16)` — the inverse,
  for "I changed a color preference but nothing on screen moved"
  regressions.

### `tests-e2e/visuals/`

- `visuals/__init__.py` (empty package marker).
- `visuals/test_drawer_paint_baseline.py` — three baseline tests:
  - `test_drawer_bottom_sheet_paint` — sample the bottom-sheet bg.
  - `test_drawer_search_bar_paint` — sample the search container.
  - `test_drawer_paint_stable_across_open_close_cycle` — open → sample
    → close → reopen → sample → assert match within tolerance. Catches
    state-machine glitches that change the drawer's paint after a cycle
    (e.g., a stale `mHeaderColor` that survives close → reopen).

The baselines deliberately do NOT assert specific RGB values. The
absolute color depends on theme + dynamic colors + DPI, all of which
vary across AVD images and host configurations. Asserting specific
colors would flake. The baselines instead verify:

1. The pixel is samplable (view exists, screen capture works).
2. Channels are in 8-bit range (sanity).
3. **Across an open/close cycle the color is stable** — the load-bearing
   check that catches accidental paint changes.

### `pyproject.toml`

Adds `visuals` to `testpaths` and adds the `visuals` marker.

## Why this approach (not golden images)

Golden image comparison is the default playbook for visual regression
testing, but on AVDs:

- Compositor varies by kernel/skin combination even for the same image
  build.
- DPI rounding produces sub-pixel anti-alias differences.
- Wallpaper rendering uses a system service that can return slightly
  different bitmaps for the same theme.

Pixel sampling at view-relative coordinates is deterministic. The
trade-off is reduced sensitivity (we'd miss a layout shift that doesn't
change the sampled pixel), which is acceptable because layout
regressions ARE caught by the existing bounds-based smoke tests.

## How Phase 2/3 will use this

When the drawer-color migration lands:

1. **Phase 2** (subscriber wiring, no behavior change): the existing
   baseline tests continue passing because the subscriber updates the
   same fields the legacy on-demand reader did. Smoke + regression +
   visuals together gate every migration commit.

2. **Phase 3** (impact downgrade + onConfigChanged removal): new tests
   in `regression/test_prefs_cascade.py` will:
   - Open Settings → drawer colors → tap a color chip.
   - Return home → open drawer.
   - Use `assert_pixel_changed(before, after)` to confirm the color
     update actually applied.
   - Use `assert_pixel_close(stable_surface, baseline)` to confirm
     other surfaces were not accidentally repainted.
   - Sample a `Debug.mGetMethodCount` (instrumented) or logcat count to
     confirm `mModel.rebindCallbacks` did NOT fire — the perf-win
     contract from `docs/plans/003-unified-prefs-framework-v2.md`.

## Verification

- `assembleDebug` not required — pure test code addition.
- `tests-e2e/smoke/ + regression/ + visuals/`: 25/25 in 303s.
- Standalone `visuals/` run: 3/3 in 32s.

## Files

- `tests-e2e/lib/visuals.py` (new, 105 lines)
- `tests-e2e/visuals/__init__.py` (new, empty)
- `tests-e2e/visuals/test_drawer_paint_baseline.py` (new, 81 lines)
- `tests-e2e/pyproject.toml` — +`visuals` testpath + marker
- `docs/changes/069-visuals-baseline-framework.md` (this file)
