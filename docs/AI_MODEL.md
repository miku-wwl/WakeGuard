# AI model and verification

## Models

The live verifier combines two replaceable local models:

1. **EfficientDet-Lite0 float32** from Google's MediaPipe model endpoint, pinned at the `/1/` artifact. It is trained on COCO and supports 80 object classes, including `sink` and `toothbrush`. COCO has no toothpaste class. The model is bundled in `app/src/main/assets/efficientdet_lite0.tflite` and runs through MediaPipe Tasks on device.
2. **WakeGuard toothpaste product classifier**, MobileNetV3-Small trained from random initialization (no ImageNet/pretrained weights) on the two toothpaste labels plus the other 26 MIMEX classes. The dataset was pinned to Hugging Face revision `fa7017f07b10efdb4dc461c14b8da1ae8aa01e73`; its dataset card declares MIT. The ONNX model is bundled at `app/src/main/assets/toothpaste_classifier.onnx` and evaluated using ONNX Runtime on device.

The classifier outputs 28 product probabilities. WakeGuard uses the sum for `sensodyne_toothpaste` and `colgate_toothpaste`, scanning the full frame and four overlapping crops. The detector score for sink and toothbrush and the toothpaste probability each have a user-configurable confidence threshold. A passing frame must meet all thresholds for three consecutive camera frames.

## Data and privacy

The MIMEX card reports 10,357 training images and 4,920 test images across 28 product classes. Raw parquet files and extracted images were used only under `D:\workshop\nov\downloads` and `D:\workshop\nov\cache`; they are not included in Git or the production APK. Model training and inference were local. The camera flow does not accept gallery photos and does not save frames.

## Evaluation scope

`D:\workshop\nov\reports\ai-classifier-metrics.json` and the matching CSV record actual counts, validation threshold, and test confusion metrics once training finishes. These metrics are for product imagery in the MIMEX split. They are not bathroom-scene metrics and do not prove real-world recall or false-positive rates when toothpaste is small or partly occluded. COCO detection of sink/toothbrush and a natural-scene three-object set are reported separately. If natural-scene evidence is unavailable or weak, verification remains an alpha feature and the release gate must reflect that.

## License notes

The MIMEX Hugging Face dataset card declares MIT. The EfficientDet-Lite0 model card distributed by LiteRT Community identifies the Google MediaPipe `/1/` artifact as Apache-2.0 and documents its upstream URL; the downloaded file's SHA-256 is recorded in `THIRD_PARTY_NOTICES.md`. These are source-provided license statements; the project does not redistribute the raw training data.
