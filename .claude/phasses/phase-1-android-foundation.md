# Phase 1 — Android Foundation

**Timeline:** Weeks 1–4  
**Milestone:** M2 (Android App)  
**Exit criteria:** App installs on device, BLE scans/connects, MJPEG video renders on screen, pinch gesture fires a log event. No cloud needed.

---

## What This Phase Builds

The complete local layer: glasses connection + camera stream + ML vision + gesture recognition + basic UI. Everything runs on-device, nothing hits the network except the MJPEG stream from the glasses (or phone camera as stand-in).

**Phase 1 does NOT include:** voice, cloud WebSocket, smart home, billing.

---

## Feature Breakdown

### Feature 1.1 — Project Setup & Build System

**Goal:** App builds and installs on a real Android device.

**Files to verify/complete:**
- `android/build.gradle.kts` — project-level Gradle config
- `android/app/build.gradle.kts` — app module: dependencies, compileSdk, minSdk (26)
- `android/gradle/libs.versions.toml` — version catalog

**Dependencies to confirm are in libs.versions.toml:**
```toml
[versions]
kotlin = "2.0.x"
compose-bom = "2024.x.x"
hilt = "2.51.x"
mediapipe = "0.10.x"
okhttp = "4.12.x"
livekit-android = "2.x.x"
opencv = "4.10.x"

[libraries]
# Compose
compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "compose-bom" }
compose-ui = { group = "androidx.compose.ui", name = "ui" }
compose-material3 = { group = "androidx.compose.material3", name = "material3" }
compose-navigation = { group = "androidx.navigation", name = "navigation-compose" }

# Hilt DI
hilt-android = { group = "com.google.dagger", name = "hilt-android", version.ref = "hilt" }
hilt-compiler = { group = "com.google.dagger", name = "hilt-android-compiler", version.ref = "hilt" }
hilt-navigation = { group = "androidx.hilt", name = "hilt-navigation-compose" }

# ML
mediapipe-tasks-vision = { group = "com.google.mediapipe", name = "tasks-vision", version.ref = "mediapipe" }

# Network
okhttp = { group = "com.squareup.okhttp3", name = "okhttp", version.ref = "okhttp" }
okhttp-ws = { group = "com.squareup.okhttp3", name = "okhttp-urlconnection", version.ref = "okhttp" }

# LiveKit
livekit-android = { group = "io.livekit", name = "livekit-android", version.ref = "livekit-android" }

# OpenCV (ArUco)
# Add as AAR from opencv.org/releases

# Billing
billing = { group = "com.android.billingclient", name = "billing-ktx", version = "7.x.x" }
```

**AndroidManifest.xml — confirm all permissions are declared:**
```xml
<uses-permission android:name="android.permission.BLUETOOTH_SCAN"
    android:usesPermissionFlags="neverForLocation" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
<uses-permission android:name="android.permission.BLUETOOTH_ADVERTISE" />
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MICROPHONE" />

<service
    android:name=".glasses.service.GlassesForegroundService"
    android:foregroundServiceType="connectedDevice|microphone"
    android:exported="false" />
```

**Testing 1.1:**
- `./gradlew assembleDebug` — must succeed with zero errors
- Install APK on physical device, app opens to MainScreen
- Check logcat for no crash on startup

---

### Feature 1.2 — Permissions Screen

**Goal:** All required permissions requested in sequence on first launch. App remembers granted state across restarts.

**Files:**
- `core/permissions/RequiredPermissions.kt` — list: BLUETOOTH_SCAN, BLUETOOTH_CONNECT, RECORD_AUDIO, ACCESS_FINE_LOCATION
- `core/permissions/PermissionState.kt` — sealed class: Granted, Denied, PermanentlyDenied
- `ui/permissions/PermissionsScreen.kt` — walks user through each permission with explanation

