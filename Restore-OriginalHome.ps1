param(
    [Parameter(Mandatory=$true)][string]$Serial,
    [string]$AdbPath = 'adb',
    [string]$StateRoot = (Join-Path $PSScriptRoot 'tvhome-state'),
    [string]$SnapshotPath
)

$ErrorActionPreference = 'Stop'
$homeHelperService = 'com.gathin.tvhome/com.gathin.tvhome.HomeOnlyAccessibilityService'

function Invoke-AdbText {
    param([Parameter(Mandatory=$true)][string[]]$Arguments)
    $output = (& $AdbPath '-s' $Serial @Arguments 2>&1 | Out-String).Trim()
    $exitCode = $LASTEXITCODE
    if ($exitCode -ne 0 -or $output -match '(?im)(^|\s)(error|failure):') {
        throw "ADB failed ($exitCode): $($Arguments -join ' ')`n$output"
    }
    return $output
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
    $activityFocusedApp = ''
    foreach ($line in ($activity -split '\r?\n')) {
        if ($line -match '(?:topResumedActivity|mResumedActivity)[:=].*?\bu0\s+([A-Za-z0-9._]+/[A-Za-z0-9.$_]+)') {
            $resumed = Normalize-Component $matches[1]
        }
        if (-not $activityFocusedApp -and $line -match 'mFocusedApp=ActivityRecord\{.*?\bu0\s+([A-Za-z0-9._]+/[A-Za-z0-9.$_]+)') {
            $activityFocusedApp = Normalize-Component $matches[1]
        }
    }
    $currentFocus = ''
    $focusedApp = $activityFocusedApp
    foreach ($line in ($window -split '\r?\n')) {
        if (-not $currentFocus -and $line -match 'mCurrentFocus=.*?\bu0\s+([A-Za-z0-9._]+/[A-Za-z0-9.$_]+)') {
            $currentFocus = Normalize-Component $matches[1]
        }
        if (-not $focusedApp -and $line -match 'mFocusedApp=.*?\bu0\s+([A-Za-z0-9._]+/[A-Za-z0-9.$_]+)') {
            $focusedApp = Normalize-Component $matches[1]
        }
    }
    if (-not $currentFocus -and $resumed -and $focusedApp -eq $resumed -and
            $window -match "Window\{.*?\bu0\s+$([regex]::Escape($resumed))\}") {
        $currentFocus = $resumed
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

function Disable-TVHomeHomeHelper {
    $enabled = [string](Invoke-AdbText @('shell', 'settings', 'get', 'secure', 'enabled_accessibility_services'))
    $remaining = @($enabled -split ':' | Where-Object {
            $_ -and $_.Trim() -and $_.Trim() -ne $homeHelperService
        } | ForEach-Object { $_.Trim() })
    $value = $remaining -join ':'
    Invoke-AdbText @('shell', 'settings', 'put', 'secure', 'enabled_accessibility_services', $value) | Out-Null
    if ($remaining.Count -eq 0) {
        Invoke-AdbText @('shell', 'settings', 'put', 'secure', 'accessibility_enabled', '0') | Out-Null
    }
    Write-Journal "DISABLE_HOME_HELPER removed=$homeHelperService remaining=$value"
}

function Disarm-TVHomeRecovery {
    $uri = 'content://com.gathin.tvhome.recovery'
    # RecoveryProvider accepts the value as the documented string "true"/"false". Do not use
    # content's boolean extra type here: the provider deliberately validates getString(value).
    Invoke-AdbText @('shell', 'content', 'call', '--uri', $uri, '--method', 'helper_enabled', '--extra', 'value:s:false') | Out-Null
    Invoke-AdbText @('shell', 'content', 'call', '--uri', $uri, '--method', 'boot_recovery', '--extra', 'value:s:false') | Out-Null
    Invoke-AdbText @('shell', 'content', 'call', '--uri', $uri, '--method', 'reset_gesture') | Out-Null
    Write-Journal 'DISARM_RECOVERY helper_enabled=false boot_recovery=false reset_gesture=true'
}

if ([string]::IsNullOrWhiteSpace($Serial)) { throw 'Serial is required; no device address is built in.' }
if (-not $SnapshotPath) {
    $candidate = Get-ChildItem -LiteralPath $StateRoot -Filter 'tvhome-snapshot-*.json' -File -ErrorAction Stop |
        Sort-Object LastWriteTime -Descending | ForEach-Object {
            try {
                $item = Get-Content -LiteralPath $_.FullName -Raw -Encoding UTF8 | ConvertFrom-Json
                if ($item.productName -eq 'TVHome' -and $item.originalHomePackage -and
                        $item.originalHomePackage -ne 'com.gathin.tvhome' -and
                        $item.originalHomeComponent -notmatch '^com\.gathin\.tvhome/') { return $_ }
            } catch { }
        } | Select-Object -First 1
    if (-not $candidate) { throw "No TVHome snapshot found under $StateRoot." }
    $SnapshotPath = $candidate.FullName
}
$snapshotFile = (Resolve-Path -LiteralPath $SnapshotPath -ErrorAction Stop).Path
$snapshot = Get-Content -LiteralPath $snapshotFile -Raw -Encoding UTF8 | ConvertFrom-Json
if ($snapshot.productName -ne 'TVHome' -or [int]$snapshot.userId -ne 0) { throw 'Snapshot is not a TVHome user-0 snapshot.' }
if ([string]::IsNullOrWhiteSpace($snapshot.originalHomeComponent) -or
        $snapshot.originalHomeComponent -notmatch '^[A-Za-z0-9._]+/[A-Za-z0-9.$_]+$') {
    throw 'Snapshot has no safe original HOME component.'
}
if ([string]$snapshot.originalHomePackage -eq 'com.gathin.tvhome' -or
        [string]$snapshot.originalHomeComponent -match '^com\.gathin\.tvhome/') {
    throw 'Snapshot points to TVHome and cannot be used as the original HOME baseline.'
}

New-Item -ItemType Directory -Force -Path $StateRoot | Out-Null
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$JournalPath = Join-Path $StateRoot "tvhome-restore-$stamp.log"
New-Item -ItemType File -Force -Path $JournalPath | Out-Null
Write-Journal "BEGIN restore snapshot=$snapshotFile"

if ((Invoke-AdbText @('get-state')).Trim() -ne 'device') { throw 'ADB device is not ready.' }
$actualSerial = (Invoke-AdbText @('get-serialno')).Trim()
if ($snapshot.deviceSerial -and $snapshot.deviceSerial -ne $actualSerial) { throw 'Snapshot belongs to a different ADB device.' }
$model = (Invoke-AdbText @('shell', 'getprop', 'ro.product.model')).Trim()
$fingerprint = (Invoke-AdbText @('shell', 'getprop', 'ro.build.fingerprint')).Trim()
if ($snapshot.model -and $snapshot.model -ne $model) { throw 'Snapshot model does not match the connected device.' }
if ($snapshot.fingerprint -and $snapshot.fingerprint -ne $fingerprint) { throw 'Snapshot firmware fingerprint does not match.' }
if ((Invoke-AdbText @('shell', 'cmd', 'activity', 'get-current-user')).Trim() -ne '0') { throw 'Current Android user is not 0.' }
Write-Journal "DEVICE model=$model serial=$actualSerial"

$originalHome = [string]$snapshot.originalHomeComponent
$originalPackage = [string]$snapshot.originalHomePackage
if (-not $originalPackage) { $originalPackage = $originalHome.Split('/')[0] }
if (-not (Invoke-AdbText @('shell', 'pm', 'path', $originalPackage))) { throw "Original HOME package is not installed: $originalPackage" }
$disabled = (Invoke-AdbText @('shell', 'pm', 'list', 'packages', '-d', '--user', '0')) -match "(?m)^package:$([regex]::Escape($originalPackage))$"
if ($snapshot.originalHomeEnabled -and $disabled) {
    Invoke-AdbText @('shell', 'pm', 'enable', '--user', '0', $originalPackage) | Out-Null
    Write-Journal "ENABLE original package=$originalPackage"
}

Invoke-AdbText @('shell', 'cmd', 'package', 'set-home-activity', '--user', '0', $originalHome) | Out-Null
Write-Journal "SET_HOME $originalHome"
if ((Normalize-Component (Read-HomeComponent)) -ne (Normalize-Component $originalHome)) { throw 'HOME resolver did not read back the snapshot component.' }
# Remove only TVHome's optional helper before synthesizing Home. Otherwise the helper would intercept
# this very key and reopen TVHome, making a restore appear to succeed while takeover stayed active.
Disable-TVHomeHomeHelper
Disarm-TVHomeRecovery
Invoke-AdbText @('shell', 'input', 'keyevent', '3') | Out-Null
$foreground = Wait-Foreground $originalHome
Write-Journal "READBACK resolver and foreground verified resumed=$($foreground.Resumed) currentFocus=$($foreground.CurrentFocus) focusedApp=$($foreground.FocusedApp)"
Write-Journal 'END restore'

Write-Output "Original HOME restored: $originalHome"
Write-Output "CURRENT_FOREGROUND=PASS resumed=$($foreground.Resumed); currentFocus=$($foreground.CurrentFocus); focusedApp=$($foreground.FocusedApp)"
Write-Output "Snapshot: $snapshotFile"
Write-Output "Journal: $JournalPath"
