"""Launcher settings golden paths: can open from long-press menu, screens present.

These do not assert specific preference UI text — just that the entry point
exists and the settings activity opens."""

import pytest

from lib import selectors as S


@pytest.mark.smoke
@pytest.mark.settings
def test_launcher_settings_activity_launches(launcher):
    """Launcher settings activity launches via intent and renders content."""
    launcher.go_home()
    launcher.open_launcher_settings()
    # Verify some preference content rendered. Settings is a PreferenceFragment
    # so look for an Android-canonical preferences list.
    has_prefs = launcher.d(className="androidx.recyclerview.widget.RecyclerView").wait(
        timeout=S.DEFAULT_WAIT
    )
    assert has_prefs, "settings activity opened but no preferences rendered"
    launcher.go_home()
    assert launcher.is_home()


@pytest.mark.smoke
@pytest.mark.settings
def test_settings_top_level_categories_present(launcher):
    """At least one expected category header is visible on the top settings page."""
    launcher.go_home()
    launcher.open_launcher_settings()
    # Categories on the top settings page (case as rendered).
    expected_any = ("Grids", "Home Screen", "App Drawer", "Colors", "Search")
    found = any(launcher.d(text=label).wait(timeout=S.ANIMATION_WAIT) for label in expected_any)
    launcher.go_home()
    assert found, "no recognizable settings category visible"
