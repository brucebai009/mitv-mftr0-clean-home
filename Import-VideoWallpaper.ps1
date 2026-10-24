param(
    [Parameter(Mandatory=$true)][string]$Serial,
    [Parameter(Mandatory=$true)][string]$VideoPath,
    [string]$Title = 'Dynamic wallpaper',
    [string]$Author = 'Visualdon',
    [string]$PosterPath,
    [switch]$WriteCatalog,
    [ValidateSet('com.gathin.tvhome','com.ts.launcher')][string]$PackageName = 'com.gathin.tvhome',
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
if ($Title.Length -gt 80) { throw 'Title is too long.' }

$poster = $null
if ($PosterPath) {
    $poster = Get-Item -LiteralPath $PosterPath -ErrorAction Stop
    if ($poster.PSIsContainer -or $poster.Length -le 0 -or $poster.Length -gt 10MB) { throw 'PosterPath must be a non-empty image below 10 MiB.' }
    if ([IO.Path]::GetExtension($poster.Name).ToLowerInvariant() -notin @('.jpg', '.jpeg', '.png', '.webp')) {
        throw 'PosterPath must be JPEG, PNG or WebP.'
    }
}

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

function Write-ProviderFile {
    param(
        [Parameter(Mandatory=$true)][string]$LocalPath,
        [Parameter(Mandatory=$true)][string]$RemoteTemp,
        [Parameter(Mandatory=$true)][string]$Uri,
        [Parameter(Mandatory=$true)][string]$ExpectedHash,
        [Parameter(Mandatory=$true)][string]$ReadbackPath
    )
    $localInput = Join-Path $StateRoot ([IO.Path]::GetFileName($RemoteTemp) + '.input')
    try {
        Copy-Item -LiteralPath $LocalPath -Destination $localInput -Force
        Invoke-AdbText @('push', $localInput, $RemoteTemp) | Out-Null
        $remoteCommand = "content write --uri '$Uri' < '$RemoteTemp'"
        Invoke-AdbText @('shell', $remoteCommand) | Out-Null
        Invoke-AdbToFile $ReadbackPath @('exec-out', 'content', 'read', '--uri', $Uri)
        $actual = (Get-FileHash -LiteralPath $ReadbackPath -Algorithm SHA256).Hash.ToLowerInvariant()
        if ($actual -ne $ExpectedHash) { throw "Provider readback hash does not match $Uri." }
    } finally {
        Remove-Item -LiteralPath $localInput -Force -ErrorAction SilentlyContinue
        try { Invoke-AdbText @('shell', 'rm', '-f', $RemoteTemp) | Out-Null } catch { }
    }
}

New-Item -ItemType Directory -Force -Path $StateRoot | Out-Null
$hash = (Get-FileHash -LiteralPath $source.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
$safeName = "video-wallpaper-$($hash.Substring(0,16)).mp4"
$remoteTemp = "/data/local/tmp/tvhome-$($hash.Substring(0,16)).mp4"
$uri = "content://${PackageName}.theme/videos/$safeName"
$localReadback = Join-Path $StateRoot "$safeName.readback"
$posterSafeName = $null
$posterReadback = $null
try {
    Write-ProviderFile -LocalPath $source.FullName -RemoteTemp $remoteTemp -Uri $uri -ExpectedHash $hash -ReadbackPath $localReadback
    if ($poster) {
        $posterHash = (Get-FileHash -LiteralPath $poster.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
        $posterExt = [IO.Path]::GetExtension($poster.Name).ToLowerInvariant()
        $posterSafeName = "wallpaper-poster-$($hash.Substring(0,16))$posterExt"
        $posterTemp = "/data/local/tmp/tvhome-poster-$($hash.Substring(0,16))$posterExt"
        $posterUri = "content://${PackageName}.theme/wallpapers/$posterSafeName"
        $posterReadback = Join-Path $StateRoot "$posterSafeName.readback"
        Write-ProviderFile -LocalPath $poster.FullName -RemoteTemp $posterTemp -Uri $posterUri -ExpectedHash $posterHash -ReadbackPath $posterReadback
    }
    Write-Output "Imported $safeName ($([int]$stream.width)x$([int]$stream.height), $([math]::Round($duration,1))s, $([math]::Round($fps,2))fps)"
    if ($posterSafeName) { Write-Output "Imported poster $posterSafeName for '$Title'." }
    if ($WriteCatalog) {
        $catalogLocal = Join-Path $StateRoot 'wallpaper_catalog.json'
        $catalogReadback = Join-Path $StateRoot 'wallpaper_catalog.readback.json'
        $catalogItems = @()
        try {
            Invoke-AdbToFile $catalogReadback @('exec-out', 'content', 'read', '--uri', "content://${PackageName}.theme/wallpaper_catalog.json")
            $existing = Get-Content -LiteralPath $catalogReadback -Raw | ConvertFrom-Json
            if ([int]$existing.schemaVersion -eq 1 -and $existing.items) { $catalogItems = @($existing.items) }
        } catch { $catalogItems = @() }
        if (-not $catalogItems) {
            $catalogItems = @([ordered]@{
                id = 'default'; title = 'Default background'; type = 'default'; relativeFile = ''
                posterRelativeFile = ''; sha256 = ''; width = 0; height = 0; fps = 0
                durationMs = 0; author = ''; variants = @()
            })
        }
        $catalogItems = @($catalogItems | Where-Object { [string]$_.id -ne "video-$($hash.Substring(0,16))" })
        $catalogItems += [ordered]@{
            id = "video-$($hash.Substring(0,16))"; title = $Title; type = 'video'
            relativeFile = "videos/$safeName"; posterRelativeFile = $(if ($posterSafeName) { "wallpapers/$posterSafeName" } else { '' })
            sha256 = $hash; width = [int]$stream.width; height = [int]$stream.height
            fps = [int][math]::Round($fps); durationMs = [long][math]::Round($duration * 1000)
            author = $Author; variants = @()
        }
        [ordered]@{ schemaVersion = 1; items = $catalogItems } | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $catalogLocal -Encoding UTF8
        $catalogHash = (Get-FileHash -LiteralPath $catalogLocal -Algorithm SHA256).Hash.ToLowerInvariant()
        Write-ProviderFile -LocalPath $catalogLocal -RemoteTemp "/data/local/tmp/tvhome-catalog-$($hash.Substring(0,16)).json" -Uri "content://${PackageName}.theme/wallpaper_catalog.json" -ExpectedHash $catalogHash -ReadbackPath (Join-Path $StateRoot 'wallpaper_catalog.verify.json')
        Write-Output 'Updated wallpaper catalog with the imported candidate.'
    }
    Write-Output 'Open TVHome settings and choose the imported video background to activate it.'
} finally {
    Remove-Item -LiteralPath $localReadback -Force -ErrorAction SilentlyContinue
    if ($posterReadback) { Remove-Item -LiteralPath $posterReadback -Force -ErrorAction SilentlyContinue }
    Remove-Item -LiteralPath (Join-Path $StateRoot 'wallpaper_catalog.json') -Force -ErrorAction SilentlyContinue
    Remove-Item -LiteralPath (Join-Path $StateRoot 'wallpaper_catalog.readback.json') -Force -ErrorAction SilentlyContinue
    Remove-Item -LiteralPath (Join-Path $StateRoot 'wallpaper_catalog.verify.json') -Force -ErrorAction SilentlyContinue
}
