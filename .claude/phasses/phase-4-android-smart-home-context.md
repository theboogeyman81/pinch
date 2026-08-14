# Phase 4 — Android: Smart Home + Context Provider

**Timeline:** Weeks 9–12 (overlaps Phase 3)  
**Milestone:** M2 (Android — final features)  
**Exit criteria:** Gary knows your calendar, location, and can control your lights — all through voice, all working on a real device.

---

## What This Phase Builds

The last Android features before the app is feature-complete: `SmartHomeManager`, `ContextProvider`, and the final onboarding flow. After this phase, the Android app is shippable (minus billing and Play Store).

---

## Feature Breakdown

---

### Feature 4.1 — SmartHomeManager

**Goal:** Phone receives `homeAssistantCall` from cloud via WebSocket, executes it against the local HA instance, and returns the result. Phone is the relay — cloud never touches the home network directly.

**Files (all new):**
- `smarthome/SmartHomeManager.kt` (interface)
  ```kotlin
  interface SmartHomeManager {
      suspend fun execute(command: HomeAssistantCommand): SmartHomeResult
      fun isHomeAssistantConfigured(): Boolean
  }
  
  data class HomeAssistantCommand(
      val service: String,   // "light.turn_on"
      val entityId: String,  // "light.kitchen"
      val data: Map<String, Any> = emptyMap()
  )
  
  sealed class SmartHomeResult {
      object Success : SmartHomeResult()
      data class Error(val message: String) : SmartHomeResult()
      object NotConfigured : SmartHomeResult()
      object NotHome : SmartHomeResult()  // HA unreachable, Nabu Casa also failed
  }
  ```
- `smarthome/SmartHomeManagerImpl.kt`
  ```kotlin
  // Execution logic:
  // 1. Check geofence → at home? → use local HA URL
  // 2. Not home + Nabu Casa URL configured? → use Nabu Casa URL
  // 3. Neither? → return NotHome error (Gary speaks "I can't reach your home right now")
  
  // HA REST API call:
  // POST {ha_url}/api/services/{domain}/{service}
  // Body: {"entity_id": entityId, ...data}
  // Header: Authorization: Bearer {token from Keystore}
  ```
- `smarthome/SmartHomeModule.kt` — Hilt module
- `smarthome/ha/HomeAssistantClient.kt` — OkHttp HTTP client for HA API
  - `callService(domain: String, service: String, body: JsonObject): Boolean`
  - Timeout: 5 seconds (local HA should respond fast)
  - If 401: token is wrong → notify user via overlay
  - If timeout: try Nabu Casa if configured

**Security rules:**
- HA long-lived token: `KeystoreManager.getHaToken()` — stored at onboarding, never logged, never sent to cloud
- Token only used for outbound calls from phone to HA
- Cloud sends only the command (service + entityId) — never sees the token

**Hue fallback (no HA):**
- `smarthome/hue/HueBridgeClient.kt` — local Hue API
  - Auto-discover bridge via `https://discovery.meethue.com/`
  - Store bridge IP + username in `UserPreferences`
  - `turnOn(lightId: String)`, `turnOff(lightId: String)`, `setBrightness(lightId: String, level: Int)`

**GaryClient integration:**
- When GaryClient receives `homeAssistantCall` command:
  - Parse into `HomeAssistantCommand`
  - Call `SmartHomeManager.execute(command)`
  - If error: send error event back to cloud → Gary speaks the error

**Testing 4.1:**
- Set up a real HA instance (or use HA demo) on local network
- Configure HA URL + token in app settings
- Say "Gary, turn off the living room light" → confirm HA API receives the call
- Simulate being away (mock geofence) → confirm app tries Nabu Casa URL
- Remove HA token → confirm `SmartHomeResult.Error` returned, Gary speaks it
- Malformed service name → confirm graceful error, no crash

---

### Feature 4.2 — ContextProvider

**Goal:** Phone proactively builds user context (calendar, location, ArUco devices) and sends it to cloud every time it changes. Gary always has fresh context.

**Files (all new):**
- `context/ContextProvider.kt` (interface)
  ```kotlin
  interface ContextProvider {
      val context: StateFlow<UserContext>
      fun start()
      fun stop()
  }
  
  data class UserContext(
      val upcomingEvents: List<CalendarEvent>,
      val location: String,           // "home" | "office" | "unknown"
      val timeOfDay: String,          // "morning" | "afternoon" | "evening" | "night"
      val devicesSeen: List<Int>,     // ArUco marker IDs
      val objectsInView: List<String> // YOLO class labels
  )
  
  data class CalendarEvent(
      val title: String,
      val startTime: String,          // ISO 8601
      val location: String?
  )
  ```
