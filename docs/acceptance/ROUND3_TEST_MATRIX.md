# Round 3 acceptance matrix

Statuses describe evidence, not an aggregate score. Local emulator evidence is distinguished from physical-device and GitHub acceptance.

| ID | Status | Verification method and result | Limitation / evidence |
|---|---|---|---|
| R3-01 | PASS | Reused Windows SDK, cache, JBR and API 36 `WakeGuard_API36` AVD. | No tools reinstalled; no phone used. |
| R3-02 | PASS | Round 2 baseline commit and old behavior reproduced before changes. | 15 JVM, 19 functional; strict positive 0/4; independent 0/2; holdout 0/33. |
| R3-03 | PASS | Product-owner requirement revision recorded in README and acceptance docs. | Round 2 data and failure reports retained. |
| R3-04 | PASS | Actual Android model scores compared for A/B/C on six positive and five obvious negative scenes. | Black/blank/bed-specific images were unavailable. |
| R3-05 | PASS | Policy C selected from measurements and actual CameraX completion. | 4/6 complete positives; 1/2 independent; 0/5 obvious unrelated. |
| R3-06 | PASS | Bundled EfficientDet/MediaPipe and ONNX models executed on Android. | Local API 36 x86_64 runtime. |
| R3-07 | PASS | Gate accepted 4/6; three scheduled CameraX runs reached Room `VERIFIED`. | Two independent positives were measured; one accepted. |
| R3-08 | PASS | 0/5 obvious unrelated accepted; 3/33 older incomplete scenes accepted; invalid, stale and duplicate evidence tests fail closed. | Blank, bedroom, bed and ceiling scenes were not separately available. |
| R3-09 | PASS | UI-created one-time exact alarms triggered, with token-backed Room events. | API 36 AVD. |
| R3-10 | PASS | Back on ringing and camera did not terminate; Back returned to ringing; repeated Back preserved the event. | Instrumented and scheduled AVD evidence. |
| R3-11 | PASS | Home and actual Recents card removal kept the service and restored the same event. | API 36 AVD. |
| R3-12 | PASS | `Asleep` was observed before alarm delivery and during active CameraX; active event/service survived resume. | Screen input reproduced using AVD power key (`KEYCODE_POWER`). |
| R3-13 | PASS | WakeGuard task card was swiped from Recents; same active event restored afterward. | AVD launcher behavior. |
| R3-14 | PASS | `kill -9` changed process PID; sticky recovery resumed same Room event. | Ordinary process death only; force-stop is system controlled. |
| R3-15 | PASS | Orderly `adb reboot` restored same future schedule time and replaced its token. | Dedicated weekly test alarm deleted after verification. |
| R3-16 | PASS | Active alarm UI omitted regular settings/editor tabs; code gives active event precedence. | Android system settings remain accessible outside the app. |
| R3-17 | PASS | Policy is event/session scoped; evaluator tests cover expiry, invalidation, stale/duplicate frames and errors. | No cross-event evidence reuse. |
| R3-18 | PASS | Three actual scheduled CameraX/model sessions each produced the same event as `VERIFIED`. | AVD `imagefile:` source; no phone liveness claim. |
| R3-19 | PASS | Verified completion stopped event-owned audio and service. | Logs show resource release; service absent. |
| R3-20 | PASS | Actual scheduled nonqualifying scene was explicitly emergency-stopped. | Room outcome `EMERGENCY_STOP`; success statistic unchanged. |
| R3-21 | PASS | Notification absent, service absent, audio released, active WakeLock count zero, vibrator IDLE and no current/next vibration. | Verified and emergency-stop terminal states checked. |
| R3-22 | PASS | Reviewed `INTERRUPTED` and `MISSED` source transitions and old evidence; orderly reboot passed. | The old same-event discrepancy was not reproduced; abrupt host power loss remains untested. |
| R3-23 | PASS | Exact schedule, one-use token, Room event ID and boot reschedule were observed. | AVD only. |
| R3-24 | PASS | 19 JVM tests pass. | JDK 25.0.4 local; project bytecode target 17. |
| R3-25 | PASS | 16 API 36 functional Android tests pass. | Local AVD. |
| R3-26 | PASS | New production-model gate asserts ≥4/6, ≥1 independent and 0/5 obvious unrelated; passed. | Historical strict gate remains a named diagnostic. |
| R3-27 | PENDING | Final pushed commit's GitHub Actions run. | Update after push and verify the actual run URL. |
| R3-28 | PASS | x86_64 and ARM64-v8a APKs built, v2 signatures and SHA-256 verified. | x86_64 runtime tested; ARM64 build/signature only. |
| R3-29 | PENDING | Permanent acceptance evidence and screenshots prepared in this directory. | Set PASS after commit/push. |
| R3-30 | PENDING | GitHub alpha prerelease is gated on final CI and release criteria. | Publish only after green CI, then verify asset URLs. |

## P0 review

No observed P0 alarm/playback defect remains in the tested emulator flows. The only partial evidence concerns scientific coverage and physical-device behavior, which are documented limitations, not claims of production certification. R3-22 investigation found distinct source transitions and no reproduced same-event conflict; the old abrupt-stop capture remains inconclusive.
