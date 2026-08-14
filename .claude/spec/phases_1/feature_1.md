# Feature 1 — Project Setup & Build System

**Phase:** Phase 1 — Android Foundation  
**Status:** Not started  
**Session name:** Feature 1  
**Ref:** `.claude/phasses/phase-1-android-foundation.md` → Feature 1.1  

---

## Goal

Confirm the existing Android scaffold compiles to a working APK and runs on a real Android device without crashing. The scaffold code already exists — this feature is pure verification + fixup, not greenfield implementation.

No new product logic. No UI changes. No new Kotlin files unless a build error forces it.

---

## What Already Exists

The `android/` scaffold was generated with all structure in place. Before writing a single line of new code, we need to verify every piece is correct and consistent.

### `android/gradle/libs.versions.toml` — current state

| Library group | Version pinned | Status |
|---|---|---|
| Kotlin | 2.0.20 | OK |
| AGP | 8.5.2 | OK |
| KSP | 2.0.20-1.0.24 | OK (matches Kotlin) |
| Compose BOM | 2024.09.00 | OK |
| Navigation Compose | 2.8.0 | OK |
| Hilt | 2.51.1 | OK |
| OkHttp | 4.12.0 | OK |
| MediaPipe Tasks Vision | 0.10.14 | OK |
| TFLite Support | 0.4.4 | OK |
| CameraX | 1.3.4 | OK |
| DataStore | 1.1.1 | OK |
| Coroutines | 1.8.1 | OK |
| LiveKit Android | 2.9.1 | Cataloged, NOT wired — correct for now |
| OpenCV | 4.10.0 | Cataloged, NOT wired — correct for now |
| Billing | 7.1.1 | Cataloged, NOT wired — correct for now |

### `android/app/build.gradle.kts` — current state

- `compileSdk = 34`, `minSdk = 26`, `targetSdk = 34` ✓
- `versionCode = 1`, `versionName = "0.1.0"` ✓
- Plugins: android-application, kotlin-android, kotlin-compose, kotlin-serialization, ksp, hilt ✓
- Dependencies wired: core-ktx, lifecycle, activity-compose, navigation-compose, datastore, compose-bom stack, hilt, okhttp, serialization, coroutines, mediapipe-tasks-vision, tflite-support, camerax (core + camera2 + lifecycle) ✓
- NOT wired (intentional): opencv, livekit-android, billing-ktx ✓
- Test deps: junit, mockk, turbine, coroutines-test ✓

### `android/app/src/main/AndroidManifest.xml` — current state

Permissions declared:
- `BLUETOOTH_SCAN` (with `neverForLocation` flag) ✓
- `BLUETOOTH_CONNECT` ✓
- `BLUETOOTH_ADVERTISE` ✓
- `RECORD_AUDIO` ✓
- `ACCESS_FINE_LOCATION` ✓
- `ACCESS_COARSE_LOCATION` ✓
- `ACCESS_BACKGROUND_LOCATION` ✓
- `CAMERA` ✓ (needed for CameraX phone-camera source, ADR-010)
- `INTERNET` ✓
- `ACCESS_NETWORK_STATE` ✓
- `FOREGROUND_SERVICE` ✓
- `FOREGROUND_SERVICE_CONNECTED_DEVICE` ✓
- `FOREGROUND_SERVICE_MICROPHONE` ✓
- `POST_NOTIFICATIONS` ✓

Service declared:
```xml
<service
    android:name=".glasses.service.GlassesForegroundService"
    android:exported="false"
    android:foregroundServiceType="connectedDevice|microphone" />
```
✓

### Scaffold files that already exist

All of the following exist and contain stub implementations:
- `GaryApplication.kt` — Hilt `@HiltAndroidApp`
- `MainActivity.kt` — entry point + nav host
- `core/di/` — AppScopeModule, DispatchersModule, NetworkModule
- `core/navigation/` — Destinations, GaryNavHost
- `core/permissions/` — RequiredPermissions, PermissionState
- `core/theme/` — Color, GaryTheme, Shape, Type
- `core/appstate/` — AppState, GaryOrchestrator
- `glasses/` — GlassesManager, GlassesManagerImpl, GlassesModule, GlassesViewModel, BleScanner, BleGattProfile, BleConnectionManager, GlassesConnectionState, GlassesDevice, GlassesForegroundService, MjpegStreamClient, FrameRingBuffer
- `vision/` — VisionEngine, VisionEngineImpl, VisionModule, PhoneCameraViewModel, BitmapFrameDecoder, DecodedFrame, FrameDecoder, HandLandmarkDetector, MediaPipeHandLandmarkDetector, ObjectDetector, YoloObjectDetector, VisionResult, CameraXPhoneCameraSource, PhoneCameraSource
- `gesture/` — GestureRecognizer, GestureRecognizerImpl, GestureModule, GaryCommand, RawGestureEvent, GestureSource, LandmarkGestureClassifier, LandmarkGestureClassifierImpl, CameraGestureSourceImpl
- `ui/` — MainScreen, MainViewModel, StatusDot, PermissionsScreen, OnboardingScreen, SettingsScreen
- Tests: FrameRingBufferTest, VisionEngineImplTest, GestureRecognizerImplTest, CameraGestureSourceImplTest, LandmarkGestureClassifierImplTest

