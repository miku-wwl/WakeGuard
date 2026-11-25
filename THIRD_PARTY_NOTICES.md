# Third-party notices

## EfficientDet-Lite0 float32

- Source: `https://storage.googleapis.com/mediapipe-models/object_detector/efficientdet_lite0/float32/1/efficientdet_lite0.tflite`
- Model package/card: LiteRT Community, `MediaPipe-ObjectDetector-EfficientDet-Lite0`, Apache-2.0; the card identifies the binary as byte-identical to Google's upstream `/1/` artifact.
- SHA-256: `40338EDF5EC70D43E318B0A716A84D4564CD1802759A7A07170C7E43796DBF58`.
- Model: trained on COCO; labels include sink and toothbrush but not toothpaste.
- Citation: Mingxing Tan, Ruoming Pang, Quoc V. Le, “EfficientDet: Scalable and Efficient Object Detection,” CVPR 2020.

## MediaPipe Tasks Vision

- Maven: `com.google.mediapipe:tasks-vision:0.10.29`.
- License: Apache License 2.0.

## ONNX Runtime Android

- Maven: `com.microsoft.onnxruntime:onnxruntime-android:1.25.0`.
- License: MIT.

## MIMEX training data

- Dataset: `Anilot/MIMEX`, revision `fa7017f07b10efdb4dc461c14b8da1ae8aa01e73`.
- Dataset card license tag: MIT. The custom classifier was trained locally from random initialization; no pretrained weights are bundled.
- The raw dataset and extracted product images are not redistributed in this repository.
- Project-generated ONNX classifier: `app/src/main/assets/toothpaste_classifier.onnx`, SHA-256 `612059EB8395EF670ACE3A0571F150D58F4BA9345E1D24C5557DD6E1917A004F` (6,195,838 bytes). It recognizes only the two labels `sensodyne_toothpaste` and `colgate_toothpaste` as toothpaste.

## Round 2 experimental models and datasets

- OmDet-Turbo desktop comparison: Om AI Lab's [Apache-2.0 safetensors checkpoint](https://huggingface.co/omlab/omdet-turbo-swin-tiny-hf/tree/e6da9e53bea06e035ce8864ab6a8953c5eb5bf25), revision e6da9e53bea06e035ce8864ab6a8953c5eb5bf25 and SHA-256 439d1575d7e237ad565ed6969ea2a2dfcedf2086155e9ca3ac96cd6180a48cfd. The app does not distribute it.
- SSDlite desktop transfer: [TorchVision BSD-3-Clause source](https://github.com/pytorch/vision/blob/main/LICENSE) and official COCO checkpoint a79551df. TorchVision notes that model weights can have their own terms. The experimental checkpoint and its derivative stay local. [TorchVision weight terms](https://docs.pytorch.org/vision/stable/models.html#general-information-on-pre-trained-weights)
- Reviewed Wikimedia Commons test and training renditions carry photo-level Creative Commons or public-domain terms in the external Round 2 manifest. The Pexels fixtures already in this repository were used for historical diagnostics only, without training or bulk collection. [Pexels terms](https://www.pexels.com/terms-of-service/)
- The short backup alarm audio is synthesized for WakeGuard and distributed under the project's MIT license.
