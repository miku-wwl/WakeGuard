# WakeGuard Round 4 Android and OEM Compatibility

Research date: 2026-11-25. The attached phone's Android, OriginOS and firmware versions are unknown.

## Android platform behavior

- [`android:stopWithTask`](https://developer.android.com/guide/topics/manifest/service-element) defaults to `false`. WakeGuard declares it explicitly, but it does not prevent process death or override OEM policy.
- [`Service.onTaskRemoved()`](https://developer.android.com/reference/android/app/Service#onTaskRemoved(android.content.Intent)) reports removal of a running service's task when the stop-with-task flag is not set. It is not a persistence guarantee.
- `START_STICKY` requests recreation after some process deaths but does not guarantee timing or universal restart. Background foreground-service starts are restricted on Android 12+; exact alarms for user-requested actions and user-visible actions have documented exceptions ([restrictions](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start)).
- Android 13+ foreground-service Task Manager Stop intentionally stops the app/media and is distinct from Recents swipe; scheduled alarms may still be delivered ([user-initiated stopping](https://developer.android.com/develop/background-work/services/fgs/handle-user-stopping)). WakeGuard does not defeat this system control or Settings Force Stop.
- [`ApplicationExitInfo`](https://developer.android.com/reference/android/app/ApplicationExitInfo) is available from API 30. `REASON_USER_REQUESTED` can cover both Recents removal and Settings Force Stop; it cannot distinguish them. Exit history is system-maintained and bounded ([ActivityManager history](https://developer.android.com/reference/android/app/ActivityManager#getHistoricalProcessExitReasons(java.lang.String,int,int))).
- [`AlarmManager.setAlarmClock()`](https://developer.android.com/reference/android/app/AlarmManager#setAlarmClock(android.app.AlarmManager.AlarmClockInfo,android.app.PendingIntent)) registers an exact alarm-clock event. Exact schedules can be deleted when `SCHEDULE_EXACT_ALARM` access is revoked; WakeGuard must check permission and restore registration when permission returns ([alarm guide](https://developer.android.com/develop/background-work/services/alarms)).

## vivo/iQOO guidance and limits

The [vivo support article](https://kefu.vivo.com.cn/robot/imgmsgData/2616a9cd7cd64a5083a264d16e5767da/index_1.html) and [another general support article](https://kefu.vivo.com.cn/robot/imgmsgData/3dfcdb616d0542b4baa4e9ead6d75c5e/index_1.html) describe background launch/power controls and optional locking of a Recents card for some devices. They are generic and dated; they do not establish the controls or behavior on the Neo9 Pro's unknown firmware. A [vivo community explanation](https://bbs.vivo.com.cn/newbbs/thread/35223773) is community guidance, not a platform guarantee.

WakeGuard can query supported Android exact-alarm, notification, full-screen intent, battery-optimization and background-restriction state. Public Android APIs do not expose proprietary OriginOS autostart, background high-power or Recents-lock switches. The UI therefore reports those as **需用户手动检查** and does not use hidden intents or change settings.

## Physical test order

1. Record model, Android/OriginOS/firmware and relevant Android permission states.
2. Reproduce TC01 and TC02 with the phone's current/default settings before changing OEM controls.
3. Preserve a timestamp for each swipe and capture scoped service, audio, alarm, Room and exit-info evidence.
4. Only then compare owner-approved autostart/background high-power controls, recording the actual menu labels and setting state.
5. Treat Recents-card locking as an optional diagnostic/configuration comparison, never as the only way to pass ordinary task-removal acceptance.

Do not require WakeGuard to survive Android Task Manager Stop, Settings Force Stop, uninstall, shutdown or denied platform recovery. Keep the emergency stop available.
