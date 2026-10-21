param(
    [Parameter(Mandatory=$true)][string]$Serial,
    [string]$AdbPath = 'adb',
    [string]$StateRoot = (Join-Path $PSScriptRoot 'tvhome-state')
)

$ErrorActionPreference = 'Stop'
$Candidates = @(
    'com.xiaomi.mitv.appstore',
    'com.xiaomi.mitv.shop',
    'com.duokan.videodaily'
)
$Protected = @(
    'com.gathin.tvhome',
    'com.mitv.tvhome',
    'com.xiaomi.voicecontrol',
    'com.xiaomi.mitv.tvplayer',
    'com.mitv.mivideoplayer',
    'com.xiaomi.mitv.mediaexplorer',
    'com.xiaomi.mitv.hyper.screensaver',
    'com.xiaomi.mitv.settings'
)

function Invoke-AdbText {
    param([Parameter(Mandatory=$true)][string[]]$Arguments)
    $output = (& $AdbPath '-s' $Serial @Arguments 2>&1 | Out-String).Trim()
    if ($LASTEXITCODE -ne 0 -or $output -match '(?im)(^|\s)(error|failure):') {
        throw "ADB failed: $($Arguments -join ' ')`n$output"
    }
    return $output
}

if ((Invoke-AdbText @('get-state')).Trim() -ne 'device') { throw 'ADB device is not ready.' }
if ((Invoke-AdbText @('shell', 'cmd', 'activity', 'get-current-user')).Trim() -ne '0') { throw 'Current Android user is not 0.' }

New-Item -ItemType Directory -Force -Path $StateRoot | Out-Null
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$snapshotPath = Join-Path $StateRoot "content-policy-$stamp.json"
$installed = Invoke-AdbText @('shell', 'pm', 'list', 'packages', '--user', '0')
$disabled = Invoke-AdbText @('shell', 'pm', 'list', 'packages', '-d', '--user', '0')
$present = @($installed -split '\r?\n' | ForEach-Object { ($_ -replace '^package:', '').Trim() } | Where-Object { $_ })
$disabledNames = @($disabled -split '\r?\n' | ForEach-Object { ($_ -replace '^package:', '').Trim() } | Where-Object { $_ })

foreach ($protectedPackage in $Protected) {
    if ($protectedPackage -notin $present) { throw "Protected package is not installed: $protectedPackage" }
}
$actions = foreach ($package in $Candidates) {
    if ($package -notin $present) {
        [pscustomobject]@{ package = $package; installed = $false; wasDisabled = $false; disabledByPolicy = $false }
        continue
    }
    $wasDisabled = $package -in $disabledNames
    [pscustomobject]@{ package = $package; installed = $true; wasDisabled = $wasDisabled; disabledByPolicy = $false }
}
$snapshot = [ordered]@{
    schema = 1
    policy = 'independent-content-components'
    createdUtc = (Get-Date).ToUniversalTime().ToString('o')
    deviceSerial = (Invoke-AdbText @('get-serialno')).Trim()
    model = (Invoke-AdbText @('shell', 'getprop', 'ro.product.model')).Trim()
    protected = $Protected
    actions = @($actions)
}
$snapshot | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $snapshotPath -Encoding UTF8

foreach ($action in $actions) {
    if (-not $action.installed -or $action.wasDisabled) { continue }
    Invoke-AdbText @('shell', 'pm', 'disable-user', '--user', '0', $action.package) | Out-Null
    $action.disabledByPolicy = $true
}
$afterDisabled = Invoke-AdbText @('shell', 'pm', 'list', 'packages', '-d', '--user', '0')
$afterDisabledNames = @($afterDisabled -split '\r?\n' | ForEach-Object { $_.Trim() } | Where-Object { $_ })
foreach ($action in $actions | Where-Object { $_.disabledByPolicy }) {
    if ("package:$($action.package)" -notin $afterDisabledNames) {
        throw "Content package did not read back disabled: $($action.package)"
    }
}
foreach ($protectedPackage in $Protected) {
    $protectedPath = Invoke-AdbText @('shell', 'pm', 'path', $protectedPackage)
    if ([string]::IsNullOrWhiteSpace($protectedPath) -or $protectedPath -match '(?i)unknown package|unable to find') {
        throw "Protected package path readback failed: $protectedPackage"
    }
}

$snapshot | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $snapshotPath -Encoding UTF8
Write-Output 'CONTENT_POLICY=PASS'
Write-Output "DISABLED=$((@($actions | Where-Object disabledByPolicy | ForEach-Object { $_.package }) -join ','))"
Write-Output "SNAPSHOT=$snapshotPath"
