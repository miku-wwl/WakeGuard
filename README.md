# WakeGuard

WakeGuard is an offline Android alarm prototype written in Kotlin and Jetpack Compose. It schedules exact alarms, rings through ordinary backgrounding and uses live camera input to look for a bathroom sink, toothbrush and toothpaste. Wake history stays in Room on the device.

Round 2 is an **experimental build**. It adds event-scoped playback and recovery, persisted schedule registration, and a lifecycle-aware camera pipeline. The historical positive model gate still fails. The independent holdout contains two complete scenes, below the 30 required for alpha acceptance. See [model evaluation](docs/AI_MODEL.md) and [release criteria](docs/RELEASE.md).

## Use the app

1. Open **闹钟**, add a time, select repeat days and save. Leave all days unselected for a one-time alarm.
2. Grant exact-alarm access from **设置**. Android provides notification and full-screen access separately. An enabled alarm with no registration shows **尚未排程**.
3. During ringing, select **开始现场验证** and keep the three objects in view. The app requires three consecutive valid frames. **紧急停止** is available throughout the alarm and records an unverified outcome after confirmation.

Camera inference stays on the device. The app does not save camera frames.

## Build and test

```powershell
. '.\scripts\set-local-environment.ps1'
.\scripts\start-emulator.ps1
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
.\gradlew.bat connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.notAnnotation=androidx.test.filters.LargeTest'
```

Use the x86_64 APK for the provisioned emulator. The ARM64 artifact was built for future testing. See [Windows setup](docs/SETUP.md) and [test plan](docs/TEST_PLAN.md).

## Emulator evidence

Round 2 reports and raw logs are under `D:\workshop\nov\reports\round2`. The [emulator screenshots](docs/screenshots) show the app in use. The alpha gate still requires a successful camera-to-dismissal recording with the bundled model.

See [architecture](docs/ARCHITECTURE.md), [Android platform limits](docs/ANDROID_LIMITATIONS.md) and [known issues](docs/KNOWN_ISSUES.md).

## License

Source code is MIT licensed. Model, runtime and fixture provenance are in [Third-party notices](THIRD_PARTY_NOTICES.md).
