# Folder Structure

Living map of `android/app/src/main/java/com/pinch/gary/`. Update this whenever a new feature package is scaffolded (at the start of that feature's build-order week) — keep it in sync with the actual tree, don't let it drift into aspirational territory.

Status key: **[BUILT]** exists with real logic · **[STUB]** placeholder only · **[PLANNED]** not created yet, listed here so the eventual location is known in advance.

```
com.pinch.gary/
├── GaryApplication.kt                  [BUILT]
├── MainActivity.kt                     [BUILT]  — single Activity, hosts NavHost
│
├── core/
│   ├── di/                             [BUILT]  NetworkModule, DispatchersModule, AppScopeModule
│   ├── security/                       [BUILT]  KeystoreManager.kt
│   ├── permissions/                    [BUILT]  RequiredPermissions.kt, PermissionState.kt
│   ├── theme/                          [BUILT]  Color/Type/Shape/GaryTheme.kt
│   ├── navigation/                     [BUILT]  Destinations.kt, GaryNavHost.kt
│   ├── appstate/                       [BUILT]  AppState.kt (glasses/vision/gesture wired), GaryOrchestrator.kt
│   └── util/                           [BUILT]  Logger.kt — never accepts ByteArray/InputStream
│
├── glasses/                            [BUILT]  — week 1–2 feature
│   ├── GlassesManager.kt / GlassesManagerImpl.kt
│   ├── ble/                            BleScanner.kt, BleConnectionManager.kt, BleGattProfile.kt
│   ├── mjpeg/                          MjpegStreamClient.kt, FrameRingBuffer.kt
│   ├── service/                        GlassesForegroundService.kt
│   ├── model/                          GlassesConnectionState.kt, GlassesDevice.kt
│   └── GlassesViewModel.kt
│
├── vision/                             [BUILT]  — week 3–4. See .claude/save/features/vision-engine.md
│   ├── VisionEngine.kt / VisionEngineImpl.kt
│   ├── VisionModule.kt
│   ├── PhoneCameraViewModel.kt         bind/unbind called from ui/main/MainScreen.kt
│   ├── decode/                         DecodedFrame.kt, FrameDecoder.kt, BitmapFrameDecoder.kt
│   ├── detector/                       HandLandmarkDetector.kt (+ MediaPipe impl, TODO-stub — no .task asset yet),
│   │                                   ObjectDetector.kt (+ Yolo impl, TODO-stub — no .tflite asset yet)
│   ├── camera/                         PhoneCameraSource.kt, CameraXPhoneCameraSource.kt — v0 phone-camera
│   │                                   substitute for the ESP32 camera (ADR-010)
│   └── model/                          VisionResult.kt
│
├── gesture/                            [BUILT]  — week 3–4, camera-only v0 per ADR-010. See .claude/save/features/gesture-recognizer.md
│   ├── GestureRecognizer.kt / GestureRecognizerImpl.kt   depends only on GestureSource
│   ├── GestureModule.kt                binds GestureSource → CameraGestureSourceImpl (swap point for radar later)
│   ├── model/                          GaryCommand.kt, RawGestureEvent.kt
│   └── source/                         GestureSource.kt, CameraGestureSourceImpl.kt (v0 impl, depends on VisionEngine),
│                                       LandmarkGestureClassifier.kt (+ impl, pure heuristics)
│
├── voice/                              [PLANNED] — week 5–6
├── garyclient/                         [PLANNED] — week 7–8, gets domain/ + data/
├── smarthome/                          [PLANNED] — week 9–10, gets domain/ + data/
├── usercontext/                        [PLANNED] — week 9–10, gets domain/ + data/ (named to avoid shadowing android.content.Context)
│
└── ui/
    ├── main/                           [BUILT]  MainScreen.kt, MainViewModel.kt — status dot + state label, no buttons
    ├── onboarding/                     [STUB]   real content week 11–12
    ├── settings/                       [STUB]   real content week 11–12
    ├── permissions/                    [BUILT]  BLE + location + camera (vision/'s v0 phone-camera source) runtime requests
    └── components/                    [BUILT]  StatusDot.kt
```

Resources:
```
app/src/main/res/xml/network_security_config.xml   [BUILT]  scoped to local LAN cleartext traffic
```
