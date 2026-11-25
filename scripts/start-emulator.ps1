[CmdletBinding(DefaultParameterSetName='Default')]
param(
    [switch]$Window,
    [Parameter(ParameterSetName='Image', Mandatory)]
    [string]$CameraImage,
    [Parameter(ParameterSetName='NoCamera', Mandatory)]
    [switch]$NoBackCamera,
    [ValidateRange(5554, 5682)][int]$Port = 5554,
    [string]$EvidenceDirectory,
    [switch]$SoftwareGraphics
)
$ErrorActionPreference = 'Stop'
$WorkspaceRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
. (Join-Path $PSScriptRoot 'set-local-environment.ps1')
if (($Port % 2) -ne 0) { throw 'Use an even emulator console port.' }
$Target = "emulator-$Port"
$Adb = Join-Path $env:ANDROID_HOME 'platform-tools\adb.exe'
$Emulator = Join-Path $env:ANDROID_HOME 'emulator\emulator.exe'
$LogRoot = if ($EvidenceDirectory) { $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($EvidenceDirectory) } else { Join-Path $WorkspaceRoot 'reports\runtime' }
if (-not $LogRoot.StartsWith($WorkspaceRoot + '\', [StringComparison]::OrdinalIgnoreCase)) {
    throw 'Store WakeGuard emulator evidence under the D-drive workspace.'
}
New-Item -ItemType Directory -Force -Path $LogRoot | Out-Null
$connected = & $Adb devices | Where-Object { $_ -match ('^' + $Target + '\s') }
if ($connected) {
    $name = & $Adb -s $Target emu avd name 2>$null | Select-Object -First 1
    if ($name -ne 'WakeGuard_API36') { throw "Port $Port is already in use by another emulator." }
    if ($CameraImage -or $NoBackCamera) { throw 'Stop this AVD before changing the back camera source.' }
    Write-Output "WakeGuard_API36 already connected on $Target."
    return
}

$camera = 'emulated'
if ($CameraImage) {
    $image = (Resolve-Path -LiteralPath $CameraImage).Path
    if (-not $image.StartsWith($WorkspaceRoot + '\', [StringComparison]::OrdinalIgnoreCase)) {
        throw 'Keep test camera input files in the D-drive workspace.'
    }
    $camera = '"imagefile:' + $image + '"'
}
if ($NoBackCamera) { $camera = 'none' }
$gpu = if ($SoftwareGraphics) { 'swiftshader' } else { 'auto' }
$arguments = @('-avd','WakeGuard_API36','-port',"$Port",'-no-snapshot','-no-boot-anim','-gpu',$gpu,
    '-camera-back',$camera,'-camera-front','none')
if (-not $Window) { $arguments += '-no-window' }
$parameters = @{
    FilePath = $Emulator
    ArgumentList = $arguments
    PassThru = $true
    RedirectStandardOutput = Join-Path $LogRoot 'emulator.stdout.log'
    RedirectStandardError = Join-Path $LogRoot 'emulator.stderr.log'
}
if (-not $Window) { $parameters.WindowStyle = 'Hidden' }
$process = Start-Process @parameters
$process.Id | Set-Content -LiteralPath (Join-Path $LogRoot 'emulator.pid')
Write-Output "Started WakeGuard_API36 on $Target (PID $($process.Id)) with back camera $camera."
for ($attempt = 0; $attempt -lt 120; $attempt++) {
    $ready = & $Adb -s $Target shell getprop sys.boot_completed 2>$null
    if ($ready -eq '1') {
        Write-Output 'Android boot completed.'
        return
    }
    if ($process.HasExited) { break }
    Start-Sleep -Seconds 2
}
throw "Android did not boot. See $LogRoot."
