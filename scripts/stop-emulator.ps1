param([ValidateRange(5554, 5682)][int]$Port = 5554)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'set-local-environment.ps1')
$target = "emulator-$Port"
$adb = Join-Path $env:ANDROID_HOME 'platform-tools\adb.exe'
if (-not (& $adb devices | Where-Object { $_ -match ('^' + $target + '\s+device\b') })) {
    Write-Output "WakeGuard_API36 is not connected on $target."
    return
}
$name = & $adb -s $target emu avd name | Select-Object -First 1
if ($name -ne 'WakeGuard_API36') { throw "Refusing to stop a different AVD on $target." }
& $adb -s $target emu kill
if ($LASTEXITCODE -ne 0) { throw 'Emulator shutdown command failed.' }
