"""Cold-start drawer rendering: after process restart, the drawer must
show icons on the first open.

Why this test exists: a user-reported bug shows the drawer painting empty
on the first open after fresh-process bring-up, with the recovery being to
type a query then press back. Verifying that the just-bound state renders
icons end-to-end guards against regressions where the AllAppsStore.setApps
notification doesn't reach the main RecyclerView in a re-layout-triggering
form.

We use `am force-stop` (not `pm clear`) so the launcher database stays
intact — the bug we're testing for lives in the in-memory bind path
between AllAppsStore and the RV, not in the on-disk default-layout loader.
This also keeps the test side-effect-free for subsequent smoke tests that
depend on workspace state.
"""

import time

import pytest

from lib import selectors as S


# Same floor as smoke/test_drawer_basics — resilient to AVD image variation,
# well above the "empty grid" failure mode.
MIN_ICONS = 8

# Warmup before swipe-up. Below ~1s on this AVD, the drawer transition gets
# stuck mid-animation due to a gesture-routing race that's outside the
# launcher process — that's a separate issue, not what this test guards.
COLD_START_WARMUP_S = 2.0


@pytest.mark.regression
@pytest.mark.drawer
def test_drawer_shows_icons_on_cold_open(launcher):
    """Drawer must paint with icons on first open after force-stop + warmup."""
    # In-memory reset: force-stop kills the launcher process so AllAppsStore
    # starts empty on next launch. Database stays intact so workspace
    # contents survive — keeps the test from poisoning subsequent smoke
    # tests that read workspace state.
    launcher.d.shell(f"am force-stop {S.PACKAGE}")
    launcher.d.press("home")
    time.sleep(COLD_START_WARMUP_S)

    info = launcher.d.info
    w, h = info["displayWidth"], info["displayHeight"]
    launcher.d.swipe(w // 2, int(h * 0.9), w // 2, int(h * 0.3), duration=0.20)

    appeared = launcher.d(resourceId=S.ID_ALL_APPS_CONTAINER).wait(
        timeout=S.DEFAULT_WAIT
    )
    assert appeared, "drawer container never appeared after cold swipe-up"

    deadline = time.time() + S.DEFAULT_WAIT
    icons_count = 0
    while time.time() < deadline:
        icons = launcher.d(resourceId=S.ID_ALL_APPS_RECYCLER).child(
            descriptionMatches=r".+"
        )
        icons_count = icons.count
        if icons_count >= MIN_ICONS:
            break
        time.sleep(0.15)

    if icons_count < MIN_ICONS:
        hierarchy = launcher.d.dump_hierarchy()
        pytest.fail(
            f"Drawer showed {icons_count} icons on cold open (expected >= "
            f"{MIN_ICONS}). Cold-start race in apps RV binding — see "
            f"test docstring.\nhierarchy (first 1500 chars):\n"
            f"{hierarchy[:1500]}"
        )

    launcher.close_drawer()
