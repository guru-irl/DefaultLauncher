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

## Execution log

### Session 1 (handoff point)

Branch: `refactor/t0.1-search-4param-override` — 9 commits ahead of `dev`.

| ID | Task | Status | Doc |
|----|------|--------|-----|
| P0 | Test harness + 19 smoke + 2 regression tests | ✅ done | — |
| T0.1 | Search 4-param onSearchResult override | ✅ done | `docs/changes/048` |
| T0.2 | Orphan-pref triage (verified no real orphans) | ✅ done | `docs/changes/049` |
| T0.3 | Mass-delete circuit breaker floor 3→5, ratio 25%→20% | ✅ done | `docs/changes/050` |
| T0.4 | Drawer invariants reference doc | ✅ done | `docs/architecture/drawer-invariants.md` |
| T1.1 | DiffUtil decoration equality | ✅ done | `docs/changes/051` |
| T1.2 | Grid init guard + nav-mode invalidation key | ✅ done | `docs/changes/052` |
| T1.3 | AllAppsStore volatile + snapshot | ✅ done | `docs/changes/053` |
| T1.4 | View-type duplicate guard + misuse fix | ✅ done | `docs/changes/054` |
| T2.0a | Workspace reliability v2 plan | ✅ drafted | `docs/plans/001-…` |
| T2.0b | Search reliability v2 plan | ✅ drafted | `docs/plans/002-…` |
| T2.0c | Prefs framework v2 plan | ✅ drafted | `docs/plans/003-…` |
| T2.1 Item 1 | FLAG_EXPANDED cleared at SquareGridReflow clamp | ✅ done | `docs/changes/055` |
| T2.1 Item 6 | strip-empty-screens drag guard | ✅ done | `docs/changes/056` |

### Session 2

| ID | Task | Status | Doc |
|----|------|--------|-----|
| T2.1 Item 3 | Folder span persist call-site consolidation | ✅ done | `docs/changes/057` |
| T2.1 Item 5 | Close folder on DP change | ✅ done | `docs/changes/058` |
| T2.1 Item 7 | Workspace acceptDrop async drop-layout snapshot | ✅ done | `docs/changes/059` |
| T2.1 Item 4 | Folder.replaceFolderWithFinalItem re-entry guard | ✅ done | `docs/changes/060` |
| T2.1 Item 8 | CellLayout.resetCellSize occupancy doc note | ✅ done | `docs/changes/061` |
| T2.2 Phase 1a | ProviderCategory enum migration | ✅ done | `docs/changes/062` |
| T2.2 Phase 1b | SearchSession + provider snapshotting | ✅ done | `docs/changes/063` |
| T2.2 Phase 2 | SearchState five-state machine | ✅ done | `docs/changes/064` |
| T2.2 Phase 3 | convertResults moved to adapter provider | ✅ done | `docs/changes/065` |
| T2.2 Phase 4 | DefaultAppSearchAlgorithm + DefaultSearchAdapterProvider deletion | ✅ done | `docs/changes/066` |
| T2.3 Phase 1 | Prefs framework foundation (dormant) | ✅ done | `docs/changes/067` |
| Cold-start drawer test | force-stop regression test + investigation | ✅ done | `docs/changes/068` |
| T2.3 Phase 2 prep | Visuals baseline framework | ✅ done | `docs/changes/069` |

**T2.1 + T2.2 complete. T2.3 Phase 1 + visuals-baseline prerequisites shipped.**

T2.3 Phases 2 and 3 (drawer-color migration + IDP.onConfigChanged downgrade) can now ship safely: the visuals tests at `tests-e2e/visuals/` will catch paint regressions, and `LauncherPrefs.get(context).prefChanges.subscribe(...)` is the framework consumers wire into.

### How to resume in a new session

**Quick start (5 min):**

```bash
cd /mnt/data/src/DefaultLauncher
git checkout refactor/t0.1-search-4param-override
# AVD should already be running; verify:
export ANDROID_HOME=$HOME/Android/Sdk
export PATH=$ANDROID_HOME/platform-tools:$PATH
adb devices                                  # expect: emulator-5554 device

# Rebuild + reinstall the in-tree state
/opt/android-studio/jbr/bin/java -Xmx2g -Xms256m \
  -Dorg.gradle.appname=gradlew \
  -classpath gradle/wrapper/gradle-wrapper.jar \
  org.gradle.wrapper.GradleWrapperMain assembleDebug
adb install -r -d -g build/outputs/apk/debug/DefaultLauncher-debug.apk

# Confirm baseline still green
cd tests-e2e
.venv/bin/pytest smoke/ regression/ -v --tb=short   # expect 21/21 in ~60s
```

