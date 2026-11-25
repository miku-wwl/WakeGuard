# WakeGuard Round 4 Test Matrix

Statuses reflect observed evidence. AVD behavior is not physical iQOO acceptance.

| ID | Status | Evidence / limit |
|---|---|---|
| R4-01 | PASS | Reused the existing Windows checkout, Android SDK, Gradle cache and `WakeGuard_API36` AVD. |
| R4-02 | PASS | Round 3 source, documentation, alpha.3 assets and release were preserved. |
| R4-03 | PASS | Owner-reported defect recorded as `WG-R4-P0-001`; physical reproduction is not claimed. |
| R4-04 | PASS | Current official Android documentation and generic vivo guidance reviewed; no Neo9-specific firmware evidence. |
| R4-05 | NOT_TESTED | No physical iQOO attached; Android/OriginOS/firmware and permission state unknown. |
| R4-06 | PARTIAL | CI run 45585131166 confirmed future-alarm TC01 removal and delivery. Its active TC02 gesture left task ID 37 and its card present; a corrected local run removed task 225 once with playback retained but the next swipe left task 226. Physical owner-reported behavior remains unverified. |
| R4-07 | PARTIAL | Confirmed foreground-reconciliation source vulnerability; physical root cause remains unknown. |
| R4-08 | PASS | Explicit `stopWithTask=false`; `onTaskRemoved` logs only and does not stop or self-restart playback. |
| R4-09 | PASS | Service remains playback owner; instrumentation verifies task-removal declaration and same-event user resume. |
| R4-10 | PASS | JVM/instrumentation cover service-absent reconciliation, exactly-once boot interruption and terminal-event non-resurrection. |
| R4-11 | PASS | CI run 45585131166 artifact confirms task 36 and its card absent after the future-alarm swipe; the one-time alarm then delivered and played. Recents closed normally after its last card was removed. |
| R4-12 | PASS | Scheduled AVD flow exercised ordinary process recreation and retained the same event before camera verification. Not force-stop. |
| R4-13 | PARTIAL | Public Android status is shown and OEM switches are honestly labeled `需用户手动检查`; actual iQOO menus remain unknown. |
| R4-14 | PASS | Local JSONL diagnostics pass bounded 400-record test; exit metadata is recorded where supported. |
| R4-15 | PARTIAL | JVM, lint, 25/25 functional instrumentation and model gates passed. CI future-alarm delivery after task removal passed. CI active swipe did not remove its task/card; one local active-alarm removal preserved playback, but the repeated local swipe failed. The scheduled camera run did not reach VERIFIED with the default scene. |
| R4-16 | BLOCKED | Physical active-alarm swipe test unavailable. |
| R4-17 | BLOCKED | Three physical repeated swipes unavailable. |
| R4-18 | PARTIAL | Earlier Round 3 AVD screen-off evidence exists; Round 4 re-run did not observe `Asleep`. Physical screen-off unavailable. |
| R4-19 | NOT_TESTED | No physical CameraX preview or washing-area scene. Android model fixture gate passed separately. |
| R4-20 | PASS | AVD and instrumentation emergency stop produced `EMERGENCY_STOP`, not VERIFIED; service/resource cleanup was observed. |
| R4-21 | PASS | Existing production vision gate and fixtures/policy were unchanged; metrics remained 4/6, 1/2 independent, 0/5 obvious unrelated. |
| R4-22 | PASS | 19/19 JVM and 25/25 API 36 instrumentation tests passed, including the corrected alarm-edit synchronization test. |
| R4-23 | FAIL | Run 45585131166 rerun on `deee9fc`: `verify` and 25/25 API 36 functional instrumentation passed. TC01 task 36 was removed and its alarm delivered. TC02's ADB swipe left task 37 and the card present while the same event, foreground service and MediaPlayer remained active. The emulator job and fail-closed `real-vision-gate` failed; the model gate and artifact upload passed. This did not establish playback after actual active-task removal. |
| R4-24 | PASS | Local ARM64-v8a and x86_64 alpha.4 candidates built, v2 signatures and matching alpha.3 debug certificate verified, hashes recorded. |
| R4-25 | PASS | Corrected Recents-closure harness, local rerun review, CI result and updated acceptance report/matrix are committed and pushed. Raw AVD and CI evidence remains in the local reports directory and linked artifact. |
| R4-26 | BLOCKED | Final alpha.4 publication still requires physical `PASS_NATIVE` or `PASS_WITH_OEM_CONFIGURATION`. Owner authorization for the separately versioned alpha.4-rc.1 experimental manual-test prerelease does not satisfy this gate. |

## Round 4B evidence

The app now exposes **导出诊断日志** in the existing reliability card. The export is generated only after an explicit tap, uses an app-private cache file and read-only FileProvider sharing, and does not start/stop playback, alter a wake event, enter camera verification, or change emergency-stop behavior. Its formatter redacts identifying strings in reasons and omits free-form process-exit descriptions. Bounded local results: JVM **20/20**, API 36 functional instrumentation **26/26**, production-model acceptance **1/1**, lint **0 errors**, both APK builds **PASS**. The strict Recents test was not rerun. See [ROUND4_MANUAL_IQOO_TEST_GUIDE.md](ROUND4_MANUAL_IQOO_TEST_GUIDE.md) and [ROUND4B_EXPERIMENTAL_DELIVERY.md](ROUND4B_EXPERIMENTAL_DELIVERY.md) for evidence and publication status.

## Release gate

The active-alarm iQOO compatibility gate is the P0 criterion. The AVD PASS results do not change its blocked status. Alpha.4 is a local experimental candidate only.
