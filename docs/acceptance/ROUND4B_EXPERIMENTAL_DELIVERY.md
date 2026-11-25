# WakeGuard Round 4B — Experimental Delivery Record

## Status

**PARTIAL — READY FOR MANUAL PHYSICAL DEVICE TESTING.** The owner-authorized experimental prerelease is published for manual testing on an iQOO Neo9 Pro running OriginOS. Physical acceptance is still **NOT TESTED**, the cause of `WG-R4-P0-001` is **NOT CONFIRMED**, and the final `v0.1.0-alpha.4` remains blocked.

## Candidate and source

- Application ID: `com.mikuwwl.wakeguard`
- Version name: `0.1.0-alpha.4-rc.1`
- Version code: `4` (published alpha.3 uses version code 3)
- ABIs: `arm64-v8a`, `x86_64`
- Source commit: [`964b1ffd37693a7af88f84f8e816a1485dcafddd`](https://github.com/miku-wwl/WakeGuard/commit/964b1ffd37693a7af88f84f8e816a1485dcafddd)
- Published prerelease: [WakeGuard v0.1.0-alpha.4-rc.1](https://github.com/miku-wwl/WakeGuard/releases/tag/v0.1.0-alpha.4-rc.1)
- Direct ARM64-v8a APK: [Download WakeGuard-0.1.0-alpha.4-rc.1-arm64-v8a.apk](https://github.com/miku-wwl/WakeGuard/releases/download/v0.1.0-alpha.4-rc.1/WakeGuard-0.1.0-alpha.4-rc.1-arm64-v8a.apk)

The release contains exactly four assets: ARM64-v8a APK, x86_64 APK, `SHA256SUMS.txt`, and `MANUAL_DEVICE_TEST_GUIDE.md`. Release API metadata confirms the tag targets the source commit. HTTP HEAD checks returned 200 for all four asset URLs.

| Asset | Size | SHA-256 |
|---|---:|---|
| `WakeGuard-0.1.0-alpha.4-rc.1-arm64-v8a.apk` | 52,733,736 bytes | `397c0f462dd9b8e396b9258fc92150230b3d1e413df5b56cf95b3d022fa541c1` |
| `WakeGuard-0.1.0-alpha.4-rc.1-x86_64.apk` | 55,887,166 bytes | `04b085dfe8ce1e0caf5e0d69834f6f6e175e8375a68994706dcf69fbc2c512bc` |

GitHub's uploaded SHA-256 digests match the local APK checksums. Both APKs were inspected for application ID, version name/code, ABI contents, and valid v2 signature. The signing certificate SHA-256 is `d5e7e8aa3565c2ba8870b98cdafdb5d82aa392190d58710455e1ba7934106658`; it matches both downloaded APKs from the existing published alpha.3 release. The same application ID and higher version code preserve the normal update path; do not uninstall alpha.3 or clear app data.

## Experimental warning

**EXPERIMENTAL — NOT PHYSICALLY VALIDATED**

OriginOS compatibility has not been proven. `WG-R4-P0-001` remains open. This candidate does not claim the active-alarm Recents swipe issue is fixed, does not mean all mandatory CI gates are green, and is not production-ready or final alpha.4.

## Diagnostic export

- **导出诊断日志** is available in the existing reliability settings card.
- Export occurs only after the user taps the button. The JSON is written under app-private cache and shared using a scoped, read-only FileProvider URI and the Android share sheet. No data is transmitted automatically.
- It contains app/device version, public Android permission and battery/background states, alarm event state, foreground-service status, recent bounded lifecycle records, and available `ApplicationExitInfo` metadata.
- Serial number, IMEI, Android ID, account, contacts, location, photos, camera frames, unrelated notifications, and other-app logs are not collected. Free-form process-exit descriptions are omitted.
- Export does not modify alarm records, start/stop playback, or enter camera verification. In a failed ringing scenario, record visible state first and use emergency stop safely before exporting if needed.

## Recovery behavior

If Room retains an active event but WakeGuard cannot confirm its foreground service, the ringing screen offers **继续响铃**. This is a user-confirmed attempt to restart the same persisted event through Android's supported foreground-service API. It is not uninterrupted playback and does not count as automatic recovery after a Recents swipe. Android or OriginOS may reject service startup. Emergency stop remains available through the alarm/verification flow.

## Verification and CI

Bounded local verification for the export change:

- JVM: **20/20 PASS**.
- Lint: **0 errors, 34 warnings, 4 hints**.
- Debug and instrumentation APK builds: **PASS**.
- API 36 functional instrumentation: **26/26 PASS**.
- Production-model acceptance: **1/1 PASS**.
- Export tests covered private-cache output, read-only scoped share URI, preservation of alarm/service state, and identifier/exit-description redaction.

CI run [45604309140](https://github.com/miku-wwl/WakeGuard/actions/runs/45604309140), source SHA `964b1ffd37693a7af88f84f8e816a1485dcafddd`, completed **FAIL / fail-closed**:

- `verify`: **PASS** (JVM, lint, dual-ABI build and checksum steps).
- API 36 functional instrumentation: **26/26 PASS**.
- Production-model acceptance: **1/1 PASS**.
- The Recents task-removal harness was **PARTIAL**. In TC01 the ADB swipe did not remove the task/card; the future alarm nevertheless delivered while that card remained. In TC02 the first active-alarm swipe also did not remove the card; event 2 remained `RINGING`, with the foreground service and MediaPlayer active. This does not demonstrate playback after actual active-task removal.
- The harness emergency-stopped event 2: `EMERGENCY_STOP`, `verified_count=0`, no foreground service remaining.
- The strict `real-vision-gate` and overall workflow failed closed because the emulator job failed. Do not report the mandatory strict Recents gate as passing.

The downloaded CI evidence is retained locally under `D:\workshop\nov\releases\round4b\ci-evidence`; it is not included in the public release.

## Manual device guide

See [ROUND4_MANUAL_IQOO_TEST_GUIDE.md](ROUND4_MANUAL_IQOO_TEST_GUIDE.md), also included in the prerelease as `MANUAL_DEVICE_TEST_GUIDE.md`. It covers TC01–TC07, initial baseline settings, repeated active-alarm swipes, the 15-second observation cap, camera verification, emergency stop, OriginOS setting comparison, and manual diagnostic sharing.

**Physical iQOO/OriginOS status: NOT TESTED. Physical root cause: NOT CONFIRMED.** The final `v0.1.0-alpha.4` remains blocked pending physical acceptance and all remaining release gates.
