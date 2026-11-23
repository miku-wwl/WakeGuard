# Windows setup

The verified local installation used for this project is under `D:\workshop\nov`:

- Android Studio Stable 2026.2.2 Patch 2 and bundled JDK 25.0.4.
- Android SDK API 36, Build Tools 36.0.0, Platform Tools 37.0.3, Emulator 37.3.10.
- Pixel 7 profile AVD `WakeGuard_API36`, Google APIs x86_64, Android 16, 4 GB guest RAM.
- Gradle Wrapper 9.9.2 with the official distribution SHA-256 pinned in `gradle/wrapper/gradle-wrapper.properties`.
- `ANDROID_HOME`, `ANDROID_AVD_HOME`, `ANDROID_USER_HOME`, `GRADLE_USER_HOME`, Java, and temporary paths are set for the current PowerShell process by `D:\workshop\nov\set-wakeguard-env.ps1`. Global Windows environment values are not changed.

## Build

```powershell
. 'D:\workshop\nov\set-wakeguard-env.ps1'
.\gradlew.bat --version
.\gradlew.bat assembleDebug
.\gradlew.bat testDebugUnitTest
.\gradlew.bat lintDebug
```

## Emulator

```powershell
D:\workshop\nov\WakeGuard\scripts\start-emulator.ps1
.\gradlew.bat connectedDebugAndroidTest
D:\workshop\nov\WakeGuard\scripts\stop-emulator.ps1
```

The emulator uses WHPX. Use `-Window` with the start script if visual interaction is needed. AVD disk images, SDK packages, Gradle cache, and downloads remain on D:. Emulator evidence cannot verify physical speaker output, OEM battery policies, or a physical camera.

## Install manually

```powershell
adb install -r app\build\outputs\apk\debug\app-debug.apk
adb shell monkey -p com.mikuwwl.wakeguard 1
```

The first alarm will require Android's exact-alarm access. Notification permission is needed on Android 13+, full-screen access may be separately gated on Android 14+, and camera permission is requested when verification opens.
