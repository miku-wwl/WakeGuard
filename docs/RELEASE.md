# Build and release gates

Round 2 prepares local experimental APKs. Public GitHub Releases and Google Play are outside this round.

```powershell
. '.\scripts\set-local-environment.ps1'
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
Get-FileHash app\build\outputs\apk\debug\app-x86_64-debug.apk -Algorithm SHA256
Get-FileHash app\build\outputs\apk\debug\app-arm64-v8a-debug.apk -Algorithm SHA256
```

The debug APKs have version name `0.1.0-alpha.2` and version code 2. The x86_64 build is for the emulator. The ARM64 package is build- and signature-checked. Verify both with Android Build Tools `apksigner`. CI saves temporary build and test artifacts with SHA-256 checksums.

The production model gate requires at least 90% of the four historical complete fixtures to pass three actual inferences and rejects both incomplete fixtures and invalid outputs. Failure makes the real-vision gate red. The historical gate is one condition for alpha acceptance. Independent acceptance also requires:

- Functional alarm, camera and lifecycle tests with no critical unresolved defects.
- At least 30 independently grouped complete and 30 incomplete real holdout scenes. Positive acceptance must be at least 90%. The negative set must cover each missing-object category with zero observed false accepts.
- A genuine live CameraX flow that stops playback and records one verified Room event.
- Reviewed model and dataset provenance, checksums, signing and acceptance evidence.

Local output is under `D:\workshop\nov\releases\round2` and `D:\workshop\nov\reports\round2`.
