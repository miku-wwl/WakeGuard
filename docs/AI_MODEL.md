# AI model and verification

## Bundled pipeline

WakeGuard uses Google's EfficientDet-Lite0 float32 model with MediaPipe Tasks for COCO sink and toothbrush detections. The detector scores an aspect-preserving full frame and overlapping square tiles. Higher pixel density in each tile helps some small objects and the full view retains room context. The app loads the detector into a shared read-only direct buffer to keep simultaneous graph creation independent of writable model caches.

The MobileNetV3-Small ONNX model sums scores for MIMEX classes 20 and 21, the Sensodyne and Colgate toothpaste labels. It scores five centered views using the Python calibration crop geometry and RGB CHW normalization. Android uses platform bilinear resampling and Python uses PIL. They agree on the controlled RGB and geometry tests. High-frequency resampling differences can change natural-image scores. This classifier estimates a product class and returns no toothpaste box.

The default confidence thresholds are 0.45 for the sink, 0.45 for the toothbrush and 0.93 for toothpaste. The evaluator requires all three for three consecutive fresh camera frames. Model assets and their provenance appear in [Third-party notices](../THIRD_PARTY_NOTICES.md).

## What Round 2 measured

The original Android fixture run rejected all four historical positives. The corrected input path recovers sink or toothbrush scores on some fixtures. For example, the Miriam Alonso scene 7622566 changed from `(0.000, 0.304, 0.434)` to about `(0.489, 0.393, 0.427)` in sink, brush, toothpaste order. It remains below the acceptance rule.

The comparison also ran a CPU SSDlite transfer experiment with a four-output head, including background, and the Apache-2.0 OmDet-Turbo open-vocabulary model. Thresholds were locked using calibration data before the frozen holdout was scored. The holdout has two independently grouped positives and 33 negatives. SSDlite and OmDet each accepted zero positives. OmDet accepted one incomplete scene. Its localized predictions also sometimes confused a toothbrush with a tube. Neither candidate is included in the app.

The small independent scene set does not establish alpha performance. The four historical positives are from two photo shoots and are tracked separately. Product-image metrics from the first round remain useful for checking the 28-class model, with a reported toothpaste precision of 87.16% and recall of 82.02% on 4,920 product images. The domain of use is different.

## Reproduce

- `tools/model_training/prepare_mimex.py` and `train_classifier.py` reproduce the Round 1 classifier with the pinned MIMEX dataset revision and a training holdout for threshold selection.
- `tools/round2/ssdlite-transfer.py` uses the pinned official TorchVision checkpoint with a frozen backbone and box regressor, and a three-class detection head trained on labeled local photos.
- `tools/round2/evaluate-omdet.py` loads a pinned safetensors checkpoint offline. It uses fixed text prompts and records box and confidence outputs.
- `tools/round2/android_dataset.py` and `score_scenes.py` reproduce the Android and grouped holdout measurements. The manifest, raw scores, artifact hashes and commands are under `D:\workshop\nov\reports\round2`.

Raw photos and exploratory checkpoints remain in the D-drive workspace. The app saves no camera images and uses no inference service.
