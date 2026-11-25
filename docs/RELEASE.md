# Build and release

Round 3 targets `v0.1.0-alpha.3` (`versionName=0.1.0-alpha.3`, `versionCode=3`). This is a personal-use experimental alpha, not a production-certified Android alarm. It uses debug signing and is not intended for Google Play.

Build both ABI APKs and verify them with Android Build Tools:

```powershell
. '.\scripts\set-local-environment.ps1'
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest --no-daemon
$apksigner = Join-Path $env:ANDROID_HOME 'build-tools\36.0.0\apksigner.bat'
& $apksigner verify --verbose app\build\outputs\apk\debug\app-x86_64-debug.apk
& $apksigner verify --verbose app\build\outputs\apk\debug\app-arm64-v8a-debug.apk
Get-FileHash releases\round3\WakeGuard-0.1.0-alpha.3-x86_64.apk -Algorithm SHA256
Get-FileHash releases\round3\WakeGuard-0.1.0-alpha.3-arm64-v8a.apk -Algorithm SHA256
```

The x86_64 build is runtime-tested on the API 36 Google APIs Pixel 7 emulator. The ARM64-v8a package is build- and signature-checked only; no compatible ARM64 runtime or physical phone was used.

Publish the GitHub prerelease only after the exact release implementation commit has green JVM/lint/build/signing, functional emulator, and replacement real-model policy gates; verified scheduled CameraX-to-`VERIFIED` completion; successful playback, notification and wake-lock cleanup; independently verified emergency stop; and no unresolved critical alarm defect. The historical Round 2 strict vision gate is retained as a non-blocking diagnostic because the product owner explicitly revised the product criteria. Permanent acceptance evidence records the commit, workflow URL, hashes, and known limitations. If a mandatory gate fails, the APKs remain experimental local builds and no GitHub Release is created.
