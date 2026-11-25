# WakeGuard Round 4 Final Acceptance Report

Date: 2026-11-25 (Pacific/Auckland)

## 1. Executive summary

**Verdict: BLOCKED — PHYSICAL ACCEPTANCE UNAVAILABLE.** Round 4 implements Android-compliant service and state-reconciliation changes, bounded local lifecycle diagnostics, and an explicit user-resume path. JVM, lint, build, API 36 instrumentation, the production-model fixture gate, and a dedicated API 36 Recents-removal harness passed. The owner-reported iQOO Neo9 Pro failure could not be reproduced or cleared because the phone was not connected. Alpha.4 is therefore a local experimental candidate only; alpha.3 remains the last published prerelease.

## 2. Owner-reported physical defect

`WG-R4-P0-001 — Recent-task swipe terminates active alarm on iQOO Neo9 Pro` is owner-reported behavior. The reported scenario is active alarm audio stopping after an upward Recents swipe. No physical trace or device firmware details were available in this run; the report is not represented as an independently reproduced event.

## 3. Round 3 baseline

The repository began on `main` at `504578e5672086403f8cc44a667c3c3e2ba43014`; the prior tested implementation is `55a4f1837ce816ddfdc777072774018e8aab6a97`. Round 3's standard API 36 AVD Recents test passed, as did its scheduled CameraX-to-VERIFIED tests and CI. Those results remain intact and are not treated as evidence of OriginOS compatibility. The existing alpha.3 artifacts and release were preserved.

## 4. Android and OriginOS behavior

Android documents `android:stopWithTask` as defaulting to false. `Service.onTaskRemoved()` is an observation callback, not a process-survival guarantee. `START_STICKY` does not promise universal or immediate recreation. Android 13+ Task Manager Stop is distinct from a Recents swipe and intentionally stops foreground media. Android's `ApplicationExitInfo.REASON_USER_REQUESTED` can represent either Recents removal or Settings Force Stop, so it is ambiguous. Exact-alarm access can be revoked and registrations then need to be restored after access returns.

Generic vivo support material describes autostart, background power allowance and optional Recents-card locking, but it is dated and does not establish the menus or behavior on the owner's Neo9 Pro firmware. The app reports those proprietary settings as **需用户手动检查**. Sources and limits are listed in [ROUND4_OEM_COMPATIBILITY.md](ROUND4_OEM_COMPATIBILITY.md).

## 5. Physical device environment

The only attached test target was the existing API 36 x86_64 `WakeGuard_API36` AVD. No physical iQOO serial was present. Android version, SDK, OriginOS version, firmware, installed app state, permission state, OEM battery settings, and physical swipe timestamp therefore remain unknown.

## 6. Root cause investigation

**Physical root cause: NOT CONFIRMED.** No iQOO PID, service, audio, Room, alarm registration, Logcat, or exit-history data was collected.

**Confirmed source vulnerability:** `AlarmCoordinator.restore(FOREGROUND)` terminalized an active Room event as `INTERRUPTED` when a foreground-service lookup temporarily returned false. That transition could race service recreation and block same-event recovery. This code behavior is confirmed; whether it caused the owner's audio stop is unknown. The source review and raw emulator observations are retained locally under `D:\workshop\nov\reports\round4`.

## 7. Confirmed findings

- Playback resources are event-scoped and owned by `AlarmForegroundService`, not the Activity.
- The prior manifest relied on the Android default for `stopWithTask`; omission alone did not establish the physical failure cause.
- A standard API 36 AVD delivered a scheduled alarm after its task was removed.
- Three active-service Recents removals preserved the same event and foreground service on that AVD; emergency stop ended that event without counting verification.
- Ordinary sticky recovery and same-event state checks passed in the scheduled AVD flow before its live camera scene failed to qualify.
- The default AVD camera scene did not qualify within the scheduled flow's 35-second window. The harness emergency-stopped the event; this is a scenario limitation, not a model-gate pass.

## 8. Implemented remediation

- Declared `android:stopWithTask="false"` explicitly and added a non-restarting `onTaskRemoved()` diagnostic callback.
- Kept audio, vibration and WakeLock ownership in the foreground service.
- Preserved active Room events across temporary foreground-service lookup misses; boot interruption remains terminal and exactly-once.
- Added a visible, same-event **继续响铃** action when the persisted event remains active but the service cannot be confirmed. Terminal events cannot be resumed.
- Added local bounded diagnostics and system process-exit metadata.
- Added live Android reliability status and transparent manual OriginOS checks.
- Set candidate version to `0.1.0-alpha.4` / versionCode 4.

