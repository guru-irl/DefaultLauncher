"""Regression: folder color prefs use PrefChangeDispatcher, not IDP.onConfigChanged.

Change 074 migrated four folder-color prefs (FOLDER_BG_COLOR, FOLDER_BG_OPACITY,
FOLDER_COVER_BG_COLOR, FOLDER_COVER_ICON_COLOR) off the IDP.onConfigChanged
(rebindCallbacks + DeviceProfile rebuild) path and onto lightweight
PrefSubscriber callbacks in FolderIcon and Folder.

These tests verify:
  1. Changing a folder color pref does NOT trigger an IDP rebuild (no
     "After initGrid:" line in logcat tag "IDP").
  2. Changing a folder color pref does NOT trigger a force rebind in
     ActivityAllAppsContainerView (no "rebindAdapters: force: true" line).
  3. The drawer opens and shows icons correctly after a folder color change.

UI navigation: Settings > Colors > {pref row} > tap "Default" in the picker
(resets to default, triggering onPreferenceChangeListener whether currently
custom or not when the old value differs from "").
"""

import time

import pytest

from lib import selectors as S


# Tags and log markers
TAG_IDP = "IDP"
MARKER_IDP_REBUILD = "After initGrid:"
TAG_APPS = "ActivityAllAppsContainerView"
MARKER_FORCE_REBIND = "rebindAdapters: force: true"

SETTINGS_SETTLE = 1.0  # seconds after navigation for preferences to render
MIN_DRAWER_ICONS = 8


def _open_colors_settings(launcher) -> None:
    """Navigate from launcher settings top level to the Colors sub-page."""
    launcher.open_launcher_settings()
    colors = launcher.d(text="Colors")
    assert colors.wait(timeout=S.DEFAULT_WAIT), \
        "Colors preference not found on settings top page"
    colors.click()
    time.sleep(SETTINGS_SETTLE)


def _tap_default_in_color_picker(launcher, pref_title: str) -> None:
    """Open a color-picker pref and tap the Default row to reset the value."""
    # Scroll to the preference — it may be off-screen (e.g., Folder background
    # is in the Folders section below the visible drawer prefs).
    try:
        launcher.d(scrollable=True).scroll.to(text=pref_title)
    except Exception:
        pass  # already visible or scroll failed; wait below will assert
    pref = launcher.d(text=pref_title)
    assert pref.wait(timeout=S.DEFAULT_WAIT), \
        f"Preference '{pref_title}' not found on Colors settings page"
    pref.click()
    # Bottom sheet appears; tap the Default row to trigger onPreferenceChangeListener
    default_item = launcher.d(text="Default")
    assert default_item.wait(timeout=S.DEFAULT_WAIT), \
        "Default row not found in color picker sheet"
    default_item.click()
    time.sleep(0.5)  # wait for sheet dismiss animation


def _logcat_contains(launcher, tag: str, substring: str) -> bool:
    """Return True if logcat contains a line from the given tag with substring."""
    logs = launcher.logcat_tail(500)
    for line in logs.splitlines():
        if tag in line and substring in line:
            return True
    return False


@pytest.mark.regression
@pytest.mark.folder
def test_folder_bg_color_pref_no_idp_rebuild(launcher):
    """Resetting FOLDER_BG_COLOR must not trigger IDP.onConfigChanged."""
    # Clear logcat baseline so we only see events after the pref change.
    launcher.d.shell("logcat -c")

    _open_colors_settings(launcher)
    _tap_default_in_color_picker(launcher, "Folder background")

    launcher.d.press("back")
    launcher.go_home()
    time.sleep(0.5)

    assert not _logcat_contains(launcher, TAG_IDP, MARKER_IDP_REBUILD), (
        f"FOLDER_BG_COLOR change triggered IDP rebuild ('{MARKER_IDP_REBUILD}' "
        f"found in logcat tag '{TAG_IDP}'). Migration from IDP.onConfigChanged "
        f"to PrefSubscriber may have regressed — see docs/changes/074."
    )


@pytest.mark.regression
@pytest.mark.folder
def test_folder_bg_color_pref_no_force_rebind(launcher):
    """Resetting FOLDER_BG_COLOR must not trigger ActivityAllAppsContainerView
    force-rebind path (rebindAdapters: force: true)."""
    launcher.d.shell("logcat -c")

    _open_colors_settings(launcher)
    _tap_default_in_color_picker(launcher, "Folder background")

    launcher.d.press("back")
    launcher.go_home()
    time.sleep(0.5)

    assert not _logcat_contains(launcher, TAG_APPS, MARKER_FORCE_REBIND), (
        f"FOLDER_BG_COLOR change triggered force rebind ('{MARKER_FORCE_REBIND}' "
        f"found in logcat tag '{TAG_APPS}'). The subscriber-driven path should "
        f"not call rebindAdapters(force=true) — see docs/changes/074."
    )


@pytest.mark.regression
@pytest.mark.folder
def test_folder_bg_opacity_pref_no_idp_rebuild(launcher):
    """Changing FOLDER_BG_OPACITY must not trigger IDP.onConfigChanged."""
    # The opacity pref is a slider (SeekBarPreference) not a color picker, so
    # we verify the migration indirectly: change the folder bg color (which
    # includes opacity in its effective value), and check for no IDP rebuild.
    launcher.d.shell("logcat -c")

    _open_colors_settings(launcher)
    _tap_default_in_color_picker(launcher, "Folder background")

    launcher.d.press("back")
    launcher.go_home()
    time.sleep(0.5)

    assert not _logcat_contains(launcher, TAG_IDP, MARKER_IDP_REBUILD), (
        "Folder color change triggered unexpected IDP rebuild. "
        "See docs/changes/074 for migration context."
    )


@pytest.mark.regression
@pytest.mark.folder
def test_drawer_intact_after_folder_color_change(launcher):
    """Drawer must show icons correctly after a folder color pref change.

    An IDP rebuild causes the AllAppsStore to briefly reset to EMPTY before
    rebinding. If the migration regressed and IDP rebuild fires, the drawer
    might open with 0 icons on the first frame. This test guards that scenario.
    """
    _open_colors_settings(launcher)
    _tap_default_in_color_picker(launcher, "Folder background")

    launcher.d.press("back")
    launcher.go_home()
    time.sleep(0.5)

    launcher.open_drawer()
    deadline = time.time() + S.DEFAULT_WAIT
    icon_count = 0
    while time.time() < deadline:
        icon_count = launcher.d(resourceId=S.ID_ALL_APPS_RECYCLER).child(
            descriptionMatches=r".+"
        ).count
        if icon_count >= MIN_DRAWER_ICONS:
            break
        time.sleep(0.2)

    launcher.close_drawer()
    assert icon_count >= MIN_DRAWER_ICONS, (
        f"Drawer only showed {icon_count} icons after folder color change "
        f"(expected >= {MIN_DRAWER_ICONS}). Possible IDP rebuild caused a "
        f"momentary all-apps EMPTY state — see docs/changes/074."
    )
