# Phase 6 — Hardware v1: Custom PCB + Radar

**Timeline:** Month 4–8  
**Milestone:** M4 Full  
**Exit criteria:** Custom PCB assembled. BGT60TR13C radar replaces camera-based gesture. XIAO dev board replaced. Two-PCB design fits in glasses temples.

---

## What This Phase Builds

The real hardware. Custom KiCad PCBs ordered from JLCPCB (PCB + PCBA). Infineon BGT60TR13C 60GHz radar handles all gestures. OV5640 camera replaces XIAO's built-in OV2640. Proper audio amp (MAX98357A I2S). Everything fits in the glasses temples.

This phase has the longest lead time (PCB design → JLCPCB order → 3–4 week assembly) — start design early.

---

## PCB Architecture

**Two boards per glasses pair:**

**Right temple board (primary):**
- ESP32-S3-WROOM-1 (MCU)
- OV5640 + M12 120° lens (camera)
- BGT60TR13C (60GHz radar, via SPI)
- AT42QT1010 (touch strip)
- BQ24079 (battery charger)
- USB-C connector (charging + UART flash)
- RGB LED (WS2812B)
- FPC connector (to left temple)

**Left temple board (secondary):**
- ICS-43434 PDM MEMS microphones ×2
- MAX98357A I2S amp
- Bone conduction transducer connector
- LiPo 150mAh ×2 connectors
- FPC connector (to right temple)

**FPC ribbon:** 20-pin, 0.5mm pitch, 150mm length

---

## Feature Breakdown

---

### Feature 6.1 — KiCad PCB Design

**Goal:** Fully routed, DRC-clean KiCad schematics and layouts for both boards, ready for JLCPCB.

**Files:**
```
hardware/
├── kicad/
│   ├── right-temple/
│   │   ├── right-temple.kicad_pro
│   │   ├── right-temple.kicad_sch
│   │   └── right-temple.kicad_pcb
│   ├── left-temple/
│   │   ├── left-temple.kicad_pro
│   │   ├── left-temple.kicad_sch
│   │   └── left-temple.kicad_pcb
│   └── symbols/           # Custom KiCad symbols for BGT60TR13C, AT42QT1010
├── 3d-models/
│   ├── frame-v1.step      # Full glasses frame STEP file
│   └── temple-right.stl   # Right temple 3D printable enclosure
└── bom/
    ├── right-temple-bom.csv
    └── left-temple-bom.csv
```

**PCB design constraints:**
- Board thickness: 0.8mm (standard, allows thinner temple)
- Min trace width: 0.2mm (JLCPCB standard)
- Min via drill: 0.3mm
- FPC footprint: must be accessible for flat-flex insertion
- Camera FPC connector must align with OV5640 module ribbon position
- Radar: BGT60TR13C requires 50Ω controlled-impedance traces for RF signals
- USB-C: ESD protection diodes on CC1/CC2 and VBUS

**Critical routing rules:**
- ESP32 antenna keep-out: no copper fills within 3mm of antenna area on WROOM module
- Radar RF signals: short as possible, matched length, ground plane directly below
- Analog audio signals: keep away from digital switching signals (I2S, SPI)
- Power planes: separate analog AVCC from digital VCC with ferrite bead

**JLCPCB order specs:**
- Layer count: 4 (GND + PWR planes for signal integrity)
- Surface finish: HASL (LeadFree) or ENIG (more expensive, better for fine pitch)
- PCB color: black
- PCBA: yes, for all JLCPCB-stocked parts; manual solder for OV5640 flex connector
- Stencil: order with PCBA

**Timeline:** Design → review → order → 3–4 weeks for PCBA delivery

**Testing 6.1 (before ordering):**
- KiCad DRC: zero errors, zero unconnected nets
- 3D view: verify all components fit within temple dimensions
- Cross-reference BOM against JLCPCB parts stock (check via LCSC)
- Review radar RF layout against BGT60TR13C evaluation board reference design

---

### Feature 6.2 — BGT60TR13C Radar Integration

**Goal:** Radar detects hand gestures. Gesture events sent via BLE to Android. Camera-based gesture (v0) becomes fallback.

**Hardware:**
- BGT60TR13C connects to ESP32-S3 via SPI
- Driver: Infineon provides Arduino/ESP-IDF driver
- Pinout: MOSI, MISO, SCK, CSN, IRQ (interrupt on gesture event)

**Firmware — radar driver:**
```cpp
// radar/RadarGesture.cpp
#include "ifxRadar.h"  // Infineon SDK

void RadarGesture::begin() {
    // Init SPI
    SPI.begin(SCK_PIN, MISO_PIN, MOSI_PIN, CS_PIN);
    
    // Init BGT60TR13C
    ifx_Radar_t *device = ifx_avian_create();
    ifx_avian_config_t config;
    config.sample_rate_Hz = 2000000;
    config.num_samples_per_chirp = 64;
    config.num_chirps_per_frame = 32;
    // ... configure for presence + gesture detection
    
    // Register interrupt on IRQ pin
    attachInterrupt(IRQ_PIN, onRadarIRQ, RISING);
}

void onRadarIRQ() {
    _frameReady = true;  // process in main loop, not in ISR
}

uint8_t RadarGesture::processFrame() {
    ifx_Matrix_R_t *frame = ifx_avian_get_next_frame(device, nullptr);
    // Run gesture classification algorithm
    // Returns gesture code or 0 for no gesture
    return ifx_gesture_classify(frame);
}
```

