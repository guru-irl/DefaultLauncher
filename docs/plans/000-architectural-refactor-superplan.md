# Architectural Refactor — Superplan

This document is the canonical reference for the multi-tier architectural refactor following the three-pass audit of workspace, grid, drawer, search, and model-loading subsystems.

It also defines a permanent e2e testing suite as a first-class deliverable.

## Scope

- **Audit findings** are summarized in `docs/architecture/` (one file per subsystem invariants table).
- **Implementation tiers** (T0 hotfixes through T3 architectural changes) are tracked in the task system and reflected here as section headings.
- **Testing suite** (`tests-e2e/`) is built in Phase 0 and grown with each tier.

## Non-goals

- No Quickstep/Go reintegration.
- No new product features. This is hardening + groundwork.
- No upstream AOSP rebase coordination beyond minimizing divergence per CLAUDE.md.

## Phase 0 — Test Harness (DELIVERABLE)

Phase 0 establishes a permanent e2e testing suite that survives the refactor. Smoke runs guard every later phase; the suite itself is the artifact users gain.

### Stack

| Layer | Choice | Why |
|-------|--------|-----|
| Driver | `uiautomator2` (Python) | Wraps Google's UiAutomator2; no separate server (unlike Appium); selector API resembles Playwright |
| Test runner | `pytest` | Fixtures, parametrization, JUnit XML, plugin ecosystem |
| Assertion helpers | Custom `LauncherDriver` class | Hides selector churn behind named operations (`open_drawer`, `type_search`, `drag_to_hotseat`) |
| AVD lifecycle | Headless emulator | Spawned via `emulator -no-window -no-audio -no-snapshot-save` |
| CI hook | `tests-e2e/run.sh` | Single entry point; supports `--smoke`, `--full`, `--feature workspace` |

### Directory layout

```
tests-e2e/
├── README.md                       # how to run + add tests
├── pyproject.toml                  # uv/pip deps
├── conftest.py                     # session/device fixtures
├── pytest.ini                      # markers, timeouts
├── run.sh                          # entry point
├── lib/
│   ├── __init__.py
│   ├── launcher.py                 # LauncherDriver facade
│   ├── selectors.py                # central selector strings
│   ├── adb_setup.py                # ensure device + reinstall APK
│   ├── perf.py                     # frame timing helpers
│   └── screencap.py                # diff helpers for visual snapshots
├── smoke/                          # <5 min total, run on every PR
│   ├── test_workspace_basics.py
│   ├── test_drawer_basics.py
│   ├── test_search_basics.py
│   ├── test_settings_basics.py
│   └── test_grid_basics.py
├── regression/                     # longer; nightly or per-tier
│   ├── test_workspace_state.py     # drag races, folder open during rebuild
│   ├── test_drawer_state.py        # search-anim suppression, private space
│   ├── test_search_progressive.py  # INTERMEDIATE/FINAL contract
│   ├── test_prefs_cascade.py       # cascade matrix per pref
│   └── test_model_load.py          # deletion safety scenarios
├── stress/                         # off by default; long-running
│   ├── test_rapid_typing.py
│   ├── test_grid_thrash.py         # column-change cycles
│   └── test_widget_restore.py      # AppWidgetManager flaps
└── visuals/                        # golden image diffs
    └── baseline/                   # PNG references (small, gitignored binaries optional)
```

### Coverage commitments by tier

| Tier | New smokes | New regressions | New stress |
|------|-----------|----------------|-----------|
| Phase 0 | ~20 (golden paths across all features) | 0 | 0 |
| T0 | +2 (4-param search code, circuit breaker behavior) | +2 | 0 |
| T1 | +3 (DiffUtil decoration, view-type stability) | +4 | 0 |
| T2 | +6 (per redrafted plan) | +10 | +2 |
| T3 | +10 (drawer phases, deletion paths) | +15 | +3 |
| **Total** | **~41** | **~31** | **~5** |

### Test design principles