---

## What This Session Must Do

This is a **verify and fix** session, not a build session.

### Step 1 — Run the build
```
cd android && ./gradlew assembleDebug
```
Expected: `BUILD SUCCESSFUL`. If not, fix every compiler error before moving on.

### Step 2 — Verify unit tests compile and pass
```
./gradlew test
```
Expected: all test classes compile and pass (stubs may be trivial — that's fine).

### Step 3 — Install on physical device
```
./gradlew installDebug
```
- Open app on phone — must not crash immediately
- Check logcat for 30 seconds: no `FATAL EXCEPTION`, no missing Hilt providers, no `InflateException`

### Step 4 — Spot-check stub correctness (read, do not change unless broken)
- `GaryApplication.kt` — must be annotated `@HiltAndroidApp`
- `MainActivity.kt` — must call `setContent { GaryNavHost() }` (or equivalent)
- `GlassesForegroundService.kt` — must call `startForeground()` with a valid notification
- `FrameRingBuffer.kt` — must never call `File.write()` or any disk I/O

### Step 5 — Confirm model asset stubs exist (or note what's missing)
The following are needed by `MediaPipeHandLandmarkDetector` and `YoloObjectDetector`:
- `android/app/src/main/assets/hand_landmarker.task`
- `android/app/src/main/assets/yolov8n.tflite`

If missing: note it, but DO NOT download them here. Those are wired in Feature 5 (VisionEngine). For Feature 1, the asset absence is OK as long as it doesn't crash the app at startup.

---

## Acceptance Criteria

Feature 1 is complete when ALL of the following are true:

- [ ] `./gradlew assembleDebug` exits `BUILD SUCCESSFUL` with zero compilation errors
- [ ] `./gradlew test` exits with zero test failures  
- [ ] APK installs on a physical Android 10+ device via `./gradlew installDebug`
- [ ] App opens to its start screen (MainScreen or OnboardingScreen) without crashing
- [ ] Logcat shows no `FATAL EXCEPTION` in the first 30 seconds
- [ ] Logcat shows no missing Hilt binding errors
- [ ] `FrameRingBuffer.kt` contains no `File.write()`, `FileOutputStream`, or `OutputStreamWriter` calls (grep check)

---

## What Is Explicitly OUT OF SCOPE

Do not implement any of the following in this session:

- BLE scanning or connection logic (Feature 3)
- MJPEG stream logic (Feature 4)
- ML model loading or inference (Feature 5)
- Gesture detection logic (Feature 6)
- Permission request flow (Feature 2)
- Smart home, voice, cloud (Phase 2+)

If the build fails due to a missing stub in one of these areas, add the minimum stub to unblock compilation — nothing more.

---

## Known Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| Hilt `@InstallIn` mismatch causes `MissingBinding` at runtime | Medium | Check DI modules define all injected types |
| MediaPipe or TFLite AAR triggers `PackagingOptions` conflict | Low | Already handled in `packaging { excludes += … }` |
| CameraX permission not declared → crash on first frame request | Low | `CAMERA` is already in manifest |
| `GlassesForegroundService.startForeground()` called without valid notification channel → crash on API 26+ | Medium | Verify notification channel is created in `GaryApplication.onCreate()` |
| Missing `assets/` folder causes `IOException` at MediaPipe init | Medium | Confirm MediaPipe/YOLO init is lazy (not in `Application.onCreate()`) |

---

## Files to Read Before Planning

The plan should be based on the actual file contents, not these notes. Read these before generating any fix:

- `android/app/build.gradle.kts`
- `android/gradle/libs.versions.toml`
- `android/app/src/main/AndroidManifest.xml`
- `android/app/src/main/java/com/pinch/gary/GaryApplication.kt`
- `android/app/src/main/java/com/pinch/gary/glasses/service/GlassesForegroundService.kt`
- `android/app/src/main/java/com/pinch/gary/glasses/mjpeg/FrameRingBuffer.kt`
- `android/app/src/main/java/com/pinch/gary/vision/VisionEngineImpl.kt`

---

## Done When

Mark Feature 1 `[x]` in `current.md` when:

1. `./gradlew assembleDebug` → BUILD SUCCESSFUL
2. APK opens on device without crash
3. No logcat errors in first 30 seconds
