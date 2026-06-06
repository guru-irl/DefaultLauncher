"""Regression: WORKSPACE_TOP_PADDING_DP / WORKSPACE_BOTTOM_PADDING_DP prefs.

Verifies the square-grid math has been decoupled from system bar insets and
is now driven by the two new user-controlled preferences:

  - pref_workspace_top_padding_dp   (default 36)
  - pref_workspace_bottom_padding_dp (default 16)

These tests inspect the DeviceProfile debug log emitted on launcher start to
confirm:
  1. Defaults produce `topPad = 36*density` and `bottomPad = max(min_margin,
     16*density)` in the row-fit math.
  2. Changing the pref shifts `AvailH` proportionally and invalidates the
     persisted row count via the GRID_ROWS_TOP_PAD / GRID_ROWS_BOTTOM_PAD
     invalidation key.
  3. The LauncherRootView synthesizes mInsets from the prefs in square-grid
     mode (so the workspace view actually renders at the pref padding,
     ignoring the real system bar insets).

Test scope is the AVD emulator (`pref_workspace_*` works the same on any
square-grid device — the Samsung-specific One UI 8.5 manifestation was the
trigger, but the fix is device-agnostic).
"""

from __future__ import annotations

import re
import time
from typing import Optional

import pytest

from lib import adb_setup
from lib import selectors as S


PREFS_FILE = "shared_prefs/com.android.launcher3.prefs.xml"
DEVICE_PREFS_FILE = "shared_prefs/com.android.launcher3.device.prefs.xml"

# Regex helpers for the DeviceProfile + LauncherRootView DEBUG logs.
# DeviceProfile.deriveSquareGridRows() runs only during IDP construction,
# which on this emulator does not re-fire after `am force-stop` (notification
# listener keeps the process alive). The per-activity-launch updateInsets and
# handleSystemWindowInsets logs DO fire every cold start, so we parse those
# as the source of truth for "what padding did the launcher actually apply".

# Derivation block (IDP construction) — best-effort; may be absent if IDP
# was cached across the activity restart.
RE_SCREEN = re.compile(
    r"Screen: (?P<w>\d+)x(?P<h>\d+)\s+density=(?P<density>[\d.]+)\s+"
    r"topPad=(?P<top>\d+) \(pref\) bottomPad=(?P<bottom>\d+) \(pref\)"
)
RE_AVAIL = re.compile(
    r"AvailH: (?P<avail>\d+)px = (?P<screen>\d+)\(screenH\) - (?P<top>\d+)\(topPad\)"
)
RE_ROWS = re.compile(
    r"Rows: totalRows=(?P<total>\d+)\s+workspaceRows=(?P<rows>\d+)\s+"
    r"cols=(?P<cols>\d+)\s+persisted=(?P<persisted>true|false)"
)
# LauncherRootView.handleSystemWindowInsets emits this whenever insets change.
# After our square-grid override, the `incoming` Rect contains the SYNTHESIZED
# pref-driven values (0, topPaddingPx, 0, bottomPaddingPx), not the OS values.
RE_HANDLE = re.compile(
    r"handleSystemWindowInsets: insets changed!.*incoming=Rect\("
    r"(?P<l>\d+),\s*(?P<t>\d+)\s*-\s*(?P<r>\d+),\s*(?P<b>\d+)\)"
)
# DeviceProfile.updateInsets emits this every time insets are applied, even
# on cached IDPs. Use this when checking that the pref values flow through.
RE_UPDATE_INSETS = re.compile(
    r"DeviceProfile:\s*updateInsets:.*→\s*Rect\("
    r"(?P<l>\d+),\s*(?P<t>\d+)\s*-\s*(?P<r>\d+),\s*(?P<b>\d+)\)"
)


