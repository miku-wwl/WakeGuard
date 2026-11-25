# WakeGuard Round 4B — Experimental Delivery Record

## Status

**PARTIAL — LOCAL PACKAGE READY; PUBLICATION PENDING.** The owner has authorized the experimental `v0.1.0-alpha.4-rc.1` prerelease for manual iQOO Neo9 Pro testing. This is not physical acceptance and does not close `WG-R4-P0-001`.

## Candidate identity

- Application ID: `com.mikuwwl.wakeguard`
- Version name: `0.1.0-alpha.4-rc.1`
- Version code: `4` (alpha.3 is version code 3)
- ABI packages: `arm64-v8a`, `x86_64`
- Signing: local Android debug certificate, certificate SHA-256 `d5e7e8aa3565c2ba8870b98cdafdb5d82aa392190d58710455e1ba7934106658`.
- Signing compatibility: verified against **both downloaded APKs from the published alpha.3 release**, each signed with the same certificate. The new APKs have the same application ID and higher versionCode 4 versus 3, preserving the normal update path.
- Implementation commit, CI workflow result, GitHub release URL, uploaded asset digests and file sizes: recorded in the completion update after publication.

## Local candidate artifacts

The two APKs and the guide are staged under `D:\workshop\nov\releases\round4b`.

| Asset | Size | SHA-256 |
|---|---:|---|
| `WakeGuard-0.1.0-alpha.4-rc.1-arm64-v8a.apk` | 52,733,736 bytes | `397c0f462dd9b8e396b9258fc92150230b3d1e413df5b56cf95b3d022fa541c1` |
| `WakeGuard-0.1.0-alpha.4-rc.1-x86_64.apk` | 55,887,166 bytes | `04b085dfe8ce1e0caf5e0d69834f6f6e175e8375a68994706dcf69fbc2c512bc` |

Both packages were checked with `aapt` for `com.mikuwwl.wakeguard`, versionCode 4 and versionName `0.1.0-alpha.4-rc.1`; their packaged native libraries match the named ABI. APK v2 signature verification passed for both. `SHA256SUMS.txt` was recomputed and checked against the staged files.

## Implemented in Round 4B

- **导出诊断日志** appears in the existing reliability settings card.
- Export is an explicit user action. It writes a JSON file under app-private cache and opens the Android share sheet through a scoped, read-only FileProvider URI. Nothing is sent automatically.
- The report includes app/device version, public Android permission and battery/background states, alarm event state, foreground-service status, recent bounded lifecycle records, and available `ApplicationExitInfo` metadata.
- Serial number, IMEI, Android ID, account, contacts, location, photos, camera frames, unrelated notifications and other-app logs are not collected. Free-form process-exit descriptions are omitted from the export.
- The action does not alter alarm records or start/stop playback. Export after a failed active-alarm scenario by first recording the visible state and safely using emergency stop if needed.

## Existing alarm recovery behavior

When Room still contains an active event but WakeGuard cannot confirm its foreground service, the ringing screen offers **继续响铃**. It is a user-confirmed attempt to start the same persisted event through the supported foreground-service API. It is not uninterrupted playback and does not count as automatic recovery after a Recents swipe. Android or OriginOS may still reject service startup. Emergency stop remains available from the alarm/verification flow.

## Verification, CI and physical status

Bounded local verification: JVM **20/20 PASS**; lint **0 errors, 34 warnings, 4 hints**; debug and instrumentation APK builds **PASS**; API 36 functional instrumentation **26/26 PASS**; production-model acceptance **1/1 PASS**. The new export test verified a private-cache file, scoped read-only `content://` share access, a read-only active-event/service status, and identifier/exit-description redaction. Final source CI status and publication URL will be added after the automatic workflow and upload. The existing Round 4 strict task-removal evidence remains unchanged: TC01 passed; active-task CI TC02 did not actually remove the card; one local active swipe retained playback but a repeated swipe did not. Do not claim all CI gates passed.

Physical iQOO/OriginOS test status: **NOT TESTED**. Physical root cause: **NOT CONFIRMED**. Final `v0.1.0-alpha.4` remains blocked pending physical acceptance and other release gates.

## Manual test guide

See [ROUND4_MANUAL_IQOO_TEST_GUIDE.md](ROUND4_MANUAL_IQOO_TEST_GUIDE.md). The release asset will be named `MANUAL_DEVICE_TEST_GUIDE.md`.