**Implementation notes:**
- Use `rememberMultiplePermissionsState` from Accompanist (or Compose built-in `rememberPermissionState`)
- BLUETOOTH_SCAN requires `android.permission.ACCESS_FINE_LOCATION` on API <31 — handle both
- If PermanentlyDenied: show "Open Settings" button → `Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)`
- Store granted state in `DataStore<Preferences>` — NOT SharedPreferences (SharedPreferences is not coroutine-safe)

**Flow:**
```
App launch → PermissionsScreen → user grants all → navigate to OnboardingScreen or MainScreen
```

**Testing 1.2:**
- Manually deny each permission, confirm screen handles PermanentlyDenied state
- Revoke all permissions in Android settings, relaunch — permissions screen must appear
- Grant all, kill + reopen app — must go straight to MainScreen (no re-ask)

---

### Feature 1.3 — GlassesManager: BLE Scan + Connect

**Goal:** App finds the ESP32 by its BLE advertisement name, connects, and shows connection state in the UI. State machine works correctly.

**Files:**
- `glasses/ble/BleScanner.kt` — scans for device named "Pinch-Glasses" (or service UUID)
- `glasses/ble/BleGattProfile.kt` — defines service/characteristic UUIDs for:
  - Battery level
  - Touch/tap events
  - LED control commands
  - Bone conduction audio (write characteristic)
- `glasses/ble/BleConnectionManager.kt` — GATT connect, discover services, subscribe to notifications
- `glasses/GlassesManagerImpl.kt` — implements state machine:
  ```
  Disconnected → Scanning → BLE_Connected → Streaming
  ```
  - Auto-reconnect with exponential backoff (1s → 2s → 4s → 8s → max 30s)
  - On app foreground: restart scan if Disconnected
- `glasses/service/GlassesForegroundService.kt` — keeps BLE alive when app is backgrounded
  - Persistent notification: "Gary is connected" or "Gary is looking for glasses"
  - foregroundServiceType = connectedDevice
- `glasses/GlassesViewModel.kt` — exposes `StateFlow<GlassesConnectionState>` to UI

