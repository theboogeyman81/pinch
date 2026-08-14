# Pinch — Build Tracker

> How to use: After finishing a feature, mark it `[x]`, update the CURRENT FOCUS block at the top, and start a fresh session. The next session reads this file first for full context.

---

## CURRENT FOCUS

**Next feature to build:** Feature 1 — Project Setup & Build System  
**Phase:** Phase 1 — Android Foundation  
**File:** `.claude/phasses/phase-1-android-foundation.md` → Feature 1.1  

### What this session needs to do
Verify the existing Android scaffold builds and runs on a real device. The `android/` folder already has Gradle files, DI modules, navigation, theme, and all module scaffolds — but nothing has been verified on a real device yet. 

This session's job:
1. Confirm `./gradlew assembleDebug` succeeds with zero errors
2. Confirm all dependencies in `libs.versions.toml` are correct (versions, group IDs)
3. Confirm `AndroidManifest.xml` has all required permissions and the Foreground Service declaration
4. Install APK on physical Android device → app opens to MainScreen without crash
5. Check logcat: no crashes, no missing Hilt providers, no missing resources

### Key files for this feature
- `android/app/build.gradle.kts`
- `android/gradle/libs.versions.toml`
- `android/app/src/main/AndroidManifest.xml`
- `android/app/src/main/java/com/pinch/gary/GaryApplication.kt`

### Done when
- `./gradlew assembleDebug` → BUILD SUCCESSFUL
- APK installs and opens on physical Android 10+ device
- No crash on startup (check logcat for 30 seconds)

---

## Legend
- `[ ]` Not started
- `[~]` In progress
- `[x]` Done and verified on device

---

## Phase 1 — Android Foundation
> Goal: App builds, BLE connects, MJPEG streams, gesture fires. No cloud needed.  
> Ref: `.claude/phasses/phase-1-android-foundation.md`

- [ ] **Feature 1** — Project Setup & Build System (Gradle, libs.versions.toml, AndroidManifest)
- [ ] **Feature 2** — Permissions Screen (BLE, mic, location, calendar — with PermanentlyDenied handling)
- [ ] **Feature 3** — GlassesManager: BLE Scan + Connect + Foreground Service + auto-reconnect
- [ ] **Feature 4** — MJPEG Stream + Frame Ring Buffer (3–5 frames, RAM only, no disk writes)
- [ ] **Feature 5** — VisionEngine: MediaPipe Hands + YOLOv8n TFLite + ArUco (all three running)
- [ ] **Feature 6** — GestureRecognizer: Camera-based pinch → GaryCommand.Wake (v0, no radar yet)
- [ ] **Feature 7** — GaryOrchestrator: wire GestureRecognizer → AppState transitions
- [ ] **Feature 8** — MainScreen UI: StatusDot with animated states, glasses battery, state label
- [ ] **Feature 9** — OnboardingScreen + SettingsScreen + DataStore preferences

---

## Phase 2 — Voice + Cloud Core
> Goal: Pinch → Gary speaks back. Minimal FastAPI server up. LiveKit + Deepgram + Gemini + Cartesia.  
> Ref: `.claude/phasses/phase-2-voice-cloud-core.md`

- [ ] **Feature 10** — Cloud scaffold: FastAPI app + Docker + docker-compose + config.py (all secrets from env)
- [ ] **Feature 11** — Cloud auth: JWT register / login / refresh endpoints + bcrypt
- [ ] **Feature 12** — Cloud WebSocket event bus: /ws endpoint + all event schemas (Pydantic)
- [ ] **Feature 13** — Cloud LiveKit room management: create room + return join token
- [ ] **Feature 14** — Cloud Gary voice agent: Deepgram STT → Gemini 2.5 Flash → Cartesia TTS (no tools yet)
- [ ] **Feature 15** — Android VoiceSession: LiveKit room join + mic open + audio to speaker
- [ ] **Feature 16** — Android AppState UI updates: StatusDot animations for Listening / Thinking / Speaking

---

## Phase 3 — Cloud Backend Full
> Goal: Gary has tools. HA relay, vision on-demand, memory, context injection all working.  
> Ref: `.claude/phasses/phase-3-cloud-backend-full.md`

- [ ] **Feature 17** — Postgres schema + Alembic migrations (User, GaryMemory, ConversationSummary)
- [ ] **Feature 18** — Gary agent tools: control_home_device, see_through_glasses, save_memory, lookup_memory
- [ ] **Feature 19** — Context injection: Redis session state, context block prepended to every LLM call
- [ ] **Feature 20** — Gemini Vision: describe_image() — JPEG in RAM → text → image discarded
- [ ] **Feature 21** — Conversation summarization: session end → Gemini summarize → Postgres → raw discarded
- [ ] **Feature 22** — Error handling + structured logging (structlog JSON, no sensitive data in logs)
- [ ] **Feature 23** — LiveKit agent auto-dispatch (no manual process start needed)

---

## Phase 4 — Android Smart Home + Context
> Goal: Calendar, location, HA commands all working through voice on real device.  
> Ref: `.claude/phasses/phase-4-android-smart-home-context.md`

