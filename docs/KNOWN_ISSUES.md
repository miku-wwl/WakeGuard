# Known issues

- The custom toothpaste classifier's source data is product imagery, not a bathroom-scene detection dataset. Its held-out classification metrics cannot establish accuracy in real camera scenes. Natural-scene performance must be evaluated before calling verification production-ready.
- EfficientDet-Lite0 only labels the COCO classes, which do not include toothpaste. A separately trained product classifier is required.
- Real-time inference scans five image crops at a limited frame rate. Older or low-memory devices may have noticeable latency.
- Exact alarms, notifications, and full-screen launches require Android-granted access. Settings can be reopened to recover.
- The repository has no release signing key. The alpha APK is debug-signed.
- Emulator testing cannot validate physical camera, audio hardware, OEM battery policies, or all lock-screen implementations.
