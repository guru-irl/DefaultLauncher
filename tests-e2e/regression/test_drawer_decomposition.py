"""Regression: drawer decomposition Phase 1 invariants.

T3.1 Phase 1 (docs/plans/004-drawer-decomposition-v2.md) extracts
DrawerColorController and SearchFabController out of
ActivityAllAppsContainerView. These tests are written BEFORE the extraction
to define the behavioral contract that must survive it.

Tests in this file:
  Phase 1 — color + FAB behavior (5 tests):
    test_drawer_color_pref_invalidates_paint_without_relayout
    test_drawer_tab_color_pref_no_setup_header_storm
    test_search_fabs_appear_when_query_nonempty
    test_search_fabs_disappear_on_query_clear
    test_search_fab_web_intent_launches_browser

Each test must pass both before and after the extraction. A failing test
post-extraction signals an invariant was broken during Phase 1.
"""

import time

import pytest

from lib import selectors as S


# --------------------------------------------------------------------------- #
# Log markers reused across multiple tests
# --------------------------------------------------------------------------- #
TAG_APPS = "ActivityAllAppsContainerView"
MARKER_FORCE_REBIND = "rebindAdapters: force: true"
TAG_IDP = "IDP"
MARKER_IDP_REBUILD = "After initGrid:"

# FAB text label (defined in strings.xml)
FAB_WEB_SEARCH_TEXT = "Web search"

SETTINGS_SETTLE = 1.0


def _logcat_contains(launcher, tag: str, substring: str) -> bool:
    """Return True if current logcat has a line matching tag+substring."""
    for line in launcher.logcat_tail(500).splitlines():
        if tag in line and substring in line:
            return True
    return False


def _open_colors_settings(launcher) -> None:
    launcher.open_launcher_settings()
    colors = launcher.d(text="Colors")
    assert colors.wait(timeout=S.DEFAULT_WAIT), "Colors pref not found"
    colors.click()
    time.sleep(SETTINGS_SETTLE)


def _tap_default_in_color_picker(launcher, pref_title: str) -> None:
    pref = launcher.d(text=pref_title)
    assert pref.wait(timeout=S.DEFAULT_WAIT), f"Pref '{pref_title}' not found"
    pref.click()
    default_item = launcher.d(text="Default")
    assert default_item.wait(timeout=S.DEFAULT_WAIT), "Default row not found"
    default_item.click()
    time.sleep(0.5)


# --------------------------------------------------------------------------- #
# Phase 1: DrawerColorController invariants
# --------------------------------------------------------------------------- #

@pytest.mark.regression
@pytest.mark.drawer
def test_drawer_color_pref_invalidates_paint_without_relayout(launcher):
    """Toggling DRAWER_BG_COLOR must NOT trigger a force rebind.

    Pre-T2.3: any drawer-color change called IDP.onConfigChanged, which
    called rebindAdapters(force=true). Post-T2.3/Phase1: the subscriber
    calls refreshCustomColors() only — no adapter rebuild.

    Verification: reset the drawer bg color (which triggers
    onPreferenceChangeListener) and assert no force-rebind log line.
    """
    launcher.go_home()
    launcher.d.shell("logcat -c")

    _open_colors_settings(launcher)
    _tap_default_in_color_picker(launcher, "Drawer background")

    launcher.d.press("back")
    launcher.go_home()
    time.sleep(0.5)

    assert not _logcat_contains(launcher, TAG_APPS, MARKER_FORCE_REBIND), (
        f"DRAWER_BG_COLOR change triggered '{MARKER_FORCE_REBIND}'. "
        "The DrawerColorController subscriber must not cause a force rebind. "
        "See T2.3 docs/changes/071 + docs/plans/004 Phase 1."
    )
    assert not _logcat_contains(launcher, TAG_IDP, MARKER_IDP_REBUILD), (
        "DRAWER_BG_COLOR change triggered IDP rebuild — regression from T2.3."
    )


@pytest.mark.regression
@pytest.mark.drawer
def test_drawer_tab_color_pref_no_setup_header_storm(launcher):
    """Toggling DRAWER_TAB_SELECTED_COLOR must not cause a force rebind.

    The tab-color prefs are handled by the subscriber calling
    applyCustomTabColors() + invalidate() — not setupHeader() or rebindAdapters.

    Skipped when no work profile is present (tab color prefs are hidden).
    """
    launcher.go_home()
    launcher.d.shell("logcat -c")

    _open_colors_settings(launcher)

    # Tab-color prefs are only visible when a work profile exists.
    pref = launcher.d(text="Selected tab")
    if not pref.wait(timeout=3.0):
        launcher.d.press("back")
        launcher.go_home()
        pytest.skip("No work profile on this device — tab color prefs are hidden")

    pref.click()
    default_item = launcher.d(text="Default")
    assert default_item.wait(timeout=S.DEFAULT_WAIT), "Default row not found in color picker"
    default_item.click()
    time.sleep(0.5)

    launcher.d.press("back")
    launcher.go_home()
    time.sleep(0.5)

    assert not _logcat_contains(launcher, TAG_APPS, MARKER_FORCE_REBIND), (
        "DRAWER_TAB_SELECTED_COLOR change triggered force rebind. "
        "Tab-color path must use applyCustomTabColors(), not rebindAdapters. "
        "See docs/changes/073 + docs/plans/004 Phase 1."
    )


