# WakeGuard Round 4 Test Matrix

Statuses reflect observed evidence. AVD behavior is not physical iQOO acceptance.

| ID | Status | Evidence / limit |
|---|---|---|
| R4-01 | PASS | Reused the existing Windows checkout, Android SDK, Gradle cache and `WakeGuard_API36` AVD. |
| R4-02 | PASS | Round 3 source, documentation, alpha.3 assets and release were preserved. |
| R4-03 | PASS | Owner-reported defect recorded as `WG-R4-P0-001`; physical reproduction is not claimed. |
| R4-04 | PASS | Current official Android documentation and generic vivo guidance reviewed; no Neo9-specific firmware evidence. |
| R4-05 | NOT_TESTED | No physical iQOO attached; Android/OriginOS/firmware and permission state unknown. |
| R4-06 | PARTIAL | CI artifact review confirmed future-alarm TC01 removal. Corrected local active-alarm TC02 removed one task with playback retained, but the second swipe did not remove the reopened task; CI's first active swipe also failed. Physical owner-reported behavior remains unverified. |
| R4-07 | PARTIAL | Confirmed foreground-reconciliation source vulnerability; physical root cause remains unknown. |
| R4-08 | PASS | Explicit `stopWithTask=false`; `onTaskRemoved` logs only and does not stop or self-restart playback. |
| R4-09 | PASS | Service remains playback owner; instrumentation verifies task-removal declaration and same-event user resume. |
| R4-10 | PASS | JVM/instrumentation cover service-absent reconciliation, exactly-once boot interruption and terminal-event non-resurrection. |
| R4-11 | PASS | CI run 45577205967 artifact review confirmed task 36 absent and card absent after the future-alarm swipe; the one-time alarm then delivered and played. Recents closed normally after its last card was removed. |
| R4-12 | PASS | Scheduled AVD flow exercised ordinary process recreation and retained the same event before camera verification. Not force-stop. |
| R4-13 | PARTIAL | Public Android status is shown and OEM switches are honestly labeled `需用户手动检查`; actual iQOO menus remain unknown. |
| R4-14 | PASS | Local JSONL diagnostics pass bounded 400-record test; exit metadata is recorded where supported. |
| R4-15 | PARTIAL | JVM, lint, instrumentation and model gates passed. Future-alarm delivery after task removal passed; one local active-alarm removal preserved playback, but repeated behavior was inconsistent and the CI active swipe failed. The scheduled camera run did not reach VERIFIED with the default scene. |
| R4-16 | BLOCKED | Physical active-alarm swipe test unavailable. |
| R4-17 | BLOCKED | Three physical repeated swipes unavailable. |
| R4-18 | PARTIAL | Earlier Round 3 AVD screen-off evidence exists; Round 4 re-run did not observe `Asleep`. Physical screen-off unavailable. |
| R4-19 | NOT_TESTED | No physical CameraX preview or washing-area scene. Android model fixture gate passed separately. |
| R4-20 | PASS | AVD and instrumentation emergency stop produced `EMERGENCY_STOP`, not VERIFIED; service/resource cleanup was observed. |
| R4-21 | PASS | Existing production vision gate and fixtures/policy were unchanged; metrics remained 4/6, 1/2 independent, 0/5 obvious unrelated. |
| R4-22 | PASS | 19/19 JVM and 25/25 API 36 instrumentation tests passed. |
| R4-23 | FAIL | Run 45577205967 on `f925bd3`: `verify` and API 36 functional instrumentation passed. TC01 task 36 was removed and its alarm delivered, though the old harness falsely marked it partial because Recents closed. TC02 active swipe left task 37 present; the emulator step and fail-closed `real-vision-gate` failed. The model gate and artifact upload passed. A corrected local rerun removed one active task while playback remained, but its second swipe failed. |
| R4-24 | PASS | Local ARM64-v8a and x86_64 alpha.4 candidates built, v2 signatures and matching alpha.3 debug certificate verified, hashes recorded. |
| R4-25 | PASS | Corrected Recents-closure harness, local rerun review, CI result and updated acceptance report/matrix are committed and pushed. Raw AVD and CI evidence remains in the local reports directory and linked artifact. |
| R4-26 | BLOCKED | Alpha.4 publication gate requires physical `PASS_NATIVE` or `PASS_WITH_OEM_CONFIGURATION`; no release will be published now. |

## Release gate

The active-alarm iQOO compatibility gate is the P0 criterion. The AVD PASS results do not change its blocked status. Alpha.4 is a local experimental candidate only.
