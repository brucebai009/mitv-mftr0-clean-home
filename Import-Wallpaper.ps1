param(
    [Parameter(Mandatory=$true)][string]$Serial,
    [Parameter(Mandatory=$true)][string]$ImagePath,
    [string]$AdbPath = 'adb',
    [string]$StateRoot = (Join-Path $PSScriptRoot 'tvhome-state')
)

$ErrorActionPreference = 'Stop'
$MaxBytes = 10MB
$source = (Get-Item -LiteralPath $ImagePath -ErrorAction Stop)
if (-not $source.PSIsContainer -and $source.Length -gt 0) { } else { throw 'ImagePath must point to a non-empty file.' }
$extension = [IO.Path]::GetExtension($source.Name).TrimStart('.').ToLowerInvariant()
if ($extension -notin @('jpg', 'jpeg', 'png', 'webp')) { throw 'Only JPEG, PNG and static WebP files are supported.' }
if ($source.Length -gt $MaxBytes) { throw 'Image file is larger than 10 MB.' }
if ([string]::IsNullOrWhiteSpace($Serial)) { throw 'Serial is required; no device address is built in.' }
if ($Serial -notmatch '^[A-Za-z0-9._:-]+$') { throw 'Serial contains unsupported characters.' }

function Invoke-AdbText {
    param([Parameter(Mandatory=$true)][string[]]$Arguments)
    $start = New-Object System.Diagnostics.ProcessStartInfo
    $start.FileName = $AdbPath
    $start.Arguments = ((@('-s', $Serial) + $Arguments) | ForEach-Object { Quote-ProcessArgument $_ }) -join ' '
    $start.UseShellExecute = $false
    $start.CreateNoWindow = $true
    $start.RedirectStandardOutput = $true
    $start.RedirectStandardError = $true
    $process = New-Object System.Diagnostics.Process
    $process.StartInfo = $start
    if (-not $process.Start()) { throw 'Could not start adb.' }
    try {
        $stdout = $process.StandardOutput.ReadToEnd()
        $stderr = $process.StandardError.ReadToEnd()
        $process.WaitForExit()
        $output = ($stdout + $stderr).Trim()
        if ($process.ExitCode -ne 0 -or $output -match '(?im)(^|\s)(error|failure):') {
            throw "ADB failed ($($process.ExitCode)): $($Arguments -join ' ')`n$output"
        }
        return $output
    } finally { $process.Dispose() }
}

function Quote-ProcessArgument([string]$Value) {
    return '"' + $Value.Replace('"', '\"') + '"'
}

function Invoke-AdbToFile([string]$OutputFile, [string[]]$Arguments) {
    $start = New-Object System.Diagnostics.ProcessStartInfo
    $start.FileName = $AdbPath
    $start.Arguments = (($Arguments | ForEach-Object { Quote-ProcessArgument $_ }) -join ' ')
    $start.UseShellExecute = $false
    $start.CreateNoWindow = $true
    $start.RedirectStandardOutput = $true
    $start.RedirectStandardError = $true
    $process = New-Object System.Diagnostics.Process
    $process.StartInfo = $start
    if (-not $process.Start()) { throw 'Could not start adb.' }
    try {
        $outputStream = [IO.File]::Create($OutputFile)
        try { $process.StandardOutput.BaseStream.CopyTo($outputStream) } finally { $outputStream.Dispose() }
        $stderr = $process.StandardError.ReadToEnd()
        $process.WaitForExit()
        if ($process.ExitCode -ne 0 -or $stderr -match '(?im)(^|\s)(error|failure):') {
            throw "ADB binary output failed ($($process.ExitCode)): $stderr"
        }
    } finally { $process.Dispose() }
}

New-Item -ItemType Directory -Force -Path $StateRoot | Out-Null
$hash = (Get-FileHash -LiteralPath $source.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
$safeName = "wallpaper-$($hash.Substring(0,16)).$extension"
$remoteTemp = "/data/local/tmp/tvhome-$($hash.Substring(0,16)).$extension"
$uri = "content://com.gathin.tvhome.theme/wallpapers/$safeName"
$localInput = Join-Path $StateRoot "$safeName.input"
$localReadback = Join-Path $StateRoot "$safeName.readback"
try {
    Copy-Item -LiteralPath $source.FullName -Destination $localInput -Force
    Invoke-AdbText @('push', $localInput, $remoteTemp) | Out-Null
    Invoke-AdbText @('shell', 'ls', '-l', $remoteTemp) | Out-Null
    # Use only the generated private copy on the device; the user path never enters a shell command.
    # The Android 11 content CLI requires the redirection to be parsed by the device shell.
    $remoteCommand = "content write --uri '$uri' < '$remoteTemp'"
    Invoke-AdbText @('shell', $remoteCommand) | Out-Null
    Invoke-AdbToFile $localReadback @('-s', $Serial, 'exec-out', 'content', 'read', '--uri', $uri)
    $readbackHash = (Get-FileHash -LiteralPath $localReadback -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($readbackHash -ne $hash) { throw 'Provider readback hash does not match the source.' }
    Invoke-AdbText @('shell', 'rm', '-f', $remoteTemp) | Out-Null
    Write-Output "Imported $safeName"
    Write-Output "TVHome will list it under Desktop settings > Background picture."
} finally {
    Remove-Item -LiteralPath $localInput -Force -ErrorAction SilentlyContinue
    Remove-Item -LiteralPath $localReadback -Force -ErrorAction SilentlyContinue
    try { Invoke-AdbText @('shell', 'rm', '-f', $remoteTemp) | Out-Null } catch { }
}