# --------------------------------------------------------------------------- #
# Phase 1: SearchFabController invariants
# --------------------------------------------------------------------------- #

@pytest.mark.regression
@pytest.mark.drawer
@pytest.mark.search
def test_search_fabs_appear_when_query_nonempty(launcher):
    """FAB container becomes visible when search has non-empty text.

    updateSearchFabs() is called on every query change. When query is
    non-empty, mFabContainer.setVisibility(VISIBLE) is called. After
    Phase 1 extraction, SearchFabController.onQueryChanged(query, true)
    must do the same.
    """
    launcher.go_home()
    launcher.open_drawer()
    launcher.type_search("c")

    # Wait up to DEFAULT_WAIT for the web-search FAB to appear
    web_fab = launcher.d(text=FAB_WEB_SEARCH_TEXT)
    appeared = web_fab.wait(timeout=S.DEFAULT_WAIT)

    launcher.clear_search()
    launcher.close_drawer()

    assert appeared, (
        f"'{FAB_WEB_SEARCH_TEXT}' FAB did not appear after typing 'c' in search. "
        "updateSearchFabs / SearchFabController.onQueryChanged may be broken."
    )


@pytest.mark.regression
@pytest.mark.drawer
@pytest.mark.search
def test_search_fabs_disappear_on_query_clear(launcher):
    """FAB container goes GONE when search query is cleared.

    After typing a query (FABs visible), clearing the search box should
    trigger updateSearchFabs(null/empty) which animates FABs out.
    """
    launcher.go_home()
    launcher.open_drawer()
    launcher.type_search("c")

    # Verify FABs appeared first
    web_fab = launcher.d(text=FAB_WEB_SEARCH_TEXT)
    appeared = web_fab.wait(timeout=S.DEFAULT_WAIT)
    assert appeared, f"'{FAB_WEB_SEARCH_TEXT}' FAB did not appear after typing"

    # Now clear the search
    launcher.clear_search()

    # FAB should disappear (animation + gone). Wait up to DEFAULT_WAIT.
    deadline = time.time() + S.DEFAULT_WAIT
    gone = False
    while time.time() < deadline:
        if not launcher.d(text=FAB_WEB_SEARCH_TEXT).exists:
            gone = True
            break
        time.sleep(0.2)

    launcher.close_drawer()

    assert gone, (
        f"'{FAB_WEB_SEARCH_TEXT}' FAB remained visible after clearing search. "
        "updateSearchFabs / SearchFabController.onQueryChanged(empty) may be "
        "broken — the container should animate out and end GONE."
    )


@pytest.mark.regression
@pytest.mark.drawer
@pytest.mark.search
def test_search_fab_web_intent_launches_browser(launcher):
    """Tapping the Web search FAB should launch a browser / web search.

    launchWebSearch() fires an ACTION_WEB_SEARCH or ACTION_VIEW intent.
    After Phase 1 extraction, SearchFabController must wire this click
    identically.
    """
    launcher.go_home()
    launcher.open_drawer()
    launcher.type_search("launcher test")

    web_fab = launcher.d(text=FAB_WEB_SEARCH_TEXT)
    assert web_fab.wait(timeout=S.DEFAULT_WAIT), \
        f"'{FAB_WEB_SEARCH_TEXT}' FAB not visible after typing"

    web_fab.click()
    # Wait briefly for the intent to fire and the app (or chooser) to appear.
    # On Android 17 app_current() may be stale, so also probe the UI hierarchy
    # for the intent-chooser dialog ("Complete action using" / "Open with")
    # which proves the FAB fired the intent even if no default browser is set.
    deadline = time.time() + S.DEFAULT_WAIT
    left_launcher = False
    current_pkg = S.PACKAGE
    while time.time() < deadline:
        current_pkg = launcher.d.app_current().get("package", S.PACKAGE)
        if current_pkg != S.PACKAGE:
            left_launcher = True
            break
        # Chooser dialog is a system overlay; app_current() returns the launcher
        # package because the chooser runs in-process on Android 17. Detect by
        # looking for the dialog header text that Android shows when multiple apps
        # can handle the intent.
        if (launcher.d(textContains="Complete action").exists
                or launcher.d(textContains="Open with").exists
                or launcher.d(textContains="Choose an app").exists):
            left_launcher = True
            break
        time.sleep(0.3)

    # Force return to launcher regardless of outcome (avoid leaving Chrome in foreground).
    # Multiple back presses + HOME to handle first-run dialogs that intercept back.
    for _ in range(3):
        if launcher.is_home():
            break
        launcher.d.press("back")
        time.sleep(0.5)
    if not launcher.is_home():
        launcher.d.press("home")
        time.sleep(S.ANIMATION_WAIT)
    launcher.go_home()

    assert left_launcher, (
        f"Tapping '{FAB_WEB_SEARCH_TEXT}' FAB did not leave the launcher "
        f"(still in '{current_pkg}'). launchWebSearch() may not be firing the "
        "ACTION_WEB_SEARCH intent. See docs/plans/004 Phase 1."
    )
