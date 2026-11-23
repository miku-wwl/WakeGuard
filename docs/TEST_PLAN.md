# Test plan

Run checks on the local Android Emulator only; no physical-device testing is permitted for this release.

## Automated checks

- `testDebugUnitTest`: one-time/repeating schedule boundaries, weekday rollover, timezone-local time, threshold boundaries, consecutive-frame reset, missing-object failure, and non-finite scores.
- `connectedDebugAndroidTest`: app startup, create/edit/delete and persist alarms in Room, idempotent verified/emergency event transitions, live camera preview and model score rows, plus a dependency-injected camera check that three identical valid frames complete verification.
- `lintDebug` and `assembleDebug`: Android static analysis and installable debug APK.
- Vision model evaluation: actual bundled ONNX and MediaPipe inference over the pinned MIMEX product test split, four licensed all-three bathroom photos, one additional bathroom/toothbrush scene with uncertain toothpaste visibility, and two incomplete object combinations. The report includes held-out product-image confusion counts and actual scene scores. None of the four complete natural scenes passes the production thresholds; this is recorded as an acceptance limitation, not hidden by the injected evaluator test.

## Emulator scenarios

Executed on 2026-11-23 with the local API 36 AVD:

1. Granted exact-alarm and notification permissions in the emulator. The one-time alarm appeared in `dumpsys alarm` as an exact `RTC_WAKEUP` for `com.mikuwwl.wakeguard.action.TRIGGER`.
2. The 18:18 alarm triggered, opened the lock-screen alarm UI, posted ongoing notification ID 4101, and started `AlarmForegroundService` as a foreground service.
3. At 18:19, `adb shell am force-stop com.mikuwwl.wakeguard` stopped the app process and foreground service and removed its active notification. Reopening displayed the alarm list. Statistics retained a `系统中断` event for 18:18. This is the Android force-stop boundary; there is no claim that WakeGuard prevents it.
4. The 18:23 alarm also triggered. Pressing Home moved the launcher to the foreground while `AlarmForegroundService` remained foreground and notification 4101 remained active. Reopening `MainActivity` restored the active alarm screen.
5. `adb shell input keyevent 223` placed the emulator in `Asleep`; during sleep the foreground service and ongoing notification remained present. `adb shell input keyevent 224` woke it and the alarm UI returned. `KEYCODE_POWER` (26) did not switch this alarm window to `Asleep` in this AVD, so the explicit sleep/wake key events were used for the documented state check.
6. Camera permission was requested and granted through the app UI. The live emulator camera preview produced `(sink=0%, toothbrush=0%, toothpaste=87%)` against thresholds `(45%, 45%, 93%)`; it correctly remained unverified. This synthetic AVD camera frame is not bathroom-scene accuracy evidence.
7. Confirming **紧急停止** stopped the alarm and produced one `紧急停止 · 未验证` statistics record; successful-verification count remained zero. The earlier force-stop remains separately listed as `系统中断`.
8. Licensed positive and incomplete photos were run through the real local models. The actual per-image scores and positive-scene miss are recorded in [AI model notes](AI_MODEL.md). The injected camera test separately proves the three-frame state machine, not model accuracy.

The exact commands, output, and any scenarios that could not be completed are captured in `D:\workshop\nov\reports` and the final acceptance report.