**Pick up at:** **T2.3 Phase 2** (per `docs/plans/003-unified-prefs-framework-v2.md`). T2.1, T2.2, T2.3 Phase 1, the visuals baseline framework, and the cold-start drawer regression test are all shipped as of `docs/changes/057-069`. The prefs dispatcher is live at `LauncherPrefs.get(context).prefChanges`; the visuals tests at `tests-e2e/visuals/` are ready to gate paint-affecting changes.

**Remaining work (ordered):**

1. **T2.3 Phase 2.** Migrate audit-flagged on-demand readers in `ActivityAllAppsContainerView` (drawer colors + DRAWER_HIDE_TABS), `AppsSearchContainerLayout` (search bg color + opacity), `AllAppsState` (drawer bg), `RecyclerViewFastScroller` (scrollbar color) to subscribe-and-cache. Add a `tests-e2e/visuals/` baseline at `docs/architecture/drawer-invariants.md`-defined checkpoints before changing color paths; the smoke suite covers structure but not paint.
2. **T2.3 Phase 3.** Per-pref impact downgrades. Tag each drawer color / folder color pref with `SettingImpact.VIEW_INVALIDATE`; remove the `InvariantDeviceProfile.onConfigChanged` calls from `AppDrawerColorsFragment.java:72/81/90/121/135` (5 sites). The big perf win lands here.
3. **T2.3 Phase 4** — deferred (RotationHelper / SysUiScrim / ThemeManager / DisplayController migrations).
4. **T3.0a + T3.0b** redrafts. Dispatch Plan agents for drawer decomposition v2 (incorporate the 14-invariant table from `docs/architecture/drawer-invariants.md`) and deletion safety v2 (drop `isLauncherAppsHealthy`, use double-IPC verification, keep WidgetInflater guards). Inputs: the secondary + tertiary audit findings preserved in this superplan.
5. **T3.1** drawer decomposition (5 phases, smoke gate per phase).
6. **T3.2** deletion safety v2.

**Execution invariants** for any session:

- Every plan execution **must** pass `tests-e2e/smoke/` and `tests-e2e/regression/` before commit (21+ tests, ~60s).
- Every change **must** carry a `docs/changes/0NN-…md` entry (next number: **070**).
- AOSP-origin file edits (BaseAllAppsAdapter, FloatingHeaderView, LoaderCursor, WorkspaceLayoutManager, DeviceProfile, InvariantDeviceProfile, Workspace, Folder, AllAppsStore) require explicit justification per change doc.
- `docs/architecture/drawer-invariants.md` is required reading before any all-apps refactor.
- All commits attribute Co-Authored-By: Claude Opus 4.7 and use `git -c user.name="Guna Raya" -c user.email="gunaraya@microsoft.com" commit ...` (CLAUDE.md forbids permanent git config changes).

**Known good baselines:**
- AVD: `emulator-5554`, Pixel 7 Pro (sdk_gphone16k_x86_64), Android 17 (SDK 37), 1440×3120 @ 560dpi.
- DefaultLauncher set as default home activity.
- 21/21 tests green at branch HEAD.
- Full smoke suite runtime: ~60s. Full + regression: ~60s.

**Risk flags carried forward:**
- T2.1 Item 4 resolved Session 2: delegate is non-idempotent but Folder's `mDestroyed` flag is set synchronously before any re-entrant caller, so the simple `if (mDestroyed) return;` guard is sufficient — no token machinery needed. See `docs/changes/060`.
- T2.3 Phase 3 drawer-color migrations are the highest-perf-win but most touchy — each pref needs its own commit + smoke + manual visual check on the AVD.
- T3.1 Phase 2 (HeaderCoordinator + FloatingHeaderView state machine) is the riskiest single phase — preserve `mSuppressSetupHeader` + `mPendingSearchExitWork` + `mKeepKeyboardOnSearchExit` invariants explicitly.