- [ ] **Feature 24** — SmartHomeManager: HA local API relay + Nabu Casa fallback + Hue bridge fallback
- [ ] **Feature 25** — ContextProvider: Calendar Provider + Geofencing API + VisionEngine results
- [ ] **Feature 26** — ContextProvider → GaryClient: contextUpdate / objectsInView / deviceSeen auto-send
- [ ] **Feature 27** — Onboarding full flow: all 7 steps (Welcome → Permissions → Account → Glasses → SmartHome → Location → Done)
- [ ] **Feature 28** — SettingsScreen full: HA URL, Nabu Casa, Hue, account, subscription placeholder
- [ ] **Feature 29** — Frame-on-demand phone side: requestFrame → ring buffer → jpeg_b64 → cloud (<100ms)

---

## Phase 5 — Hardware v0 (ESP32 Prototype)
> Goal: Physical glasses prototype works. BLE + MJPEG + touch + LED + bone conduction.  
> Ref: `.claude/phasses/phase-5-hardware-v0.md`  
> Hardware needed: XIAO ESP32-S3 Sense (~$15) + glasses frame + piezo transducer + LiPo

- [ ] **Feature 30** — BLE GATT server: advertise "Pinch-Glasses", all characteristics (UUIDs match Android)
- [ ] **Feature 31** — WiFi credential provisioning: Android writes SSID+pass over BLE → ESP32 joins WiFi
- [ ] **Feature 32** — MJPEG camera server: HTTP /stream endpoint, 10–15fps at VGA
- [ ] **Feature 33** — Touch strip: AT42QT1010 interrupt → BLE notify (tap + hold events)
- [ ] **Feature 34** — LED control: WS2812B RGB → responds to Android BLE writes (green/blue/yellow/purple)
- [ ] **Feature 35** — Bone conduction audio: PCM chunks over BLE → ESP32 DAC → transducer
- [ ] **Feature 36** — Battery level: ADC read → BLE notify every 30 seconds → shows in Android UI

---

## Phase 6 — Hardware v1 (Custom PCB)
> Goal: Custom KiCad PCBs ordered from JLCPCB. Radar replaces camera-based gesture.  
> Ref: `.claude/phasses/phase-6-hardware-v1.md`  
> Lead time: ~4 weeks from design to assembled PCB delivery

- [ ] **Feature 37** — KiCad PCB design: right temple + left temple schematics + layout, DRC clean
- [ ] **Feature 38** — BGT60TR13C radar: SPI driver + all 9 gestures → BLE events → Android RadarGestureSourceImpl
- [ ] **Feature 39** — OV5640 camera + MAX98357A I2S amp: replace XIAO built-ins
- [ ] **Feature 40** — ICS-43434 dual MEMS mics: PDM → PCM, stereo capture, send left channel to phone
- [ ] **Feature 41** — Power management: BQ24079 charger + ESP32 sleep states (active / semi-idle / deep sleep)
- [ ] **Feature 42** — 3D printed enclosure + frame integration: both PCBs mounted, wearable, ≤70g

---

## Phase 7 — Mac Companion App
> Goal: Swift overlay app connects to Gary cloud, shows floating text, screen context on demand.  
> Ref: `.claude/phasses/phase-7-mac-companion.md`  
> Prerequisite: Android + cloud (Phases 1–4) stable first

- [ ] **Feature 43** — Swift project setup: PinchApp @main, NSApplicationDelegate, menu bar item, no Dock icon
- [ ] **Feature 44** — GaryOverlay: NSPanel floating window, SwiftUI content, click-through, auto-dismiss 8s
- [ ] **Feature 45** — GaryClient Swift: URLSessionWebSocketTask, reconnect backoff, event routing
- [ ] **Feature 46** — ScreenCapture: ScreenCaptureKit single frame → JPEG Data → WebSocket → never stored
- [ ] **Feature 47** — VoiceSession Mac: LiveKit Swift SDK, Mac mic → Gary → Mac speaker
- [ ] **Feature 48** — Login + Settings: SwiftUI login sheet, Keychain JWT, settings window
- [ ] **Feature 49** — macOS permissions: Screen Recording + Microphone — explanation before system prompt

---

## Phase 8 — Launch Polish
> Goal: Play Store (beta), billing active, production cloud, crash reporting, 5 beta users.  
> Ref: `.claude/phasses/phase-8-launch.md`

- [ ] **Feature 50** — Google Play Billing: monthly + annual subscription, purchase → Stripe sync
- [ ] **Feature 51** — Production cloud deployment: Railway (or Render/Fly.io), HTTPS, real domain, DB backups
- [ ] **Feature 52** — Crash reporting + analytics: Firebase Crashlytics + Analytics (no personal data logged)
- [ ] **Feature 53** — Onboarding edge cases: all 9 error states handled, resume logic across sessions
- [ ] **Feature 54** — Rate limiting: voice 60min/day, vision 50/day, HA 500/day — Redis-backed, graceful message
- [ ] **Feature 55** — Play Store submission: icon, screenshots, privacy policy, data safety form, closed beta
- [ ] **Feature 56** — Gary system prompt final tuning: 20-interaction review, no AI-speak, latency P90 < 2s

---

## Progress Summary
**Done:** 0 / 56 features  
**Current phase:** Phase 1 — Android Foundation  
**Android app functional (Gary talks back):** after Feature 16  
**Full product (hardware + voice + smart home):** after Feature 36  
