# Round 3 handover

## Delivered

WakeGuard is now a personal anti-snooze alarm with lightweight offline camera verification. The selected production evaluator is sink ≥0.25 plus toothbrush ≥0.25 or toothpaste ≥0.40 across at least two fresh, relevant CameraX frames in a 12-second single-event session. The old strict three-object model test remains as a named CI diagnostic; a separate CI gate enforces the revised positive and obvious-negative acceptance conditions.

The API 36 x86_64 AVD produced three real scheduled CameraX-to-`VERIFIED` Room events and one scheduled unrelated-scene `EMERGENCY_STOP`. Back, Home, task removal, ordinary process death, screen-off, exact schedule restoration after orderly reboot, notification/audio/vibration/wake-lock cleanup, and exactly-once statistics were checked. The product requirement change, historical comparison, test matrix, metrics and known limitations are in this directory.

## Reproduce locally

```powershell
. '.\scripts\set-local-environment.ps1'
.\scripts\start-emulator.ps1
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest --no-daemon
.\gradlew.bat connectedDebugAndroidTest --no-daemon '-Pandroid.testInstrumentationRunnerArguments.notAnnotation=androidx.test.filters.LargeTest'
.\gradlew.bat connectedDebugAndroidTest --no-daemon '-Pandroid.testInstrumentationRunnerArguments.class=com.mikuwwl.wakeguard.RealVisionAcceptanceTest'
```

For the scheduled local-camera walkthrough, use the scoped harness in `tools/round3/emulator_e2e.py`; it refuses physical devices, records Room and system-state evidence, and uses a confirmed emergency cleanup if the model does not verify. Fixture attribution is in `app/src/androidTest/assets/vision/round3/ATTRIBUTION.md`.

## Implementation and release

- Base: Round 2 `2486dc75deb83e79aef203b62915fb9d597783ba`.
- Implementation SHA: `55a4f1837ce816ddfdc777072774018e8aab6a97`.
- GitHub Actions: [run 45545722112](https://github.com/miku-wwl/WakeGuard/actions/runs/45545722112), final attempt 2 passed the mandatory gates.
- Published prerelease: [v0.1.0-alpha.3](https://github.com/miku-wwl/WakeGuard/releases/tag/v0.1.0-alpha.3); tag points to the tested implementation commit and all assets were download-checked.
- x86_64 runtime is tested on API 36 emulator. ARM64-v8a is build/signature checked only. Physical phones are untested.
- Local user artifacts and raw evidence stay under `D:\workshop\nov\reports\round3` and `D:\workshop\nov\releases\round3`; they are not all committed. Sanitized permanent evidence and screenshots are committed here.

## Remaining limits

See [known limitations](ROUND3_KNOWN_LIMITATIONS.md). Key items are 4/6 labeled positives, 1/2 independent positives, 3/33 older incomplete-scene accepts, AVD-only runtime, black virtual-camera preview screenshots, and the inconclusive old abrupt-stop snapshot discrepancy. This handover does not claim production certification or physical-device acceptance.
