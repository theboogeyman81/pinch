# Phase 5 — Hardware v0: ESP32 Prototype

**Timeline:** Month 2–3 (runs parallel to Phases 3–4)  
**Milestone:** M4 Early  
**Exit criteria:** Wearable prototype works. BLE connects to Android. MJPEG streams. Touch strip fires. LED responds. Bone conduction plays Gary's voice.

---

## What This Phase Builds

The first physical glasses prototype. Uses off-the-shelf parts (XIAO ESP32-S3 Sense dev board) mounted on a regular pair of glasses with a 3D-printed temple mount. No custom PCB — that's Phase 6.

Goal is a working system where Gary runs through physical glasses, not just a phone camera. This lets you test the full loop before committing to PCB costs.

---

## Hardware BOM (v0 Prototype)

| Part | Source | Cost |
|------|--------|------|
| Seeed XIAO ESP32-S3 Sense (has OV2640 onboard) | Seeed Studio / AliExpress | ~$15 |
| Off-the-shelf glasses frame (large temples) | Optical shop or Amazon | ~$15 |
| 3D printed temple mount (right side) | PLA, your printer | ~$2 |
| Piezo bone conduction transducer | AliExpress | ~$8 |
| LiPo 300mAh flat | Amazon | ~$8 |
| BQ25504 or TP4056 charger module | AliExpress | ~$2 |
| USB-C breakout (charging) | AliExpress | ~$2 |
| AT42QT1010 touch breakout or touch wire | Adafruit / AliExpress | ~$5 |
| RGB LED (WS2812B single) | AliExpress | ~$1 |
| FPC ribbon or hookup wire | — | ~$3 |
| **Total** | | **~$61** |

**Note:** XIAO ESP32-S3 Sense has a built-in OV2640 camera. It's lower quality than OV5640 but fine for v0. The full spec OV5640 comes in Phase 6.

---

## Firmware Architecture

**Language:** C++ (ESP-IDF or Arduino framework via PlatformIO)  
**Framework:** PlatformIO + Arduino core for ESP32  
**Repo location:** `hardware/firmware/`

```
hardware/firmware/
├── platformio.ini
├── src/
│   ├── main.cpp              # Entry point, setup + loop
│   ├── ble/
│   │   ├── BleServer.cpp     # GATT server setup
│   │   ├── BleServer.h
│   │   ├── BleProfile.h      # UUID definitions (must match Android BleGattProfile.kt)
│   │   ├── BleEvents.cpp     # Touch + battery → BLE notify
│   │   └── BleAudio.cpp      # Receive audio bytes via BLE, play via DAC
│   ├── camera/
│   │   ├── CameraStream.cpp  # MJPEG HTTP server over WiFi
│   │   └── CameraStream.h
│   ├── touch/
│   │   ├── TouchSensor.cpp   # AT42QT1010 polling
│   │   └── TouchSensor.h
│   ├── led/
│   │   ├── LedControl.cpp    # WS2812B RGB LED
│   │   └── LedControl.h
│   ├── power/
│   │   ├── Battery.cpp       # ADC battery voltage reading
│   │   └── Battery.h
│   └── wifi/
│       ├── WifiManager.cpp   # Connect to AP, receive credentials via BLE
│       └── WifiManager.h
└── lib/                      # Third-party libraries
```

---

## Feature Breakdown

---

### Feature 5.1 — BLE GATT Server

**Goal:** ESP32 advertises as "Pinch-Glasses". Android app can discover and connect. All BLE characteristics defined and matching Android's `BleGattProfile.kt`.

**BLE Profile (UUIDs — keep in sync with Android `BleGattProfile.kt`):**
```cpp
// Service UUIDs
#define GLASSES_SERVICE_UUID     "12345678-1234-1234-1234-123456789abc"

// Characteristic UUIDs
#define BATTERY_LEVEL_UUID       "12345678-1234-1234-1234-123456789001"
#define TOUCH_EVENT_UUID         "12345678-1234-1234-1234-123456789002"
#define LED_CONTROL_UUID         "12345678-1234-1234-1234-123456789003"
#define AUDIO_OUTPUT_UUID        "12345678-1234-1234-1234-123456789004"
#define WIFI_SSID_UUID           "12345678-1234-1234-1234-123456789005"
#define WIFI_PASS_UUID           "12345678-1234-1234-1234-123456789006"
#define GESTURE_EVENT_UUID       "12345678-1234-1234-1234-123456789007"
```

