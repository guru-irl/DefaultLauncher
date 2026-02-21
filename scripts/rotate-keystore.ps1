# rotate-keystore.ps1 — Generate a new release keystore, back up to OneDrive, and push secrets to GitHub.
# Run from the project root. Requires: keytool (JDK), gh (GitHub CLI).
$ErrorActionPreference = "Stop"

$KeystoreFile = "release-keystore.jks"
$KeyAlias = "defaultlauncher"
$DName = "CN=DefaultLauncher, O=guru-irl"
$Validity = 10000
$BackupDir = "$env:USERPROFILE\OneDrive\DefaultLauncher-Signing"

# Find keytool
$Keytool = Get-Command keytool -ErrorAction SilentlyContinue
if ($Keytool) {
    $KeytoolPath = $Keytool.Source
} elseif (Test-Path "C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe") {
    $KeytoolPath = "C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe"
} else {
    Write-Error "keytool not found. Set JAVA_HOME or install a JDK."
    exit 1
}

# Verify gh is authenticated
$null = gh auth status 2>&1
if ($LASTEXITCODE -ne 0) {
    Write-Error "gh CLI not authenticated. Run 'gh auth login' first."
    exit 1
}

# Clean up any leftover keystore
Remove-Item -Path $KeystoreFile -ErrorAction SilentlyContinue

# Generate random password
$Bytes = New-Object byte[] 24
$Rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$Rng.GetBytes($Bytes)
$Rng.Dispose()
$Password = [Convert]::ToBase64String($Bytes)

Write-Host "Generating new keystore..."
& $KeytoolPath -genkeypair -v `
    -keystore $KeystoreFile `
    -keyalg RSA -keysize 2048 -validity $Validity `
    -alias $KeyAlias `
    -storepass "$Password" -keypass "$Password" `
    -dname "$DName"

if ($LASTEXITCODE -ne 0) {
    Write-Error "keytool failed."
    exit 1
}

# Back up keystore and password to OneDrive
Write-Host ""
Write-Host "Backing up to OneDrive..."
New-Item -ItemType Directory -Path $BackupDir -Force | Out-Null
Copy-Item -Path $KeystoreFile -Destination "$BackupDir\release-keystore.jks" -Force
Set-Content -Path "$BackupDir\keystore-password.txt" -Value @"
Keystore: release-keystore.jks
Alias: $KeyAlias
Password: $Password
DN: $DName
Generated: $(Get-Date -Format "yyyy-MM-dd HH:mm:ss")
"@
Write-Host "  Backed up to: $BackupDir"

# Push secrets to GitHub
Write-Host ""
Write-Host "Pushing secrets to GitHub..."
$Base64Keystore = [Convert]::ToBase64String([IO.File]::ReadAllBytes((Resolve-Path $KeystoreFile)))
$Base64Keystore | gh secret set KEYSTORE_BASE64
gh secret set KEYSTORE_PASSWORD --body "$Password"
gh secret set KEY_ALIAS --body "$KeyAlias"
gh secret set KEY_PASSWORD --body "$Password"

# Clean up local keystore
Write-Host ""
Write-Host "Cleaning up local keystore..."
Remove-Item -Path $KeystoreFile -ErrorAction SilentlyContinue

Write-Host ""
Write-Host "========================================="
Write-Host "  Keystore rotated successfully."
Write-Host ""
Write-Host "  Backup:  $BackupDir"
Write-Host "  Password saved to: $BackupDir\keystore-password.txt"
Write-Host ""
Write-Host "  GitHub secrets updated:"
Write-Host "    KEYSTORE_BASE64"
Write-Host "    KEYSTORE_PASSWORD"
Write-Host "    KEY_ALIAS"
Write-Host "    KEY_PASSWORD"
Write-Host "========================================="
