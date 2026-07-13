# Build vision/ + gesture/ — camera-only v0 (Week 3-4, per ADR-010)

## Context

Pinch/Gary's Android build order (CLAUDE.md) has shipped `glasses/` (weeks 1-2: BLE + MJPEG, state machine, `FrameRingBuffer`). CLAUDE.md and `.claude/save/architecture-decisions.md` were updated mid-session to add **ADR-010**: no glasses hardware (no ESP32, no BGT60TR13C radar) exists yet, so `GestureRecognizer` v0 is camera-only — MediaPipe hand landmarks via the phone's own camera, not radar. Radar is deferred to the next hardware prototype. To avoid rework then, gesture input goes through a `GestureSource` interface so radar becomes a second implementation later without touching command-mapping/debounce logic.

This replaces an earlier draft of this plan (written before ADR-010 landed), which assumed real ESP32/radar hardware and had `GestureRecognizer` consume `GlassesManager.events` as primary input. That assumption no longer holds. Nothing in this build should require glasses hardware — `VisionEngine` still reads from `GlassesManager`'s `FrameRingBuffer` (unchanged buffer, unchanged consumer contract), but the producer feeding it is now either fake frames (unit tests) or **the phone's own camera via CameraX** (real on-device verification), not the ESP32 MJPEG stream. User confirmed: build full CameraX wiring now (new `CAMERA` permission, real capture, lifecycle-bound to `MainScreen`) so gesture recognition is actually verifiable on-device this session, per CLAUDE.md's "always verify on device" rule.

Confirmed via repo reads: no `.tflite`/`.task` model assets exist, no MediaPipe/TFLite/CameraX code exists yet, `GaryOrchestrator.start()` is called from `GaryApplication.onCreate()` (no `LifecycleOwner` there — camera binding must happen from `MainActivity`/`MainScreen`, which do have one). `PermissionsScreen.kt` currently requests BLE + location only; `RequiredPermissions.kt` has no camera group yet. `AndroidManifest.xml` has no `CAMERA` permission.

This also establishes the repo's first StateFlow/SharedFlow/coroutine test pattern (MockK + Turbine + kotlinx-coroutines-test are on the classpath, unused so far).

## Key architectural resolution (supersedes the old plan's ADR-007 workaround)

Previously, `GestureRecognizer` needed both `GlassesManager` and `VisionEngine` as inputs, which would've violated ADR-007 (only `GaryOrchestrator` may depend on >1 manager) — the old plan worked around this with orchestrator-pushed input methods. **ADR-010's `GestureSource` abstraction removes the problem entirely**: `GestureRecognizerImpl` depends on exactly one thing, `GestureSource` — an internal collaborator *within* the `gesture/` package, not a second feature manager. `CameraGestureSourceImpl` (the v0 impl) is the one that depends on `VisionEngine` (a single manager — ADR-007-compliant). When radar hardware lands, a `RadarGestureSourceImpl` depending on `GlassesManager` gets added as a sibling, and `GestureModule`'s `@Binds` is the only line that changes to switch — `GestureRecognizerImpl` never changes.

## 1. `vision/` package (mostly unchanged from the original plan)

```
android/app/src/main/java/com/pinch/gary/vision/
├── VisionEngine.kt              interface: visionResult: StateFlow<VisionResult?>, isRunning: StateFlow<Boolean>, start(), stop()
├── VisionEngineImpl.kt          @Singleton, polls FrameRingBuffer.latest() every 66ms (~15fps), dedups by reference equality, decode → detect hands → detect objects → emit VisionResult
├── VisionModule.kt              Hilt @Binds module
├── decode/
│   ├── DecodedFrame.kt          empty marker interface — real impl wraps android.graphics.Bitmap; test fakes are `object : DecodedFrame {}`
│   ├── FrameDecoder.kt          interface: decode(jpegBytes: ByteArray): DecodedFrame?
│   └── BitmapFrameDecoder.kt    real impl (BitmapFactory.decodeByteArray) — only class touching android.graphics.*, not unit tested (Robolectric-only gap, flagged in feature doc)
├── detector/
│   ├── HandLandmarkDetector.kt          interface: detect(frame: DecodedFrame): List<HandLandmarks>
│   ├── MediaPipeHandLandmarkDetector.kt real class, TODO-stub detect() (logs + returns emptyList) pending hand_landmarker.task asset — mirrors BleGattProfile's placeholder-UUID pattern
│   ├── ObjectDetector.kt                interface: detect(frame: DecodedFrame): List<DetectedObject>
│   └── YoloObjectDetector.kt            same shape, TODO-stub pending yolov8n.tflite
├── camera/
│   ├── PhoneCameraSource.kt         interface: fun bind(lifecycleOwner: LifecycleOwner), fun unbind()
│   └── CameraXPhoneCameraSource.kt  real CameraX impl (below) — v0 substitute for the ESP32 camera
└── model/
    └── VisionResult.kt   VisionResult, HandLandmarks, NormalizedLandmark (+ Handedness), DetectedObject, BoundingBox — documents MediaPipe's 21-point index convention inline (gesture/'s classifier depends on it)
```