**Characteristic properties:**
| Characteristic | Properties | Description |
|---------------|-----------|-------------|
| BATTERY_LEVEL | READ + NOTIFY | 0–100 uint8 |
| TOUCH_EVENT | NOTIFY | 1 byte: 0x01=tap, 0x02=hold_start, 0x03=hold_end |
| LED_CONTROL | WRITE | 3 bytes: R, G, B (0–255 each) |
| AUDIO_OUTPUT | WRITE_NO_RESPONSE | Raw PCM audio chunks (16kHz, 16-bit) |
| WIFI_SSID | WRITE | UTF-8 string |
| WIFI_PASS | WRITE | UTF-8 string (write clears after 30s) |
| GESTURE_EVENT | NOTIFY | 1 byte gesture code (Phase 6 — radar) |

**Advertisement:**
```cpp
BLEAdvertisementData advData;
advData.setName("Pinch-Glasses");
advData.setCompleteServices(BLEUUID(GLASSES_SERVICE_UUID));
pAdvertising->setAdvertisementData(advData);
pAdvertising->start();
```

**Testing 5.1:**
- Use nRF Connect app on phone to discover "Pinch-Glasses"
- Verify all characteristics listed with correct UUIDs and properties
- Write 0xFF 0x00 0x00 to LED_CONTROL → LED turns red
- Subscribe to BATTERY_LEVEL NOTIFY → value updates every 30 seconds

---

### Feature 5.2 — WiFi Credential Provisioning

**Goal:** Android app sends WiFi SSID + password to glasses over BLE. Glasses join home WiFi. No hardcoded credentials.

**Firmware flow:**
```
1. On boot: check if WiFi creds stored in NVS (non-volatile storage)
2. If no creds: wait for BLE writes to WIFI_SSID + WIFI_PASS
3. On WIFI_PASS write: attempt WiFi connect with stored SSID + received password
4. If connect success: store in NVS, notify Android via LED (green flash)
5. If connect fails: flash LED red 3x, clear stored creds, wait for retry
6. Password cleared from RAM after use (set bytes to 0x00)
```

**Android side (`glasses/ble/BleConnectionManager.kt`):**
- After BLE connect: check if glasses need WiFi setup (read battery → if fails, maybe not fully setup yet)
- If onboarding step 4 (glasses pairing): write SSID + password from user's current network
- Use `WifiManager.getConnectionInfo().ssid` to get current SSID automatically
- Show password entry dialog for the password

**Security:**
- Password sent over BLE (encrypted if bonded — bond the devices during first pairing)
- Password stored in ESP32 NVS (encrypted partition if possible)
- Password NOT logged anywhere

**Testing 5.2:**
- Fresh glasses (no creds): connect via BLE, write SSID + password → glasses connect to WiFi
- Glasses joins WiFi → MJPEG server starts → Android can access stream
- Reboot glasses → auto-reconnects to WiFi using stored creds
- Wrong password → LED red 3x → try again

---

### Feature 5.3 — MJPEG Camera Server

**Goal:** ESP32 serves a live MJPEG stream over WiFi HTTP. Android app connects and decodes it in `MjpegStreamClient.kt`.

**Firmware:**
```cpp
// CameraStream.cpp
void CameraStream::start() {
    // Init OV2640 camera at 320x240 or 640x480 (lower res = more FPS over WiFi)
    camera_config_t config = {
        .pin_d0 = ...,  // XIAO ESP32-S3 Sense pinout
        .frame_size = FRAMESIZE_VGA,  // 640x480
        .jpeg_quality = 12,           // 0-63, lower = higher quality
        .fb_count = 2,                // double buffer
        .grab_mode = CAMERA_GRAB_WHEN_EMPTY,
    };
    esp_camera_init(&config);
    
    // Start HTTP server
    httpd_config_t httpConfig = HTTPD_DEFAULT_CONFIG();
    httpd_start(&_server, &httpConfig);
    
    httpd_uri_t streamUri = {
        .uri = "/stream",
        .method = HTTP_GET,
        .handler = stream_handler,
    };
    httpd_register_uri_handler(_server, &streamUri);
}

static esp_err_t stream_handler(httpd_req_t *req) {
    httpd_resp_set_type(req, "multipart/x-mixed-replace; boundary=frame");
    while (true) {
        camera_fb_t *fb = esp_camera_fb_get();
        // Write boundary + JPEG frame
        httpd_resp_send_chunk(req, "--frame\r\n", ...);
        httpd_resp_send_chunk(req, "Content-Type: image/jpeg\r\n\r\n", ...);
        httpd_resp_send_chunk(req, (char *)fb->buf, fb->len);
        esp_camera_fb_return(fb);
    }
}
```

