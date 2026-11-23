# Build and release

The workflow in `.github/workflows/android.yml` runs unit tests, compiles Android instrumentation tests, runs Android lint, and builds a debug APK, then uploads the APK as a CI artifact. Instrumentation executes locally on the API 36 emulator.

For local testing:

```powershell
. '.\scripts\set-local-environment.ps1'
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
.\gradlew.bat connectedDebugAndroidTest
Get-FileHash app\build\outputs\apk\debug\app-debug.apk -Algorithm SHA256
```

`v0.1.0-alpha.1` is a debug-signed prerelease, never a production-signed APK. Publish only after the alarm trigger, camera/inference, emergency stop, screen-off/background behavior, automated tests, lint, CI, and model/license evidence all pass. If a required gate is partial or blocked, keep the APK in the local `releases` directory and report the outstanding evidence. Attach the APK, its SHA-256 file, release notes, known limitations, and exact test status. Do not publish to Google Play.