**Testability boundary** (unchanged from original plan): `FrameDecoder`/`HandLandmarkDetector`/`ObjectDetector` interfaces let `VisionEngineImpl`'s polling/dedup/emission logic be fully unit tested on the plain JVM with MockK fakes. `BitmapFrameDecoder`, the two detector impls, and `CameraXPhoneCameraSource` are the only classes touching real Android/ML/CameraX APIs — explicitly out of unit-test scope, manual-verification only.

**`CameraXPhoneCameraSource`** (new — the piece ADR-010 requires):
```kotlin
@Singleton
class CameraXPhoneCameraSource @Inject constructor(
    @ApplicationContext private val context: Context,
    private val frameRingBuffer: FrameRingBuffer
) : PhoneCameraSource {
    private var cameraProvider: ProcessCameraProvider? = null

    override fun bind(lifecycleOwner: LifecycleOwner) {
        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()
        analysis.setAnalyzer(ContextCompat.getMainExecutor(context)) { imageProxy ->
            imageProxy.toBitmap().toJpegBytes()?.let(frameRingBuffer::push)
            imageProxy.close()
        }
        val provider = ProcessCameraProvider.getInstance(context).get()
        provider.unbindAll()
        provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, analysis)
        cameraProvider = provider
    }

    override fun unbind() {
        cameraProvider?.unbindAll()
        cameraProvider = null
        frameRingBuffer.clear()
    }
}
```
Uses `ImageAnalysis` with `OUTPUT_IMAGE_FORMAT_RGBA_8888` (CameraX 1.3+) + `ImageProxy.toBitmap()` rather than hand-rolling YUV_420_888→NV21 conversion — simpler, and per-frame cost is irrelevant here since this is a dev/test substitute, not shipping firmware. `Bitmap.compress(JPEG, quality, ByteArrayOutputStream)` produces the JPEG bytes handed to `frameRingBuffer.push()`, keeping `FrameRingBuffer`'s contract (JPEG `ByteArray`) identical regardless of producer, so `VisionEngineImpl`/`FrameDecoder` need zero changes. Everything stays RAM-only — `ByteArrayOutputStream`, never a file. `unbind()` clears the ring buffer (mirrors `GlassesManagerImpl.disconnect()`'s existing behavior) so no stale frame lingers once the camera stops.

`VisionModule` additionally binds `PhoneCameraSource` → `CameraXPhoneCameraSource`.

## 2. `gesture/` package (redesigned around `GestureSource`)