**Stream URL:** `http://{glasses_ip}/stream` (IP discovered from BLE after WiFi connect)

**Android integration:**
- After WiFi connect, glasses send their IP over BLE (write to a characteristic or notification)
- Android builds stream URL: `http://{ip}/stream`
- `MjpegStreamClient.kt` connects to this URL

**FPS target:** 10–15fps at VGA over 2.4GHz WiFi. Acceptable for v0.

**Testing 5.3:**
- Open `http://{glasses_ip}/stream` in a browser on the same WiFi → live video
- Android `MjpegStreamClient` connects → VisionEngine processes frames
- Gesture pinch detected from the glasses camera view (not phone camera)
- Run for 5 minutes → no memory leak, no WiFi drop

---

### Feature 5.4 — Touch Strip (AT42QT1010)

**Goal:** Touch the temple → BLE notify event → Android receives tap/hold → maps to gesture.

**Wiring (XIAO ESP32-S3):**
- AT42QT1010 SIGNAL pin → GPIO pin (e.g., GPIO 1)
- Power: 3.3V + GND
- `change` line connected to GPIO with interrupt

**Firmware:**
```cpp
// TouchSensor.cpp
void IRAM_ATTR touchISR() {
    // Debounce: minimum 50ms between events
    uint32_t now = millis();
    if (now - _lastEvent < 50) return;
    _lastEvent = now;
    
    bool touched = digitalRead(TOUCH_PIN) == HIGH;
    if (touched) {
        _touchStart = now;
    } else {
        uint32_t duration = now - _touchStart;
        uint8_t eventType = (duration > 500) ? 0x02 : 0x01;  // hold vs tap
        // Notify via BLE
        pTouchCharacteristic->setValue(&eventType, 1);
        pTouchCharacteristic->notify();
    }
}
```

**Android mapping (in `GestureRecognizerImpl.kt`):**
- `0x01` (tap) → `GaryCommand.Wake` (same as pinch)
- `0x02` (hold) → `GaryCommand.PushToTalk`
- This gives hardware backup for gesture recognition

**Testing 5.4:**
- Touch the strip → nRF Connect shows NOTIFY on TOUCH_EVENT characteristic
- Android receives notification → logcat shows `GaryCommand.Wake`
- Hold touch strip for 1 second → `GaryCommand.PushToTalk`
- Rapid taps → debounce prevents multiple events

---

### Feature 5.5 — LED Control

**Goal:** Android sends RGB values via BLE → glasses LED changes color. Gary state → LED color mapping.

**Wiring:** WS2812B single LED, DATA → GPIO pin, 3.3V + GND (with 330Ω resistor on data line)

**Firmware:**
```cpp
// LedControl.cpp — handle WRITE to LED_CONTROL characteristic
void onLedWrite(BLECharacteristic *c) {
    uint8_t *data = c->getData();
    // data[0]=R, data[1]=G, data[2]=B
    setLed(data[0], data[1], data[2]);
}

// LED states to implement:
// Black (0,0,0) = off
// Green (0,255,0) = connected + idle
// Blue (0,0,255) = listening
// Yellow (255,200,0) = thinking
// Purple (150,0,255) = speaking
// Red (255,0,0) = error
// White pulsing = charging
```

**Android — `GlassesManagerImpl.kt` — LED control:**
```kotlin
fun setLedState(state: AppState) {
    val (r, g, b) = when (state) {
        AppState.Idle -> Triple(0, 255, 0)
        AppState.Listening -> Triple(0, 0, 255)
        AppState.Thinking -> Triple(255, 200, 0)
        AppState.Speaking -> Triple(150, 0, 255)
        AppState.Error -> Triple(255, 0, 0)
    }
    writeLedCharacteristic(r, g, b)
}
```

**GaryOrchestrator integration:**
```kotlin
// On every AppState change:
scope.launch {
    appState.collect { state ->
        glassesManager.setLedState(state)
    }
}
```

**Testing 5.5:**
- Write RGB values via nRF Connect → LED color changes
- Trigger voice session on Android → LED turns blue
- Gary speaks → LED turns purple
- Disconnect → LED turns off

---

### Feature 5.6 — Bone Conduction Audio Output

**Goal:** Gary's voice (from LiveKit) plays through the bone conduction transducer on the glasses frame.

**v0 approach:** Phone receives LiveKit audio → sends PCM chunks via BLE to glasses → ESP32 outputs via DAC → bone conduction.

**Wiring:**
- Bone conduction transducer → MAX98357A I2S amp → ESP32 I2S pins (or built-in DAC for v0)
- For v0: use ESP32 8-bit DAC on GPIO25/26 (simpler, lower quality but functional)
- For Phase 6: proper MAX98357A I2S connection

