# AI model and verification

## Models

The live verifier combines two replaceable local models:

1. **EfficientDet-Lite0 float32** from Google's MediaPipe model endpoint, pinned at the `/1/` artifact. It is trained on COCO and supports 80 object classes, including `sink` and `toothbrush`. COCO has no toothpaste class. The model is bundled in `app/src/main/assets/efficientdet_lite0.tflite` and runs through MediaPipe Tasks on device.
2. **WakeGuard toothpaste product classifier**, MobileNetV3-Small trained from random initialization (no ImageNet/pretrained weights) on the two toothpaste labels plus the other 26 MIMEX classes. The dataset was pinned to Hugging Face revision `fa7017f07b10efdb4dc461c14b8da1ae8aa01e73`; its dataset card declares MIT. The ONNX model is bundled at `app/src/main/assets/toothpaste_classifier.onnx` and evaluated using ONNX Runtime on device.

The classifier outputs 28 product probabilities. WakeGuard uses the sum for `sensodyne_toothpaste` and `colgate_toothpaste`, scanning the full frame and four overlapping crops. The detector score for sink and toothbrush and the toothpaste probability each have a user-configurable confidence threshold. A passing frame must meet all thresholds for three consecutive camera frames.

## Data and privacy

The MIMEX card reports 10,357 training images and 4,920 test images across 28 product classes. Raw parquet files and extracted images were used only under `D:\workshop\nov\downloads` and `D:\workshop\nov\cache`; they are not included in Git or the production APK. Model training and inference were local. The camera flow does not accept gallery photos and does not save frames.

## Evaluation scope

`D:\workshop\nov\reports\ai-classifier-metrics.json` and the matching CSV record actual counts, validation threshold, and held-out MIMEX product-image test confusion metrics. Those metrics do not prove bathroom-scene recall or false-positive rates. Android instrumentation also runs the bundled local models on one licensed positive bathroom photo containing a sink, toothbrush, and toothpaste, plus two incomplete scenes; it logs each actual score and tests the three-frame evaluator. The positive scene is a smoke-test sample, not a representative scene benchmark. The app remains an alpha feature until more varied bathroom imagery establishes reliable real-world behavior.

## License notes

The MIMEX Hugging Face dataset card declares MIT. The EfficientDet-Lite0 model card distributed by LiteRT Community identifies the Google MediaPipe `/1/` artifact as Apache-2.0 and documents its upstream URL; the downloaded file's SHA-256 is recorded in `THIRD_PARTY_NOTICES.md`. These are source-provided license statements; the project does not redistribute the raw training data.

The positive instrumentation photo is a compressed, resized rendition from Miriam Alonso's Pexels photo. Pexels permits free use and modification, with restrictions against redistribution on stock-photo and wallpaper platforms; source, license link, and bundled-file SHA-256 are recorded in `app/src/androidTest/assets/vision/SOURCES.txt`. It is test-only and is not packaged into the production app.
