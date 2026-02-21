#!/usr/bin/env bash
# apk-certificate.sh — Print the signing certificate details of an APK.
# Usage: bash scripts/apk-certificate.sh path/to/app.apk
set -euo pipefail

APK_PATH="${1:?Usage: apk-certificate.sh <path-to-apk>}"

if [ ! -f "$APK_PATH" ]; then
    echo "ERROR: APK not found: $APK_PATH" >&2
    exit 1
fi

# Find apksigner
SDK_ROOT="${ANDROID_HOME:-${HOME}/AppData/Local/Android/Sdk}"
APKSIGNER=""
if [ -d "$SDK_ROOT/build-tools" ]; then
    LATEST=$(ls -1 "$SDK_ROOT/build-tools" | sort -V | tail -1)
    if [ -n "$LATEST" ]; then
        APKSIGNER="$SDK_ROOT/build-tools/$LATEST/apksigner"
        [ -f "$APKSIGNER.bat" ] && APKSIGNER="$APKSIGNER.bat"
    fi
fi
if [ -z "$APKSIGNER" ] || [ ! -f "$APKSIGNER" ]; then
    echo "ERROR: apksigner not found. Install Android SDK Build Tools." >&2
    exit 1
fi

# Set JAVA_HOME if not set
if [ -z "${JAVA_HOME:-}" ]; then
    if [ -d "/c/Program Files/Android/Android Studio/jbr" ]; then
        export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
    fi
fi

"$APKSIGNER" verify --print-certs "$APK_PATH"