```
android/app/src/main/java/com/pinch/gary/gesture/
├── GestureRecognizer.kt         interface: commands: SharedFlow<GaryCommand>, start(), stop()
├── GestureRecognizerImpl.kt     @Singleton, depends only on GestureSource — debounce + RawGestureType→GaryCommand mapping
├── GestureModule.kt             @Binds GestureRecognizer, @Binds GestureSource → CameraGestureSourceImpl (the one line that changes when radar lands)
├── model/
│   ├── GaryCommand.kt        sealed interface: Pinch, DoublePinch, HoldPinch, SwipeRight, SwipeLeft, SwipeUp, SwipeDown, AirTap, Grab, ThumbSlide(delta: Float)
│   └── RawGestureEvent.kt    RawGestureType enum (same 10 values as GaryCommand, pre-mapping) + RawGestureEvent(type, magnitude: Float?, timestampMs: Long) — the common currency every GestureSource impl emits, gesture/'s own type (no dependency on glasses/model, keeps v0 fully decoupled from glasses/)
└── source/
    ├── GestureSource.kt              interface: events: Flow<RawGestureEvent>, start(), stop()
    ├── CameraGestureSourceImpl.kt    @Singleton, v0 impl — adapts VisionEngine.visionResult into RawGestureEvent via LandmarkGestureClassifier
    └── LandmarkGestureClassifier.kt  pure, stateful heuristic engine — no coroutines, no mocks needed to test
```

**`GestureRecognizerImpl`** — single dependency, plain coroutine collector:
```kotlin
private const val RADAR_DEBOUNCE_MS = 200L  // suppresses duplicate identical events; THUMB_SLIDE exempt (legitimately repeats)

@Singleton
class GestureRecognizerImpl @Inject constructor(
    private val gestureSource: GestureSource,
    @ApplicationScope private val scope: CoroutineScope
) : GestureRecognizer {
    private val _commands = MutableSharedFlow<GaryCommand>(extraBufferCapacity = 16)
    override val commands: SharedFlow<GaryCommand> = _commands.asSharedFlow()
    private var collectJob: Job? = null
    private var lastType: RawGestureType? = null
    private var lastAtMs: Long = 0L

    override fun start() {
        gestureSource.start()
        collectJob = scope.launch {
            gestureSource.events.collect { event ->
                val isDuplicate = event.type == lastType && event.type != RawGestureType.THUMB_SLIDE &&
                    (event.timestampMs - lastAtMs) < RADAR_DEBOUNCE_MS
                lastType = event.type; lastAtMs = event.timestampMs
                if (!isDuplicate) mapToCommand(event)?.let { _commands.tryEmit(it) }
            }
        }
    }
    override fun stop() { collectJob?.cancel(); gestureSource.stop(); lastType = null; lastAtMs = 0L }

    private fun mapToCommand(event: RawGestureEvent): GaryCommand? = when (event.type) {
        RawGestureType.PINCH -> GaryCommand.Pinch
        RawGestureType.DOUBLE_PINCH -> GaryCommand.DoublePinch
        RawGestureType.HOLD_PINCH -> GaryCommand.HoldPinch
        RawGestureType.SWIPE_RIGHT -> GaryCommand.SwipeRight
        RawGestureType.SWIPE_LEFT -> GaryCommand.SwipeLeft
        RawGestureType.SWIPE_UP -> GaryCommand.SwipeUp
        RawGestureType.SWIPE_DOWN -> GaryCommand.SwipeDown
        RawGestureType.AIR_TAP -> GaryCommand.AirTap
        RawGestureType.GRAB -> GaryCommand.Grab
        RawGestureType.THUMB_SLIDE -> GaryCommand.ThumbSlide(event.magnitude ?: 0f)
    }
}
```

**`CameraGestureSourceImpl`** — thin reactive adapter, delegates the actual heuristics to `LandmarkGestureClassifier`:
```kotlin
@Singleton
class CameraGestureSourceImpl @Inject constructor(
    private val visionEngine: VisionEngine,
    private val classifier: LandmarkGestureClassifier,
    @ApplicationScope private val scope: CoroutineScope
) : GestureSource {
    private val _events = MutableSharedFlow<RawGestureEvent>(extraBufferCapacity = 16)
    override val events: Flow<RawGestureEvent> = _events.asSharedFlow()
    private var job: Job? = null

    override fun start() {
        job = scope.launch {
            visionEngine.visionResult.filterNotNull().collect { result ->
                classifier.classify(result.handLandmarks, result.frameTimestampMs).forEach { _events.tryEmit(it) }
            }
        }
    }
    override fun stop() { job?.cancel() }
}
```

