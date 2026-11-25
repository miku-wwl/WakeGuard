# Build and release

## Round 4B experimental prerelease

`v0.1.0-alpha.4-rc.1` is an owner-authorized experimental distribution for manual iQOO Neo9 Pro testing. It uses `versionCode=4`, `versionName=0.1.0-alpha.4-rc.1`, and the existing debug signing certificate. It is **not** final alpha.4 acceptance and must remain labeled **EXPERIMENTAL — NOT PHYSICALLY VALIDATED**. Record every CI gate as observed; do not imply that the strict Recents gate passed.

The final `v0.1.0-alpha.4` gate remains blocked until the owner-reported recent-task swipe defect passes physical acceptance on the documented default or owner-approved OriginOS configuration, with the remaining mandatory gates satisfied. See [manual iQOO test guide](acceptance/ROUND4_MANUAL_IQOO_TEST_GUIDE.md), [Round 4 final acceptance](acceptance/ROUND4_FINAL_ACCEPTANCE_REPORT.md), and [Round 4B delivery record](acceptance/ROUND4B_EXPERIMENTAL_DELIVERY.md).

## Current final-release gate (Round 4)

The last historically validated prerelease is v0.1.0-alpha.3. Round 4's local v0.1.0-alpha.4 candidates and the separately identified alpha.4-rc.1 manual-test candidate are not a final alpha.4 release. Do not publish final alpha.4 until the owner-reported iQOO Neo9 Pro recent-task swipe defect passes ordinary task-removal acceptance under a documented default or owner-approved OEM configuration. See [Round 4 final acceptance](acceptance/ROUND4_FINAL_ACCEPTANCE_REPORT.md) and [test matrix](acceptance/ROUND4_TEST_MATRIX.md).

## Round 3 published baseline

Round 3 published `v0.1.0-alpha.3` (`versionName=0.1.0-alpha.3`, `versionCode=3`). This is a personal-use experimental alpha, not a production-certified Android alarm. It uses debug signing and is not intended for Google Play.

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
