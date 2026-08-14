# Pinch — Build Phases Master Index

**Product:** Pinch smart glasses + Gary AI assistant  
**Author:** Pratham Yaligar  
**Goal:** Functional wearable AI assistant: glasses → Android → cloud → voice  
**Status as of 2026-08-13:** Phase 1 (Android foundation) is scaffolded. Everything else is unbuilt.

---

## Phase Map

| Phase | Name | Milestone | Goal |
|-------|------|-----------|------|
| [Phase 1](phase-1-android-foundation.md) | Android Foundation | M2 Weeks 1–4 | App builds, BLE connects, MJPEG streams, gesture fires |
| [Phase 2](phase-2-voice-cloud-core.md) | Voice + Cloud Core | M2/M3 Weeks 5–8 | Pinch → Gary speaks back. Minimal cloud up. |
| [Phase 3](phase-3-cloud-backend-full.md) | Cloud Backend Full | M3 Weeks 7–10 | Tools work, memory works, HA relay works |
| [Phase 4](phase-4-android-smart-home-context.md) | Smart Home + Context | M2 Weeks 9–12 | Calendar, location, HA commands from voice |
| [Phase 5](phase-5-hardware-v0.md) | Hardware v0 | M4 Early | XIAO ESP32-S3 prototype: BLE + MJPEG + audio |
| [Phase 6](phase-6-hardware-v1.md) | Hardware v1 (Custom PCB) | M4 Full | Custom PCB + BGT60TR13C radar, 3D frame |
| [Phase 7](phase-7-mac-companion.md) | Mac Companion App | M5 | Swift overlay app: screen context, voice at desk |
| [Phase 8](phase-8-launch.md) | Launch Polish | Post-M5 | Billing, onboarding, Play Store, beta |

---

## Critical Path (Fastest to "Gary talks back")

```
Phase 1 (BLE + Vision + Gesture) 
  → Phase 2 (Voice + Cloud Core) 
  → Gary speaks at Week 6
  → Phase 3 (HA tools + memory) 
  → Phase 4 (context injection)
  → Gary is useful at Week 10
```

Hardware and Mac are parallel tracks — they do NOT block Gary being functional.

---

## Non-negotiable Constraints (apply to all phases)

1. **Streaming data never touches disk** — frames, audio PCM, screen captures are in-memory only
2. **HA token stays on phone** — Android Keystore, never sent to cloud
3. **JWT in Android Keystore / macOS Keychain** — never in SharedPreferences or UserDefaults
4. **No Gary_2 code** — built fresh, no imports from the old repo
5. **Always verify on a real Android device** — emulator is unreliable for BLE/camera

---

## What Is Already Scaffolded (Phase 1 partial)

The following files exist in `android/` and are the starting point:

**Core:**
- `core/appstate/AppState.kt` — sealed state enum
- `core/appstate/GaryOrchestrator.kt` — top-level coordinator
- `core/di/` — Hilt modules (AppScope, Dispatchers, Network)
- `core/navigation/` — NavHost + Destinations
- `core/permissions/` — PermissionState, RequiredPermissions
- `core/security/KeystoreManager.kt` — Android Keystore wrapper
- `core/theme/` — GaryTheme, Colors, Type, Shape

**Glasses (BLE + MJPEG):**
- `glasses/ble/BleConnectionManager.kt`
- `glasses/ble/BleGattProfile.kt`
- `glasses/ble/BleScanner.kt`
- `glasses/GlassesManager.kt` (interface)
- `glasses/GlassesManagerImpl.kt`
- `glasses/GlassesModule.kt`
- `glasses/GlassesViewModel.kt`
- `glasses/mjpeg/FrameRingBuffer.kt`
- `glasses/mjpeg/MjpegStreamClient.kt`
- `glasses/model/GlassesConnectionState.kt`
- `glasses/model/GlassesDevice.kt`
- `glasses/service/GlassesForegroundService.kt`

**Vision (ML inference):**
- `vision/camera/CameraXPhoneCameraSource.kt`
- `vision/camera/PhoneCameraSource.kt`
- `vision/decode/BitmapFrameDecoder.kt`
- `vision/decode/DecodedFrame.kt`
- `vision/decode/FrameDecoder.kt`
- `vision/detector/HandLandmarkDetector.kt`
- `vision/detector/MediaPipeHandLandmarkDetector.kt`
- `vision/detector/ObjectDetector.kt`
- `vision/detector/YoloObjectDetector.kt`
- `vision/model/VisionResult.kt`
- `vision/PhoneCameraViewModel.kt`
- `vision/VisionEngine.kt` (interface)
- `vision/VisionEngineImpl.kt`
- `vision/VisionModule.kt`

**Gesture:**
- `gesture/GestureModule.kt`
- `gesture/GestureRecognizer.kt` (interface)
- `gesture/GestureRecognizerImpl.kt`
- `gesture/model/GaryCommand.kt`
- `gesture/model/RawGestureEvent.kt`
- `gesture/source/CameraGestureSourceImpl.kt`
- `gesture/source/GestureSource.kt` (interface)
- `gesture/source/LandmarkGestureClassifier.kt` (interface)
- `gesture/source/LandmarkGestureClassifierImpl.kt`

**UI:**
- `ui/components/StatusDot.kt`
- `ui/main/MainScreen.kt`
- `ui/main/MainViewModel.kt`
- `ui/onboarding/OnboardingScreen.kt`
- `ui/permissions/PermissionsScreen.kt`
- `ui/settings/SettingsScreen.kt`

**Tests (unit):**
- `GestureRecognizerImplTest.kt`
- `CameraGestureSourceImplTest.kt`
- `LandmarkGestureClassifierImplTest.kt`
- `FrameRingBufferTest.kt`
- `VisionEngineImplTest.kt`

**What Phase 1 finishes:** Wire these together, run on device, confirm video renders and pinch fires a log event.
