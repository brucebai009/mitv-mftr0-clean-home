param(
    [Parameter(Mandatory=$true)][string]$Serial,
    [Parameter(Mandatory=$true)][string]$VideoPath,
    [string]$AdbPath = 'adb',
    [string]$FfprobePath = 'ffprobe',
    [string]$StateRoot = (Join-Path $PSScriptRoot 'tvhome-state')
)

$ErrorActionPreference = 'Stop'
$source = Get-Item -LiteralPath $VideoPath -ErrorAction Stop
if ($source.PSIsContainer -or $source.Length -le 0) { throw 'VideoPath must point to a non-empty file.' }
if ([IO.Path]::GetExtension($source.Name).ToLowerInvariant() -ne '.mp4') { throw 'Only MP4 files are supported.' }
if ($source.Length -gt 100MB) { throw 'Video file is larger than 100 MiB.' }
if ($Serial -notmatch '^[A-Za-z0-9._:-]+$') { throw 'Serial contains unsupported characters.' }

function Read-Probe {
    $probeArguments = @('-v', 'error', '-select_streams', 'v:0',
        '-show_entries', 'stream=codec_name,width,height,r_frame_rate,color_transfer,duration:format=duration',
        '-of', 'json', '--', $source.FullName)
    $json = (& $FfprobePath @probeArguments 2>&1 | Out-String).Trim()
    if ($LASTEXITCODE -ne 0) { throw "ffprobe failed: $json" }
    return $json | ConvertFrom-Json
}

$probe = Read-Probe
$stream = @($probe.streams)[0]
if (-not $stream) { throw 'No video stream found.' }
$durationText = if ($stream.duration) { [string]$stream.duration } else { [string]$probe.format.duration }
$duration = [double]::Parse($durationText, [Globalization.CultureInfo]::InvariantCulture)
$rate = [string]$stream.r_frame_rate
$parts = $rate -split '/'
$fps = if ($parts.Count -eq 2 -and [double]$parts[1] -ne 0) { [double]$parts[0] / [double]$parts[1] } else { [double]$rate }
$transfer = [string]$stream.color_transfer
if ([string]$stream.codec_name -ne 'h264') { throw 'Video codec is not H.264.' }
if ([int]$stream.width -gt 1920 -or [int]$stream.height -gt 1080) { throw 'Video exceeds 1080p.' }
if ($duration -le 0 -or $duration -gt 120) { throw 'Video duration must be between 0 and 120 seconds.' }
if ($fps -le 0 -or $fps -gt 30) { throw 'Video frame rate must be at most 30 fps.' }
if ($transfer -in @('smpte2084', 'arib-std-b67')) { throw 'HDR video is not accepted; use SDR.' }

function Invoke-AdbText {
    param([Parameter(Mandatory=$true)][string[]]$Arguments)
    $output = (& $AdbPath '-s' $Serial @Arguments 2>&1 | Out-String).Trim()
    if ($LASTEXITCODE -ne 0 -or $output -match '(?im)(^|\s)(error|failure):') {
        throw "ADB failed ($LASTEXITCODE): $($Arguments -join ' ')`n$output"
    }
    return $output
}

function Invoke-AdbToFile([string]$OutputFile, [string[]]$Arguments) {
    $start = New-Object System.Diagnostics.ProcessStartInfo
    $start.FileName = $AdbPath
    $start.Arguments = ((@('-s', $Serial) + $Arguments) | ForEach-Object { '"' + $_.Replace('"', '\"') + '"' }) -join ' '
    $start.UseShellExecute = $false
    $start.CreateNoWindow = $true
    $start.RedirectStandardOutput = $true
    $start.RedirectStandardError = $true
    $process = New-Object System.Diagnostics.Process
    $process.StartInfo = $start
    if (-not $process.Start()) { throw 'Could not start adb.' }
    try {
        $streamOut = [IO.File]::Create($OutputFile)
        try { $process.StandardOutput.BaseStream.CopyTo($streamOut) } finally { $streamOut.Dispose() }
        $stderr = $process.StandardError.ReadToEnd()
        $process.WaitForExit()
        if ($process.ExitCode -ne 0 -or $stderr -match '(?im)(^|\s)(error|failure):') { throw "ADB readback failed: $stderr" }
    } finally { $process.Dispose() }
}

New-Item -ItemType Directory -Force -Path $StateRoot | Out-Null
$hash = (Get-FileHash -LiteralPath $source.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
$safeName = "video-wallpaper-$($hash.Substring(0,16)).mp4"
$remoteTemp = "/data/local/tmp/tvhome-$($hash.Substring(0,16)).mp4"
$uri = "content://com.gathin.tvhome.theme/videos/$safeName"
$localInput = Join-Path $StateRoot "$safeName.input"
$localReadback = Join-Path $StateRoot "$safeName.readback"
try {
    Copy-Item -LiteralPath $source.FullName -Destination $localInput -Force
    Invoke-AdbText @('push', $localInput, $remoteTemp) | Out-Null
    $remoteCommand = "content write --uri '$uri' < '$remoteTemp'"
    Invoke-AdbText @('shell', $remoteCommand) | Out-Null
    Invoke-AdbToFile $localReadback @('exec-out', 'content', 'read', '--uri', $uri)
    $readbackHash = (Get-FileHash -LiteralPath $localReadback -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($readbackHash -ne $hash) { throw 'Provider readback hash does not match the source.' }
    Invoke-AdbText @('shell', 'rm', '-f', $remoteTemp) | Out-Null
    Write-Output "Imported $safeName ($([int]$stream.width)x$([int]$stream.height), $([math]::Round($duration,1))s, $([math]::Round($fps,2))fps)"
    Write-Output 'Open TVHome settings and choose the imported video background to activate it.'
} finally {
    Remove-Item -LiteralPath $localInput -Force -ErrorAction SilentlyContinue
    Remove-Item -LiteralPath $localReadback -Force -ErrorAction SilentlyContinue
    try { Invoke-AdbText @('shell', 'rm', '-f', $remoteTemp) | Out-Null } catch { }
}
