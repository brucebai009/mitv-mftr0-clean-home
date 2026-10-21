param(
    [Parameter(Mandatory=$true)][string]$Serial,
    [string]$AdbPath = 'adb',
    [string]$ApkPath = (Join-Path $PSScriptRoot 'TVHome-MFTR0-1.0.0-rc.1.apk'),
    [string]$ManifestPath = (Join-Path $PSScriptRoot 'release-manifest.json'),
    [string]$StateRoot = (Join-Path $PSScriptRoot 'tvhome-state'),
    [string]$ExpectedModel,
    [string]$ExpectedFingerprint,
    [switch]$ActivateHome
)

$ErrorActionPreference = 'Stop'
$PackageName = 'com.gathin.tvhome'
$MainActivity = "$PackageName/.MainActivity"

function Invoke-AdbText {
    param([Parameter(Mandatory=$true)][string[]]$Arguments)
    $output = (& $AdbPath '-s' $Serial @Arguments 2>&1 | Out-String).Trim()
    $exitCode = $LASTEXITCODE
    if ($exitCode -ne 0 -or $output -match '(?im)(^|\s)(error|failure):') {
        throw "ADB failed ($exitCode): $($Arguments -join ' ')`n$output"
    }
    return $output
}

function Read-AdbProperty([string]$Name) {
    return (Invoke-AdbText @('shell', 'getprop', $Name)).Trim()
}

function Write-Journal([string]$Message) {
    Add-Content -LiteralPath $JournalPath -Value "$(Get-Date -Format o) $Message" -Encoding UTF8
}

function Read-HomeComponent {
    $text = Invoke-AdbText @('shell', 'cmd', 'package', 'resolve-activity', '--brief', '--user', '0',
            '-a', 'android.intent.action.MAIN', '-c', 'android.intent.category.HOME')
    $line = ($text -split '\r?\n' | Where-Object { $_ -match '^[A-Za-z0-9._]+/[A-Za-z0-9.$_]+$' } | Select-Object -Last 1)
    if (-not $line) { throw "Could not read the HOME resolver: $text" }
    return $line.Trim()
}

function Read-Foreground {
    return Invoke-AdbText @('shell', 'dumpsys', 'activity', 'activities')
}

if ([string]::IsNullOrWhiteSpace($Serial)) { throw 'Serial is required; no device address is built in.' }
$apk = (Resolve-Path -LiteralPath $ApkPath -ErrorAction Stop).Path
if (-not (Test-Path -LiteralPath $ManifestPath -PathType Leaf)) { throw "Manifest not found: $ManifestPath" }
$manifest = Get-Content -LiteralPath $ManifestPath -Raw -Encoding UTF8 | ConvertFrom-Json
if ($manifest.packageName -ne $PackageName) { throw 'Manifest packageName does not match TVHome.' }
$apkHash = (Get-FileHash -LiteralPath $apk -Algorithm SHA256).Hash.ToLowerInvariant()
$manifestApkName = [string]$manifest.apk.fileName
$manifestApkHash = [string]$manifest.apk.sha256
if ($manifestApkName -and ([IO.Path]::GetFileName($apk) -ne $manifestApkName)) { throw 'APK filename does not match the release manifest.' }
if ($manifestApkHash -and ($apkHash -ne $manifestApkHash.ToLowerInvariant())) { throw 'APK SHA-256 does not match the release manifest.' }

New-Item -ItemType Directory -Force -Path $StateRoot | Out-Null
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$snapshotPath = Join-Path $StateRoot "tvhome-snapshot-$stamp.json"
$JournalPath = Join-Path $StateRoot "tvhome-install-$stamp.log"
New-Item -ItemType File -Force -Path $JournalPath | Out-Null
Write-Journal 'BEGIN install'

