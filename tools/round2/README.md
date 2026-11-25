# Round 2 evaluation tools

Use the frozen `03_DATASET_MANIFEST.csv` and `frozen-inputs.txt` in the Round 2 evidence directory. The CSV records the original source, rendition hash, labels, split and scene group. Resolve photo paths under the existing D-drive workspace. Reopen source files via the recorded license and attribution terms if a rendition is missing.

The desktop scripts use CPU PyTorch 2.12.1 and TorchVision 0.27.1. OmDet requires Transformers 4.57.3 and timm 1.0.22. Keep weight caches under the project's D-drive cache. See the handover for pinned upstream URLs and complete commands.

- `android_dataset.py` bundles the exact reviewed images for local instrumentation and verifies their hashes before use.
- `score_scenes.py --mode calibrate` reads calibration rows only. `--mode score` takes locked thresholds or fixed production defaults.
- `collect_fixture_scores.py` reads actual per-frame output from the Android acceptance test.
- `emulator_e2e.py` operates the WakeGuard AVD and records scheduling, live camera, audio and the final Room event. Its restart and force-stop modes target the same AVD only.
