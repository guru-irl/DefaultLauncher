#!/usr/bin/env bash
# DefaultLauncher e2e runner.
# Usage:
#   ./run.sh                  # smoke only (fast, <5min)
#   ./run.sh --full           # smoke + regression
#   ./run.sh --stress         # stress only
#   ./run.sh -k test_name     # any pytest selector

set -euo pipefail

REPO_DIR="$(cd "$(dirname "$0")"/.. && pwd)"
TESTS_DIR="$REPO_DIR/tests-e2e"
ANDROID_HOME=${ANDROID_HOME:-$HOME/Android/Sdk}
export PATH="$ANDROID_HOME/platform-tools:$PATH"
# If multiple devices are attached, default to the emulator unless overridden.
if [ -z "${ANDROID_SERIAL:-}" ]; then
  EMULATOR=$(adb devices | grep "emulator" | awk '{print $1}' | head -1)
  if [ -n "$EMULATOR" ]; then
    export ANDROID_SERIAL="$EMULATOR"
  fi
fi

cd "$TESTS_DIR"

if [ ! -d .venv ]; then
  echo "[setup] creating venv"
  python3 -m venv .venv
  .venv/bin/pip install --quiet --upgrade pip
  .venv/bin/pip install --quiet -e .
fi

PYTHON=.venv/bin/python
PYTEST=.venv/bin/pytest

# Sanity: device + app present.
if ! adb devices | grep -q "device$"; then
  echo "[error] no device. Start the emulator or attach a phone."
  exit 1
fi

if ! adb shell pm path com.guru.defaultlauncher >/dev/null 2>&1; then
  echo "[setup] installing DefaultLauncher-debug.apk"
  adb install -r -d -g "$REPO_DIR/build/outputs/apk/debug/DefaultLauncher-debug.apk"
fi

mode="${1:-smoke}"
case "$mode" in
  --full)
    shift || true
    "$PYTEST" smoke regression "$@"
    ;;
  --regression)
    shift || true
    "$PYTEST" regression "$@"
    ;;
  --stress)
    shift || true
    "$PYTEST" stress "$@"
    ;;
  --smoke|smoke)
    shift || true
    "$PYTEST" smoke -m smoke "$@"
    ;;
  -k|*)
    "$PYTEST" smoke regression "$@"
    ;;
esac