1. **Tests describe user intent, not UI structure.** `driver.open_drawer()` not `d(resourceId="launcher:id/apps_button").click()`. Selector changes go in one file.
2. **No hardcoded sleeps.** Use uiautomator2's wait-for-element semantics. If a test needs an explicit wait, name a constant in `lib/selectors.py`.
3. **Fixtures own setup teardown.** A test never reaches into ADB directly; it uses fixtures that record state and restore.
4. **Each test is independent.** No ordering assumptions. Each starts from a known-good state (cleared workspace, settings reset to defaults).
5. **Smokes assert one thing.** Regressions can assert multi-step flows. Stress tests assert no crashes / no leaks.
6. **Failures dump artifacts.** Screenshots + logcat tail + UI hierarchy XML on any failure.
7. **Screenshots are diagnostic, not assertions** — except in `visuals/`, where golden images are explicit.

### Add-a-test ergonomics

The `README.md` at `tests-e2e/README.md` documents:
- One-liner to install deps
- How to run locally (full / smoke / by feature)
- The LauncherDriver API surface
- How to add a new smoke (5-line template)
- How visual baselines are managed

## Execution model

For every plan (T0 through T3):

1. **Create a feature branch** off `dev`: `git checkout -b refactor/<tier>-<short-name>`.
2. **Write the change doc first** in `docs/changes/NNN-<name>.md` — sets the intent before code.
3. **Add tests first** when reasonable (TDD where applicable, e.g., the search 4-param fix has an obvious regression test).
4. **Implement** per the plan.
5. **Build** `assembleDebug` — must pass.
6. **Install** `adb install -r ...apk`.
7. **Run smoke suite** — must pass.
8. **Manual exploratory pass** — 5-10 minutes against the AVD for the feature touched. Notes captured in the PR.
9. **Commit + open PR** for review against `dev`.
10. **Merge** only after smoke + manual pass green.

Between tiers: full regression suite + perf comparison (cold start time, scroll smoothness, search latency).

## Failure handling

- **Build failure** → fix or revert. Never `--no-verify`.
- **Smoke failure** → identify root cause. If the failure is in code touched by the plan, fix. If in unrelated code, that's a pre-existing bug — file separately, don't sneak the fix into this PR.
- **Manual pass surfaces issue** → either fix in-PR (small) or file as follow-up (large) and decide whether to ship the partial work.
- **Performance regression** → block the PR. Performance is a feature.

## Tier reference

### Tier 0 — Hotfixes (single-PR each, no plan v2 needed)
- T0.1 Search 4-param onSearchResult override
- T0.2 Orphaned pref triage (split per pref family as separate small PRs)
- T0.3 Circuit breaker floor/ratio tuning
- T0.4 Drawer invariants documentation

### Tier 1 — Verified-safe small plans
- T1.1 DiffUtil decoration fix (`AdapterItem.isContentSame` + `SectionDecorationInfo.equals`)
- T1.2 Grid init `mWorkspacePaddingReady` debug log + `GRID_ROWS_NAV_MODE` invalidation key
- T1.3 AllAppsStore `volatile mApps` + threading contract
- T1.4 View-type ID renumbering + duplicate static-block guard

### Tier 2 — Plans needing v2 drafts
- T2.0 Three plan agents redraft workspace / search / prefs frameworks with audit deltas
- T2.1 Execute workspace reliability v2
- T2.2 Execute search reliability v2 (depends on T0.1)
- T2.3 Execute prefs framework v2 (5-tier taxonomy)

### Tier 3 — Architecture plans (depend on Tier 2)
- T3.0 Two plan agents redraft drawer decomposition / deletion safety
- T3.1 Execute drawer decomposition (5 phases, smoke gate per phase)
- T3.2 Execute deletion safety v2

## Status tracking

The TaskCreate/TaskList system tracks execution. See task IDs 13–34. Dependencies enforced via `blockedBy`.

Each change generates a numbered file in `docs/changes/048-…` onwards. Each merged PR moves the corresponding task to `completed`.

## Living document

This file evolves as the work progresses. After each tier completes, add a "Lessons learned" subsection capturing what the audits missed and what we learned during implementation. Future architecture work uses this as a reference.
