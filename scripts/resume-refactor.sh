#!/usr/bin/env bash
# Resume the DefaultLauncher architectural refactor in a fresh Claude session.
#
# Usage:
#   ./scripts/resume-refactor.sh
#
# This launches `claude` with a self-contained init prompt that:
#   1. Reads docs/plans/000-architectural-refactor-superplan.md (the handoff doc).
#   2. Runs the smoke + regression + visuals baseline (must pass 25/25).
#   3. Resumes execution at the next outstanding task per the superplan.
#
# All durable context lives in the handoff doc, the per-change docs under
# docs/changes/, and the v2 plans under docs/plans/. This script is a thin
# launcher — update the prompt only if the resume protocol itself changes.

set -euo pipefail

REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_DIR"

# Sanity: make sure we're on (or can get back to) the refactor branch.
EXPECTED_BRANCH="refactor/t0.1-search-4param-override"
CURRENT_BRANCH="$(git rev-parse --abbrev-ref HEAD)"
if [ "$CURRENT_BRANCH" != "$EXPECTED_BRANCH" ]; then
  echo "[resume] Current branch is '$CURRENT_BRANCH'; expected '$EXPECTED_BRANCH'."
  echo "[resume] Checking out $EXPECTED_BRANCH..."
  git checkout "$EXPECTED_BRANCH"
fi

# Cheap pre-flight checks — fail fast if the environment isn't ready, so the
# Claude session doesn't burn a turn discovering missing prerequisites.
if ! command -v claude >/dev/null 2>&1; then
  echo "[resume] 'claude' CLI not found on PATH. Install Claude Code first." >&2
  exit 1
fi

ADB="$HOME/Android/Sdk/platform-tools/adb"
if [ -x "$ADB" ]; then
  if ! "$ADB" devices | grep -q "emulator-5554[[:space:]]\+device"; then
    echo "[resume] WARNING: emulator-5554 not in 'device' state."
    echo "[resume]          Start the Pixel 7 Pro AVD before continuing, or the"
    echo "[resume]          session's first 'pytest' call will fail." >&2
  fi
else
  echo "[resume] WARNING: adb not at $ADB. Adjust PATH for the session." >&2
fi

if [ ! -d "tests-e2e/.venv" ]; then
  echo "[resume] WARNING: tests-e2e/.venv missing. Bootstrap with:" >&2
  echo "         python -m venv tests-e2e/.venv && tests-e2e/.venv/bin/pip install -e tests-e2e" >&2
fi

exec claude "$(cat <<'PROMPT'
Resume the DefaultLauncher architectural refactor.

Branch `refactor/t0.1-search-4param-override` is checked out (29+ commits ahead of dev, working tree should be clean). Your handoff doc is `docs/plans/000-architectural-refactor-superplan.md` — read the "Execution log" (Sessions 1, 2, 3) and "How to resume in a new session" sections at the bottom first. Everything you need is there: branch state, AVD config, exact commands, what shipped, where to pick up, and the operational invariants.

After reading the handoff:
1. Run the quickstart verification block from the doc verbatim. Confirm 25/25 tests pass (smoke + regression + visuals) before touching code.
2. Resume at the next outstanding task per the superplan's "Remaining work" section — currently **T2.3 Phase 2** (drawer-color migration to subscribe-and-cache per `docs/plans/003-unified-prefs-framework-v2.md`). Then continue through the remaining work in the listed order.
3. Each change: build → install → run `tests-e2e/smoke + regression + visuals` → write `docs/changes/0NN-NAME.md` (next number is **071**, or whatever follows the highest already on disk) → commit.
4. Use the per-command git identity: `git -c user.name="Guna Raya" -c user.email="gunaraya@microsoft.com" commit ...`. Never modify git config.
5. Every commit ends with: `Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>`.
6. Work continuously. Checkpoint to the user after each completed plan (T2.3, then T3.x), not each item.

AVD: `emulator-5554` (Pixel 7 Pro, Android 17). If it's not running, ask before starting it.

Required reading before touching all-apps code:
- `docs/architecture/drawer-invariants.md` (14-invariant table).
- `docs/changes/064-search-state-machine.md` (SearchState five-state machine).
- `docs/changes/070-search-state-reset-on-home-return.md` (most recent bug fix — shows why state-reset hooks matter, and documents the workspace scaffolding fixture every test depends on).

For T2.3 Phase 2 specifically:
- Consumers wire into the framework via `LauncherPrefs.get(context).prefChanges.subscribe(subscriber, item1, item2, ...)` — see `docs/changes/067` for the dispatcher contract.
- Visual changes must keep `tests-e2e/visuals/test_drawer_paint_baseline.py` green (open-close-reopen stability check is load-bearing).
- The four migration targets are: `ActivityAllAppsContainerView` (drawer colors + DRAWER_HIDE_TABS), `AppsSearchContainerLayout` (search bg color + opacity), `AllAppsState` (drawer bg), `RecyclerViewFastScroller` (scrollbar color).
PROMPT
)"
