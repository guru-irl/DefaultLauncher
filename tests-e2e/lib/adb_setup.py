"""Device setup helpers used by conftest fixtures."""

from __future__ import annotations

import os
import subprocess
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