**State machine rules:**
- `Scanning` timeout after 30 seconds → back to `Disconnected` (don't drain battery scanning forever)
- `BLE_Connected` → start MJPEG socket connection (Feature 1.4)
- BLE disconnect → drop to `BLE_Connected` immediately → back to `Disconnected` after 3 failed reconnects
- Foreground Service MUST be started before `startScan()` on API 31+

**For development without hardware:**
- Use a second Android phone running a BLE peripheral emulator app (e.g., nRF Connect) to simulate the ESP32
- Or: skip BLE and test MJPEG stream directly from a local URL

**Testing 1.3:**
- Confirm `GlassesForegroundService` starts and shows persistent notification
- Simulate ESP32 with nRF Connect app on a second phone:
  - Advertise with name "Pinch-Glasses"
  - Confirm app scans, finds, connects
  - Close nRF Connect → app must attempt reconnect with backoff
- Rotate device → confirm ViewModel state survives (Hilt ViewModel + Foreground Service owns the state)
- Background the app → confirm BLE stays connected (check notification persists)

---

### Feature 1.4 — MJPEG Stream + Frame Ring Buffer

**Goal:** Phone decodes MJPEG stream from glasses (or test source) into frames held in a 3–5 frame ring buffer. VisionEngine can grab latest frame on demand. No frames written to disk.

**Files:**
- `glasses/mjpeg/MjpegStreamClient.kt` — OkHttp-based streaming HTTP client
  - Parses multipart/x-mixed-replace boundary frames
  - Emits `ByteArray` JPEG frames to a coroutine `Channel<ByteArray>`
  - Runs on `Dispatchers.IO`
  - Auto-reconnects if stream drops
- `glasses/mjpeg/FrameRingBuffer.kt` — thread-safe ring buffer
  - Capacity: 5 frames
  - `push(frame: ByteArray)` — overwrites oldest
  - `latest(): ByteArray?` — returns most recent frame
  - `latestAsBitmap(): Bitmap?` — decodes JPEG → Bitmap for ML
  - MUST NOT call `File.write()` anywhere in this class

**For development without glasses:**
- Use phone camera via `CameraXPhoneCameraSource.kt` as the frame source
- Or: point to a test MJPEG server (many IP webcam apps serve MJPEG)

**Latency budget:**
- Frame decode must complete in <10ms (JPEG decode is fast, BitmapFactory is fine)
- Ring buffer lock must be < 1ms

**Testing 1.4:**
- `FrameRingBufferTest.kt` — unit tests for push/latest behavior, overflow, concurrency
- Point MjpegStreamClient at a test MJPEG URL (or run a local ffmpeg MJPEG server)
- Confirm frames arrive and ring buffer holds correct count
- Confirm `File.write()` is never called (grep the codebase — add a CI lint rule)
- Check RAM: ring buffer should stay ≤ 250KB (5 frames × ~50KB JPEG each)

---

### Feature 1.5 — VisionEngine: MediaPipe Hands + YOLO + ArUco

**Goal:** Every new frame from the ring buffer is processed by three ML models. Results are emitted to observers. All ML runs on-device, zero cloud cost per frame.

**Files:**
- `vision/VisionEngine.kt` (interface)
  ```kotlin
  interface VisionEngine {
      val results: StateFlow<VisionResult>
      fun start()
      fun stop()
  }
  ```
- `vision/VisionEngineImpl.kt` — drives the pipeline:
  1. Grabs latest frame from ring buffer every ~33ms (30fps budget)
  2. Decodes JPEG → Bitmap
  3. Runs three models in parallel coroutines:
     - `HandLandmarkDetector.detect(bitmap)` → List<HandLandmarks>
     - `ObjectDetector.detect(bitmap)` → List<DetectedObject>
     - ArUco detector → List<Int> (marker IDs)
  4. Merges results into `VisionResult`, emits to StateFlow
- `vision/detector/MediaPipeHandLandmarkDetector.kt`
  - Uses `HandLandmarker` from MediaPipe Tasks Android
  - Must init on background thread, not main thread
  - Returns 21 landmarks per detected hand
- `vision/detector/YoloObjectDetector.kt`
  - Loads `yolov8n.tflite` from `assets/`
  - Input: 640×640 normalized RGB
  - Output: bounding boxes + class label + confidence
  - Use TFLite GPU delegate when available, CPU fallback
- ArUco detector (inline in VisionEngineImpl or separate class)
  - Requires OpenCV Android SDK (add as local AAR module)
  - `Calib3d.detectMarkers(mat, dict, corners, ids)`
  - Returns list of marker IDs visible in frame
- `vision/model/VisionResult.kt`
  ```kotlin
  data class VisionResult(
      val hands: List<HandLandmarks>,
      val objects: List<DetectedObject>,
      val arUcoMarkers: List<Int>,
      val timestamp: Long
  )
  ```

**Model files to add to `assets/`:**
- `hand_landmarker.task` — download from MediaPipe model hub
- `yolov8n.tflite` — download from Ultralytics (float16 version)
- ArUco dictionary: built into OpenCV, no file needed

**Performance targets:**
- MediaPipe Hands: ~15ms
- YOLOv8n: ~20ms (GPU delegate)
- ArUco: ~8ms
- Total VisionEngine cycle: <35ms on mid-range phone

**Testing 1.5:**
- `VisionEngineImplTest.kt` — mock frame source, verify all three models are called
- Manual test: point phone camera at hand → confirm landmarks visible in logs
- Manual test: hold ArUco marker printout in front of camera → confirm marker ID emitted
- Memory test: run for 60 seconds → confirm no frame accumulation (adb shell dumpsys meminfo)

---

### Feature 1.6 — GestureRecognizer: Camera-based Pinch (v0)

**Goal:** MediaPipe hand landmarks → `GaryCommand` events. Specifically: pinch gesture → `GaryCommand.Wake`. This is the v0 camera-based implementation. Radar (v1) replaces this later without changing the command interface.

**Files:**
- `gesture/source/GestureSource.kt` (interface)
  ```kotlin
  interface GestureSource {
      val events: Flow<RawGestureEvent>
  }
  ```
- `gesture/source/LandmarkGestureClassifier.kt` (interface)
  - Input: `List<HandLandmarks>`
  - Output: `RawGestureEvent?`
- `gesture/source/LandmarkGestureClassifierImpl.kt` — implements pinch detection:
  - Pinch = distance between thumb tip (landmark 4) and index tip (landmark 8) < threshold (~40px normalized)
  - Debounce: minimum 500ms between consecutive same-gesture events
  - Stateful: track gesture across frames for hold detection (>500ms hold = push-to-talk)
- `gesture/source/CameraGestureSourceImpl.kt` — subscribes to VisionEngine.results, runs classifier, emits RawGestureEvents
- `gesture/GestureRecognizer.kt` (interface)
  ```kotlin
  interface GestureRecognizer {
      val commands: Flow<GaryCommand>
  }
  ```
- `gesture/GestureRecognizerImpl.kt` — maps RawGestureEvent → GaryCommand:
  - `Pinch` → `GaryCommand.Wake`
  - `HoldPinch` → `GaryCommand.PushToTalk`
  - `SwipeRight` → `GaryCommand.Next`
  - `SwipeLeft` → `GaryCommand.Back`
  - `SwipeUp` → `GaryCommand.VolumeUp`
  - `SwipeDown` → `GaryCommand.VolumeDown`
  - `DoublePinch` → `GaryCommand.ModeSwitch`
  - `AirTap` → `GaryCommand.Select`
  - `Grab` → `GaryCommand.Stop`
- `gesture/model/GaryCommand.kt` — sealed class with all the above commands
- `gesture/model/RawGestureEvent.kt` — raw gesture types before mapping

**Swipe detection (camera-based):**
- Track index fingertip x/y position across 10 frames
- Swipe = movement > 80px in one direction in <300ms
- Only detect swipe when hand is NOT in pinch position

**Testing 1.6:**
- `LandmarkGestureClassifierImplTest.kt` — feed fake landmark coordinates, verify correct gesture
- `CameraGestureSourceImplTest.kt` — mock VisionEngine, verify events emitted
- `GestureRecognizerImplTest.kt` — verify RawGestureEvent → GaryCommand mapping
- Manual test on device: perform pinch → confirm "GaryCommand.Wake" in logcat

---

### Feature 1.7 — GaryOrchestrator: Wire Everything Together

**Goal:** The top-level coordinator that listens to GestureRecognizer commands and takes action. In Phase 1, the only action is logging. In Phase 2, it will trigger VoiceSession.

**Files:**
- `core/appstate/GaryOrchestrator.kt`
  ```kotlin
  // In Phase 1:
  // GaryCommand.Wake → log "Gary: WAKE command received"
  // GaryCommand.PushToTalk → log "Gary: PTT start"
  // Everything else → log command name
  ```
- `core/appstate/AppState.kt` — sealed class:
  - `Idle` — glasses connected, vision running, waiting
  - `WakeDetected` — pinch received, transitioning to voice
  - `Listening` — mic open, recording
  - `Thinking` — STT done, waiting for LLM
  - `Speaking` — TTS playing
  - `Error(message: String)`

**Testing 1.7:**
- Perform pinch gesture → confirm AppState transitions from Idle → WakeDetected in UI
- MainScreen StatusDot must change color on state change (visual confirmation)

---

### Feature 1.8 — MainScreen UI

**Goal:** Minimal UI that shows glasses connection state and Gary's current state. No buttons — the only interaction is through gestures.

**Files:**
- `ui/main/MainScreen.kt` — Compose screen:
  - Center: small StatusDot (8dp circle)
    - Grey = Idle/Disconnected
    - Green = Connected + Ready
    - Blue (pulsing) = Listening
    - Yellow (pulsing) = Thinking
    - Purple (pulsing) = Speaking
  - Below dot: state label ("Idle", "Listening…", "Thinking…", "Speaking")
  - Top-right: glasses battery percentage (if connected)
  - No other buttons or controls
- `ui/main/MainViewModel.kt` — exposes:
  ```kotlin
  val uiState: StateFlow<MainUiState>
  data class MainUiState(
      val appState: AppState,
      val glassesBattery: Int?,
      val connectionState: GlassesConnectionState
  )
  ```
- `ui/components/StatusDot.kt` — animated dot with color transitions

**Design rules:**
- Background: pure black (#000000) 
- Text: white, small, center-aligned
- No icons, no hamburger menu, no FAB
- Font: system default (no custom fonts needed for MVP)

**Testing 1.8:**
- Run app on device, confirm all state labels and colors render correctly
- Use logcat to artificially trigger state changes, confirm UI updates

---

### Feature 1.9 — OnboardingScreen + SettingsScreen

**Goal:** First-run experience. User pairs glasses, sets HA URL, grants permissions. Settings screen lets user update these later.

**Files:**
- `ui/onboarding/OnboardingScreen.kt` — multi-step pager:
  - Step 1: "Welcome to Pinch" + illustration
  - Step 2: Permissions (inline, calls PermissionsScreen content)
  - Step 3: "Put on your glasses and hold the touch strip" → waits for BLE connect
  - Step 4: Smart home setup (HA URL input — optional, can skip)
  - Step 5: "You're set. Gary is ready."
- `ui/settings/SettingsScreen.kt`:
  - Home Assistant URL (text field)
  - Subscription status (placeholder for Phase 8)
  - "Forget glasses" (clears paired device)
  - App version

**DataStore keys (in `core/preferences/UserPreferences.kt` — new file):**
```kotlin
val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
val HA_URL = stringPreferencesKey("ha_url")
val PAIRED_DEVICE_ADDRESS = stringPreferencesKey("paired_device_address")
val GEOFENCE_LAT = floatPreferencesKey("geofence_lat")
val GEOFENCE_LNG = floatPreferencesKey("geofence_lng")
```

**Testing 1.9:**
- Fresh install: confirm onboarding shows on first launch
- Complete onboarding, kill + relaunch: confirm main screen shows directly
- Settings screen: change HA URL, relaunch, confirm URL persists

---

## Phase 1 Integration Test (Device)

Run through this checklist on a real Android device before marking Phase 1 done:

- [ ] App installs clean from `./gradlew installDebug`
- [ ] First launch → OnboardingScreen appears
- [ ] All permissions granted without crash
- [ ] After onboarding → MainScreen shows Idle state
- [ ] Persistent notification appears ("Gary is looking for glasses")
- [ ] Simulate BLE device (nRF Connect) → notification changes to "Gary is connected"
- [ ] UI shows "Connected" state with green dot
- [ ] Phone camera source active → VisionEngine processing frames
- [ ] Logcat shows YOLO detections every ~33ms
- [ ] Hold pinch gesture in front of camera → "GaryCommand.Wake" in logcat
- [ ] App backgrounded → BLE foreground service keeps running (notification stays)
- [ ] Device rotated → app does not crash, state preserved
- [ ] RAM usage after 60 seconds: under 200MB

---

## Phase 1 Exit Criteria

All of the following must be true before starting Phase 2:

1. App builds with zero errors and zero warnings (except deprecation warnings)
2. App installs and runs on Android 10+ device
3. Gesture test: pinch in front of camera → `GaryCommand.Wake` fires
4. Ring buffer test: 5 frames max in memory, no disk writes
5. Vision engine test: all three models (MediaPipe, YOLO, ArUco) produce output
6. BLE foreground service runs when app is backgrounded
7. All unit tests pass: `./gradlew test`
