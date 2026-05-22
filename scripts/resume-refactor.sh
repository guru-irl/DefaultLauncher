#!/usr/bin/env bash
# Resume the DefaultLauncher architectural refactor in a fresh Claude session.
#
# Usage:
#   ./scripts/resume-refactor.sh
#
# This launches `claude` with a self-contained init prompt that:
#   1. Reads docs/plans/000-architectural-refactor-superplan.md (the handoff doc).
#   2. Runs the baseline (must pass 34+ tests) before touching code.
#   3. Resumes execution at the next outstanding task per the superplan.
#
# All durable context lives in the handoff doc, per-change docs under
# docs/changes/, and v2 plans under docs/plans/. Update the prompt below
# when the resume protocol changes (new tasks, new invariants, etc.).

set -euo pipefail

REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_DIR"

EXPECTED_BRANCH="refactor/t0.1-search-4param-override"
CURRENT_BRANCH="$(git rev-parse --abbrev-ref HEAD)"
if [ "$CURRENT_BRANCH" != "$EXPECTED_BRANCH" ]; then
  echo "[resume] Current branch is '$CURRENT_BRANCH'; expected '$EXPECTED_BRANCH'."
  echo "[resume] Checking out $EXPECTED_BRANCH..."
  git checkout "$EXPECTED_BRANCH"
fi

if ! command -v claude >/dev/null 2>&1; then
  echo "[resume] 'claude' CLI not found on PATH. Install Claude Code first." >&2
  exit 1
fi

ADB="$HOME/Android/Sdk/platform-tools/adb"
if [ -x "$ADB" ]; then
  if ! "$ADB" devices | grep -q "emulator-5554[[:space:]]\+device"; then
    echo "[resume] WARNING: emulator-5554 not in 'device' state."
    echo "[resume]          Start the Pixel 7 Pro AVD before continuing." >&2
  fi
else
  echo "[resume] WARNING: adb not at $ADB." >&2
fi

if [ ! -d "tests-e2e/.venv" ]; then
  echo "[resume] WARNING: tests-e2e/.venv missing. Bootstrap with:" >&2
  echo "         python -m venv tests-e2e/.venv && tests-e2e/.venv/bin/pip install -e tests-e2e" >&2
fi

exec claude "$(cat <<'PROMPT'
Resume the DefaultLauncher architectural refactor.

Branch `refactor/t0.1-search-4param-override` is checked out (working tree should be clean). Your handoff doc is `docs/plans/000-architectural-refactor-superplan.md` — read the "Execution log" (Sessions 1–6) and "How to resume in a new session" sections first. Everything you need is there: branch state, AVD config, exact commands, what shipped, where to pick up, and operational invariants.

After reading the handoff:

1. **Quickstart verification** — run the block from "How to resume" verbatim. NOTE: two devices may be attached (physical phone + emulator). Always pass `ANDROID_SERIAL=emulator-5554` when running tests. The test suite now has 34 tests (19 smoke + 15 regression + 3 visuals + 4 folder visual). All 34 must pass before touching code (except the 1 skip for no-work-profile devices).

   Quick start:
   ```bash
   cd /mnt/data/src/DefaultLauncher
   export ANDROID_HOME=$HOME/Android/Sdk
   export PATH=$ANDROID_HOME/platform-tools:$PATH
   adb devices  # expect: emulator-5554 device
   /opt/android-studio/jbr/bin/java -Xmx2g -Xms256m \
     -Dorg.gradle.appname=gradlew \
     -classpath gradle/wrapper/gradle-wrapper.jar \
     org.gradle.wrapper.GradleWrapperMain assembleDebug
   adb -s emulator-5554 install -r -d -g build/outputs/apk/debug/DefaultLauncher-debug.apk
   cd tests-e2e
   ANDROID_SERIAL=emulator-5554 .venv/bin/pytest smoke/ regression/ visuals/ -v --tb=short
   # expect: 33+ passed, 1 skipped in ~7-10 min
   ```

2. **Resume at T3.1 Phase 2** — execute `docs/plans/004-drawer-decomposition-v2.md` Phase 2: extract `DrawerInsetsController` from `ActivityAllAppsContainerView`. Phase 1 (DrawerColorController + SearchFabController) shipped in Session 5 (`docs/changes/075`). The container is now at 1931 LOC (was 2168). Continue through Phases 3–5 in order after Phase 2.

3. **After T3.1** — execute `docs/plans/005-deletion-safety-v2.md` (T3.2). Pre-flight decision (made in Session 5): Phase A uses e2e-only testing, no unit-test harness. `tests/` directory remains deleted per CLAUDE.md.

4. **Each change**: build → install → run full test suite (34 tests) → write `docs/changes/0NN-NAME.md` (next number: **078**) → commit. The `_wake_and_home` fixture always presses HOME before yielding — do not remove this (it ensures mSearchState=IDLE per docs/changes/076).

5. **Git identity**: `git -c user.name="Gurupungav Narayanan" -c user.email="gurupungavn@gmail.com" commit ...`. Never modify git config.

6. **Every commit ends with**: `Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>`.

7. **Work continuously**. Checkpoint to user after each completed plan, not each item.

AVD: `emulator-5554` (Pixel 7 Pro, Android 17, SDK 37). If not running, ask before starting.

Key docs to read before touching all-apps code:
- `docs/architecture/drawer-invariants.md` (14-invariant table; line refs drifted post-068/070, trust field/method names)
- `docs/plans/004-drawer-decomposition-v2.md` Phase 2 spec (DrawerInsetsController: owns mInsets, mNavBarScrimHeight, applyAdapterSideAndBottomPaddings, computeNavBarScrimHeight, drawNavBarScrim)
- `docs/changes/075-t31-phase1-drawer-color-fab-controllers.md` (what Phase 1 did — DrawerColorController and SearchFabController are already extracted)
- `docs/changes/076-search-state-home-reset.md` (mSearchState ACTIVE_EMPTY race fix — do not revert _wake_and_home HOME press)
- `docs/changes/077-t05-workspace-fixture-seed.md` (T0.5 seed — WorkspaceSeedReceiver writes Settings+(0,2)+Chrome+(1,2); drag helpers are deleted)

Operational invariants for test suite:
- ANDROID_SERIAL=emulator-5554 is required when physical phone is also connected
- `seed_workspace()` runs pm clear + am start + SEED_WORKSPACE broadcast; produces exactly Settings+(0,2)+Chrome+(1,2)
- `_wake_and_home` always presses HOME before yielding (ensures onNewIntent fires → mAppsView.reset() → mSearchState=IDLE)
- `go_home()` and `open_launcher_settings()` use workspace-visibility probes (not wait_activity) due to Android 17 app_current() stale data
- Folder visual tests in `regression/test_folder_visual.py` require a folder to be created via drag — they run with the seed fixture and re-seed between tests
PROMPT
)"