**`LandmarkGestureClassifier`** (MVP heuristics, using the 21-point index convention from `VisionResult.kt`, distances normalized against hand span = distance(wrist[0], middleMcp[9])):
- **Pinch/HoldPinch**: `distance(thumbTip[4], indexTip[8]) < 0.35 × handSpan`. Edge-triggered: on transition to "pinched," record `pinchStartedAtMs`; if still pinched at `now - pinchStartedAtMs >= 500`, fire `HoldPinch` once (`holdFired` flag guards repeats); on release, fire `Pinch` only if `holdFired` was false.
- **DoublePinch**: two `Pinch` releases within 400ms suppresses the first, emits `DoublePinch` instead.
- **Swipe (L/R/U/D)**: rolling ~300ms centroid (wrist + index-MCP) displacement vs. `0.15 × handSpan` threshold, classified by dominant axis sign.
- **AirTap**: rapid index-tip depth (z) extend-then-retract relative to wrist — approximate, flagged low-confidence in the feature doc.
- **Grab**: average of all 5 fingertip→wrist distances `< 0.4 × handSpan`.
- **ThumbSlide**: contact-but-not-full-pinch distance band; `magnitude` = normalized frame-to-frame thumb-x delta, emitted every sample while contact holds.

All thresholds are named constants with an inline `// TODO: calibrate against real hand size/lighting on-device` comment. Pure function of `(hands, timestampMs)` plus internal state → fully unit-testable with fabricated `HandLandmarks` sequences, zero mocking.

`GestureModule`:
```kotlin
@Module @InstallIn(SingletonComponent::class)
abstract class GestureModule {
    @Binds @Singleton abstract fun bindsGestureRecognizer(impl: GestureRecognizerImpl): GestureRecognizer
    @Binds @Singleton abstract fun bindsGestureSource(impl: CameraGestureSourceImpl): GestureSource
    @Binds @Singleton abstract fun bindsLandmarkGestureClassifier(impl: LandmarkGestureClassifierImpl): LandmarkGestureClassifier
}
```

## 3. Camera permission + UI wiring (new — required for full CameraX path)

- `core/permissions/RequiredPermissions.kt`: add `val camera: Array<String> = arrayOf(Manifest.permission.CAMERA)` and `val forVision: Array<String> = camera`.
- `ui/permissions/PermissionsScreen.kt`: extend `permissionsToRequest` to `(RequiredPermissions.forGlasses + RequiredPermissions.location + RequiredPermissions.forVision).distinct()`, update the copy text to mention camera access ("Gary needs Bluetooth, location, and camera access...").
- `AndroidManifest.xml`: add
  ```xml
  <!-- Phone camera: v0 substitute for the glasses camera until ESP32 hardware exists (ADR-010) -->
  <uses-permission android:name="android.permission.CAMERA" />
  <uses-feature android:name="android.hardware.camera.any" android:required="true" />
  ```
