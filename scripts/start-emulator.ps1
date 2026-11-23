param([switch]$Window)
$ErrorActionPreference = 'Stop'
$WorkspaceRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$SdkRoot = Join-Path $WorkspaceRoot 'android-sdk'
$env:ANDROID_HOME = $SdkRoot
$env:ANDROID_SDK_ROOT = $SdkRoot
$env:ANDROID_AVD_HOME = Join-Path $WorkspaceRoot 'android-avd'
$env:ANDROID_USER_HOME = Join-Path $WorkspaceRoot 'android-user'
$env:TEMP = Join-Path $WorkspaceRoot 'cache\temp'
$env:TMP = $env:TEMP
$Emulator = Join-Path $SdkRoot 'emulator\emulator.exe'
$LogRoot = Join-Path $WorkspaceRoot 'reports'
$OutLog = Join-Path $LogRoot 'emulator.stdout.log'
$ErrLog = Join-Path $LogRoot 'emulator.stderr.log'
$Existing = & (Join-Path $SdkRoot 'platform-tools\adb.exe') devices | Select-String '^emulator-\d+\s+device$'
if ($Existing) { Write-Output "Emulator already running: $($Existing.Line)"; exit 0 }
$Args = @('-avd','WakeGuard_API36','-no-snapshot','-no-boot-anim','-gpu','auto','-camera-back','emulated','-camera-front','emulated')
if (-not $Window) { $Args += '-no-window' }
$StartParams = @{ FilePath = $Emulator; ArgumentList = $Args; PassThru = $true; RedirectStandardOutput = $OutLog; RedirectStandardError = $ErrLog }
if (-not $Window) { $StartParams.WindowStyle = 'Hidden' }
$Process = Start-Process @StartParams
Set-Content -LiteralPath (Join-Path $LogRoot 'emulator.pid') -Value $Process.Id -Encoding ascii
Write-Output "Started WakeGuard_API36 (PID $($Process.Id)); waiting for Android boot."
for ($i=0; $i -lt 180; $i++) {
  $Ready = & (Join-Path $SdkRoot 'platform-tools\adb.exe') shell getprop sys.boot_completed 2>$null
  if ($Ready -match '1') { Write-Output 'Android boot completed.'; exit 0 }
  Start-Sleep -Seconds 2
}
throw 'Timed out waiting for Android boot. Check reports/emulator.stderr.log.'