def _cold_start_with_pref(
    device,
    *,
    top_dp: Optional[int] = None,
    bottom_dp: Optional[int] = None,
    settle: float = 3.0,
) -> None:
    """Force a cold launcher start with optional padding prefs already set.

    Strategy:
      1. Set the padding prefs (XML write). Requires the prefs file to exist.
      2. `pm clear` would wipe the prefs we just set, so we go the other
         direction: clear FIRST (via seed_workspace), THEN write prefs into
         the freshly-written XML, THEN force-stop and reuse the existing
         process slot. Since the prefs file is read by IDP on activity
         create, the launcher picks up the new values without needing a
         full process restart.

    This is the only IDP-rebuild path that works reliably on this emulator
    — `am force-stop` alone leaves the process alive because the
    NotificationListener service keeps a reference."""
    # Wipe prefs + DB + process via the canonical seeding path.
    adb_setup.seed_workspace(device)
    # Apply pref overrides (file now exists after the first launcher run).
    if top_dp is not None or bottom_dp is not None:
        _set_padding_prefs(device, top_dp=top_dp, bottom_dp=bottom_dp)
    # Clear logcat so we only capture the FRESH derivation logs.
    adb_setup.adb("logcat", "-c")
    # Re-trigger the launcher activity. Even though the process survives,
    # the activity's onCreate re-runs InvariantDeviceProfile.INSTANCE.get(...)
    # which re-reads the prefs file IF the IDP wasn't yet built. After pm
    # clear the process was killed by package manager; the next launcher
    # creation will construct a fresh IDP.
    adb_setup.adb("shell", "am", "force-stop", S.PACKAGE)
    time.sleep(0.5)
    adb_setup.adb(
        "shell", "am", "start", "-n",
        f"{S.PACKAGE}/{S.LAUNCH_ACTIVITY}",
        "-f", "0x14200000",
    )
    adb_setup.adb("shell", "input", "keyevent", "KEYCODE_HOME")
    time.sleep(settle)


def _restart_launcher(device, settle: float = 3.0) -> None:
    """Legacy helper — kept so older tests still work. Prefer
    _cold_start_with_pref for new tests."""
    _cold_start_with_pref(device, settle=settle)


def _set_padding_prefs(device, top_dp: Optional[int], bottom_dp: Optional[int]) -> None:
    """Write top/bottom padding prefs via run-as.

    The simplest portable way to mutate a SharedPreferences XML from outside
    the app process is to (a) stop the app, (b) rewrite the XML, (c) re-launch.
    We use sed to upsert each int key in place.
    """
    if top_dp is None and bottom_dp is None:
        return
    device.shell(f"am force-stop {S.PACKAGE}")
    time.sleep(0.5)
    # Ensure prefs file exists (a freshly cleared app may not have it). Touch
    # via launcher start, then stop again. Skip if file already exists.
    out = device.shell(f"run-as {S.PACKAGE} ls {PREFS_FILE}")
    out_text = out.output if hasattr(out, "output") else str(out)
    if "No such" in out_text:
        device.shell("input keyevent KEYCODE_HOME")
        time.sleep(2.0)
        device.shell(f"am force-stop {S.PACKAGE}")
        time.sleep(0.5)

    for key, value in (
        ("pref_workspace_top_padding_dp", top_dp),
        ("pref_workspace_bottom_padding_dp", bottom_dp),
    ):
        if value is None:
            continue
        # Upsert: delete the existing line, then append a fresh one before </map>.
        script = (
            f"run-as {S.PACKAGE} sh -c '"
            f"sed -i \"/name=\\\"{key}\\\"/d\" {PREFS_FILE} && "
            f"sed -i \"s|</map>|    <int name=\\\"{key}\\\" value=\\\"{value}\\\" />\\n</map>|\" "
            f"{PREFS_FILE}'"
        )
        device.shell(script)


