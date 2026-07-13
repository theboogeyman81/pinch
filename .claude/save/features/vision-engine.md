# Feature: VisionEngine

Week 3–4 of the 12-week build order (CLAUDE.md). Runs MediaPipe Hands + YOLOv8n TFLite inference on frames pulled from `FrameRingBuffer` (owned by `glasses/`). Per ADR-010, no glasses hardware exists yet, so the frame producer feeding that buffer is the phone's own camera (`vision/camera/`), not the ESP32 MJPEG stream — `VisionEngine` itself doesn't know or care which.

## Processing pipeline

`FrameRingBuffer` has no Flow API (plain `@Synchronized latest()`), so `VisionEngineImpl.start()` launches a coroutine on `@ApplicationScope`:

```
while (isActive) {
    latest frame != last processed frame?
        → decode → detect hands → detect objects → emit VisionResult
    delay(66ms)  // ~15fps
}
```

66ms (~15fps) was chosen because vision is a fallback signal per CLAUDE.md (radar will be primary once it exists) — full 30fps isn't needed, and this keeps MediaPipe (~15ms) + YOLO (~20ms) well under the poll budget. Dedup is reference-equality against the last-processed `ByteArray`, so an unchanged buffer (no new frame pushed) is a cheap no-op. `stop()` cancels the polling job and resets `isRunning`/`visionResult` to `false`/`null`.

## Unit-testability boundary

Three narrow interfaces sit between orchestration and everything Android/ML-specific:

- `FrameDecoder.decode(jpegBytes): DecodedFrame?`
- `HandLandmarkDetector.detect(frame): List<HandLandmarks>`
- `ObjectDetector.detect(frame): List<DetectedObject>`

`VisionEngineImplTest` verifies the polling/dedup/emission logic entirely against MockK fakes of these three interfaces — no Android runtime, no real model files needed. `BitmapFrameDecoder` (the only class touching `android.graphics.*`), `MediaPipeHandLandmarkDetector`, `YoloObjectDetector`, and `CameraXPhoneCameraSource` (the only classes touching real Android/ML/CameraX APIs) are explicitly out of unit-test scope — manual on-device verification only.

## Phone camera as v0 frame source (ADR-010)

`CameraXPhoneCameraSource` binds a CameraX `ImageAnalysis` use case (`OUTPUT_IMAGE_FORMAT_RGBA_8888`, `STRATEGY_KEEP_ONLY_LATEST`) to whatever `LifecycleOwner` calls `bind()` — that's `ui/main/MainScreen.kt`, via `PhoneCameraViewModel`, for as long as that screen is composed. Each frame is converted `ImageProxy.toBitmap()` → JPEG (`Bitmap.compress`, quality 80) and pushed into the same `FrameRingBuffer` the ESP32 MJPEG stream will use once hardware exists — so `VisionEngineImpl`/`FrameDecoder` need zero changes when that day comes. `unbind()` clears the ring buffer (mirrors `GlassesManagerImpl.disconnect()`), so no stale frame lingers once the camera stops. Everything stays RAM-only — the JPEG encode buffer is a `ByteArrayOutputStream`, never written to disk.

## Model assets — PLACEHOLDER

Neither model asset exists in this repo yet:

- `MediaPipeHandLandmarkDetector` needs `hand_landmarker.task` (not present — `assets/models/` doesn't exist)
- `YoloObjectDetector` needs `yolov8n.tflite` (not present)

Both classes are real, DI-wired, and compile today, but their `detect()` bodies are TODO stubs that log and return an empty list — same placeholder convention as `BleGattProfile`'s UUIDs. Until these assets land, `VisionResult` will always have empty `handLandmarks`/`detectedObjects`, and `gesture/`'s classifier will never see a hand.

## Manual on-device test checklist

- [ ] Camera permission prompt appears and, once granted, `MainScreen` triggers `CameraXPhoneCameraSource.bind()`
- [ ] Backgrounding/leaving `MainScreen` unbinds the camera (no indicator light staying on, no background capture)
- [ ] Inference polling doesn't visibly block the UI thread or cause jank
- [ ] Battery drain over a few minutes of active capture is reasonable for a dev build
- [ ] `stop()`/`unbind()` actually halt CPU work (check via profiler, not just log lines)
- [ ] No frame or bitmap is ever logged or written to disk — verify app storage doesn't grow during a session
- [ ] Once real model assets are added: a hand in front of the phone camera produces non-empty `VisionResult.handLandmarks`
