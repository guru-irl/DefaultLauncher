# 070 — SearchState reset on home return + workspace scaffolding fixture

## Two-part fix

### Part 1: Workspace scaffolding fixture (conftest.py)

Test suite assumed a populated workspace but never set one up. The default
workspace XML
(`res/xml/default_workspace_5x5.xml`) declares items via AOSP
`category.APP_EMAIL/GALLERY/MARKET` intents that do **not** resolve on
the Pixel 7 Pro AOSP system image, so a freshly cleared launcher comes
up with an empty workspace. Tests like
`test_workspace_icon_bounds_nonzero` failed silently until something
external happened to place an icon.

New `_ensure_workspace_has_icon(drv)` helper:

- **Idempotent.** Early-returns when `apps_view_workspace` already has at
  least one accessibility-labelled descendant. Cost on the warm path is
  one `dump_hierarchy` query (~50ms).
- **Reproducible.** On the cold path, opens the drawer, picks the first
  available anchor from `("Chrome", "Phone", "Maps", "Messages",
  "Settings")`, uses `device.drag()` with `duration=1.0` (≥ long-press
  threshold so the launcher dispatches through the drop-target chain),
  and **verifies the icon actually landed on the workspace** before
  declaring success.
- **Retried.** Up to `_SCAFFOLD_MAX_ATTEMPTS=3` attempts. If all three
  fail the function raises `RuntimeError` rather than silently yielding
  a bad state — session aborts loudly.
- **Recoverable from app-launch fallout.** If the gesture is
  misclassified as a tap and launches the anchor app, the function
  presses HOME and continues.

Wired into:

- `launcher` session fixture (once at session start).
- `_wake_and_home` autouse fixture (every test) — fast probe, full
  scaffold only if workspace is empty.
- `clean_launcher` per-test fixture (after `pm clear`) — re-scaffolds
  the wiped state.

### Part 2: SearchState reset in `reset(animate, exitSearch=true)`

Discovered while running the suite twice in a row: smoke tests passed
the first run but `test_drawer_has_app_icons` and
`test_search_text_renders_results` failed on subsequent runs. The
drawer was opening with an **empty grid** because the dump showed
`search_results_list_view` visible instead of `apps_list_view`.

Root cause: `ActivityAllAppsContainerView.mSearchState` is process-
scoped (lives on the View hierarchy). When a test exits search via
`clear_search()` (backspace to empty), the state machine transitions to
`ACTIVE_EMPTY`. When the drawer subsequently closes via Launcher state
transition (NORMAL ← ALL_APPS), the SearchState **stays in
ACTIVE_EMPTY** — there was no hook that returned it to `IDLE`.

On the next drawer open, `mSearchState == ACTIVE_EMPTY`, so
`isSearching()` returns `true`,
`updateSearchResultsVisibility()` shows `search_results_list_view` (an
empty list) and hides `apps_list_view`. The user sees a blank drawer
with only the search bar — **exactly the bug the user originally
reported**: "drawer is empty on first open, search-and-back fixes it".

The "search-and-back" workaround works because typing a query
transitions to SEARCHING and back press routes through
`animateToSearchState(false)`, which lands in `EXITING → IDLE` via the
deferred runnable.

Fix in `reset(animate, exitSearch)` (called via
`Launcher.onNewIntent` → `mAppsView.reset(...)` when returning home):

```java
if (exitSearch) {
    // ... existing resetSearch post() ...
    if (mSearchTransitionController.isRunning()) {
        // Mid-animation: let the existing onEnd runnable land the
        // state naturally; don't fight it.
    } else {
        mSearchState = SearchState.IDLE;
        mKeepKeyboardOnSearchExit = false;
        if (mPendingSearchExitWork != null) {
            removeCallbacks(mPendingSearchExitWork);
            mPendingSearchExitWork = null;
        }
    }
}
```

The mid-animation guard preserves the established
`animateToSearchState` invariant — that the onEnd runnable owns the
final state transition. Without the guard, a `reset(true)` during a
mid-flight exit animation would beat the deferred runnable and
desynchronize the state machine.

`mPendingSearchExitWork` is canceled along with the state — it would
otherwise re-trigger ACTIVE_EMPTY on the next post.

## Why this fix is correct

The drawer-invariants doc (`docs/architecture/drawer-invariants.md`)
says explicitly that `mKeepKeyboardOnSearchExit` and
`mPendingSearchExitWork` have asymmetric set/clear lifecycles. The
clear must happen when the user-perceived "search context" is
abandoned. Returning to the workspace (home press, state transition,
`onActivePageChanged(NORMAL)`) is exactly that point — but the existing
`reset()` only cleared the search **bar** (via `resetSearch()`), not
the **state machine** introduced in T2.2 Phase 2.

This is a follow-up to docs/changes/064 (SearchState five-state
machine).

## Verification

- `assembleDebug` clean.
- `tests-e2e/smoke/ + regression/ + visuals/`: 25/25 across two
  consecutive runs (~5 min each). Previously failed on second run.
- Full session reset (`pm clear`): scaffolding rebuilds workspace
  state, 25/25 from cold device.
- Manual: opened drawer, typed `chr`, backspaced to empty, pressed
  back (twice — once for keyboard, once for drawer). Re-opened drawer
  — apps grid visible, no empty search bar.

## Files

- `tests-e2e/conftest.py` — `_ensure_workspace_has_icon` helper
  + supporting `_pick_anchor` / `_scaffold_drag_anchor_to_workspace`
  helpers + `_workspace_icon_count` probe. Wired into `launcher`,
  `_wake_and_home`, `clean_launcher` fixtures.
- `src/com/android/launcher3/allapps/ActivityAllAppsContainerView.java`
  — +13 lines in `reset(animate, exitSearch)`: reset `mSearchState` +
  `mKeepKeyboardOnSearchExit` + cancel `mPendingSearchExitWork` when
  `exitSearch` is true and no transition is mid-flight.
- `docs/changes/070-search-state-reset-on-home-return.md` (this file).