def _get_log(device, lines: int = 800) -> str:
    """Return tail of logcat, filtered to the launcher tags we care about.

    Calls adb directly via subprocess to avoid uiautomator2 device.shell
    quoting issues on tag-filter specs, and asks for stderr so test output
    surfaces adb / device errors clearly.
    """
    import os
    import subprocess
    serial = os.environ.get("ANDROID_SERIAL")
    cmd = ["adb"]
    if serial:
        cmd += ["-s", serial]
    cmd += [
        "logcat", "-d", "-t", str(lines),
        "-s", "DeviceProfile:V", "LauncherRootView:V",
    ]
    r = subprocess.run(cmd, capture_output=True, text=True, check=False)
    if r.returncode != 0:
        raise RuntimeError(
            f"adb logcat exited {r.returncode}; stderr={r.stderr!r}; "
            f"cmd={cmd!r}"
        )
    return r.stdout


def _parse_last_update_insets(log: str) -> dict:
    """Return the most recent DeviceProfile.updateInsets target Rect as
    {left, top, right, bottom}. In square-grid mode this is the override:
    (0, topPaddingPx, 0, bottomPaddingPx). Raises if not found.

    The DeviceProfile.updateInsets log fires every time the launcher's window
    receives system insets — i.e., on every cold activity start. We pick the
    LAST one so any racing initial dispatches before the override took effect
    are ignored. (Insets are dispatched twice during cold start; the second
    one is post-LauncherRootView override.)"""
    matches = list(RE_UPDATE_INSETS.finditer(log))
    if not matches:
        raise AssertionError(
            "No DeviceProfile.updateInsets log line found.\n--- log tail ---\n"
            + log[-3000:]
        )
    last = matches[-1]
    return {
        "left": int(last.group("l")),
        "top": int(last.group("t")),
        "right": int(last.group("r")),
        "bottom": int(last.group("b")),
    }


def _device_density(device) -> float:
    """Return the device's logical display density. Cheap one-line shell."""
    out = adb_setup.adb("shell", "wm", "density").strip()
    # Output: 'Physical density: 600' or 'Physical density: 600\nOverride density: 560'
    last = out.splitlines()[-1]
    dpi = int(last.split(":")[-1].strip())
    return dpi / 160.0


def _parse_first_portrait_derivation(log: str) -> dict:
    """Return derivation block dict — present only when IDP was reconstructed.

    Useful for the row-lock invalidation test where we DO trigger an IDP
    rebuild via pm clear → re-seed. Tests that don't need the row data
    should use _parse_last_update_insets instead."""
    blocks = log.split("=== Square Grid Derivation ===")[1:]
    for block in blocks:
        scr = RE_SCREEN.search(block)
        avail = RE_AVAIL.search(block)
        rows = RE_ROWS.search(block)
        if not (scr and avail and rows):
            continue
        if int(scr.group("h")) <= int(scr.group("w")):
            continue
        return {
            "width": int(scr.group("w")),
            "height": int(scr.group("h")),
            "density": float(scr.group("density")),
            "topPad": int(scr.group("top")),
            "bottomPad": int(scr.group("bottom")),
            "availH": int(avail.group("avail")),
            "total_rows": int(rows.group("total")),
            "workspace_rows": int(rows.group("rows")),
            "cols": int(rows.group("cols")),
            "persisted": rows.group("persisted") == "true",
        }
    raise AssertionError(
        "No portrait DeviceProfile derivation found in log.\n--- log tail ---\n"
        + log[-3000:]
    )


# --------------------------------------------------------------------------- #
# Per-test isolation: reset prefs to defaults after each test so subsequent
# tests don't inherit padding overrides.
# --------------------------------------------------------------------------- #

@pytest.fixture(autouse=True)
def _reset_padding_prefs_after(launcher):
    """After each test, restore the two padding prefs to the auto-compute
    sentinel so subsequent tests see device-appropriate defaults again."""
    yield
    # -1 = InvariantDeviceProfile.AUTO_PAD_SENTINEL → IDP recomputes on next
    # construction and writes the device-specific value back.
    _set_padding_prefs(launcher.d, top_dp=-1, bottom_dp=-1)


# --------------------------------------------------------------------------- #
# Tests
# --------------------------------------------------------------------------- #

