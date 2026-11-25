# Test plan

```powershell
. '.\scripts\set-local-environment.ps1'
.\scripts\start-emulator.ps1
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
.\gradlew.bat connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.notAnnotation=androidx.test.filters.LargeTest'
.\gradlew.bat connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.mikuwwl.wakeguard.RealVisionAcceptanceTest'
```

Functional coverage includes DST boundaries, persistence and Room migration, exact delivery and cleanup, stale and duplicate tokens, overlapping alarms, fallback audio, image layout, stale frame rejection, model failures and camera disposal. The dependency-injected success path checks the evaluator. The production-model gate measures the bundled models on genuine images and asserts both positive and negative behavior.

`tools\round2\android_dataset.py` verifies photo digests and passes licensed photos to a test-only instrumentation bundle. The production app accepts frames from CameraX. Run `score_scenes.py` against the frozen dataset manifest to recreate class presence and grouped scene metrics.

For the integrated test, start the AVD with the image-file camera, install x86_64, and grant Android access. The walkthrough creates a real scheduled alarm in the UI, puts the display to sleep, opens CameraX and records Home and sleep/wake behavior. It captures the persisted result after live inference or a confirmed emergency stop.

```powershell
python tools\round2\emulator_e2e.py --evidence 'D:\workshop\nov\reports\round2\emulator-walkthrough'
```

CI separates the functional and real-model checks and uploads test XML, logs, scores, APKs and hashes. See the Round 2 acceptance report for the executed results.