No vision model, inference runtime, fixture, threshold or 12-second policy window changed.

## 9. OEM configuration requirements

No OEM switch was changed or required by emulator acceptance. On the iQOO, first capture a baseline with its current settings. Then record available autostart, background high-power, battery restriction and optional Recents-lock settings. Do not claim native compatibility if a later pass depends on owner-approved configuration.

## 10. Before/after device behavior

There is no physical before/after comparison. On the standard AVD, the pre-delivery task-removal scenario delivered the scheduled event afterward; during active playback, three Recents swipes retained the same `RINGING` event and service. This demonstrates standard AVD behavior only and does not close the reported iQOO defect.

## 11. Automated regression

- JVM: **19/19 passed**.
- API 36 functional instrumentation: **25/25 passed**, 0 failures, 0 errors, 0 skipped.
- Android lint: **0 errors, 32 warnings, 4 hints**.
- Debug and instrumentation APK builds: passed.
- Production model gate: **4/6** labeled positives, **1/2** independent positives, **0/5** obvious unrelated scenes accepted; the existing gate and policy remained unchanged.
- Dedicated task-removal harness: **PASS** for future delivery after task removal and three active ringing swipes, with same event/service; emergency stop recorded `EMERGENCY_STOP`, service absent, verified count unchanged.
- Scheduled full CameraX flow with task removal and ordinary process recovery: **PARTIAL**. Earlier lifecycle steps passed, but the AVD's selected live scene did not qualify within 35 seconds; emergency cleanup passed.
- A Round 4 camera-screen-off rerun did not observe `Asleep` after its AVD key events. Round 3 has separate prior AVD screen-off evidence; neither result establishes physical screen-off behavior.

See [ROUND4_TEST_MATRIX.md](ROUND4_TEST_MATRIX.md) and local raw evidence under `D:\workshop\nov\reports\round4`.

## 12. Physical device acceptance

**NOT_TESTED.** TC01–TC10 on iQOO remain untested; TC10 also requires separate owner approval before a reboot. The physical device gate is the outstanding release blocker.

## 13. Safety and emergency stop

The app retains the explicit confirmation-gated emergency stop. Local AVD and instrumentation evidence recorded `EMERGENCY_STOP`, did not increment verified statistics, removed the service/ongoing notification, and released playback resources. No watchdog loop, privileged persistence, system Stop override, force-stop bypass, kiosk mode, or reboot was added.

## 14. GitHub CI

CI result for the Round 4 implementation commit: **to be recorded after the push**. CI must pass `verify`, `emulator`, and the unchanged `real-vision-gate`; it does not include or substitute for physical iQOO acceptance. Detailed local evidence will be retained in `D:\workshop\nov\reports\round4\09_CI_ACCEPTANCE.md`.

## 15. Acceptance matrix

See [ROUND4_TEST_MATRIX.md](ROUND4_TEST_MATRIX.md). The critical physical swipe-removal criteria remain blocked or untested regardless of the emulator score.

## 16. APK and signing evidence

Local experimental artifacts were created under `D:\workshop\nov\releases\round4`. Both ABIs report versionCode 4, versionName `0.1.0-alpha.4`, target SDK 36 and pass APK v2 signature verification. The alpha.4 certificate SHA-256 matches both locally available alpha.3 APKs (`d5e7e8aa3565c2ba8870b98cdafdb5d82aa392190d58710455e1ba7934106658`), so debug-signature install/update compatibility is verified for these artifacts. SHA-256 values are in the local `SHA256SUMS.txt`. ARM64 runtime on a physical device is not verified.

## 17. Known limitations

Physical acceptance and OriginOS settings remain unavailable. `START_STICKY` is not a guarantee against system or OEM process termination. `ApplicationExitInfo` is bounded and ambiguous for user-requested exits. The current AVD live scene did not complete a scheduled success flow in Round 4. The existing small vision fixture gate does not establish general accuracy. APKs are debug-signed and are not production-certified. See [ROUND4_KNOWN_LIMITATIONS.md](ROUND4_KNOWN_LIMITATIONS.md).

## 18. Final commit and release status

Implementation commit and final documentation commit: **to be recorded after push**. Alpha.4 is not published. Alpha.3 remains the historical emulator-validated prerelease. No release tag or GitHub release for alpha.4 is authorized until physical acceptance reaches `PASS_NATIVE` or `PASS_WITH_OEM_CONFIGURATION` and all other mandatory gates pass.

## 19. Final verdict

**BLOCKED — PHYSICAL ACCEPTANCE UNAVAILABLE.**
