param(
    [Parameter(Mandatory = $true)][string]$DeviceSerial,
    [string]$EvidenceDirectory = 'D:\workshop\nov\reports\round4\iqoo-raw'
)

$ErrorActionPreference = 'Stop'
$PackageName = 'com.mikuwwl.wakeguard'
$AdbPath = (Get-Command adb -ErrorAction Stop).Source
$DeviceList = & $AdbPath devices -l
$DeviceLine = $DeviceList | Where-Object { $_ -match "^$([regex]::Escape($DeviceSerial))\s+device\s" } | Select-Object -First 1
if (-not $DeviceLine) { throw "ADB serial '$DeviceSerial' is not attached and authorized. Approve Android's USB debugging prompt first." }

function Invoke-DeviceAdb([string[]]$Arguments) {
    & $AdbPath -s $DeviceSerial @Arguments 2>&1
}

$Qemu = (Invoke-DeviceAdb @('shell', 'getprop', 'ro.kernel.qemu') | Out-String).Trim()
if ($Qemu -eq '1') { throw 'This collector is for the physical iQOO only; refusing an emulator serial.' }

New-Item -ItemType Directory -Force -Path $EvidenceDirectory | Out-Null
$MetadataPath = Join-Path $EvidenceDirectory 'device-metadata.txt'
$Metadata = @(
    "collected_at_local=$(Get-Date -Format o)"
    "serial=$DeviceSerial (local evidence only; redact before sharing)"
    "manufacturer=$((Invoke-DeviceAdb @('shell','getprop','ro.product.manufacturer') | Out-String).Trim())"
    "model=$((Invoke-DeviceAdb @('shell','getprop','ro.product.model') | Out-String).Trim())"
    "android_release=$((Invoke-DeviceAdb @('shell','getprop','ro.build.version.release') | Out-String).Trim())"
    "sdk=$((Invoke-DeviceAdb @('shell','getprop','ro.build.version.sdk') | Out-String).Trim())"
    "build_id=$((Invoke-DeviceAdb @('shell','getprop','ro.build.display.id') | Out-String).Trim())"
    "fingerprint=$((Invoke-DeviceAdb @('shell','getprop','ro.build.fingerprint') | Out-String).Trim())"
    "wakeguard_pid=$((Invoke-DeviceAdb @('shell','pidof',$PackageName) | Out-String).Trim())"
)
$Metadata | Set-Content -LiteralPath $MetadataPath -Encoding utf8

function Save-ScopedOutput([string]$Name, [string[]]$Arguments) {
    Invoke-DeviceAdb $Arguments | Set-Content -LiteralPath (Join-Path $EvidenceDirectory $Name) -Encoding utf8
}

Save-ScopedOutput 'package.txt' @('shell','dumpsys','package',$PackageName)
Save-ScopedOutput 'appops.txt' @('shell','cmd','appops','get',$PackageName)
Save-ScopedOutput 'services.txt' @('shell','dumpsys','activity','services',$PackageName)
Save-ScopedOutput 'exit-info.txt' @('shell','dumpsys','activity','exit-info',$PackageName)
Save-ScopedOutput 'wakeguard-logcat.txt' @('logcat','-d','-s','WakeGuardAlarm:I','WakeGuardDiagnostics:I','WakeGuardRestore:I')

$AlarmDump = Invoke-DeviceAdb @('shell','dumpsys','alarm') | Out-String
$AlarmDump -split "`r?`n" | Where-Object { $_ -match [regex]::Escape($PackageName) } |
    Set-Content -LiteralPath (Join-Path $EvidenceDirectory 'alarm-manager-wakeguard-lines.txt') -Encoding utf8
$AudioDump = Invoke-DeviceAdb @('shell','dumpsys','audio') | Out-String
$AudioDump -split "`r?`n" | Where-Object { $_ -match 'WakeGuard|com\.mikuwwl\.wakeguard' } |
    Set-Content -LiteralPath (Join-Path $EvidenceDirectory 'audio-wakeguard-lines.txt') -Encoding utf8
$PowerDump = Invoke-DeviceAdb @('shell','dumpsys','power') | Out-String
$PowerDump -split "`r?`n" | Where-Object { $_ -match 'WakeGuard|com\.mikuwwl\.wakeguard' } |
    Set-Content -LiteralPath (Join-Path $EvidenceDirectory 'power-wakeguard-lines.txt') -Encoding utf8

$Sql = 'SELECT id,alarmId,triggeredAt,verificationStartedAt,finishedAt,outcome FROM wake_events ORDER BY id DESC LIMIT 8;'
Invoke-DeviceAdb @('shell', "run-as $PackageName sqlite3 -json databases/wakeguard.db '$Sql'") |
    Set-Content -LiteralPath (Join-Path $EvidenceDirectory 'recent-room-events.json') -Encoding utf8
Invoke-DeviceAdb @('shell','run-as',$PackageName,'cat','files/wakeguard-diagnostics.jsonl') |
    Set-Content -LiteralPath (Join-Path $EvidenceDirectory 'wakeguard-diagnostics.jsonl') -Encoding utf8

Write-Output "Saved scoped WakeGuard diagnostics to $EvidenceDirectory. No install, reboot, permission change, or OEM setting change was performed."