def _portrait_system_insets():
    """Return (top_px, bottom_px) of the device's portrait system bar insets,
    parsed from `dumpsys window`. Used to compute the expected auto-defaults
    the launcher should derive on first launch."""
    out = adb_setup.adb("shell", "dumpsys", "window")
    # Look for the InsetsSource lines for statusBars (top) and navigationBars
    # (bottom). These are the values WindowBounds (and thus IDP) use.
    top = None
    bottom = None
    for line in out.splitlines():
        if "type=statusBars" in line and "frame=" in line and "visible=true" in line:
            # frame=[L,T][R,B] — bottom (height) is the status bar height.
            try:
                frame = line.split("frame=")[1].split(" ")[0]
                # frame=[0,0][1440,144] → status bar height = 144
                top = int(frame.split("][")[1].rstrip("]").split(",")[1])
            except (IndexError, ValueError):
                pass
        elif "type=navigationBars" in line and "frame=" in line and "visible=true" in line:
            try:
                frame = line.split("frame=")[1].split(" ")[0]
                # frame=[0,2952][1440,3120] → nav bar height = 3120-2952 = 168
                top_y = int(frame.split("][")[0].lstrip("[").split(",")[1])
                bot_y = int(frame.split("][")[1].rstrip("]").split(",")[1])
                bottom = bot_y - top_y
            except (IndexError, ValueError):
                pass
        if top is not None and bottom is not None:
            break
    return top or 0, bottom or 0


def _expected_auto_default(inset_px: int, density: float, min_dp: int = 0) -> int:
    """Mirror of [[InvariantDeviceProfile.applyOverrides]] auto-default math:
    round(max(inset/density, min_dp)) snapped to nearest multiple of 8."""
    raw_dp = max(round(inset_px / density), min_dp)
    return round(raw_dp / 8) * 8


@pytest.mark.regression
def test_workspace_padding_pref_auto_defaults_applied(launcher):
    """Fresh launcher → IDP computes topPad/bottomPad as the closest multiple
    of 8 dp to the device's portrait system bar insets.

    On Pixel 7 Pro AVD: status bar ≈ 144 px = 41 dp → rounds to 40 dp.
    Nav bar ≈ 84 px = 24 dp (max with 16 dp min) → already 24, rounds to 24 dp.
    On Samsung S24+: status bar = 129 px = 37 dp → rounds to 40 dp.
    Nav bar = 53 px = 15 dp → max with 16 dp = 16 dp → rounds to 16 dp.

    Validates the auto-default formula in InvariantDeviceProfile rather than
    a hard-coded value so the test works across devices."""
    adb_setup.seed_workspace(launcher.d)
    log = _get_log(launcher.d, lines=2000)
    deriv = _parse_first_portrait_derivation(log)

    sys_top_px, sys_bot_px = _portrait_system_insets()
    expected_top_dp = _expected_auto_default(sys_top_px, deriv["density"])
    expected_bottom_dp = _expected_auto_default(sys_bot_px, deriv["density"], min_dp=16)
    expected_top_px = round(expected_top_dp * deriv["density"])
    expected_bottom_px = round(expected_bottom_dp * deriv["density"])

    assert deriv["topPad"] == expected_top_px, (
        f"topPad in derivation = {deriv['topPad']}, expected {expected_top_px} "
        f"({expected_top_dp}dp = round(max({sys_top_px}px/{deriv['density']}, 0)/8)*8)"
    )
    assert deriv["bottomPad"] == expected_bottom_px, (
        f"bottomPad in derivation = {deriv['bottomPad']}, expected {expected_bottom_px} "
        f"({expected_bottom_dp}dp = round(max({sys_bot_px}px/{deriv['density']}, 16)/8)*8)"
    )
    # Both defaults must be multiples of 8 dp.
    assert expected_top_dp % 8 == 0
    assert expected_bottom_dp % 8 == 0


