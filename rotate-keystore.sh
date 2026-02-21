#!/usr/bin/env bash
# rotate-keystore.sh — Generate a new release keystore, back up to OneDrive, and push secrets to GitHub.
# Run from the project root. Requires: keytool (JDK), openssl, gh (GitHub CLI), base64.
set -euo pipefail

KEYSTORE_FILE="release-keystore.jks"
KEY_ALIAS="defaultlauncher"
DNAME="CN=DefaultLauncher, O=guru-irl"
VALIDITY=10000
BACKUP_DIR="$HOME/OneDrive/DefaultLauncher-Signing"

# Find keytool
if command -v keytool &>/dev/null; then
    KEYTOOL=keytool
elif [ -x "/c/Program Files/Android/Android Studio/jbr/bin/keytool" ]; then
    KEYTOOL="/c/Program Files/Android/Android Studio/jbr/bin/keytool"
else
    echo "ERROR: keytool not found. Set JAVA_HOME or install a JDK." >&2
    exit 1
fi

# Verify gh is authenticated
if ! gh auth status &>/dev/null; then
    echo "ERROR: gh CLI not authenticated. Run 'gh auth login' first." >&2
    exit 1
fi

# Clean up any leftover keystore
rm -f "$KEYSTORE_FILE"

# Generate random password
PASSWORD=$(openssl rand -base64 24)

echo "Generating new keystore..."
"$KEYTOOL" -genkeypair -v \
    -keystore "$KEYSTORE_FILE" \
    -keyalg RSA -keysize 2048 -validity "$VALIDITY" \
    -alias "$KEY_ALIAS" \
    -storepass "$PASSWORD" -keypass "$PASSWORD" \
    -dname "$DNAME"

# Back up keystore and password to OneDrive
echo ""
echo "Backing up to OneDrive..."
mkdir -p "$BACKUP_DIR"
cp "$KEYSTORE_FILE" "$BACKUP_DIR/release-keystore.jks"
cat > "$BACKUP_DIR/keystore-password.txt" <<EOF
Keystore: release-keystore.jks
Alias: $KEY_ALIAS
Password: $PASSWORD
DN: $DNAME
Generated: $(date '+%Y-%m-%d %H:%M:%S')
EOF
echo "  Backed up to: $BACKUP_DIR"

# Push secrets to GitHub
echo ""
echo "Pushing secrets to GitHub..."
base64 -w 0 "$KEYSTORE_FILE" | gh secret set KEYSTORE_BASE64
gh secret set KEYSTORE_PASSWORD --body "$PASSWORD"
gh secret set KEY_ALIAS --body "$KEY_ALIAS"
gh secret set KEY_PASSWORD --body "$PASSWORD"

# Clean up local keystore
echo ""
echo "Cleaning up local keystore..."
rm -f "$KEYSTORE_FILE"

echo ""
echo "========================================="
echo "  Keystore rotated successfully."
echo ""
echo "  Backup:  $BACKUP_DIR"
echo "  Password saved to: $BACKUP_DIR/keystore-password.txt"
echo ""
echo "  GitHub secrets updated:"
echo "    KEYSTORE_BASE64"
echo "    KEYSTORE_PASSWORD"
echo "    KEY_ALIAS"
echo "    KEY_PASSWORD"
echo "========================================="
