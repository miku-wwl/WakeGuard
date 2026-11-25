# Windows setup

Round 2 reused the D-drive toolchain. The Android Studio 2026.2.2 Patch 2 installation provides JDK 25.0.4. The SDK contains API 36, Build Tools 36.0.0, ADB 37.0.3 and Emulator 37.3.10. The repository pins Gradle 9.9.2 and its distribution digest.

```powershell
. '.\scripts\set-local-environment.ps1'
.\scripts\start-emulator.ps1
.\gradlew.bat assembleDebug assembleDebugAndroidTest
adb -s emulator-5554 install -r app\build\outputs\apk\debug\app-x86_64-debug.apk
```

The environment script keeps tool, SDK, AVD, Gradle and subprocess temp paths scoped to the project shell. The Pixel 7 AVD uses the API 36 Google APIs x86_64 system image with WHPX, four virtual CPUs and 4 GB guest RAM. Use `-Window` to show it.

The emulator camera can use a licensed image from the D-drive workspace. Changing its input requires stopping and starting the AVD. Use a fresh evidence directory for each run.

```powershell
.\scripts\stop-emulator.ps1
.\scripts\start-emulator.ps1 -CameraImage 'D:\workshop\nov\cache\vision-data\round2\commons\wm-119921527.jpg' -EvidenceDirectory 'D:\workshop\nov\reports\round2\verification-run'
```

Use `-NoBackCamera` to exercise the unavailable-camera path. Grant exact-alarm, notification and full-screen access before a scheduled test. The project tests Android on the emulator. Model research uses the existing CPU Python environment in `D:\workshop\nov\tools\vision-venv`.