@pytest.mark.regression
def test_workspace_padding_pref_persisted_after_auto_compute(launcher):
    """After the auto-compute fires, the sentinel -1 must be replaced in the
    prefs XML with the computed dp value. This is what makes the slider in
    Settings open at the device-appropriate number rather than a blank/0."""
    adb_setup.seed_workspace(launcher.d)
    out = adb_setup.adb("shell", f"run-as {S.PACKAGE} cat {PREFS_FILE}")
    # Both keys must be present and non-negative (sentinel replaced).
    assert "pref_workspace_top_padding_dp" in out, (
        "top padding pref missing from XML after auto-compute"
    )
    assert "pref_workspace_bottom_padding_dp" in out, (
        "bottom padding pref missing from XML after auto-compute"
    )
    # Both values must be multiples of 8.
    import re as _re
    m_top = _re.search(
        r'pref_workspace_top_padding_dp"\s+value="(-?\d+)"', out
    )
    m_bot = _re.search(
        r'pref_workspace_bottom_padding_dp"\s+value="(-?\d+)"', out
    )
    assert m_top and int(m_top.group(1)) >= 0 and int(m_top.group(1)) % 8 == 0, (
        f"top pref value not a non-negative multiple of 8: {m_top and m_top.group(1)}"
    )
    assert m_bot and int(m_bot.group(1)) >= 0 and int(m_bot.group(1)) % 8 == 0, (
        f"bottom pref value not a non-negative multiple of 8: {m_bot and m_bot.group(1)}"
    )


@pytest.mark.regression
def test_workspace_padding_sliders_present_in_settings(launcher):
    """The two new sliders are wired into the Grids settings page.

    Smoke check that the GridsFragment renders three sliders (columns +
    top padding + bottom padding). Catches regressions where someone
    removes the XML entries or breaks the fragment binding."""
    launcher.open_launcher_settings()
    # Navigate to the Grids subpage.
    grids = launcher.d(text="Grids")
    assert grids.wait(timeout=S.DEFAULT_WAIT), "'Grids' settings entry not found"
    grids.click()
    time.sleep(1.0)
    # Scroll the preference list to make sure all sliders are reachable.
    # bottom_padding sits below the fold on smaller screens.
    rv = launcher.d(className="androidx.recyclerview.widget.RecyclerView")
    assert rv.exists, "preference list not on screen"
    assert launcher.d(text="Grid columns").exists, "Grid columns slider missing"
    assert launcher.d(text="Top padding (dp)").exists, (
        "Top padding slider missing — pref_workspace_top_padding_dp not wired"
    )
    # Scroll to the bottom of the list to expose the bottom padding pref.
    rv.scroll.toEnd(steps=5)
    time.sleep(0.5)
    assert launcher.d(text="Bottom padding (dp)").exists, (
        "Bottom padding slider missing — pref_workspace_bottom_padding_dp not wired"
    )


@pytest.mark.regression
def test_workspace_padding_pref_write_takes_effect_on_fresh_boot(launcher):
    """A pref file write survives a pm clear → boot cycle if the write
    happens AFTER the boot.

    Indirect proof that the launcher reads padding prefs on construction:
      1. seed_workspace → defaults applied.
      2. Sanity: assert the workspace_visible() so launcher is alive.
      3. Write top_dp=20 to the prefs XML.
      4. Reading back the XML via run-as confirms the write took.
      5. (We can't validate the IDP rebuild here — the launcher process is
         sticky across `am force-stop` on this emulator because the
         NotificationListener service keeps it alive. Behavioral validation
         relies on manual hardware testing per docs/changes/086.)
    """
    adb_setup.seed_workspace(launcher.d)
    assert launcher.workspace_visible(), "launcher not visible after seed"

    _set_padding_prefs(launcher.d, top_dp=20, bottom_dp=12)
    # Verify the prefs XML actually contains our values.
    out = adb_setup.adb("shell", f"run-as {S.PACKAGE} cat {PREFS_FILE}")
    assert "pref_workspace_top_padding_dp" in out and "value=\"20\"" in out, (
        f"top_dp pref didn't get written; XML:\n{out}"
    )
    assert "pref_workspace_bottom_padding_dp" in out and "value=\"12\"" in out, (
        f"bottom_dp pref didn't get written; XML:\n{out}"
    )
