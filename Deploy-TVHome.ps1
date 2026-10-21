param(
    [Parameter(Mandatory=$true)][string]$Serial,
    [string]$AdbPath = 'adb',
    [Parameter(Mandatory=$true)][string]$ApkPath,
    [string]$ManifestPath = (Join-Path $PSScriptRoot 'release-manifest.json'),
    [string]$StateRoot = (Join-Path $PSScriptRoot 'tvhome-state'),
    [string]$ExpectedModel = 'MiTV-MFTR0',
    [string]$OriginalSnapshotPath,
    [switch]$ActivateHome
)

$ErrorActionPreference = 'Stop'
$installer = Join-Path $PSScriptRoot 'Install-TVHome.ps1'
if (-not (Test-Path -LiteralPath $installer -PathType Leaf)) { throw "Installer not found: $installer" }
if (-not (Test-Path -LiteralPath $ApkPath -PathType Leaf)) { throw "APK not found: $ApkPath" }
if (-not (Test-Path -LiteralPath $ManifestPath -PathType Leaf)) { throw "Manifest not found: $ManifestPath" }

$arguments = @{
    Serial = $Serial
    AdbPath = $AdbPath
    ApkPath = $ApkPath
    ManifestPath = $ManifestPath
    StateRoot = $StateRoot
    ExpectedModel = $ExpectedModel
}
if ($OriginalSnapshotPath) { $arguments.OriginalSnapshotPath = $OriginalSnapshotPath }
if ($ActivateHome) { $arguments.ActivateHome = $true }
& $installer @arguments
if ($LASTEXITCODE -ne 0) { throw "Install-TVHome.ps1 failed with exit code $LASTEXITCODE" }
