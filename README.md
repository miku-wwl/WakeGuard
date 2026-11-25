# WakeGuard

WakeGuard is **a personal anti-snooze alarm with lightweight offline camera verification**. It schedules Android exact alarms, keeps alarm playback owned by a foreground service, and asks the user to approach a washing area before successful dismissal. The camera pipeline runs on-device; it does not save or upload frames.

Round 3 revises the product acceptance criteria: this is behavioral friction for personal use, not identity verification or a high-precision vision system. The alpha uses sink-anchored evidence accumulated across two fresh CameraX frames in a 12-second session. See [the policy measurements](docs/AI_MODEL.md), [test plan](docs/TEST_PLAN.md), and [release status](docs/RELEASE.md).

## Use the app

1. Open **闹钟**, add a time, select repeat days, and save. Leave all days unselected for a one-time alarm.
2. Grant exact-alarm access from **设置**. Android provides notification and full-screen intent access separately. An enabled alarm without a confirmed registration shows **尚未排程**.
3. During ringing, use **开始现场验证** and point the camera toward the sink or dental-care items. Sink and toothbrush or toothpaste evidence must arrive in two fresh frames in the same camera session. Back returns to the ringing screen; Home and app switching leave the alarm active. **紧急停止** is always available and records an unverified outcome.

## Build and test on the existing Windows workspace

```powershell
. '.\scripts\set-local-environment.ps1'
.\scripts\start-emulator.ps1
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
.\gradlew.bat connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.notAnnotation=androidx.test.filters.LargeTest'
.\gradlew.bat connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.mikuwwl.wakeguard.RealVisionAcceptanceTest'
```

The API 36 x86_64 AVD is the tested runtime. The ARM64-v8a APK is built and signature-checked for later device testing; no physical Android device has been tested. Review [acceptance evidence](docs/acceptance/ROUND3_FINAL_ACCEPTANCE_REPORT.md) and [known limitations](docs/KNOWN_ISSUES.md).

## Project documents

- [Architecture](docs/ARCHITECTURE.md)
- [Offline vision policy](docs/AI_MODEL.md)
- [Android platform limits](docs/ANDROID_LIMITATIONS.md)
- [Test plan](docs/TEST_PLAN.md)
- [Known issues](docs/KNOWN_ISSUES.md)
- [Build and release](docs/RELEASE.md)
- [Round 2 historical summary](docs/acceptance/ROUND2_SUMMARY.md)
- [Round 3 acceptance matrix](docs/acceptance/ROUND3_TEST_MATRIX.md)

Source code is MIT licensed. Model and fixture provenance is in [Third-party notices](THIRD_PARTY_NOTICES.md).
