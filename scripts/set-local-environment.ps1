$ErrorActionPreference = 'Stop'

$WakeGuardRepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$WakeGuardWorkspaceRoot = (Resolve-Path (Join-Path $WakeGuardRepoRoot '..')).Path

$env:ANDROID_HOME = Join-Path $WakeGuardWorkspaceRoot 'android-sdk'
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$env:ANDROID_AVD_HOME = Join-Path $WakeGuardWorkspaceRoot 'android-avd'
$env:ANDROID_USER_HOME = Join-Path $WakeGuardWorkspaceRoot 'android-user'
$env:GRADLE_USER_HOME = Join-Path $WakeGuardWorkspaceRoot 'cache\gradle'
$env:JAVA_HOME = Join-Path $WakeGuardWorkspaceRoot 'tools\android-studio\android-studio\jbr'
$env:STUDIO_JDK = $env:JAVA_HOME
$env:STUDIO_PROPERTIES = Join-Path $WakeGuardRepoRoot 'tools\android-studio\studio.properties'
$env:TEMP = Join-Path $WakeGuardWorkspaceRoot 'cache\temp'
$env:TMP = $env:TEMP

$WakeGuardToolPaths = @(
    (Join-Path $env:JAVA_HOME 'bin'),
    (Join-Path $env:ANDROID_HOME 'platform-tools'),
    (Join-Path $env:ANDROID_HOME 'cmdline-tools\latest\bin'),
    (Join-Path $env:ANDROID_HOME 'emulator')
)
$env:Path = ($WakeGuardToolPaths + $env:Path) -join ';'

foreach ($WakeGuardDirectory in @($env:GRADLE_USER_HOME, $env:TEMP)) {
    if (-not (Test-Path -LiteralPath $WakeGuardDirectory)) {
        New-Item -ItemType Directory -Path $WakeGuardDirectory -Force | Out-Null
    }
}

Write-Output "Loaded WakeGuard local environment from $WakeGuardWorkspaceRoot."