**Firmware:**
```cpp
// BleAudio.cpp
// Handle WRITE_NO_RESPONSE on AUDIO_OUTPUT characteristic
// Each write = 512 bytes of PCM 16kHz 16-bit mono
// Write to DAC output buffer
void onAudioWrite(BLECharacteristic *c) {
    size_t len = c->getLength();
    uint8_t *data = c->getData();
    // Buffer into circular buffer
    // I2S DMA handles playback
    i2s_write(I2S_NUM_0, data, len, &written, portMAX_DELAY);
}
```

**Android side — VoiceSession audio routing:**
- LiveKit SDK delivers Gary's audio as PCM chunks via `RemoteAudioTrack`
- Add `BoneConditionAudioRouter` class:
  ```kotlin
  class BoneConditionAudioRouter(private val glassesManager: GlassesManager) {
      fun onAudioChunk(pcmBytes: ByteArray) {
          // Split into 512-byte chunks (BLE MTU limit)
          pcmBytes.chunked(512).forEach { chunk ->
              glassesManager.writeAudioChunk(chunk.toByteArray())
          }
      }
  }
  ```
- When glasses not connected: fall back to phone speaker (no change from Phase 2)

**BLE bandwidth check:**
- 16kHz, 16-bit mono = 32KB/second = 32 BLE packets/second at 1024-byte MTU
- BLE 5.0 can handle ~2Mbps — audio is ~256Kbps — should be fine with a 2Mbps PHY

**Testing 5.6:**
- Trigger voice session → Gary's voice plays through the transducer
- Quality check: intelligible speech (bone conduction is mid-quality, acceptable)
- Disconnect glasses → voice falls back to phone speaker automatically
- Run for 5 minutes of speech → no audio glitches or buffer underruns

---

### Feature 5.7 — Battery Level

**Goal:** Glasses battery percentage shown in Android UI.

**Firmware:**
```cpp
// Battery.cpp
uint8_t Battery::getLevel() {
    // ADC read on battery voltage pin
    // XIAO ESP32-S3: battery on VBAT (pin connected to ADC)
    uint32_t adcValue = analogRead(BATTERY_PIN);
    float voltage = (adcValue / 4096.0f) * 3.3f * 2.0f;  // voltage divider
    // Map 3.7V-4.2V to 0-100%
    uint8_t level = constrain((uint8_t)((voltage - 3.7f) / 0.5f * 100), 0, 100);
    return level;
}

// Notify every 30 seconds
void Battery::update() {
    uint8_t level = getLevel();
    pBatteryCharacteristic->setValue(&level, 1);
    pBatteryCharacteristic->notify();
}
```

**Android — `GlassesViewModel.kt`:**
- Subscribe to BATTERY_LEVEL NOTIFY
- `StateFlow<Int?>` for battery level (null = unknown/disconnected)
- Show in MainScreen UI: small battery icon + percentage

**Testing 5.7:**
- Battery notification received in nRF Connect every 30 seconds
- Android UI shows battery percentage
- Drain battery below 20%: notify Android → show low battery warning in UI

---

## Phase 5 Integration Test (Full Hardware Loop)

- [ ] Flash firmware to XIAO ESP32-S3 via USB
- [ ] Mount on glasses frame with 3D-printed bracket
- [ ] Power on → LED pulses white (booting)
- [ ] Android app detects "Pinch-Glasses" via BLE scan
- [ ] Android connects → LED turns green
- [ ] Android sends WiFi credentials → glasses join home WiFi
- [ ] Android opens MJPEG stream → phone screen shows glasses camera view
- [ ] VisionEngine processes glasses camera frames
- [ ] Pinch gesture in front of glasses camera → Gary activates
- [ ] Speak command → Gary responds through bone conduction
- [ ] Touch temple strip → same as pinch (Gary activates)
- [ ] Walk away from phone → BLE stays connected up to ~10 meters
- [ ] Glasses battery shows in UI
- [ ] Wear for 30 minutes → no overheating, battery > 50%

---

## Phase 5 Exit Criteria

1. Glasses advertise via BLE and Android connects
2. MJPEG camera stream works over WiFi at 10+ fps
3. Touch strip fires tap and hold events
4. LED responds to Android commands with correct colors
5. Bone conduction plays Gary's voice (intelligible)
6. Battery level reported to Android
7. Firmware builds cleanly: `pio run`
8. No memory leaks in firmware (ESP32 heap stable over 30 minutes)
9. Device wearable: can be worn without falling off, weight acceptable
