#!/usr/bin/env bash
# Resume the DefaultLauncher architectural refactor in a fresh Claude session.
#
# Usage:
#   ./scripts/resume-refactor.sh
#
# This launches `claude` with a self-contained init prompt that:
#   1. Reads docs/plans/000-architectural-refactor-superplan.md (the handoff doc).
#   2. Runs the baseline (must pass 61 tests, 49 PASSED) before touching code.
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
  if "$ADB" devices | grep -q "RFCX712ZQDT[[:space:]]\+device"; then
    echo "[resume] Samsung phone RFCX712ZQDT is connected."
    echo "[resume] Required for verifying bug 085 widget picker → stack fix."
  else
    echo "[resume] NOTE: Samsung RFCX712ZQDT not connected."
    echo "[resume]       Connect for widget picker bug verification (see Session 10 in superplan)." >&2
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

Branch `refactor/t0.1-search-4param-override` is checked out (working tree should be clean). Your handoff doc is `docs/plans/000-architectural-refactor-superplan.md` — read the "Execution log" (Sessions 1-9) and "How to resume in a new session" sections first. Everything you need is there: branch state, AVD config, exact commands, what shipped, where to pick up, and operational invariants.

After reading the handoff:

1. **Verify the unverified fix shipped this session** — bug 085 (widget picker → stack) was diagnosed and a fix applied in `Workspace.acceptDrop()`, but the fix has NOT been verified yet on the Samsung phone (the only device that exercises this path; the emulator does not have a widget picker). Connect Samsung RFCX712ZQDT, install the current build, and reproduce the drag-from-picker-to-stack flow. Expected: widget joins the stack with no toast. If the bug persists, dump fresh logs:
   ```bash
   adb -s RFCX712ZQDT install -r -d -g build/outputs/apk/debug/DefaultLauncher-debug.apk
   # reproduce the drag manually
   adb -s RFCX712ZQDT logcat -d | grep -E "Workspace.*onDrop|willAdd|acceptDrop|DRAG_MODE_ADD|onNoCellFound"
   ```
   See `docs/changes/085-widget-picker-stack-accept-drop.md` for the root cause and patch.

2. **Quickstart verification** — run the block from "How to resume" verbatim. NOTE: two devices may be attached (physical phone + emulator). Always pass `ANDROID_SERIAL=emulator-5554` when running tests. The test suite now has 61 tests. Expected: 49 passed, 0 failed, 10 skipped, 2 xfailed.

   Quick start:
   ```bash
   cd /mnt/data/src/DefaultLauncher
   export ANDROID_HOME=$HOME/Android/Sdk
   export PATH=$ANDROID_HOME/platform-tools:$PATH
   adb devices  # expect: emulator-5554 device (+ optional physical phone)
   /opt/android-studio/jbr/bin/java -Xmx2g -Xms256m \
     -Dorg.gradle.appname=gradlew \
     -classpath gradle/wrapper/gradle-wrapper.jar \
     org.gradle.wrapper.GradleWrapperMain assembleDebug
   adb -s emulator-5554 install -r -d -g build/outputs/apk/debug/DefaultLauncher-debug.apk
   cd tests-e2e
   ANDROID_SERIAL=emulator-5554 .venv/bin/pytest smoke/ regression/ visuals/ -v --tb=short
   # expect: 49 passed, 0 failed, 10 skipped, 2 xfailed in ~25-40 min
   ```

3. **All T3 tiers complete.** No more refactor tiers in progress. Remaining work is bug fixes and the deferred T2.3 Phase 4 (RotationHelper / SysUiScrim / ThemeManager / DisplayController migrations — explicitly "deferred, optional").

4. **Status of recent fixes**:
   - **Bug 084** (empty drawer after launching from search) ✅ shipped — `docs/changes/084`. Three-site fix in `SearchLifecycle.onContainerReset`, `Launcher.onResume`, and the deferred exit runnable.
   - **Bug 085** (widget picker → stack drop rejected) ✅ patched but UNVERIFIED on hardware — `docs/changes/085`. Fix is in `Workspace.acceptDrop()`. **MUST verify with Samsung phone before claiming done.**