- `vision/PhoneCameraViewModel.kt` (new, mirrors `glasses/GlassesViewModel.kt`'s shape): `@HiltViewModel class PhoneCameraViewModel @Inject constructor(private val phoneCameraSource: PhoneCameraSource) : ViewModel() { fun bind(owner: LifecycleOwner) = phoneCameraSource.bind(owner); fun unbind() = phoneCameraSource.unbind() }`.
- `ui/main/MainScreen.kt`: add a `DisposableEffect(lifecycleOwner)` (via `LocalLifecycleOwner.current` + `val cameraViewModel: PhoneCameraViewModel = hiltViewModel()`) that calls `bind()`/`unbind()`. Purely a side effect — no new visible UI, `MainScreen` stays a status dot per its existing "presence, not an app" doc comment. Camera is only active while this screen is composed/resumed (privacy-conscious: no background capture, and `unbind()` already clears `FrameRingBuffer`).

`VisionEngine`/`GestureRecognizer` themselves still start unconditionally from `GaryOrchestrator` at app launch (matching `GlassesManager`'s existing pattern of starting regardless of hardware presence) — they simply idle with no frames until `PhoneCameraSource` is bound and permission is granted.

## 4. Unit tests

New files:
- `test/.../vision/VisionEngineImplTest.kt` — MockK fakes for `FrameDecoder`/`HandLandmarkDetector`/`ObjectDetector`, real `FrameRingBuffer`, `StandardTestDispatcher`/`TestScope`, Turbine on `visionResult`. Cases: emits result from first decoded frame; skips reprocessing an unchanged frame; decode failure emits nothing; `stop()` cancels polling and clears result.
- `test/.../gesture/GestureRecognizerImplTest.kt` — mocked `GestureSource` (a `MutableSharedFlow<RawGestureEvent>` backing `events`), Turbine on `commands`. Cases: `PINCH` → `Pinch`; duplicate `PINCH` within 200ms debounced; `THUMB_SLIDE` repeats never debounced; unmapped/unknown type emits nothing. Note: emit *inside* the `commands.test { }` block since `SharedFlow` has no replay.
- `test/.../gesture/source/LandmarkGestureClassifierImplTest.kt` — plain JUnit4, no mocks, fabricated `HandLandmarks`/timestamp sequences. Cases include: sustained pinch across samples spanning >500ms emits `HoldPinch` exactly once (not `Pinch`); quick pinch-release emits `Pinch`; two quick pinches within 400ms emit `DoublePinch` not two `Pinch`s; large lateral centroid displacement emits the correct `SwipeLeft`/`SwipeRight`.
- `test/.../gesture/source/CameraGestureSourceImplTest.kt` (thin) — mocked `VisionEngine.visionResult` StateFlow + mocked `LandmarkGestureClassifier`, verifies each emitted `VisionResult` is forwarded to `classifier.classify()` and results re-emitted on `events` via Turbine.

`CameraXPhoneCameraSource` is explicitly **not** unit tested (real CameraX/Android APIs) — same boundary as `BitmapFrameDecoder`, flagged for manual on-device verification only.

No new test-dependency lines needed — MockK/Turbine/kotlinx-coroutines-test are already on `testImplementation`.

## 5. `AppState.kt` wiring

Same scoping call as before: full `VisionResult` doesn't belong in `AppUiState` (15Hz, no current UI needs per-landmark detail) — only two cheap, human-rate fields:
```kotlin
data class AppUiState(
    val glassesConnectionState: GlassesConnectionState = GlassesConnectionState.Disconnected,
    val isVisionEngineRunning: Boolean = false,
    val lastGaryCommand: GaryCommand? = null
)

@Singleton
class AppState @Inject constructor(
    glassesManager: GlassesManager,
    visionEngine: VisionEngine,
    gestureRecognizer: GestureRecognizer,
    @ApplicationScope scope: CoroutineScope
) {
    private val lastGaryCommand: StateFlow<GaryCommand?> =
        gestureRecognizer.commands.stateIn(scope, SharingStarted.Eagerly, null)

    val uiState: StateFlow<AppUiState> = combine(
        glassesManager.connectionState, visionEngine.isRunning, lastGaryCommand
    ) { connectionState, visionRunning, garyCommand -> AppUiState(connectionState, visionRunning, garyCommand) }
        .stateIn(scope, SharingStarted.Eagerly, AppUiState())
}
```

## 6. `GaryOrchestrator.kt` wiring

```kotlin
@Singleton
class GaryOrchestrator @Inject constructor(
    private val glassesManager: GlassesManager,
    private val visionEngine: VisionEngine,
    private val gestureRecognizer: GestureRecognizer
) {
    fun start() {
        Logger.d(TAG, "GaryOrchestrator started (glasses/, vision/, gesture/ wired — gesture v0 is camera-only per ADR-010)")
        visionEngine.start()
        gestureRecognizer.start()
        // TODO(voice/, week 5-6): collect gestureRecognizer.commands here and route
        // Pinch/HoldPinch into VoiceSession.activate()/deactivate(). AppState.lastGaryCommand
        // already surfaces commands for now.
    }
}
```
`glassesManager` stays injected (unused beyond the log line, as in the current real code) — reserved for v1 wiring when `RadarGestureSourceImpl` needs it. No frame-routing logic needed here: `FrameRingBuffer` is already shared between `CameraXPhoneCameraSource` (push) and `VisionEngineImpl` (poll); orchestrator only starts the pipeline, camera *binding* is UI-lifecycle-owned per section 3.

## 7. Gradle wiring

`android/gradle/libs.versions.toml` — add a CameraX version block:
```toml
camerax = "1.3.4"
```
```toml
camerax-core = { group = "androidx.camera", name = "camera-core", version.ref = "camerax" }
camerax-camera2 = { group = "androidx.camera", name = "camera-camera2", version.ref = "camerax" }
camerax-lifecycle = { group = "androidx.camera", name = "camera-lifecycle", version.ref = "camerax" }
```
`android/app/build.gradle.kts` — add:
```kotlin
implementation(libs.mediapipe.tasks.vision)
implementation(libs.tflite.support)
implementation(libs.camerax.core)
implementation(libs.camerax.camera2)
implementation(libs.camerax.lifecycle)
```
Do **not** add `opencv` — ArUco/device recognition stays out of scope. Confirm during implementation that CameraX 1.3.4 resolves in this environment (same offline/tooling caveat as ADR-009 — flagged as a risk, not a blocker).

## 8. Docs

- `.claude/save/folder-structure.md` — flip `vision/` and `gesture/` from `[PLANNED]` to `[BUILT]`, list the new files (including `vision/camera/`, `gesture/source/`, `vision/PhoneCameraViewModel.kt`), note the ADR-010 camera-only-v0 caveat inline.
- `.claude/save/features/vision-engine.md` (new) — processing pipeline (poll interval, dedup, decode→detect→emit), the testability boundary, phone-camera-as-substitute-source design (CameraX config, RGBA_8888→JPEG choice, why `unbind()` clears the buffer), "Model assets — PLACEHOLDER" section listing `hand_landmarker.task`/`yolov8n.tflite` as not-yet-present, manual on-device checklist (camera permission flow, inference doesn't block UI/drain battery excessively, `stop()`/`unbind()` actually halt capture, confirm no frame/bitmap ever logged or disk-written).
- `.claude/save/features/gesture-recognizer.md` (new) — the `GestureSource` abstraction and why (ADR-010, radar swap-in plan), radar-vocabulary→`GaryCommand` mapping table, debounce constant + rationale, fallback/classifier thresholds (labeled "needs calibration on real hand size/lighting"), manual checklist (pinch/hold/double-pinch/swipe/grab detection actually fire on a real hand in front of the phone camera, no false positives during idle/resting hand, camera permission denial handled gracefully).

## Verification

- `./gradlew :app:testDebugUnitTest` — all new tests pass, existing `FrameRingBufferTest` unaffected.
- `./gradlew :app:assembleDebug` — confirms Hilt DI graph compiles with `VisionModule`/`GestureModule` and the expanded `AppState`/`GaryOrchestrator` constructors, and that CameraX/MediaPipe/TFLite dependencies resolve.
- Manual, on the developer's real Android phone (per CLAUDE.md — emulator unreliable for camera/BLE): grant camera permission, confirm `MainScreen` triggers camera bind, and — once real model assets are added in a follow-up (they're stubbed this pass) — confirm a real pinch/swipe in front of the phone's camera produces a `GaryCommand`. Until model assets land, the on-device check is limited to: permission flow works, camera binds/unbinds cleanly with the screen lifecycle, no crashes, no frame ever logged/written to disk.

### Critical files
- `android/app/src/main/java/com/pinch/gary/vision/VisionEngineImpl.kt`
- `android/app/src/main/java/com/pinch/gary/vision/camera/CameraXPhoneCameraSource.kt`
- `android/app/src/main/java/com/pinch/gary/gesture/GestureRecognizerImpl.kt`
- `android/app/src/main/java/com/pinch/gary/gesture/source/CameraGestureSourceImpl.kt`
- `android/app/src/main/java/com/pinch/gary/gesture/source/LandmarkGestureClassifierImpl.kt`
- `android/app/src/main/java/com/pinch/gary/core/appstate/AppState.kt`
- `android/app/src/main/java/com/pinch/gary/core/appstate/GaryOrchestrator.kt`
- `android/app/src/main/java/com/pinch/gary/ui/main/MainScreen.kt`, `core/permissions/RequiredPermissions.kt`, `ui/permissions/PermissionsScreen.kt`, `AndroidManifest.xml`
- `android/app/build.gradle.kts`, `android/gradle/libs.versions.toml`
