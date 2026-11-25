# Android platform limits

- Exact alarm access is required on Android 12 and higher. An enabled alarm with no confirmed registration is shown as unscheduled. Android can cancel exact alarms when access is revoked.
- Notification permission and full-screen intent access are separate. The foreground service still owns the session when Android limits the notification or full-screen UI.
- Android may recreate a sticky service after ordinary process death. It recovers only a persisted active event. A user force-stop, shutdown or system volume change remains authoritative. Reopening the app with an abandoned event and no service records a system interruption.
- Boot reschedules repeats and records expired one-time alarms as missed. Clock and timezone changes recompute wall times. A time in a daylight-saving gap moves forward by the gap. A repeated wall time uses its earlier instant once that date.
- Emulator audio and vibration state establish Android service behavior. Physical audibility, haptics, camera focus and OEM power behavior require separate evidence.
- The supported image-file camera supplies frames through CameraX on the AVD. Static fixture input can exercise that path without proving user presence or real-world liveness.
