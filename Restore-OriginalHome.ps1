param(
    [Parameter(Mandatory=$true)][string]$Serial,
    [string]$AdbPath = 'adb',
    [string]$StateRoot = (Join-Path $PSScriptRoot 'tvhome-state'),
    [string]$SnapshotPath
)

$ErrorActionPreference = 'Stop'

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

if ([string]::IsNullOrWhiteSpace($Serial)) { throw 'Serial is required; no device address is built in.' }
if (-not $SnapshotPath) {
    $candidate = Get-ChildItem -LiteralPath $StateRoot -Filter 'tvhome-snapshot-*.json' -File -ErrorAction Stop |
        Sort-Object LastWriteTime -Descending | Select-Object -First 1
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
if ((Read-HomeComponent) -ne $originalHome) { throw 'HOME resolver did not read back the snapshot component.' }
Invoke-AdbText @('shell', 'input', 'keyevent', '3') | Out-Null
$foreground = Invoke-AdbText @('shell', 'dumpsys', 'activity', 'activities')
if ($foreground -notmatch "(?m)(mResumedActivity|mCurrentFocus).*$([regex]::Escape($originalPackage))") {
    throw 'Original HOME did not become the foreground application.'
}
Write-Journal 'READBACK resolver and foreground verified'
Write-Journal 'END restore'

Write-Output "Original HOME restored: $originalHome"
Write-Output "Snapshot: $snapshotFile"
Write-Output "Journal: $JournalPath"
