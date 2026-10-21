param(
    [Parameter(Mandatory=$true)][string]$Serial,
    [string]$AdbPath = 'adb',
    [string]$ApkPath = (Join-Path $PSScriptRoot 'TVHome-MFTR0-1.0.0-v13.2.apk'),
    [string]$ManifestPath = (Join-Path $PSScriptRoot 'release-manifest.json'),
    [string]$StateRoot = (Join-Path $PSScriptRoot 'tvhome-state'),
    [string]$ExpectedModel,
    [string]$ExpectedFingerprint,
    [string]$OriginalSnapshotPath,
    [switch]$ActivateHome
)

$ErrorActionPreference = 'Stop'
$PackageName = 'com.gathin.tvhome'
$MainActivity = "$PackageName/.MainActivity"
$TrustedCertificateSha256 = '8edb7ec47cf01743c0059740451a0dc4570b5312fe4c0e06c83595948f95d595'

function Read-StrictReleaseManifest([string]$Path, [string]$Apk) {
    try {
        $value = Get-Content -LiteralPath $Path -Raw -Encoding UTF8 | ConvertFrom-Json
    } catch {
        throw "Release manifest is not valid UTF-8 JSON: $Path ($($_.Exception.Message))"
    }
    if ([int]$value.schemaVersion -ne 2) { throw 'Release manifest schemaVersion must be 2.' }
    if ([string]$value.packageName -ne $PackageName) { throw 'Manifest packageName does not match TVHome.' }
    if ([int]$value.versionCode -le 0 -or [string]::IsNullOrWhiteSpace([string]$value.versionName)) {
        throw 'Manifest versionCode/versionName is missing or invalid.'
    }
    if (-not $value.apk) { throw 'Manifest apk object is missing.' }
    $manifestName = [string]$value.apk.fileName
    if ([string]::IsNullOrWhiteSpace($manifestName) -or [IO.Path]::GetFileName($manifestName) -ne $manifestName -or
            $manifestName.Contains('/') -or $manifestName.Contains('\')) {
        throw 'Manifest apk.fileName must be a basename.'
    }
    if ([IO.Path]::GetFileName($Apk) -ne $manifestName) { throw 'APK filename does not match the release manifest.' }
    $manifestHash = [string]$value.apk.sha256
    if ($manifestHash -notmatch '^[0-9a-fA-F]{64}$') { throw 'Manifest apk.sha256 must be 64 hexadecimal characters.' }
    if ([int64]$value.apk.sizeBytes -le 0) { throw 'Manifest apk.sizeBytes must be positive.' }
    $certificate = [string]$value.certificateSha256
    if ($certificate -notmatch '^[0-9a-fA-F]{64}$' -or $certificate.ToLowerInvariant() -ne $TrustedCertificateSha256) {
        throw 'Manifest certificateSha256 is not the trusted TVHome release certificate.'
    }
    if ([string]$value.sourceCommit -notmatch '^[0-9a-fA-F]{40}$') { throw 'Manifest sourceCommit must be a full commit hash.' }
    if ([int]$value.build.compileSdk -ne 34 -or [int]$value.build.targetSdk -ne 34 -or [int]$value.build.minSdk -ne 26) {
        throw 'Manifest build SDK contract must be compileSdk=34, targetSdk=34, minSdk=26.'
    }
    $actualHash = (Get-FileHash -LiteralPath $Apk -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($actualHash -ne $manifestHash.ToLowerInvariant()) { throw 'APK SHA-256 does not match the release manifest.' }
    if ((Get-Item -LiteralPath $Apk).Length -ne [int64]$value.apk.sizeBytes) { throw 'APK size does not match the release manifest.' }
    return $value
}

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

function Read-OptionalPackagePath([string]$Package) {
    $output = (& $AdbPath '-s' $Serial 'shell' 'pm' 'path' $Package 2>&1 | Out-String).Trim()
    $exitCode = $LASTEXITCODE
    if ($exitCode -eq 0) { return $output }
    if ([string]::IsNullOrWhiteSpace($output) -or $output -match '(?i)unknown package|unable to find') { return '' }
    throw "ADB package query failed ($exitCode): $output"
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

function Normalize-Component([string]$Value) {
    if ([string]::IsNullOrWhiteSpace($Value) -or $Value -notmatch '^([A-Za-z0-9._]+)/([A-Za-z0-9.$_]+)$') { return '' }
    $package = $matches[1]
    $class = $matches[2]
    if ($class.StartsWith('.')) { $class = $package + $class }
    elseif ($class -notmatch '\.') { $class = "$package.$class" }
    return "$package/$class"
}

function Read-ForegroundState {
    $activity = Invoke-AdbText @('shell', 'dumpsys', 'activity', 'activities')
    $window = Invoke-AdbText @('shell', 'dumpsys', 'window')
    $resumed = ''
    foreach ($line in ($activity -split '\r?\n')) {
        if ($line -match '(?:topResumedActivity|mResumedActivity)[:=].*?\bu0\s+([A-Za-z0-9._]+/[A-Za-z0-9.$_]+)') {
            $resumed = Normalize-Component $matches[1]
            break
        }
    }
    $currentFocus = ''
    $focusedApp = ''
    foreach ($line in ($window -split '\r?\n')) {
        if (-not $currentFocus -and $line -match 'mCurrentFocus=.*?\bu0\s+([A-Za-z0-9._]+/[A-Za-z0-9.$_]+)') {
            $currentFocus = Normalize-Component $matches[1]
        }
        if (-not $focusedApp -and $line -match 'mFocusedApp=.*?\bu0\s+([A-Za-z0-9._]+/[A-Za-z0-9.$_]+)') {
            $focusedApp = Normalize-Component $matches[1]
        }
    }
    return [pscustomobject]@{ Resumed = $resumed; CurrentFocus = $currentFocus; FocusedApp = $focusedApp }
}

function Wait-Foreground([string]$ExpectedComponent, [int]$TimeoutSeconds = 10) {
    $expected = Normalize-Component $ExpectedComponent
    if (-not $expected) { throw "Expected foreground component is invalid: $ExpectedComponent" }
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    $last = $null
    do {
        $last = Read-ForegroundState
        if ($last.Resumed -eq $expected -and $last.CurrentFocus -eq $expected -and $last.FocusedApp -eq $expected) {
            return $last
        }
        Start-Sleep -Milliseconds 500
    } while ((Get-Date) -lt $deadline)
    throw "Foreground readback did not stabilize on $expected (resumed=$($last.Resumed); currentFocus=$($last.CurrentFocus); focusedApp=$($last.FocusedApp))."
}

if ([string]::IsNullOrWhiteSpace($Serial)) { throw 'Serial is required; no device address is built in.' }
$apk = (Resolve-Path -LiteralPath $ApkPath -ErrorAction Stop).Path
if (-not (Test-Path -LiteralPath $ManifestPath -PathType Leaf)) { throw "Manifest not found: $ManifestPath" }
$manifest = Read-StrictReleaseManifest $ManifestPath $apk
$apkHash = (Get-FileHash -LiteralPath $apk -Algorithm SHA256).Hash.ToLowerInvariant()

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

$preInstallHome = Read-HomeComponent
$baselineSnapshot = $null
if ($OriginalSnapshotPath) {
    $baselinePath = (Resolve-Path -LiteralPath $OriginalSnapshotPath -ErrorAction Stop).Path
    $baselineSnapshot = Get-Content -LiteralPath $baselinePath -Raw -Encoding UTF8 | ConvertFrom-Json
} else {
    $baselineCandidate = Get-ChildItem -LiteralPath $StateRoot -Filter 'tvhome-snapshot-*.json' -File -ErrorAction SilentlyContinue |
        Sort-Object LastWriteTime -Descending | ForEach-Object {
            try {
                $candidate = Get-Content -LiteralPath $_.FullName -Raw -Encoding UTF8 | ConvertFrom-Json
                if ($candidate.productName -eq 'TVHome' -and $candidate.originalHomePackage -and
                        $candidate.originalHomePackage -ne $PackageName -and
                        $candidate.originalHomeComponent -notmatch "^$([regex]::Escape($PackageName))/") { return $candidate }
            } catch { }
        } | Select-Object -First 1
    if ($baselineCandidate) { $baselineSnapshot = $baselineCandidate }
}
if ($baselineSnapshot) {
    if ($baselineSnapshot.productName -ne 'TVHome' -or [int]$baselineSnapshot.userId -ne 0 -or
            [string]$baselineSnapshot.originalHomePackage -eq $PackageName -or
            [string]$baselineSnapshot.originalHomeComponent -notmatch '^[A-Za-z0-9._]+/[A-Za-z0-9.$_]+$' -or
            [string]$baselineSnapshot.originalHomeComponent -match "^$([regex]::Escape($PackageName))/") {
        throw 'The supplied original HOME snapshot is invalid or points to TVHome.'
    }
    if ($baselineSnapshot.deviceSerial -and $baselineSnapshot.deviceSerial -ne $actualSerial) { throw 'Original HOME snapshot belongs to a different device.' }
    if ($baselineSnapshot.model -and $baselineSnapshot.model -ne $model) { throw 'Original HOME snapshot model does not match.' }
    if ($baselineSnapshot.fingerprint -and $baselineSnapshot.fingerprint -ne $fingerprint) { throw 'Original HOME snapshot fingerprint does not match.' }
    $originalHome = [string]$baselineSnapshot.originalHomeComponent
    $originalPackage = [string]$baselineSnapshot.originalHomePackage
    Write-Journal "ORIGINAL_BASELINE trusted snapshot=$OriginalSnapshotPath component=$originalHome"
} else {
    $preInstallPackage = $preInstallHome.Split('/')[0]
    if ($preInstallPackage -eq $PackageName) { throw 'Current HOME is TVHome but no trusted original HOME snapshot was supplied.' }
    if ($preInstallPackage -ne 'com.mitv.tvhome') { throw "Current HOME package is not the known original Xiaomi HOME: $preInstallPackage" }
    $originalHome = $preInstallHome
    $originalPackage = $preInstallPackage
    Write-Journal "ORIGINAL_BASELINE live known Xiaomi HOME=$originalHome"
}
$originalDisabled = (Invoke-AdbText @('shell', 'pm', 'list', 'packages', '-d', '--user', '0')) -match "(?m)^package:$([regex]::Escape($originalPackage))$"
if ($originalDisabled) { throw "Original HOME package is disabled: $originalPackage" }
if (-not (Invoke-AdbText @('shell', 'pm', 'path', $originalPackage))) { throw "Original HOME package is not installed: $originalPackage" }
$existingPackage = Read-OptionalPackagePath $PackageName
$packageDump = if ($existingPackage) { Invoke-AdbText @('shell', 'dumpsys', 'package', $PackageName) } else { '' }
if ($packageDump -and $packageDump -match '(?m)versionCode=(\d+)') {
    $installedVersionCode = [int]$matches[1]
    if ($installedVersionCode -gt [int]$manifest.versionCode) {
        throw "Refusing downgrade from installed versionCode $installedVersionCode to $($manifest.versionCode)."
    }
}

$snapshot = [ordered]@{
    schema = 1
    productName = 'TVHome'
    createdUtc = (Get-Date).ToUniversalTime().ToString('o')
    deviceSerial = $actualSerial
    userId = 0
    model = $model
    fingerprint = $fingerprint
    preInstallHomeComponent = $preInstallHome
    originalHomeComponent = $originalHome
    originalHomePackage = $originalPackage
    originalHomeEnabled = (-not $originalDisabled)
    originalBaselineSnapshot = if ($OriginalSnapshotPath) { $baselinePath } else { $null }
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
$foreground = Wait-Foreground $MainActivity
Write-Journal "DIRECT_START foreground verified resumed=$($foreground.Resumed) currentFocus=$($foreground.CurrentFocus) focusedApp=$($foreground.FocusedApp)"
Write-Output 'INSTALL_APK=PASS'
Write-Output 'DIRECT_START=PASS'
Write-Output "CURRENT_FOREGROUND=PASS resumed=$($foreground.Resumed); currentFocus=$($foreground.CurrentFocus); focusedApp=$($foreground.FocusedApp)"
Write-Output "ORIGINAL_HOME_ENABLED=PASS package=$originalPackage"
Write-Output "RELEASE_MANIFEST=PASS schemaVersion=$($manifest.schemaVersion); versionCode=$($manifest.versionCode); versionName=$($manifest.versionName)"

if ($ActivateHome) {
    try {
        Invoke-AdbText @('shell', 'cmd', 'package', 'set-home-activity', '--user', '0', $MainActivity) | Out-Null
        if ((Normalize-Component (Read-HomeComponent)) -ne (Normalize-Component $MainActivity)) { throw 'HOME resolver did not read back TVHome.' }
        Invoke-AdbText @('shell', 'input', 'keyevent', '3') | Out-Null
        $homeForeground = Wait-Foreground $MainActivity
        Write-Journal "ACTIVATE_HOME resolver and foreground verified resumed=$($homeForeground.Resumed) currentFocus=$($homeForeground.CurrentFocus) focusedApp=$($homeForeground.FocusedApp)"
        Write-Output 'HOME_ACTIVATION=PASS'
        Write-Output "CURRENT_FOREGROUND_AFTER_HOME=PASS resumed=$($homeForeground.Resumed); currentFocus=$($homeForeground.CurrentFocus); focusedApp=$($homeForeground.FocusedApp)"
    } catch {
        Write-Journal "ACTIVATE_HOME failed: $($_.Exception.Message)"
        & (Join-Path $PSScriptRoot 'Restore-OriginalHome.ps1') -Serial $Serial -AdbPath $AdbPath -SnapshotPath $snapshotPath -StateRoot $StateRoot
        Write-Output 'HOME_ACTIVATION=FAIL_RESTORED_ORIGINAL'
        throw
    }
} else {
    Write-Output 'HOME_ACTIVATION=NOT_REQUESTED'
    Write-Output '尚未更改默认 HOME'
}

Write-Journal 'END install'
Write-Output "TVHome installed: $apk"
Write-Output "Snapshot: $snapshotPath"
Write-Output "Journal: $JournalPath"
Write-Output "Home activated: $ActivateHome"