5. **Each change**: build → install → run full test suite (61 tests) → write `docs/changes/0NN-NAME.md` (next number: **086**) → commit. The `_wake_and_home` fixture always presses HOME before yielding — do not remove this.

6. **Git identity**: `git -c user.name="Gurupungav Narayanan" -c user.email="gurupungavn@gmail.com" commit ...`. Never modify git config.

7. **Every commit ends with**: `Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>`.

8. **Push regularly**: `git push origin refactor/t0.1-search-4param-override` after each commit or at least each task.

9. **Work continuously**. Checkpoint to user after each completed plan, not each item.

AVD: `emulator-5554` (Pixel 7 Pro, Android 17, SDK 37). If not running, ask before starting.
Samsung phone: `RFCX712ZQDT` (Galaxy, larger grid, has widget picker support). Required for bug 085 verification.

Key docs to read:
- `docs/architecture/drawer-invariants.md` (14-invariant table)
- `docs/changes/081` through `docs/changes/085` (the T3.1 phases 3-5 + bugs 084, 085)
- `docs/changes/079` (mSearchState ACTIVE_EMPTY race fix — do not revert _wake_and_home HOME press)
- `docs/changes/080` (widget deletion prevention; UnavailableWidgetView placeholder)
- `docs/changes/084` (empty-drawer alpha fix — touches SearchLifecycle.onContainerReset, Launcher.onResume, deferred exit runnable)
- `docs/changes/085` (widget picker → stack acceptDrop fix — UNVERIFIED, needs Samsung)

Operational invariants for test suite:
- ANDROID_SERIAL=emulator-5554 is required when physical phone is also connected
- `seed_workspace()` runs pm clear + am start + SEED_WORKSPACE broadcast; produces exactly Settings+(0,2)+Chrome+(1,2)
- `_wake_and_home` always presses HOME before yielding (ensures onNewIntent fires → mAppsView.reset() → mSearchState=IDLE → alpha reset)
- `go_home()` and `open_launcher_settings()` use workspace-visibility probes (not wait_activity) due to Android 17 app_current() stale data
- Folder visual tests require drag-to-folder; xfail(strict=False) on loaded emulators
- deletion_safety tests skip on AVD (no widget); they pass on the physical phone with widget stacks
- profile_coordinator tests skip on AVD (no work profile / private space)
- The emulator degrades over long test runs (35+ min full suite); reset via pm clear + set-home-activity + SEED_WORKSPACE if tests start failing inexplicably (look for "drawer did not open after swipe-up" with launcher visible in hierarchy)

Container LOC progress (T3.1 done):
- Pre-T3.1: 2168 LOC
- After Phase 1 (075): 1931
- After Phase 2 (078): 1914
- After Phase 3 (081): 1877
- After Phase 4 (082): ~1820
- After Phase 5 (083): **1607** LOC

Five collaborator classes own former container concerns:
- `DrawerColorController`, `SearchFabController` (Phase 1)
- `DrawerInsetsController` (Phase 2)
- `ProfileCoordinator` (Phase 3): mWorkManager, mPrivateProfileManager, mHasWork/PrivateApps, mPersonalMatcher, onAppsUpdated branches, resetAndScrollToPrivateSpaceHeader, inflateWorkCardsIfNeeded
- `SearchLifecycle` (Phase 4): SearchState enum, mSearchState, mSuppressSetupHeader, mKeepKeyboardOnSearchExit, mPendingSearchExitWork, mRebindAdaptersAfterSearchAnimation, mSearchTransitionController, animateToSearch()
- `HeaderCoordinator` (Phase 5): mUsingTabs, mDrawerHideTabs, setupHeader(), updateRVContainerRules(), replaceAppsRVContainer(), updateHeaderScroll(), scroll listener
PROMPT
)"
