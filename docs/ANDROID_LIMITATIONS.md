# Android platform limits

- Exact alarm access is required on Android 12 and later. Android can cancel schedules if access is revoked; WakeGuard reports the alarm as unscheduled until registration is accepted.
- Notification permission, exact alarm access and full-screen intent access are separate user/system controls. Android may limit lock-screen presentation while the foreground service continues.
- A sticky foreground service recovered the same Room event after ordinary app-process death on the API 36 AVD. Android can still stop services under platform policy. User force-stop, shutdown, permission revocation and system controls remain authoritative.
- An orderly Android reboot was tested on the API 36 AVD: the same future due time was re-registered with a new token. A host-side abrupt AVD termination previously produced a different MISSED/INTERRUPTED observation; recent-write durability through abrupt emulator power loss remains unresolved.
- Emulator screen-off, audio routing, vibration and image-file camera behavior are not proof of physical-device audibility, haptics, camera framing or OEM battery policy. No physical Android hardware was tested.
- Round 3's virtual image-file camera supplied fresh frames through production CameraX analysis and produced a real `VERIFIED` Room event. Captured emulator preview screenshots were black beneath the notification shade; physical preview rendering and CameraX framing still need on-device validation.
- Android's system Back/Home, recents, permissions, Doze, and force-stop behaviors cannot be completely overridden by an ordinary alarm app. Emergency stop is available to the user at all times.
