# Feature: GlassesManager

Week 1–2 of the 12-week build order (CLAUDE.md). Owns the physical connection to the ESP32-S3 glasses: BLE for touch/battery/radar events + LED control, WiFi MJPEG for the camera stream.

## State machine

```
Disconnected → Scanning → BleConnected → Streaming
```

- **Disconnected → Scanning**: triggered on app foreground / Foreground Service start.
- **Scanning → BleConnected**: BLE GATT connection established to the ESP32's advertised service.
- **BleConnected → Streaming**: WiFi credentials exchanged over BLE (if needed), ESP32 joins home WiFi, MJPEG socket opens successfully.
- Any state → **Disconnected**: BLE link lost, explicit disconnect, or Foreground Service killed.
- Auto-reconnect with exponential backoff from `Disconnected`, per CLAUDE.md's GlassesManager spec.

## BLE GATT UUIDs

**Placeholder — pending `hardware/` firmware decisions.** Do not hardcode real UUIDs until the ESP32 firmware defines its GATT profile. `BleGattProfile.kt` should centralize these constants so there's exactly one place to update once hardware/ lands.

## MJPEG endpoint scheme

ESP32 serves MJPEG over plain HTTP on the local WiFi network (requires `network_security_config.xml` cleartext exception — see ADR-006 in `architecture-decisions.md`). Exact path/port TBD alongside firmware — `MjpegStreamClient.kt` should take the base URL as a constructor param, not hardcode it.

## Foreground Service notification spec

`GlassesForegroundService` — `foregroundServiceType="connectedDevice|microphone"` (per CLAUDE.md). Must show a persistent notification (Android requirement) reflecting current `GlassesConnectionState`. No user-facing controls in the notification for v1 — status only.

## Ring-buffer handoff contract (→ future VisionEngine)

`FrameRingBuffer` holds 3–5 decoded frames in RAM only (~250KB max), never written to disk (golden privacy rule in CLAUDE.md). When `vision/` is scaffolded (week 3–4), `VisionEngine` will read from this buffer on a background coroutine. Until then, frames are decoded and immediately dropped once the ring buffer evicts them — no consumer yet, which is expected and fine.

## Manual on-device test checklist

Per CLAUDE.md: the Android emulator is unreliable for BLE and camera. These must be verified on the real Android phone, not CI:

- [ ] BLE scan discovers the ESP32 within a few seconds of powering it on
- [ ] BLE connect succeeds and survives moving the phone to another room and back (auto-reconnect)
- [ ] MJPEG stream displays live video on `MainScreen` (or a debug preview) with acceptable latency
- [ ] Killing/backgrounding the app does not drop the BLE connection (Foreground Service keeps it alive)
- [ ] Foreground Service notification appears and reflects state changes correctly
- [ ] Airplane-mode / WiFi-off simulates disconnect and triggers correct backoff/reconnect behavior
- [ ] No writes to disk occur for camera frames — verified by checking app storage does not grow during a streaming session
