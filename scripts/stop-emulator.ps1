$ErrorActionPreference = 'Stop'
$WorkspaceRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$SdkRoot = Join-Path $WorkspaceRoot 'android-sdk'
$env:ANDROID_HOME = $SdkRoot
$env:ANDROID_SDK_ROOT = $SdkRoot
$env:ANDROID_AVD_HOME = Join-Path $WorkspaceRoot 'android-avd'
$Adb = Join-Path $SdkRoot 'platform-tools\adb.exe'
$Devices = & $Adb devices | Select-String '^emulator-\d+\s+device$'
if ($Devices) { & $Adb -s ($Devices.Line -split '\s+')[0] emu kill; Write-Output 'Sent graceful emulator shutdown.' } else { Write-Output 'No running emulator found.' }
