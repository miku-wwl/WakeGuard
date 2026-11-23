# Test plan

Run checks on the local Android Emulator only; no physical-device testing is permitted for this release.

## Automated checks

- `testDebugUnitTest`: one-time/repeating schedule boundaries, weekday rollover, timezone-local time, threshold boundaries, consecutive-frame reset, missing-object failure, and non-finite scores.
- `connectedDebugAndroidTest`: app startup, create/edit/delete and persist alarms in Room, idempotent verified/emergency event transitions, live camera preview and model score rows, plus a dependency-injected camera check that three identical valid frames complete verification.
- `lintDebug` and `assembleDebug`: Android static analysis and installable debug APK.
- Vision model evaluation: actual ONNX classifier inference over the pinned MIMEX official test split, one licensed all-three bathroom scene, and two incomplete fixtures. Report includes the held-out threshold-selection split, product-image confusion counts, and per-scene scores; the single scene image is a smoke test, not a broad accuracy estimate.

## Emulator scenarios

1. Grant exact-alarm and notification permissions.
2. Create a near-future one-time alarm and confirm it appears in `dumpsys alarm`.
3. Observe receiver/service logs and the ongoing notification at trigger time.
4. Press the emulator power key while ringing; check display state, service, and active audio-player state; wake the emulator and return to the event.
5. Background with Home and reopen from the notification.
6. Deny camera access and verify the task remains active with an emergency stop path.
7. Run licensed positive/negative fixtures through the real local model and evaluator; never use a test flag to turn an inference into success.
8. Use Android force-stop and document that Android stops the service and blocks automatic restart.

The exact commands, output, and any scenarios that could not be completed are captured in `D:\workshop\nov\reports` and the final acceptance report.
