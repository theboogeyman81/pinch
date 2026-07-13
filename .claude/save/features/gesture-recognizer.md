# Feature: GestureRecognizer

Week 3–4 of the 12-week build order (CLAUDE.md). Converts a raw gesture signal into a `GaryCommand` — CLAUDE.md's full gesture vocabulary (Pinch, DoublePinch, HoldPinch, Swipe×4, AirTap, Grab, ThumbSlide). Per ADR-010, v0 is camera-only: MediaPipe hand landmarks via the phone's own camera, no radar hardware yet.

## The `GestureSource` seam (ADR-010)

`GestureRecognizerImpl` depends on exactly one thing — `GestureSource` — not on `GlassesManager` or `VisionEngine` directly. This is what lets radar be added later without touching command-mapping/debounce logic:

```
GestureSource (interface)
  ├── CameraGestureSourceImpl (v0, today) — depends on VisionEngine
  └── RadarGestureSourceImpl  (v1, future) — depends on GlassesManager, not built yet
```

Both implementations emit the same currency, `RawGestureEvent(type: RawGestureType, timestampMs, magnitude)` — gesture/'s own type, deliberately not a reuse of `glasses/model/GlassesEvent.RadarGesture`, so v0 stays fully decoupled from `glasses/`. When radar lands, `RadarGestureSourceImpl` will map `GlassesEvent.RadarGesture` into `RawGestureEvent` 1:1 (firmware already classifies hold-vs-tap timing before the BLE event arrives). `GestureModule`'s `@Binds` line is the only thing that changes to switch sources.

## Radar-vocabulary → GaryCommand mapping

| RawGestureType | GaryCommand | Meaning |
|---|---|---|
| PINCH | Pinch | wake / confirm |
| DOUBLE_PINCH | DoublePinch | mode switch |
| HOLD_PINCH | HoldPinch | push-to-talk trigger |
| SWIPE_RIGHT | SwipeRight | next / skip / dismiss |
| SWIPE_LEFT | SwipeLeft | back |
| SWIPE_UP | SwipeUp | volume/brightness up (semantics decided by active mode) |
| SWIPE_DOWN | SwipeDown | volume/brightness down |
| AIR_TAP | AirTap | single select |
| GRAB | Grab | stop / cancel |
| THUMB_SLIDE | ThumbSlide(delta) | continuous brightness slider |

1:1, no timing logic in `GestureRecognizerImpl` itself — a 200ms debounce suppresses duplicate identical events (THUMB_SLIDE is exempt; it legitimately repeats every sample while contact holds).

## Fallback/classifier thresholds — needs calibration on real hardware

`LandmarkGestureClassifierImpl` is where the actual hold/double/swipe *timing* logic lives (radar firmware would classify this itself; a phone camera only gives continuous landmark samples). All distances are normalized against hand span (wrist→middle-MCP). All thresholds below are MVP guesses, not measured:

- **Pinch/HoldPinch**: thumb-tip/index-tip distance < 0.35× hand span; held ≥500ms → HoldPinch instead of Pinch
- **DoublePinch**: two pinch-releases within 400ms (the first is deferred, never emitted standalone, then upgraded)
- **Swipe**: rolling 300ms centroid (wrist + index-MCP) displacement > 0.15× hand span, classified by dominant axis
- **Grab**: average of all 5 fingertip→wrist distances < 0.4× hand span
- **AirTap**: index-tip depth (z) vs. wrist > 0.15× hand span — approximate, flagged low-confidence
- **ThumbSlide**: pinch-distance ratio in the 0.35–0.55 band (contact but not full pinch); magnitude = normalized frame-to-frame thumb-x delta

None of these have been tested against a real hand — they're unit-tested only against fabricated landmark coordinates (`LandmarkGestureClassifierImplTest`). Expect false positives/negatives until calibrated on-device.

## Manual on-device test checklist

- [ ] A real pinch (thumb+index touching) in front of the phone camera fires `Pinch`
- [ ] Holding a pinch for >500ms fires `HoldPinch`, not `Pinch`
- [ ] Two quick pinches fire `DoublePinch`, not two `Pinch`s
- [ ] Swiping the hand left/right/up/down fires the correct `Swipe*`
- [ ] A closed fist fires `Grab`
- [ ] An idle/resting hand in frame does not spuriously fire gestures (false-positive check)
- [ ] Denying camera permission doesn't crash — `MainScreen` degrades gracefully with no gesture recognition
- [ ] `AppState.lastGaryCommand` updates when a gesture fires (check via a debug log or temporary UI)
