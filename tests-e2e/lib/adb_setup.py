"""Device setup helpers used by conftest fixtures."""

from __future__ import annotations

import os
import subprocess
import time
from pathlib import Path
from typing import Optional

import uiautomator2 as u2

from . import selectors as S

REPO_ROOT = Path(__file__).resolve().parents[2]
DEFAULT_APK = REPO_ROOT / "build" / "outputs" / "apk" / "debug" / "DefaultLauncher-debug.apk"

# Default serial from environment (set by run.sh or caller when multiple devices attached).
_DEFAULT_SERIAL: Optional[str] = os.environ.get("ANDROID_SERIAL")


def adb(*args: str, serial: Optional[str] = None, check: bool = True) -> str:
    cmd = ["adb"]
    effective_serial = serial or _DEFAULT_SERIAL
    if effective_serial:
        cmd += ["-s", effective_serial]
    cmd += list(args)
    result = subprocess.run(cmd, capture_output=True, text=True, check=check)
    return result.stdout


def ensure_device_ready(d: u2.Device) -> None:
    """Wake screen, dismiss keyguard, set rotation to natural."""
    d.shell("input keyevent KEYCODE_WAKEUP")
    d.shell("wm dismiss-keyguard")
    try:
        d.set_orientation("natural")
    except Exception:
        pass


def install_apk(apk_path: Path = DEFAULT_APK, serial: Optional[str] = None) -> None:
    if not apk_path.exists():
        raise FileNotFoundError(f"APK not found at {apk_path}; build with assembleDebug first.")
    adb("install", "-r", "-d", "-g", str(apk_path), serial=serial)


def set_as_default_home(serial: Optional[str] = None) -> None:
    adb("shell", "cmd", "package", "set-home-activity",
        f"{S.PACKAGE}/{S.LAUNCH_ACTIVITY}", serial=serial)


def reset_launcher_data(serial: Optional[str] = None) -> None:
    """pm clear wipes prefs + database. Use for stateful tests that need
    a clean start. Re-applies as default home after clear."""
    adb("shell", "pm", "clear", S.PACKAGE, serial=serial)
    set_as_default_home(serial=serial)


# Action handled by WorkspaceSeedReceiver (DEBUG-only BroadcastReceiver).
_SEED_ACTION = "com.guru.defaultlauncher.test.SEED_WORKSPACE"
# Seed anchor icon — must match WorkspaceSeedReceiver.SETTINGS_INTENT title.
SEED_ICON_DESC = "Settings"
# Second seed icon for folder-creation tests.
SEED_ICON_2_DESC = "Chrome"


def seed_workspace(d: u2.Device, serial: Optional[str] = None) -> None:
    """Reset the launcher to the canonical two-icon test fixture.

    Steps:
      1. pm clear (wipes DB + prefs)
      2. am start to restart the launcher (pm clear doesn't auto-restart it)
      3. Wait for the launcher to load (model bind)
      4. Broadcast SEED_WORKSPACE to WorkspaceSeedReceiver, which calls
         ModelDbController directly to insert Settings+(0,2) and Chrome+(1,2),
         then triggers forceReload()
      5. Verify Settings icon appears on the workspace

    After this call the workspace has exactly two icons: Settings at (0,2)
    and Chrome at (1,2). All other workspace items are removed.
    """
    effective_serial = serial or _DEFAULT_SERIAL

    # 1. Clear all launcher data (DB + prefs + icon cache).
    adb("shell", "pm", "clear", S.PACKAGE, serial=effective_serial)
    set_as_default_home(serial=effective_serial)
    time.sleep(1)

    # 2. Start the launcher. pm clear doesn't auto-restart the home app.
    adb("shell", "am", "start", "-n",
        f"{S.PACKAGE}/{S.LAUNCH_ACTIVITY}", "-f", "0x10200000",
        serial=effective_serial, check=False)
    time.sleep(3)  # Allow model to bind and default workspace to load.

    # 3. Fire the seed broadcast. WorkspaceSeedReceiver handles it on
    #    MODEL_EXECUTOR to avoid ContentProvider same-process restrictions.
    adb("shell", "am", "broadcast",
        "-p", S.PACKAGE,
        "-a", _SEED_ACTION,
        serial=effective_serial, check=False)
    time.sleep(2)  # Allow forceReload() to complete.

    # 4. Verify the Settings icon is visible.
    appeared = d(resourceId=S.ID_WORKSPACE).child(description=SEED_ICON_DESC).wait(
        timeout=10.0
    )
    if not appeared:
        raise RuntimeError(
            f"Workspace seed failed: '{SEED_ICON_DESC}' icon did not appear "
            f"after SEED_WORKSPACE broadcast. Check WorkspaceSeedReceiver logs."
        )
