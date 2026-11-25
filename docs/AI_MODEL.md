# Offline vision model and verification

## Purpose and product change

WakeGuard is a personal anti-snooze alarm with lightweight offline camera verification. The product owner clarified for Round 3 that the camera is behavioral friction to encourage approaching a wash area. It is not biometric or identity verification and does not need research-grade precision. This authorized requirement replaces Round 2's strict three-object and 30-positive/30-negative release criteria. Round 2 datasets and failed results remain intact for comparison.

## Bundled pipeline

The app uses EfficientDet-Lite0 through MediaPipe Tasks for COCO sink and toothbrush detections, plus a local MobileNetV3-Small ONNX classifier for two MIMEX toothpaste product labels. The detector scores the aspect-preserving frame and overlapping crops. The product classifier has no toothpaste localization box and can confuse visually similar objects. Inference runs on-device without a network service. See [third-party notices](../THIRD_PARTY_NOTICES.md).

Model outputs are scores, not calibrated probabilities. The app does not expose tuning sliders or store threshold preferences. The production evaluator is replaceable and unit-tested:

- **A — relaxed simultaneous:** sink ≥0.25, toothbrush ≥0.25, toothpaste ≥0.40 in two consecutive fresh frames.
- **B — sink anchored:** sink ≥0.25 plus toothbrush ≥0.25 or toothpaste ≥0.40 in two consecutive fresh frames.
- **C — session accumulated (selected):** collect sink ≥0.25 and either toothbrush ≥0.25 or toothpaste ≥0.40 across at least two fresh relevant frames within 12 seconds in the same active camera attempt.

Policy C was selected from measured acceptance and the live CameraX trial. It tolerates objects appearing in different frames while still requiring actual CameraX input and bundled model inference. Sessions do not cross alarms. Back, backgrounding, screen-off, permission loss, camera close, invalid model output and stale or duplicated frames reset evidence. Emergency stop is never counted as verification.

## Round 3 measurements

The Android API 36 x86_64 emulator ran the bundled production models on six labeled washing-area images and five visually reviewed, obviously unrelated images. Each image was actually inferred on Android; the policy experiment fed two fresh evaluator frames for the same score vector.

| Policy | Complete labeled washing-area fixtures accepted | Independent positives accepted | Obviously unrelated scenes accepted |
|---|---:|---:|---:|
| A — relaxed simultaneous | 2/6 | 1/2 | 0/5 |
| B — sink anchored | 4/6 | 1/2 | 0/5 |
| C — session accumulated | 4/6 | 1/2 | 0/5 |

The selected policy accepted three of the four historical Pexels fixtures and the independent Wikimedia scene `wm-64176029`. It rejected historical fixture `pexels-toothbrush-toothpaste-sink-8091047` and independent scene `wm-119921527` in still-image evaluation. The latter did pass during actual CameraX image-file AVD runs with live scores sink 0.539, toothbrush 0.264, toothpaste 0.213; the brush threshold allowed it. This illustrates why screenshot and actual camera pipeline evidence are reported separately.

On the Round 2 Android holdout's 33 incomplete scenes, the selected thresholds accepted 3: `wm-66390412`, `wm-62169110`, and `wm-24333608`. These are bathroom/basin or dental-item confusers rather than obviously unrelated scenes, but they remain observed false accepts against the Round 2 incomplete-scene labels. All five new obvious unrelated scenes were rejected. The small labeled sample and intentional acceptance of approximate scenes do not establish general accuracy.

The fixture gate's model call latency was 377–578 ms per image on this AVD. During live CameraX runs, one cold run reported 6.8 seconds for the initial model frame followed by 1.2–1.4 seconds; later warm-model verification completed in 5.9 seconds from opening camera to terminal result. Another run took 18.0 seconds end-to-end. Emulator timing is not a physical-device performance claim.

The machine-readable scores and source fixture provenance are in [`acceptance/ROUND3_VISION_METRICS.json`](acceptance/ROUND3_VISION_METRICS.json) and [`round3/ATTRIBUTION.md`](../app/src/androidTest/assets/vision/round3/ATTRIBUTION.md).

## Historical measurements

Round 2's original strict model gate accepted 0/4 historical positives and 0/2 independent positives, and the separate 33-incomplete-scene set accepted 0/33. Round 3 preserves that result as a non-blocking historical diagnostic; the new production-model gate enforces the owner's revised requirements instead of silently disabling vision checks.

Raw Round 2 datasets and exploratory desktop models remain in the local `D:\workshop\nov\reports\round2` workspace and are not duplicated in this repository. Round 3's seven additional test-only images carry per-image licenses, source links and hashes in the checked-in attribution file.
