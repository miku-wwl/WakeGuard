# Round 2 summary

Round 2's release baseline is commit [`2486dc75deb83e79aef203b62915fb9d597783ba`](https://github.com/miku-wwl/WakeGuard/commit/2486dc75deb83e79aef203b62915fb9d597783ba). On the existing Windows API 36 x86_64 AVD it reproduced 15 JVM tests and 19 functional Android tests, with a roughly 56 MB x86_64 debug APK and a signed ARM64 build.

The [Round 2 GitHub Actions run](https://github.com/miku-wwl/WakeGuard/actions/runs/45521306944) passed its build and functional emulator jobs; its historical real-vision gate failed as recorded below.

The round improved exact alarm occurrence tokens, Room persistence, event-scoped playback ownership, foreground-service recovery, fallback audio, camera crop/rotation handling, and model lifecycle. Ordinary process recovery, reboot schedule restoration, and explicit emergency cleanup had emulator evidence. Its strict historical complete-scene detector gate still accepted 0/4 historical positives and 0/2 independent positives; the separate 33-incomplete-scene holdout accepted 0/33. Its actual scheduled CameraX workflow did not reach `VERIFIED`; tested episodes were explicitly recorded as `EMERGENCY_STOP`. No public release was created.

The Round 2 product acceptance rules asked for high-precision recognition and at least 30 independent positive and negative scenes. In Round 3 the product owner explicitly revised those rules for a personal anti-snooze use case. The old metrics, datasets, local reports and diagnostic remain unchanged; the historical model gate is now non-blocking, and the new, enforceable gate measures the revised policy.

Round 2's full local reports remain under `reports/round2` in the development workspace. The Round 3 source review noted an inconsistent `INTERRUPTED`/`MISSED` observation in prior emulator evidence; Round 3 rechecked the transition code and orderly reboot restoration and records the remaining evidence limit in its acceptance report.
