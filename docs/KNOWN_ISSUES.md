# Known issues and limitations

- Lightweight policy C accepted 4/6 labeled washing-area images, including 1/2 independent positives. It missed two labeled scenes, including `wm-119921527` in still-image evaluation. The small fixture sample does not measure general recognition accuracy.
- On the separate 33-image incomplete-scene holdout, policy C accepted three basin/dental confusers (`wm-66390412`, `wm-62169110`, `wm-24333608`). It rejected all five newly tested obvious unrelated scenes. Recognition is a behavioral prompt, not a security boundary.
- The model scores two toothpaste product families and does not locate a tube. It can miss unfamiliar packaging, small items, glare, occlusion, framing and lighting changes.
- Live inference on the x86_64 AVD is slow on the first model frame (about 6.8 seconds in one run); later frames were about 1.2–1.4 seconds. End-to-end completion ranged from 5.9 to 18.0 seconds in three successful AVD runs. Physical ARM64 performance is unknown.
- The virtual image-file camera produced valid CameraX analysis frames and three successful scheduled `VERIFIED` events. The camera preview screenshots on the AVD were black beneath the ongoing-notification shade. A physical camera preview and framing experience have not been tested.
- Android may deny exact alarm scheduling, notifications or full-screen presentation and may enforce its own process/service rules. An orderly reboot restored a future alarm in the emulator; abrupt host termination durability is not yet understood.
- The x86_64 API 36 emulator is the only runtime-tested target. ARM64-v8a builds and signing verify, but ARM64 runtime and physical hardware are untested. Alpha APKs use debug signing.
- Round 2's strict three-object criterion still fails historically (0/4 positive, 0/2 independent) and remains visible as a diagnostic. It was explicitly replaced by the product owner's Round 3 requirements, not erased.
