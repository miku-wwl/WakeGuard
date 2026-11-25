# Known issues

- The local models still miss complete bathroom scenes. Round 2 has two independently grouped holdout positives. Wider variation in rooms, brands, lighting and placement is required for an alpha verdict.
- EfficientDet's COCO sink label includes sinks outside bathrooms. The toothpaste classifier estimates two MIMEX toothpaste product labels and does not localize a tube. Cosmetics and partially occluded items remain difficult.
- Local alternatives were compared. SSDlite and OmDet-Turbo missed both holdout positives. OmDet-Turbo accepted one incomplete scene. The bundled thresholds remain 0.45, 0.45 and 0.93.
- Android can prevent exact delivery and full-screen presentation. A system interruption, missed overlap and playback failure each have distinct history outcomes.
- The ARM64 build was produced for future testing. API 36 x86_64 is the tested emulator ABI. The experimental APKs use debug signing.