- `context/ContextProviderImpl.kt`
  - Combines data from three sources:
    1. Android Calendar Provider (calendar events)
    2. Android Geofencing API (location)
    3. VisionEngine.results StateFlow (devices + objects)
  - Debounces updates: only sends `contextUpdate` to cloud if context changed meaningfully
  - Sends context update every 5 minutes minimum, or immediately on significant change (e.g., location changed)
- `context/calendar/CalendarRepository.kt`
  - Queries `CalendarContract.Events` ContentProvider
  - Fetches next 3 events within 24 hours
  - Requires `READ_CALENDAR` permission (add to AndroidManifest)
- `context/location/GeofenceManager.kt`
  - Sets geofence center = user's home location (set during onboarding)
  - Radius: 200 meters
  - `isAtHome(): Boolean` — checks current geofence status
  - Used by both ContextProvider and SmartHomeManager
- `context/ContextModule.kt` — Hilt module

**Privacy note:**
- Location is reduced to "home" / "not home" — never send GPS coordinates to cloud
- Calendar event titles are sent — warn user in onboarding that Gary reads calendar

**Testing 4.2:**
- Grant `READ_CALENDAR` permission, add test events → confirm ContextProvider includes them
- Mock geofence: set home to current location → confirm location = "home"
- Move outside geofence (or mock it) → confirm location = "unknown"
- VisionEngine detects objects → confirm `objectsInView` updated within one cycle
- Send `contextUpdate` to cloud → confirm Gary uses it in next response

---

### Feature 4.3 — ContextProvider → GaryClient Integration

**Goal:** Context flows automatically from phone to cloud. GaryClient sends `contextUpdate` whenever UserContext changes. Cloud stores in Redis.

**Integration in GaryClient:**
```kotlin
// In GaryClientImpl, after connect:
scope.launch {
    contextProvider.context.collect { ctx ->
        sendContextUpdate(ctx)
    }
}

// Also: VisionEngine results → send objectsInView and deviceSeen events
scope.launch {
    visionEngine.results.collect { result ->
        if (result.objects.isNotEmpty()) sendObjectsInView(result.objects.map { it.label })
        result.arUcoMarkers.forEach { sendDeviceSeen(it) }
    }
}
```