**Gesture codes (BLE GESTURE_EVENT characteristic):**
| Code | Gesture | GaryCommand |
|------|---------|-------------|
| 0x01 | Pinch | Wake |
| 0x02 | Double pinch | ModeSwitch |
| 0x03 | Hold pinch (>500ms) | PushToTalk |
| 0x04 | Swipe right | Next |
| 0x05 | Swipe left | Back |
| 0x06 | Swipe up | VolumeUp |
| 0x07 | Swipe down | VolumeDown |
| 0x08 | Air tap | Select |
| 0x09 | Grab | Stop |

**Android side — `gesture/source/RadarGestureSourceImpl.kt` (new file):**
```kotlin
class RadarGestureSourceImpl @Inject constructor(
    private val glassesManager: GlassesManager
) : GestureSource {
    override val events: Flow<RawGestureEvent> = flow {
        glassesManager.gestureEvents.collect { bleEvent ->
            // BLE GESTURE_EVENT notification → RawGestureEvent
            val gesture = when (bleEvent.code) {
                0x01.toByte() -> RawGestureEvent.Pinch
                0x03.toByte() -> RawGestureEvent.HoldPinch
                0x04.toByte() -> RawGestureEvent.SwipeRight
                // etc.
                else -> return@collect
            }
            emit(gesture)
        }
    }
}
```

**GestureModule update — switch from camera to radar as primary source:**
```kotlin
// When glasses connected + radar active: use RadarGestureSourceImpl
// When glasses disconnected: fall back to CameraGestureSourceImpl
// GestureSource interface means GestureRecognizer doesn't change
```

**Intent detection (anti-false-positive):**
- Require pinch gesture first to "enter gesture mode" (active window: 3 seconds)
- Only recognize other gestures within that window
- This prevents walking motions from triggering commands

**Testing 6.2:**
- Bench test: radar on breadboard → Infineon GUI shows gesture detection
- All 9 gestures recognized at desk (static, no body motion)
- Walking test: random gestures while walking → no false triggers (body motion noise suppressed)
- Accuracy target: ≥90% for all gestures at desk, ≥75% while walking
- BLE notification latency from gesture to Android: <50ms

---

### Feature 6.3 — OV5640 Camera + MAX98357A Audio

**Goal:** Replace XIAO's OV2640 with OV5640. Replace DAC with MAX98357A I2S amp.

**OV5640 init (ESP-IDF):**
```cpp
camera_config_t config = {
    .ledc_channel = LEDC_CHANNEL_0,
    .ledc_timer = LEDC_TIMER_0,
    .pin_d0 = ...,  // Match PCB schematic
    // ... (8 data pins)
    .pin_xclk = XCLK_PIN,
    .pin_pclk = PCLK_PIN,
    .pin_vsync = VSYNC_PIN,
    .pin_href = HREF_PIN,
    .pin_sscb_sda = SIOD_PIN,
    .pin_sscb_scl = SIOC_PIN,
    .pin_reset = RESET_PIN,
    .xclk_freq_hz = 20000000,
    .pixel_format = PIXFORMAT_JPEG,
    .frame_size = FRAMESIZE_VGA,    // 640x480 for v1
    .jpeg_quality = 10,
    .fb_count = 2,
};
esp_camera_init(&config);
```

**OV5640 vs OV2640:**
- 5MP vs 2MP
- Better color accuracy and low-light performance
- 120° wide-angle M12 lens fits on M12 mount adapter
- Frame rate: up to 30fps at VGA over WiFi

**MAX98357A I2S audio:**
```cpp
// Audio playback via I2S
i2s_config_t i2s_config = {
    .mode = I2S_MODE_MASTER | I2S_MODE_TX,
    .sample_rate = 16000,
    .bits_per_sample = I2S_BITS_PER_SAMPLE_16BIT,
    .channel_format = I2S_CHANNEL_FMT_ONLY_LEFT,
    .communication_format = I2S_COMM_FORMAT_STAND_I2S,
    .dma_buf_count = 8,
    .dma_buf_len = 512,
    .use_apll = false,
};
i2s_driver_install(I2S_NUM_0, &i2s_config, 0, NULL);
```

**Testing 6.3:**
- OV5640 streams at 640×480 30fps over WiFi
- MJPEG stream quality visibly better than OV2640
- Audio through MAX98357A → bone conduction: test with sine wave tone
- No audio distortion at 80% volume

---

### Feature 6.4 — ICS-43434 Dual Microphone + Beamforming

