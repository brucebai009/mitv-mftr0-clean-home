param(
    [Parameter(Mandatory = $true)]
    [string]$Serial,
    [string]$Adb = 'adb'
)

$ErrorActionPreference = 'Stop'
& $Adb -s $Serial shell am force-stop com.gathin.tvhome
& $Adb -s $Serial shell pm enable --user 0 com.mitv.tvhome
& $Adb -s $Serial shell cmd package set-home-activity --user 0 com.mitv.tvhome/.MainActivityUserMode
& $Adb -s $Serial shell input keyevent 3
& $Adb -s $Serial shell am start -n com.mitv.tvhome/.MainActivityUserMode
if ($LASTEXITCODE -ne 0) {
    throw "Could not start the original HOME on $Serial"
}
Write-Output "Original HOME requested on $Serial. Verify com.mitv.tvhome/.MainActivityUserMode on the TV."
