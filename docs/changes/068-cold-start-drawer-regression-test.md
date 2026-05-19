# 068 — Cold-start drawer-empty: regression test + investigation log

## User report

Opening the app drawer immediately after a fresh-process bring-up
sometimes renders an empty A-Z grid. The reported workaround is to type a
search query, then press back — after that the apps appear.

## Investigation

Reproduced two distinct cold-start failure modes on the AVD:

1. **Aggressive race (warmup < 1s)**: `adb shell input swipe ...`
   dispatched before the launcher activity is fully responsive results in
   a stuck mid-animation state — `apps_view` never makes it onto the
   screen at all, hotseat shows but the drawer is half-pulled and frozen.
   This is a gesture-routing race outside the launcher process (SystemUI
   queues the swipe before the launcher's input window is ready). Not the
   user's bug.
2. **Empty-grid (the user's report)**: drawer opens fully, search bar is
   interactive, but the main RV is empty. AllAppsStore has the apps
   (search works). DiffUtil from `AlphabeticalAppsList.updateAdapterItems`
   dispatches `notifyItemRangeInserted(0, N)` to the bound adapter, but
   the RV doesn't pick up the new children. Search + back forces a
   visibility cycle on `apps_view` (GONE → VISIBLE in
   `updateSearchResultsVisibility`), which forces the RV to re-measure
   and re-layout — pulling in the queued adapter updates.

The empty-grid mode was hard to reproduce deterministically on the AVD;
the gesture-race mode reproduced consistently with sub-1s warmup but is
not what the user is hitting. I attempted two defensive fixes in
`ActivityAllAppsContainerView.onAppsUpdated`:

- `notifyDataSetChanged()` on the main RV when the adapter has items but
  the RV has zero attached children.
- `requestLayout()` + `invalidate()` follow-up.

Neither was reached in my reproduction path because in the gesture-race
mode the RV's width/height stay zero (apps_view never makes it on
screen), so the guard condition wasn't met. I removed both, leaving the
file in its pre-fix state — defensive code without a reproducible
benefit is just dead code.

## What ships

- New regression test
  `tests-e2e/regression/test_drawer_cold_start.py::test_drawer_shows_icons_on_cold_open`.
- Test uses `am force-stop` (not `pm clear`) so the on-disk database
  stays intact; only the in-memory `AllAppsStore` resets. This isolates
  the in-memory bind race we want to catch from the workspace-default-
  layout reload that `pm clear` triggers, which would poison subsequent
  smoke tests that read workspace state.
- Test gives a `COLD_START_WARMUP_S = 2.0` window before the swipe so
  the launcher is responsive — guards specifically against the
  empty-grid mode, not the gesture-routing race.

## What does NOT ship

- No source change in `ActivityAllAppsContainerView.java`. The empty-
  grid mode reproduces against the e2e baseline only intermittently and
  the defensive fixes I tried didn't fire on the deterministic
  reproductions I had. A confirmed fix needs:
  - A reliable reproduction inside the test harness (not yet built).
  - Identification of the precise RV/adapter state at the bad path
    (likely with a logcat-instrumented test).

## Next steps

If the user hits the empty-grid mode again:

1. Run `adb logcat -d | grep -E "AllAppsStore|AlphabeticalAppsList|onAppsUpdated"`
   immediately after the bad state appears.
2. Capture the main RV's adapter count, child count, width, and height
   at the moment the bug is visible — via a temporary
   `Log.d(TAG, ...)` in `onAppsUpdated`.
3. With those numbers the right `requestLayout`/`notifyDataSetChanged`
   site becomes obvious. Re-attempt the fix targeted at the actual
   bad state.

The test harness now catches the empty-grid mode as soon as it can be
reproduced. If a future change re-introduces it visibly, the test
fails with diagnostic UI hierarchy.

## Files

- `tests-e2e/regression/test_drawer_cold_start.py` (new, 76 lines).
- `docs/changes/068-cold-start-drawer-regression-test.md` (this file).
