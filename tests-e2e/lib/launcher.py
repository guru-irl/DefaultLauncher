"""LauncherDriver — high-level facade over uiautomator2 for DefaultLauncher.

Tests use this. Selectors stay in lib/selectors.py. If a UI structure changes,
update this file + selectors.py and ALL tests benefit.

Design notes:
- No raw sleeps. Use uiautomator2 wait semantics.
- Each method either succeeds or raises with diagnostic info.
- Methods are named for user intent (open_drawer, type_search, drag_to_hotseat),
  not UI mechanics.
"""

from __future__ import annotations

import time
from contextlib import contextmanager
from dataclasses import dataclass
from typing import Iterator, Optional

import uiautomator2 as u2

from . import selectors as S


@dataclass
class DriverError(Exception):
    """Test-level failure with diagnostic context."""
    message: str
    hierarchy_snippet: Optional[str] = None

    def __str__(self) -> str:
        if self.hierarchy_snippet:
            return f"{self.message}\n--- hierarchy ---\n{self.hierarchy_snippet[:2000]}"
        return self.message


class LauncherDriver:
    """High-level facade over uiautomator2 for DefaultLauncher tests."""

    def __init__(self, device: u2.Device) -> None:
        self.d = device

    # ----- lifecycle -----------------------------------------------------

    def is_home(self) -> bool:
        # Primary check via app_current(). On Android 17 with a recent
        # background task (e.g., Phone app launched by a test), app_current()
        # can return a stale background activity instead of the foreground one.
        # Secondary check via workspace visibility avoids fixture thrash.
        cur = self.d.app_current()
        if (cur.get("package") == S.PACKAGE
                and cur.get("activity") == S.LAUNCH_ACTIVITY):
            return True
        # Fallback: workspace exists ↔ launcher is the foreground activity.
        return self.d(resourceId=S.ID_WORKSPACE).exists

    def go_home(self) -> None:
        """Send HOME key. No-op if already home."""
        if self.is_home():
            return
        self.d.press("home")
        # wait_activity uses app_current() which is stale on Android 17.
        # Wait for workspace to appear instead.
        deadline = time.time() + S.DEFAULT_WAIT
        while time.time() < deadline:
            if self.is_home():
                return
            time.sleep(0.2)

    def force_stop(self) -> None:
        """Hard-reset launcher process. Use sparingly (loses state)."""
        self.d.app_stop(S.PACKAGE)
        self.d.app_start(S.PACKAGE)
        self.d.wait_activity(S.LAUNCH_ACTIVITY, timeout=S.DEFAULT_WAIT * 2)

    # ----- workspace ----------------------------------------------------

    def workspace_visible(self) -> bool:
        return self.d(resourceId=S.ID_WORKSPACE).exists

    def hotseat_visible(self) -> bool:
        return self.d(resourceId=S.ID_HOTSEAT).exists

    def find_app_icon(self, label: str) -> u2.UiObject:
        """Find an app icon by its accessibility label. Raises if missing."""
        icon = self.d(description=label)
        if not icon.wait(timeout=S.DEFAULT_WAIT):
            raise DriverError(
                f"App icon '{label}' not found",
                hierarchy_snippet=self.d.dump_hierarchy(),
            )
        return icon

    def launch_app(self, label: str) -> str:
        """Tap an app icon; return the resulting foreground package."""
        self.find_app_icon(label).click()
        time.sleep(S.ANIMATION_WAIT)
        return self.d.app_current().get("package", "")

    # ----- app drawer ---------------------------------------------------

    def drawer_open(self) -> bool:
        return self.d(resourceId=S.ID_ALL_APPS_CONTAINER).exists

    def open_drawer(self) -> None:
        """Swipe up from hotseat area to open all-apps drawer."""
        if self.drawer_open():
            return
        info = self.d.info
        w, h = info["displayWidth"], info["displayHeight"]
        # Swipe from ~90% to ~30% of screen height, vertical center.
        self.d.swipe(w // 2, int(h * 0.9), w // 2, int(h * 0.3), duration=0.25)
        # Wait for either the container or the recycler to appear.
        if not self.d(resourceId=S.ID_ALL_APPS_CONTAINER).wait(timeout=S.DEFAULT_WAIT):
            if not self.d(resourceId=S.ID_ALL_APPS_RECYCLER).wait(timeout=S.DEFAULT_WAIT):
                raise DriverError("drawer did not open after swipe-up",
                                  hierarchy_snippet=self.d.dump_hierarchy())

    def close_drawer(self) -> None:
        """Press back / press home to dismiss."""
        if not self.drawer_open():
            return
        self.d.press("back")
        time.sleep(S.ANIMATION_WAIT)
        if self.drawer_open():
            self.d.press("home")
            time.sleep(S.ANIMATION_WAIT)

    # ----- search -------------------------------------------------------

    def search_input(self) -> u2.UiObject:
        # Search field is inside the drawer header. The resource ID may be
        # slightly different across builds — fall back to className.
        candidate = self.d(resourceId=S.ID_SEARCH_INPUT)
        if candidate.exists:
            return candidate
        return self.d(className="android.widget.EditText")

    def type_search(self, query: str, clear: bool = True) -> None:
        if not self.drawer_open():
            self.open_drawer()
        edit = self.search_input()
        if not edit.wait(timeout=S.DEFAULT_WAIT):
            raise DriverError("search input not visible",
                              hierarchy_snippet=self.d.dump_hierarchy())
        edit.click()
        if clear:
            edit.clear_text()
        edit.send_keys(query)
        time.sleep(S.ANIMATION_WAIT)  # debounce window

    def clear_search(self) -> None:
        edit = self.search_input()
        if edit.exists:
            edit.clear_text()
            time.sleep(S.ANIMATION_WAIT)

    # ----- folder -------------------------------------------------------

    def folder_open(self) -> bool:
        return self.d(resourceId=S.ID_FOLDER_CONTAINER).exists

    def close_folder(self) -> None:
        if self.folder_open():
            self.d.press("back")
            time.sleep(S.ANIMATION_WAIT)

    # ----- settings -----------------------------------------------------

    def open_launcher_settings(self) -> None:
        """Launch the launcher settings activity directly via intent.

        Uses CLEAR_TOP + RESET_TASK_IF_NEEDED so we always land on the top
        preference page, never a previously-visited subpage. Long-press →
        menu → settings flow is exercised separately in regression tests.
        """
        # FLAG_ACTIVITY_CLEAR_TOP (0x04000000) + FLAG_ACTIVITY_NEW_TASK (0x10000000) +
        # FLAG_ACTIVITY_RESET_TASK_IF_NEEDED (0x00200000) = 0x14200000
        self.d.shell(
            f"am start -n {S.PACKAGE}/{S.SETTINGS_ACTIVITY} -f 0x14200000"
        )
        # wait_activity() uses app_current() which returns stale data on Android 17
        # when a background task (e.g., Phone dialer) sits in the activity stack.
        # Instead, wait for the workspace to disappear, then wait for the
        # PreferenceFragment RecyclerView to render. Avoids the semi-transparent
        # white-screen window where the workspace is gone but preferences haven't
        # drawn yet.
        deadline = time.time() + S.DEFAULT_WAIT
        while time.time() < deadline:
            if not self.d(resourceId=S.ID_WORKSPACE).exists:
                # Settings window is up. Wait for preference content to render.
                self.d(
                    className="androidx.recyclerview.widget.RecyclerView"
                ).wait(timeout=S.DEFAULT_WAIT)
                return
            time.sleep(0.2)
        raise DriverError(
            f"Settings activity did not appear: current={self.d.app_current()}",
            hierarchy_snippet=self.d.dump_hierarchy(),
        )

    # ----- clock widget -------------------------------------------------

    def place_clock_widget(self) -> None:
        """Place the Danfo clock widget via the debug broadcast seam, then wait.

        Uses the `-p <package>` broadcast form (not `-n <receiver>`): on
        Android 16 a backgrounded process defers `-n` broadcasts, so the
        receiver may never run. This mirrors adb_setup.seed_workspace().
        """
        self.d.shell(
            f"am broadcast -p {S.PACKAGE} -a {S.SEED_ACTION_PLACE_CLOCK}"
        )
        self.d(description=S.DESC_CLOCK_WIDGET).wait(timeout=S.DEFAULT_WAIT)

    def reset_workspace(self) -> None:
        """Restore the canonical seed workspace (removes any placed widget)."""
        self.d.shell(
            f"am broadcast -p {S.PACKAGE} -a {S.SEED_ACTION_RESET}"
        )
        self.d(description=S.SEED_ICON_DESC).wait(timeout=S.DEFAULT_WAIT)

    def clock_widget_present(self) -> bool:
        return self.d(description=S.DESC_CLOCK_WIDGET).exists

    def open_widget_picker(self) -> None:
        """Long-press an empty home cell and open the Widgets picker."""
        info = self.d.info
        w, h = info["displayWidth"], info["displayHeight"]
        self.d.long_click(w // 2, int(h * 0.22))  # empty area above the seed row
        widgets = self.d(text="Widgets")
        assert widgets.wait(timeout=S.DEFAULT_WAIT), "Widgets option not shown"
        widgets.click()
        # Wait for the picker list to render before callers interact with it.
        self.d(resourceId=S.ID_WIDGETS_LIST).wait(timeout=S.DEFAULT_WAIT)

    def search_widget_picker(self, query: str) -> None:
        """Type into the widget picker's search field to filter the list.

        Widgets in the picker are grouped under collapsed app headers, so a
        launcher-shipped widget like the clock is not visible until its app
        group is expanded. The search bar surfaces it directly and is far
        more robust than scrolling/expanding on a loaded AVD.
        """
        sb = self.d(resourceId=S.ID_WIDGETS_SEARCH_INPUT)
        if not sb.wait(timeout=S.DEFAULT_WAIT):
            raise DriverError(
                "widget picker search field not visible",
                hierarchy_snippet=self.d.dump_hierarchy(),
            )
        sb.click()
        sb.send_keys(query)
        time.sleep(S.ANIMATION_WAIT)  # debounce window

    # ----- diagnostics --------------------------------------------------

    @contextmanager
    def on_failure_dump(self, name: str, artifacts_dir: str) -> Iterator[None]:
        """Context manager: on any exception, dump screenshot + hierarchy."""
        import os
        os.makedirs(artifacts_dir, exist_ok=True)
        try:
            yield
        except Exception:
            png_path = os.path.join(artifacts_dir, f"{name}.png")
            xml_path = os.path.join(artifacts_dir, f"{name}.xml")
            try:
                self.d.screenshot(png_path)
            except Exception:
                pass
            try:
                with open(xml_path, "w", encoding="utf-8") as f:
                    f.write(self.d.dump_hierarchy())
            except Exception:
                pass
            raise

    def screenshot(self, path: str) -> None:
        self.d.screenshot(path)

    def logcat_tail(self, lines: int = 200) -> str:
        out = self.d.shell(f"logcat -d -t {lines}")
        if hasattr(out, "output"):
            return out.output
        return str(out)