**Goal:** Two MEMS mics for better voice pickup. Raw PDM audio sent to phone via BLE.

**Wiring:**
- ICS-43434 ×2: one on each side of the frame
- Both mics on same PDM bus (CLK shared, DATA separated)
- L channel: left mic, R channel: right mic

**Firmware:**
```cpp
// PDM to PCM conversion on ESP32-S3 using built-in PDM-to-PCM hardware
i2s_chan_config_t chan_cfg = I2S_CHANNEL_DEFAULT_CONFIG(I2S_NUM_1, I2S_ROLE_MASTER);
i2s_pdm_rx_config_t pdm_rx_cfg = {
    .clk_cfg = I2S_PDM_RX_CLK_DEFAULT_CONFIG(16000),
    .slot_cfg = I2S_PDM_RX_SLOT_DEFAULT_CONFIG(I2S_DATA_BIT_WIDTH_16BIT, I2S_SLOT_MODE_STEREO),
    .gpio_cfg = {
        .clk = PDM_CLK_PIN,
        .din = PDM_DATA_PIN,
    },
};
// Read stereo PCM → send left channel over BLE to phone
// Phone handles beamforming / channel mixing in VoiceSession
```

**Testing 6.4:**
- Record test audio via each microphone independently
- Compare SNR to XIAO's built-in mic (should be better)
- Voice pickup at 50cm distance: intelligible speech

---

### Feature 6.5 — Power Management + Battery

**Goal:** BQ24079 manages LiPo charging. ESP32 enters light sleep when idle. Target: 6+ hour battery life.

**Power states:**
- **Active (BLE + WiFi + camera + radar):** ~180mA → 150mAh × 2 = ~1.7h
- **Semi-idle (BLE only, no camera, no radar):** ~30mA → ~10h
- **Low-power (BLE advertising only, deep sleep):** ~5mA → ~60h

**Sleep strategy:**
- Camera + WiFi off when app is backgrounded (Android sends "app_backgrounded" BLE command)
- Radar stays on in semi-idle (low power mode: 15mW)
- ESP32 light sleep between radar frames
- Wake on radar interrupt, touch interrupt, or BLE command

**Charging:**
- BQ24079: charge rate 500mA (USB), trickle charge when battery low
- Charge status pin → BLE notify → Android shows charging indicator

**Testing 6.5:**
- Full charge → use continuously → measure time to 20% battery
- Sleep current measurement with ammeter: confirm < 5mA in deep sleep
- Charging: from 0% to 100% in < 1 hour with 500mA charge rate

---

### Feature 6.6 — 3D Printed Enclosure + Frame Integration

**Goal:** Both PCBs mounted in glasses temples. Frame looks like regular glasses. Target weight: 55–70g total.

**Enclosure design (Fusion 360 or FreeCAD):**
- Right temple: hollow channel running full length, PCB slides in from the back
- Left temple: same, houses mics + amp
- Endpiece: custom designed to match frame hinge
- Lens: standard optical shop frames used as base (swap lenses for clear lenses)
- Front frame: standard or 3D printed to match style

**Weight budget:**
| Component | Weight |
|-----------|--------|
| Frame (standard) | ~25g |
| PCBs + components | ~15g |
| Batteries ×2 | ~10g |
| Enclosures | ~8g |
| Misc (FPC, connectors) | ~5g |
| **Total** | **~63g** |

**Print settings:**
- Material: PLA (light) or Nylon-12 (more flexible, durable)
- Layer height: 0.15mm for smooth finish
- Infill: 20% (mostly shell strength needed)

**Testing 6.6:**
- Wear for 1 hour: no hotspots, no skin irritation
- Drop test from 1m: frame survives (may need rubber bumpers)
- Compare weight with standard glasses on scale

---

## Phase 6 Integration Test

- [ ] Flash new firmware to custom PCB
- [ ] Right temple powers on → BLE advertises
- [ ] Android connects via BLE
- [ ] WiFi credentials provisioned → glasses join network
- [ ] OV5640 MJPEG stream at 30fps
- [ ] Radar detects all 9 gestures correctly (desk test)
- [ ] Touch strip events work
- [ ] Bone conduction plays audio clearly
- [ ] Battery drains at expected rate (log timestamps)
- [ ] Full session (30 min): no overheating, no memory leak, no WiFi drop
- [ ] Wear test: comfortable, stays on face

---

## Phase 6 Exit Criteria

1. Custom PCB assembled and functional (all features from Phase 5 work on new PCB)
2. BGT60TR13C radar: all gestures recognized at ≥90% accuracy (desk)
3. OV5640 streams at 30fps
4. Audio through MAX98357A → bone conduction: intelligible
5. Battery life: ≥2h with camera + radar active
6. Weight: ≤70g total
7. Firmware: `pio run` succeeds, DFU update works over USB
8. Android: `RadarGestureSourceImpl` is primary source, camera is fallback
9. KiCad files committed, Gerbers exported and verified by JLCPCB DFM check