**Debounce strategy:**
- `objectsInView`: send at most once every 2 seconds (YOLO runs 30fps — don't flood WebSocket)
- `deviceSeen`: send once per unique marker per 30 seconds
- `contextUpdate`: send on meaningful change or every 5 minutes

**Testing 4.3:**
- Add a calendar event → confirm cloud receives `contextUpdate` with event
- Hold an ArUco marker in front of phone camera → confirm `deviceSeen` event on cloud
- YOLO detects objects → confirm `objectsInView` arrives at cloud within 2 seconds
- Check WebSocket message rate: should not exceed ~1 message/second during normal use

---

### Feature 4.4 — Onboarding: Full Flow

**Goal:** Complete onboarding that sets up everything the user needs. No dead ends.

**Steps:**

**Step 1: Welcome**
- Full-screen black, "Pinch" wordmark, brief tagline, "Get started" button

**Step 2: Permissions**
- Walk through each required permission with a friendly explanation:
  - Bluetooth: "So Gary can talk to your glasses"
  - Microphone: "So Gary can hear you"
  - Location: "So Gary knows when you're home"
  - Calendar: "So Gary knows what's coming up"
- Each permission shown one at a time with "Allow" button
- If denied: show explanation + "Try again" + "Skip for now" (some features won't work)

**Step 3: Create Account**
- Email + password fields
- "Create account" → calls `POST /auth/register`
- On success: JWT stored in Keystore
- Error handling: "Email already in use", "Password too short" etc.

**Step 4: Glasses Pairing**
- "Put on your glasses and hold the touch strip for 3 seconds"
- Show animated glasses graphic
- BLE scanning starts automatically
- When glasses found: brief "Connected!" animation
- No glasses yet? "Skip — I'll use phone camera for now"

**Step 5: Smart Home Setup (optional)**
- "Do you have Home Assistant?" — Yes / No / Skip
- If Yes: text field for HA URL, "Paste your long-lived token" field
  - "How do I get this token?" → opens HA profile docs URL
  - Test the connection: `GET {ha_url}/api/` → shows "Connected!" or error
- Philips Hue option: "Do you have Hue?" → auto-discover bridge on local network
- If No/Skip: proceed

**Step 6: Home Location**
- "Where is home?" → map picker (or "Use current location" button)
- Sets geofence center for SmartHomeManager
- Skip option: Gary won't know when you're home (HA commands always use remote URL)

**Step 7: Done**
- "Gary is ready." — brief success screen
- Auto-navigate to MainScreen after 2 seconds

**DataStore keys updated after onboarding:**
```kotlin
ONBOARDING_COMPLETE = true
HA_URL = "http://192.168.1.6:8123"
HA_TOKEN = stored in Keystore via KeystoreManager.saveHaToken()
NABU_CASA_URL = "https://xxxxx.ui.nabu.casa" (if provided)
PAIRED_DEVICE_ADDRESS = "AA:BB:CC:DD:EE:FF"
GEOFENCE_LAT = 37.7749
GEOFENCE_LNG = -122.4194
```

**Testing 4.4:**
- Full fresh install walkthrough on device
- Skip every optional step → app must still work (with limited features)
- Enter wrong HA URL → "Connection failed" shown, user can retry
- Complete onboarding → verify all preferences saved → kill + relaunch → all settings intact
- Re-run onboarding from Settings → existing settings pre-filled

---

### Feature 4.5 — SettingsScreen: Full Implementation

**Goal:** User can update all settings set during onboarding, without having to redo the whole flow.

**Sections:**
1. **Account** — email display, sign out button
2. **Glasses** — connected device name + "Forget glasses" button
3. **Smart Home** — HA URL + test connection, Nabu Casa URL, Hue bridge status
4. **Location** — home location display + "Change" button (opens map)
5. **Subscription** — current plan, billing date, "Manage subscription" (placeholder for Phase 8)
6. **Privacy** — "Gary does not store audio, camera footage, or screen recordings" — tap for details
7. **About** — app version, build number, "Report a bug" (mailto link)

**Testing 4.5:**
- Change HA URL → test connection → confirm new URL persists after app restart
- "Forget glasses" → app goes back to scanning state
- Sign out → tokens cleared from Keystore → back to login screen

---

### Feature 4.6 — Frame-on-Demand: Phone Side

**Goal:** Cloud sends `requestFrame` → phone grabs latest frame from ring buffer → sends JPEG to cloud. Must complete within 500ms.

**Integration in GaryClient:**
```kotlin
// Handle incoming requestFrame command:
is CloudCommand.RequestFrame -> {
    val frame = frameRingBuffer.latest()
    if (frame != null) {
        sendFrameForVision(frame)  // sends jpeg_b64 to cloud
    } else {
        // No frame available (phone camera not running, glasses not connected)
        sendFrameUnavailable()
    }
}
```

**Privacy enforcement:**
- `frameRingBuffer.latest()` returns `ByteArray` — RAM only
- `sendFrameForVision()` encodes to base64 → sends over WebSocket → frame variable goes out of scope
- No `File.write()`, no `Bitmap.compress()` to disk

**Timing:**
- `requestFrame` received → frame grabbed → WebSocket message sent in < 100ms
- Round trip (cloud → phone → cloud → Gemini) budget: < 500ms for the phone's portion

**Testing 4.6:**
- Say "Gary, what am I looking at?" → confirm `requestFrame` received in logcat
- Confirm `frameForVision` sent with valid JPEG data within 100ms
- Confirm no file created on device (`adb shell find /sdcard -newer /tmp/ref -name "*.jpg"` returns empty)
- Test with no camera active → confirm `frameUnavailable` sent gracefully

---

## Phase 4 Integration Test (Full Android Feature Set)

Run this on a real Android device with the full cloud backend running:

- [ ] Fresh install → complete full onboarding (all steps, no skips)
- [ ] MainScreen shows Idle state, glasses LED green (or status dot green)
- [ ] Pinch → Gary activates → speak "Turn off the living room light" → light turns off
- [ ] Speak "What's on my calendar?" → Gary reads next event from ContextProvider
- [ ] Hold ArUco marker in front of phone → say "what device am I looking at?" → Gary identifies it by marker ID
- [ ] Say "Remember that I like my office lights at 40 percent" → memory stored
- [ ] New voice session: "what brightness do I prefer in my office?" → Gary recalls
- [ ] Go outside home geofence → voice command for lights → Gary says "I can't reach your home network" (if Nabu Casa not configured)
- [ ] Settings: change HA URL → reconnect → command works with new URL
- [ ] App backgrounded for 10 minutes → reconnect glasses → all features still work

---

## Phase 4 Exit Criteria

1. HA commands work on local network (tested with real HA instance)
2. Calendar context injected into Gary correctly
3. Location correctly determines "home" vs "away"
4. ArUco markers trigger `deviceSeen` events
5. Frame-on-demand completes in < 300ms (phone side)
6. Onboarding completes without crash on fresh install
7. Settings persist across app restarts
8. All Android unit tests still pass
9. No `File.write()` calls in frame/audio handling code (verified by grep)
