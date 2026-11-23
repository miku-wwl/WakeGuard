# Architecture

WakeGuard uses a small MVVM-style Android app with explicit domain rules and replaceable camera inference.

```mermaid
flowchart TD
  UI[Compose screens] --> Repo[WakeGuardRepository]
  Repo --> Room[(Room: alarms and wake events)]
  Repo --> Scheduler[AlarmScheduler]
  Scheduler --> AM[AlarmManager setAlarmClock]
  AM --> Receiver[AlarmTriggerReceiver]
  Receiver --> Service[Media playback foreground service]
  Service --> Notification[Ongoing full-screen notification]
  UI --> Camera[CameraX Preview and ImageAnalysis]
  Camera --> Engine[VisionEngine]
  Engine --> Detector[MediaPipe EfficientDet-Lite0: COCO]
  Engine --> Classifier[ONNX MobileNetV3-Small: MIMEX product classes]
  Engine --> Evaluator[Three-frame threshold evaluator]
  Evaluator --> Repo
```

## Boundaries

- `domain`: local-time recurrence calculation and confidence/continuity rules. These classes have no Android dependency and are unit tested.
- `data`: Room entities, DAOs, schema, and repository operations. Event completion uses conditional SQL updates so duplicate success/emergency requests cannot overwrite a terminal outcome.
- `alarm`: exact scheduling, trigger/restore receivers, and the media playback foreground service.
- `vision`: the replaceable `VisionEngine` contract, CameraX frame handling, MediaPipe COCO detector, and ONNX toothpaste classifier.
- `presentation`: Compose alarm, edit, ringing, camera, result, statistics, and permission screens.

## Alarm event state

```mermaid
stateDiagram-v2
  [*] --> RINGING: exact alarm delivered
  RINGING --> VERIFYING: user opens live camera
  VERIFYING --> VERIFIED: all three scores pass on 3 consecutive frames
  RINGING --> EMERGENCY_STOP: confirmed emergency action
  VERIFYING --> EMERGENCY_STOP: confirmed emergency action
  RINGING --> INTERRUPTED: device reboot
  VERIFYING --> INTERRUPTED: device reboot
```

Manual failed attempts increment an event counter and leave the alarm active. A missing model, missing camera permission, or inference exception never returns a passing frame.

## Persistence and privacy

Room stores alarm configuration and wake-event timestamps/outcomes. Only camera frames are used for inference; the app does not write them to storage. Confidence thresholds are local app preferences. No account, backend, network permission, cloud inference, or paid API is used by the app.
