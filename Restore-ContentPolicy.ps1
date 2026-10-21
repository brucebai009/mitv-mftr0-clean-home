param(
    [Parameter(Mandatory=$true)][string]$Serial,
    [string]$AdbPath = 'adb',
    [Parameter(Mandatory=$true)][string]$SnapshotPath
)

$ErrorActionPreference = 'Stop'
function Invoke-AdbText {
    param([Parameter(Mandatory=$true)][string[]]$Arguments)
    $output = (& $AdbPath '-s' $Serial @Arguments 2>&1 | Out-String).Trim()
    if ($LASTEXITCODE -ne 0 -or $output -match '(?im)(^|\s)(error|failure):') {
        throw "ADB failed: $($Arguments -join ' ')`n$output"
    }
    return $output
}

$snapshot = Get-Content -LiteralPath (Resolve-Path -LiteralPath $SnapshotPath -ErrorAction Stop) -Raw -Encoding UTF8 | ConvertFrom-Json
if ($snapshot.schema -ne 1 -or $snapshot.policy -ne 'independent-content-components') { throw 'Unsupported content policy snapshot.' }
if ((Invoke-AdbText @('get-serialno')).Trim() -ne [string]$snapshot.deviceSerial) { throw 'Snapshot belongs to a different device.' }
foreach ($action in @($snapshot.actions)) {
    if ($action.disabledByPolicy -and -not $action.wasDisabled -and $action.installed) {
        Invoke-AdbText @('shell', 'pm', 'enable', '--user', '0', [string]$action.package) | Out-Null
    }
}
Write-Output 'CONTENT_POLICY_RESTORE=PASS'
Write-Output "SNAPSHOT=$SnapshotPath"
