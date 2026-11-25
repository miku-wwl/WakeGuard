# Test plan

## Local Windows/API 36

Use the existing SDK, Gradle cache and `WakeGuard_API36` Pixel 7 x86_64 AVD:

```powershell
. '.\scripts\set-local-environment.ps1'
.\scripts\start-emulator.ps1
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest --no-daemon
.\gradlew.bat connectedDebugAndroidTest --no-daemon '-Pandroid.testInstrumentationRunnerArguments.notAnnotation=androidx.test.filters.LargeTest'
.\gradlew.bat connectedDebugAndroidTest --no-daemon '-Pandroid.testInstrumentationRunnerArguments.class=com.mikuwwl.wakeguard.RealVisionAcceptanceTest'
.\gradlew.bat connectedDebugAndroidTest --no-daemon '-Pandroid.testInstrumentationRunnerArguments.class=com.mikuwwl.wakeguard.HistoricalStrictVisionDiagnosticTest'
```

The functional suite covers alarm persistence and migrations, scheduled and duplicate delivery, event ownership, terminal idempotence, Back handling, exact time and reboot restoration, camera permission and lifecycle, stale frames and model errors. The real vision gate runs the bundled model against four historical positive fixtures, two independent positive fixtures, and five unrelated fixtures. The historical strict test is diagnostic only; the production policy gate remains mandatory.

## Scheduled CameraX acceptance

For a genuine live test, start the AVD with an authorized local image source through the emulator's virtual back camera. Do not pass fixture scores to the app. Create a future one-time alarm in the UI, put the AVD screen to sleep, wait for exact delivery, verify the foreground service and ongoing notification, then open CameraX. Record model frames, the same Room event ID and its terminal outcome, statistic delta, audio/service/notification cleanup, and screenshots.

The Round 3 harness is:

```powershell
. '.\scripts\set-local-environment.ps1'
python -X utf8 tools\round3\emulator_e2e.py --evidence 'D:\workshop\nov\reports\round3\e2e-positive' --repeat 3
```

Use `--restart-process-first --remove-task-first` on the API 36 AVD to capture ordinary process recovery and Recents removal. Those flags run each disruptive action only on the first repetition. The harness rejects a physical device, checks active Room outcome, and issues an emergency stop if a live verification times out.

The real vision test is separate from injected-engine UI tests: its fixture scores come from actual bundled Android models. The scheduled CameraX evidence additionally proves a live `ImageProxy` model result through the production path. Static fixtures do not prove user presence or real-world camera liveness.

## CI

GitHub Actions runs JVM tests, lint, ABI APK build and signing verification, API 36 functional instrumentation, and the mandatory lightweight model gate. The Round 2 strict test remains a named diagnostic. XML, model logs, screenshots on failure, artifacts, and checksums are uploaded for review. The permanent acceptance report records the final workflow URL and commit SHA.
