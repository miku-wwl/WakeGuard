# WakeGuard Round 4 Known Limitations

- The iQOO Neo9 Pro was unavailable. No physical OriginOS behavior, firmware, permissions, service state, audio, camera preview or process-exit evidence was observed.
- A confirmed foreground reconciliation race was removed, but its causal link to the owner-reported audio stop is unknown.
- `START_STICKY`, exact-alarm delivery and a foreground service do not override Android or OEM decisions to stop an app. User Force Stop and Android's Task Manager Stop remain authoritative.
- The API 36 AVD passed the dedicated task-removal harness, including three active-event removals, but cannot prove OriginOS compatibility.
- The Round 4 scheduled CameraX attempt with task removal and ordinary process recreation did not reach VERIFIED because the selected default AVD camera scene did not qualify within 35 seconds. The harness performed emergency cleanup. The existing production-model fixture gate still passed, and Round 3's separate scheduled virtual-camera success evidence remains historical.
- Round 4's screen-off-during-CameraX retry did not observe AVD `Asleep`; prior Round 3 AVD evidence is separate. No physical screen-off result exists.
- OEM autostart, background high-power and Recents-lock states cannot be queried through supported public Android APIs and need manual owner recording.
- `ApplicationExitInfo.REASON_USER_REQUESTED` is ambiguous between Recents removal and Settings Force Stop; system history is bounded.
- Model gate samples remain small: 4/6 positive including 1/2 independent and 0/5 obvious unrelated. This is not a general accuracy estimate.
- Candidate APKs use the Android debug certificate. ARM64 physical runtime and install on the target phone are untested.
- Alpha.4 remains unpublished until physical acceptance reaches `PASS_NATIVE` or `PASS_WITH_OEM_CONFIGURATION` and the remaining release gates pass.
