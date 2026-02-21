# apk-certificate.ps1 — Print the signing certificate details of an APK.
# Usage: .\scripts\apk-certificate.ps1 path\to\app.apk
param(
    [Parameter(Mandatory=$true, Position=0)]
    [string]$ApkPath
)
$ErrorActionPreference = "Stop"

if (-not (Test-Path $ApkPath)) {
    Write-Error "APK not found: $ApkPath"
    exit 1
}

# Find apksigner
$SdkRoot = "$env:LOCALAPPDATA\Android\Sdk"
$ApkSigner = $null
if (Test-Path $SdkRoot) {
    $BuildTools = Get-ChildItem "$SdkRoot\build-tools" -Directory | Sort-Object Name -Descending | Select-Object -First 1
    if ($BuildTools) {
        $ApkSigner = Join-Path $BuildTools.FullName "apksigner.bat"
    }
}
if (-not $ApkSigner -or -not (Test-Path $ApkSigner)) {
    Write-Error "apksigner not found. Install Android SDK Build Tools."
    exit 1
}

# Find JAVA_HOME
if (-not $env:JAVA_HOME) {
    if (Test-Path "C:\Program Files\Android\Android Studio\jbr") {
        $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
    }
}

& $ApkSigner verify --print-certs $ApkPath
