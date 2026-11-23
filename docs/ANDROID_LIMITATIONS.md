# Android platform limits

- Exact alarm scheduling is gated by special app access on Android 12+. Without it, saved alarms remain visible but cannot be scheduled exactly; Settings offers the system access page.
- Android 13+ notification permission and Android 14+ full-screen-intent access can be denied. An ongoing notification may remain available without a full-screen launch.
- Alarm playback runs as a `mediaPlayback` foreground service. Android may still apply platform or manufacturer-specific restrictions.
- Home and screen-off do not count as verification. The service is designed to continue while backgrounded, and the ongoing notification returns to the active task.
- The OS owns force-stop, shutdown, reboot, volume controls, and security dialogs. WakeGuard does not claim to block them. A reboot closes any active event as interrupted and reschedules enabled alarms.
- AVD tests cannot prove physical speaker audibility, exact OEM lock-screen behavior, battery optimization behavior, or real camera focus/exposure.
- Local tests use a Pixel-class Android 16 x86_64 emulator and do not imply certification for every Android release or manufacturer skin.