$state = Invoke-AdbText @('get-state')
if ($state.Trim() -ne 'device') { throw "ADB device state is not device: $state" }
$actualSerial = (Invoke-AdbText @('get-serialno')).Trim()
$model = Read-AdbProperty 'ro.product.model'
$fingerprint = Read-AdbProperty 'ro.build.fingerprint'
$user = (Invoke-AdbText @('shell', 'cmd', 'activity', 'get-current-user')).Trim()
if ($user -ne '0') { throw "Current Android user is $user, expected 0." }
if ($ExpectedModel -and $ExpectedModel -ne $model) { throw "Unexpected model: $model" }
if ($ExpectedFingerprint -and $ExpectedFingerprint -ne $fingerprint) { throw 'Unexpected firmware fingerprint.' }
Write-Journal "DEVICE model=$model user=$user serial=$actualSerial"

$originalHome = Read-HomeComponent
$originalPackage = $originalHome.Split('/')[0]
$originalDisabled = (Invoke-AdbText @('shell', 'pm', 'list', 'packages', '-d', '--user', '0')) -match "(?m)^package:$([regex]::Escape($originalPackage))$"
if (-not (Invoke-AdbText @('shell', 'pm', 'path', $originalPackage))) { throw "Original HOME package is not installed: $originalPackage" }
$existingPackage = Invoke-AdbText @('shell', 'pm', 'path', $PackageName)
$packageDump = if ($existingPackage) { Invoke-AdbText @('shell', 'dumpsys', 'package', $PackageName) } else { '' }

$snapshot = [ordered]@{
    schema = 1
    productName = 'TVHome'
    createdUtc = (Get-Date).ToUniversalTime().ToString('o')
    deviceSerial = $actualSerial
    userId = 0
    model = $model
    fingerprint = $fingerprint
    originalHomeComponent = $originalHome
    originalHomePackage = $originalPackage
    originalHomeEnabled = (-not $originalDisabled)
    tvHomeWasInstalled = [bool]$existingPackage
    tvHomePackageDump = $packageDump
    apkFileName = [IO.Path]::GetFileName($apk)
    apkSha256 = $apkHash
}
$snapshot | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $snapshotPath -Encoding UTF8
Write-Journal "SNAPSHOT $snapshotPath originalHome=$originalHome"

$installOutput = Invoke-AdbText @('install', '-r', $apk)
if ($installOutput -notmatch '(?im)\bSuccess\b') { throw "Install did not report Success: $installOutput" }
Write-Journal 'INSTALL success'

$afterOriginalDisabled = (Invoke-AdbText @('shell', 'pm', 'list', 'packages', '-d', '--user', '0')) -match "(?m)^package:$([regex]::Escape($originalPackage))$"
if ($afterOriginalDisabled -ne $originalDisabled) { throw 'Original HOME enabled state changed during install.' }

Invoke-AdbText @('shell', 'am', 'start', '-n', $MainActivity) | Out-Null
$foreground = Read-Foreground
if ($foreground -notmatch [regex]::Escape($MainActivity)) { throw 'TVHome did not become the foreground activity after install.' }
Write-Journal 'DIRECT_START foreground verified'

if ($ActivateHome) {
    try {
        Invoke-AdbText @('shell', 'cmd', 'package', 'set-home-activity', '--user', '0', $MainActivity) | Out-Null
        if ((Read-HomeComponent) -ne $MainActivity) { throw 'HOME resolver did not read back TVHome.' }
        Invoke-AdbText @('shell', 'input', 'keyevent', '3') | Out-Null
        if ((Read-Foreground) -notmatch [regex]::Escape($MainActivity)) { throw 'TVHome did not become foreground after HOME.' }
        Write-Journal 'ACTIVATE_HOME resolver and foreground verified'
    } catch {
        Write-Journal "ACTIVATE_HOME failed: $($_.Exception.Message)"
        & (Join-Path $PSScriptRoot 'Restore-OriginalHome.ps1') -Serial $Serial -AdbPath $AdbPath -SnapshotPath $snapshotPath -StateRoot $StateRoot
        throw
    }
}

Write-Journal 'END install'
Write-Output "TVHome installed: $apk"
Write-Output "Snapshot: $snapshotPath"
Write-Output "Journal: $JournalPath"
Write-Output "Home activated: $ActivateHome"
