# AI model and verification

## Models

The live verifier combines two replaceable local models:

1. **EfficientDet-Lite0 float32** from Google's MediaPipe model endpoint, pinned at the `/1/` artifact. It is trained on COCO and supports 80 object classes, including `sink` and `toothbrush`. COCO has no toothpaste class. The model is bundled in `app/src/main/assets/efficientdet_lite0.tflite` and runs through MediaPipe Tasks on device.
2. **WakeGuard toothpaste product classifier**, MobileNetV3-Small trained from random initialization (no ImageNet/pretrained weights) on the two toothpaste labels plus the other 26 MIMEX classes. The dataset was pinned to Hugging Face revision `fa7017f07b10efdb4dc461c14b8da1ae8aa01e73`; its dataset card declares MIT. The ONNX model is bundled at `app/src/main/assets/toothpaste_classifier.onnx` and evaluated using ONNX Runtime on device.

The classifier outputs 28 product probabilities. WakeGuard uses the sum for `sensodyne_toothpaste` and `colgate_toothpaste`, scanning the full frame and four overlapping crops. The object detector scans both the full frame and a centered square crop to give small objects a larger view. The detector score for sink and toothbrush and the toothpaste probability each have a user-configurable confidence threshold. The defaults are 0.45, 0.45, and 0.93; a passing frame must meet all thresholds for three consecutive camera frames.

## Data and privacy

The MIMEX card reports 10,357 training images and 4,920 test images across 28 product classes. Raw parquet files and extracted images were used only under `D:\workshop\nov\downloads` and `D:\workshop\nov\cache`; they are not included in Git or the production APK. Model training and inference were local. The camera flow does not accept gallery photos and does not save frames.

## Evaluation scope

The best checkpoint was epoch 11. Five-crop calibration on the training holdout selected a 0.93 toothpaste threshold at 91.25% precision and 98.65% recall (TP 73, FP 7, TN 957, FN 1). On the separate MIMEX test split, the same threshold produced 87.16% precision, 82.02% recall, 99.06% specificity, and 84.52% F1 (TP 292, FP 43, TN 4,521, FN 64; 356 positive and 4,564 negative product images). These product-image metrics do not prove bathroom-scene recall or false-positive rates.

Android instrumentation runs the actual bundled models on four licensed photos depicting a sink, toothbrush, and toothpaste, one additional bathroom/toothbrush scene whose toothpaste visibility is uncertain, and two incomplete object combinations. With the default thresholds, **none of the four explicitly positive bathroom photos passes**, even when the same frame is fed three times to the evaluator. Lite0 scores from the API 36 emulator, in `(sink, toothbrush, toothpaste)` order, were:

| Fixture | Scores | Result |
| --- | --- | --- |
| Pexels 7622566 | `(0.000, 0.304, 0.434)` | Reject |
| Pexels 8091115 | `(0.488, 0.395, 0.000)` | Reject |
| Pexels 8091047 | `(0.000, 0.372, 0.051)` | Reject |
| Pexels 7622547 | `(0.685, 0.000, 0.804)` | Reject |
| Pexels 4154164, toothpaste visibility uncertain | `(0.646, 0.000, 0.699)` | Reject |

The two incomplete combinations were also rejected: product-only `(0.000, 0.000, 0.016)` and sink/toothbrush without toothpaste `(0.720, 0.258, 0.019)`. The verified three-frame evaluator accepts injected frames that meet every threshold and rejects incomplete frames, but the real model has not demonstrated a successful natural bathroom-scene verification. A larger EfficientDet-Lite2 comparison detected more toothbrushes in some frames, but still did not pass any complete scene; it was not retained because the model file grew from 13.8 MB to 23.1 MB without closing that gap. The classifier threshold remains tied to the held-out product-image calibration rather than being lowered to fit these few scene photos.

This is a material product limitation: the current model can run entirely on device and demonstrates correct rejection behavior, but it cannot yet be represented as a validated end-to-end bathroom verifier. The app remains an alpha prototype; the GitHub prerelease gate should remain closed until a licensed, representative scene set and an improved detector/classifier demonstrate true positive completion without weakening the negative checks.

## Training method

`tools/model_training/train_classifier.py` trains from random initialization with seed 1337, a class-balanced sampler, 15 epochs, and batches of 32. It chooses the best epoch on a stratified 10% holdout from the training split, calibrates the final threshold with the same five-crop path used on device, and evaluates once on the separate 4,920-image MIMEX test split. The raw dataset is not checked in; the pinned train/test images were prepared locally with `tools/model_training/prepare_mimex.py`.

From the repository root, rerun training against a prepared dataset folder with:

```powershell
python .\tools\model_training\train_classifier.py `
  --dataset 'D:\workshop\nov\cache\vision-data\mimex' `
  --model-out .\app\src\main\assets\toothpaste_classifier.onnx `
  --metrics-out 'D:\workshop\nov\reports\ai-classifier-metrics.json' `
  --epochs 15 --batch-size 32
```

The command uses local CPU inference/training only; it makes no network calls or paid inference requests.

## License notes

The MIMEX Hugging Face dataset card declares MIT. The EfficientDet-Lite0 model card distributed by LiteRT Community identifies the Google MediaPipe `/1/` artifact as Apache-2.0 and documents its upstream URL; the downloaded file's SHA-256 is recorded in `THIRD_PARTY_NOTICES.md`. These are source-provided license statements; the project does not redistribute the raw training data.

The generated ONNX classifier is 6,195,838 bytes with SHA-256 `612059EB8395EF670ACE3A0571F150D58F4BA9345E1D24C5557DD6E1917A004F`. The exact JSON metrics and per-image test scores are preserved outside Git under `D:\workshop\nov\reports`.

The positive instrumentation photo is a compressed, resized rendition from Miriam Alonso's Pexels photo. Pexels permits free use and modification, with restrictions against redistribution on stock-photo and wallpaper platforms; source, license link, and bundled-file SHA-256 are recorded in `app/src/androidTest/assets/vision/SOURCES.txt`. It is test-only and is not packaged into the production app.
