# WakeGuard Round 4 Final Acceptance Report

Date: 2026-11-25 (Pacific/Auckland)

## 1. Executive summary

**Verdict: PARTIAL — READY FOR MANUAL PHYSICAL DEVICE TESTING.** Round 4B adds user-initiated sanitized diagnostic export and a Chinese physical-test guide. Local JVM (20/20), lint, APK builds, API 36 functional instrumentation (26/26), and production-model acceptance (1/1) passed. CI run 45604309140 passed its build, functional, and model steps but failed closed because neither ADB-generated Recents gesture removed the task card; the strict `real-vision-gate` remains failed. The owner-authorized `v0.1.0-alpha.4-rc.1` prerelease is published for manual testing. Physical iQOO/OriginOS behavior remains **NOT TESTED**, the reported root cause remains **NOT CONFIRMED**, and final alpha.4 remains blocked.

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
- In the Round 4 baseline CI rerun (45585131166), TC01 removed task ID 36 and the one-time alarm later delivered with playback. Android closed Recents after its final card disappeared; the corrected harness accepts this when the exact task ID and card are absent.
- In that CI run's TC02, the ADB-generated upward gesture did not remove task ID 37 or its Recents card. The same `RINGING` event, foreground service and MediaPlayer stayed active. This is not evidence of playback after an actual active-task removal.
- In Round 4B CI run [45604309140](https://github.com/miku-wwl/WakeGuard/actions/runs/45604309140), the TC01 gesture also left task ID 36 and its card present, although the future alarm later delivered. The first TC02 gesture left its card present; event 2 remained `RINGING`, with its foreground service and MediaPlayer active. The harness then completed `EMERGENCY_STOP`, `verified_count=0`, and no service remained. Neither gesture proves active-task removal behavior.
- In a corrected local TC02 rerun, the first active swipe removed task ID 225; event 11, its foreground service and MediaPlayer remained active. After reopening the app, a second swipe left task ID 226 present and the same event remained active. The run stopped there and emergency cleanup completed.
- A separate idle-state smoke swipe removed a task from Recents, but this does not reproduce task removal with an alarm scheduled or active.
- Ordinary sticky recovery and same-event state checks passed in the scheduled AVD flow before its live camera scene failed to qualify.
- The default AVD camera scene did not qualify within the scheduled flow's 35-second window. The harness emergency-stopped the event; this is a scenario limitation, not a model-gate pass.

## 8. Implemented remediation

- Declared `android:stopWithTask="false"` explicitly and added a non-restarting `onTaskRemoved()` diagnostic callback.
- Kept audio, vibration and WakeLock ownership in the foreground service.
- Preserved active Room events across temporary foreground-service lookup misses; boot interruption remains terminal and exactly-once.
- Added a visible, same-event **继续响铃** action when the persisted event remains active but the service cannot be confirmed. Terminal events cannot be resumed.
- Added local bounded diagnostics and system process-exit metadata.
- Added live Android reliability status and transparent manual OriginOS checks.
- Round 4B adds a user-initiated, read-only diagnostic export in app-private cache with a scoped FileProvider URI. Free-form process-exit descriptions are omitted from the exported report; the owner chooses whether and where to share it.
- Added [the Chinese manual iQOO test guide](ROUND4_MANUAL_IQOO_TEST_GUIDE.md), including distinctions between uninterrupted playback, automatic recovery, and user-confirmed **继续响铃** recovery.
- The manual-test candidate uses `0.1.0-alpha.4-rc.1` / versionCode 4.

No vision model, inference runtime, fixture, threshold or 12-second policy window changed.

## 9. OEM configuration requirements

No OEM switch was changed or required by emulator acceptance. On the iQOO, first capture a baseline with its current settings. Then record available autostart, background high-power, battery restriction and optional Recents-lock settings. Do not claim native compatibility if a later pass depends on owner-approved configuration.

## 10. Before/after device behavior

There is no physical before/after comparison. CI run 45585131166 confirmed that one future-alarm swipe removed task ID 36 and the one-time alarm later delivered with playback. The later Round 4B run 45604309140 did not remove the TC01 task/card, although its alarm delivered; in TC02 the active task/card also remained while the same event, foreground service and MediaPlayer stayed active. A corrected local run removed active task ID 225 once with playback retained, but a later swipe left reopened task ID 226 present. No physical active-alarm result exists, so the reported iQOO defect remains open.

## 11. Automated regression

- JVM: **19/19 passed**.
- API 36 functional instrumentation: **25/25 passed**, 0 failures, 0 errors, 0 skipped.
- Android lint: **0 errors, 32 warnings, 4 hints**.
- Debug and instrumentation APK builds: passed.
- Production model gate: **4/6** labeled positives, **1/2** independent positives, **0/5** obvious unrelated scenes accepted; the existing gate and policy remained unchanged.
- Dedicated task-removal harness: **PARTIAL**. CI run 45585131166 TC01 removed task ID 36 and the one-time alarm later delivered and played. TC02's gesture did not remove task ID 37 or its card, while the same event, foreground service and MediaPlayer remained active; this does not prove behavior after active task removal. The corrected local run removed task ID 225 once with same-event playback retained, but its next swipe left task ID 226 present. Emergency cleanup recorded `EMERGENCY_STOP`, `verified_count=0`, and no service remaining. Three active task removals and physical behavior remain untested.
- Scheduled full CameraX flow with task removal and ordinary process recovery: **PARTIAL**. Earlier lifecycle steps passed, but the AVD's selected live scene did not qualify within 35 seconds; emergency cleanup passed.
- A Round 4 camera-screen-off rerun did not observe `Asleep` after its AVD key events. Round 3 has separate prior AVD screen-off evidence; neither result establishes physical screen-off behavior.

Round 4B bounded local verification added a formatter privacy test and instrumented export/share checks. Results: JVM **20/20 PASS**; lint **0 errors, 34 warnings, 4 hints**; debug and test APK builds **PASS**; API 36 functional instrumentation **26/26 PASS**; production-model acceptance **1/1 PASS**. The export test verified app-private cache output, scoped read-only URI sharing, event/service state preservation, and redaction. CI run 45604309140 also passed 26/26 functional tests and 1/1 production-model test, but the Recents harness was partial and the strict gate failed closed; see Section 14 and the [Round 4B delivery record](ROUND4B_EXPERIMENTAL_DELIVERY.md).

See [ROUND4_TEST_MATRIX.md](ROUND4_TEST_MATRIX.md) and local raw evidence under `D:\workshop\nov\reports\round4`.

## 12. Physical device acceptance

**NOT_TESTED.** TC01–TC10 on iQOO remain untested; TC10 also requires separate owner approval before a reboot. The physical device gate is the outstanding release blocker.

## 13. Safety and emergency stop

The app retains the explicit confirmation-gated emergency stop. Local AVD and instrumentation evidence recorded `EMERGENCY_STOP`, did not increment verified statistics, removed the service/ongoing notification, and released playback resources. No watchdog loop, privileged persistence, system Stop override, force-stop bypass, kiosk mode, or reboot was added.

## 14. GitHub CI

Run [45585131166](https://github.com/miku-wwl/WakeGuard/actions/runs/45585131166) tested source SHA `deee9fc5b044b2cd49d1864438651d9ebed29233`. The first emulator attempt hit a transient Gradle plugin-resolution failure; the failed jobs were rerun. On rerun, `verify` passed JVM tests, lint, dual-ABI APK builds, checksums and signature verification; all 25 API 36 functional instrumentation tests passed; the lightweight production-model gate passed at 4/6 positives, 1/2 independent positives and 0/5 obvious unrelated scenes; the historical strict diagnostic passed. TC01 task ID 36 and card disappeared and the one-time alarm later delivered with playback. TC02 did not remove task ID 37 or its card, while the same event, foreground service and MediaPlayer stayed active. Therefore the emulator job and fail-closed `real-vision-gate` failed. Safety cleanup stopped event 2 as `EMERGENCY_STOP`, left `verified_count=0`, and removed the foreground service. The corrected local run did remove active task ID 225 once with the same event and playback preserved, but its next swipe left task ID 226 present. Full CI evidence is in `D:\workshop\nov\reports\round4\ci-45585131166-final`; physical iQOO remains separate and untested.

Round 4B source CI [45604309140](https://github.com/miku-wwl/WakeGuard/actions/runs/45604309140) tested commit `964b1ffd37693a7af88f84f8e816a1485dcafddd`. The `verify` job passed JVM tests, lint, dual-ABI builds and checksums; API 36 functional instrumentation passed 26/26 and production-model acceptance passed 1/1. The Recents harness did not actually remove the task/card in either TC01 or TC02. TC01's future alarm still delivered while its card remained. For TC02, event 2 remained `RINGING`, with foreground service and MediaPlayer active; emergency cleanup completed as `EMERGENCY_STOP`, `verified_count=0`, with no service remaining. The emulator job and strict `real-vision-gate` failed, so the overall workflow is **FAIL / fail-closed**. This is not evidence of playback after active-task removal. The workflow artifact was downloaded to `D:\workshop\nov\releases\round4b\ci-evidence` and is not part of the public release.

## 15. Acceptance matrix

See [ROUND4_TEST_MATRIX.md](ROUND4_TEST_MATRIX.md). The critical physical swipe-removal criteria remain blocked or untested regardless of the emulator score.

## 16. APK and signing evidence

The owner-authorized experimental release [v0.1.0-alpha.4-rc.1](https://github.com/miku-wwl/WakeGuard/releases/tag/v0.1.0-alpha.4-rc.1) is published from source commit `964b1ffd37693a7af88f84f8e816a1485dcafddd`. Both ABIs report versionCode 4, versionName `0.1.0-alpha.4-rc.1`, target SDK 36 and valid APK v2 signatures. The certificate SHA-256 (`d5e7e8aa3565c2ba8870b98cdafdb5d82aa392190d58710455e1ba7934106658`) matches both downloaded alpha.3 APKs; installation over alpha.3 without uninstalling or clearing app data is supported. ARM64 runtime on a physical device is not verified. ARM64 APK SHA-256: `397c0f462dd9b8e396b9258fc92150230b3d1e413df5b56cf95b3d022fa541c1`; x86_64 APK SHA-256: `04b085dfe8ce1e0caf5e0d69834f6f6e175e8375a68994706dcf69fbc2c512bc`.

## 17. Known limitations

Physical acceptance and OriginOS settings remain unavailable. `START_STICKY` is not a guarantee against system or OEM process termination. `ApplicationExitInfo` is bounded and ambiguous for user-requested exits. The current AVD live scene did not complete a scheduled success flow in Round 4. The existing small vision fixture gate does not establish general accuracy. APKs are debug-signed and are not production-certified. See [ROUND4_KNOWN_LIMITATIONS.md](ROUND4_KNOWN_LIMITATIONS.md).

## 18. Final commit and release status

Product implementation commit: `8d5d92d101d0a9c8ed35c579d90a1b28473b0080`. Strict harness and evidence correction commits: `03393692ecafe3f35c37c5d15113028dafab53ef` and `9b3f8099c3f91bf8ebe057668d93f1b6fdf86350`. Instrumentation synchronization fix: `deee9fc5b044b2cd49d1864438651d9ebed29233`. Round 4B source commit: `964b1ffd37693a7af88f84f8e816a1485dcafddd`; the experimental `v0.1.0-alpha.4-rc.1` release tag targets that commit. It does not claim physical acceptance or a green strict Recents gate. Final `v0.1.0-alpha.4` remains blocked until physical acceptance reaches `PASS_NATIVE` or `PASS_WITH_OEM_CONFIGURATION` and all other mandatory gates pass. See [ROUND4B_EXPERIMENTAL_DELIVERY.md](ROUND4B_EXPERIMENTAL_DELIVERY.md) for the delivery record.

## 19. Final verdict

**PARTIAL — READY FOR MANUAL PHYSICAL DEVICE TESTING.** Physical acceptance and final alpha.4 publication remain blocked; the experimental RC exists only to support the owner's manual test.
