# Phase 7 — Mac Companion App

**Timeline:** Month 6–8 (after Android + cloud are stable)  
**Milestone:** M5  
**Exit criteria:** Mac app runs, connects to Gary cloud, floats overlay on screen when Gary speaks, screen context sent on demand, Mac mic works with Gary.

---

## What This Phase Builds

The desktop companion. When you're at your desk with glasses on, the Mac app gives Gary screen context — Gary can see what you're coding or reading and respond with a floating text overlay. Voice works through the Mac mic too.

This is a clean Swift app — NOT a port of Gary_2. Same product behavior (floating overlay, screen context), entirely new codebase built for the Pinch architecture.

---

## Tech Stack

| Layer | Framework |
|-------|-----------|
| Language | Swift 5.10+ |
| UI | SwiftUI |
| Overlay window | NSPanel (non-activating, always on top) |
| Screen capture | ScreenCaptureKit (macOS 12.3+) |
| Voice | LiveKit Swift SDK |
| WebSocket | URLSessionWebSocketTask |
| Secrets | macOS Keychain (SecItem API) |
| Distribution | Direct download (DMG) or Mac App Store (later) |

---

## Feature Breakdown

---

### Feature 7.1 — Swift Project Setup

**Goal:** Xcode project builds and runs. Menu bar app (no Dock icon). Basic window structure.

**Directory structure:**
```
mac/
├── Pinch.xcodeproj/
├── Pinch/
│   ├── App/
│   │   ├── PinchApp.swift         # @main entry point
│   │   ├── AppDelegate.swift      # NSApplicationDelegate
│   │   └── MenuBarController.swift # NSStatusItem setup
│   ├── Overlay/
│   │   ├── OverlayWindow.swift    # NSPanel setup
│   │   ├── OverlayView.swift      # SwiftUI content view
│   │   └── OverlayViewModel.swift # State management
│   ├── Gary/
│   │   ├── GaryClient.swift       # WebSocket connection
│   │   ├── EventModels.swift      # Codable event structs
│   │   └── KeychainManager.swift  # JWT + HA token storage
│   ├── Screen/
│   │   ├── ScreenCapture.swift    # ScreenCaptureKit wrapper
│   │   └── JPEGEncoder.swift      # CGImage → JPEG Data
│   └── Voice/
│       └── VoiceSession.swift     # LiveKit room management
```

**PinchApp.swift:**
```swift
@main
struct PinchApp: App {
    @NSApplicationDelegateAdaptor(AppDelegate.self) var appDelegate
    
    var body: some Scene {
        // No main window — this is a menu bar app
        Settings { SettingsView() }
    }
}
```

**AppDelegate.swift:**
```swift
class AppDelegate: NSObject, NSApplicationDelegate {
    func applicationDidFinishLaunching(_ notification: Notification) {
        NSApp.setActivationPolicy(.accessory)  // No Dock icon
        MenuBarController.shared.setup()
        OverlayWindow.shared.setup()
        GaryClient.shared.connect()
    }
}
```

**Testing 7.1:**
- Build succeeds in Xcode
- App launches: no Dock icon, menu bar item appears
- Menu bar: show/hide overlay, preferences, quit

---

### Feature 7.2 — GaryOverlay: NSPanel Floating Window

**Goal:** Floating text overlay appears in bottom-right corner when Gary has something to show. Fades out automatically. Never steals focus.

**Files:**
- `Overlay/OverlayWindow.swift`
  ```swift
  class OverlayWindow {
      static let shared = OverlayWindow()
      private var panel: NSPanel?
      
      func setup() {
          let screen = NSScreen.main!.visibleFrame
          let size = CGSize(width: 320, height: 120)
          let origin = CGPoint(
              x: screen.maxX - size.width - 16,
              y: screen.minY + 16
          )
          
          panel = NSPanel(
              contentRect: NSRect(origin: origin, size: size),
              styleMask: [.borderless, .nonactivatingPanel],
              backing: .buffered,
              defer: false
          )
          panel?.isFloatingPanel = true
          panel?.level = .floating
          panel?.backgroundColor = .clear
          panel?.isOpaque = false
          panel?.hasShadow = true
          panel?.contentView = NSHostingView(rootView: OverlayView())
          panel?.ignoresMouseEvents = true  // click-through
      }
      
      func show(text: String) {
          OverlayViewModel.shared.text = text
          OverlayViewModel.shared.isVisible = true
          panel?.orderFront(nil)
          
          // Auto-dismiss after 8 seconds
          DispatchQueue.main.asyncAfter(deadline: .now() + 8) {
              self.hide()
          }
      }
      
      func hide() {
          OverlayViewModel.shared.isVisible = false
      }
  }
  ```

- `Overlay/OverlayView.swift`
  ```swift
  struct OverlayView: View {
      @ObservedObject var viewModel = OverlayViewModel.shared
      
      var body: some View {
          if viewModel.isVisible {
              VStack(alignment: .leading, spacing: 8) {
                  Text("Gary")
                      .font(.system(size: 10, weight: .medium))
                      .foregroundColor(.secondary)
                  Text(viewModel.text)
                      .font(.system(size: 14, weight: .regular))
                      .foregroundColor(.primary)
                      .lineLimit(4)
                      .fixedSize(horizontal: false, vertical: true)
              }
              .padding(12)
              .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 12))
              .transition(.opacity.combined(with: .move(edge: .bottom)))
          }
      }
  }
  ```

**Animation:** Use SwiftUI `.animation(.easeInOut, value: viewModel.isVisible)` — slides up from bottom, fades out.

**Testing 7.2:**
- Trigger show() programmatically → overlay appears in correct corner
- Auto-dismisses after 8 seconds
- Click on overlay area → click passes through to desktop behind it
- Multiple show() calls → each replaces the previous text, resets dismiss timer
- Overlay works on external monitors (check NSScreen.screens)

---

### Feature 7.3 — GaryClient: WebSocket Connection

**Goal:** Mac connects to Gary cloud with same WebSocket protocol as Android. Different session, same user.

**Files:**
- `Gary/GaryClient.swift`
  ```swift
  actor GaryClient {
      static let shared = GaryClient()
      private var task: URLSessionWebSocketTask?
      private var isConnected = false
      
      func connect() async {
          guard let token = KeychainManager.shared.getAccessToken() else {
              // Not logged in — show login
              return
          }
          
          var request = URLRequest(url: URL(string: "wss://api.pinch.app/ws?token=\(token)")!)
          task = URLSession.shared.webSocketTask(with: request)
          task?.resume()
          isConnected = true
          
          // Send macActive event
          await send(["type": "macActive"])
          
          // Start receive loop
          await receiveLoop()
      }
      
      private func receiveLoop() async {
          while isConnected {
              do {
                  let message = try await task!.receive()
                  if case .string(let json) = message {
                      await handleEvent(json)
                  }
              } catch {
                  // Reconnect with backoff
                  await reconnect()
              }
          }
      }
      
      private func handleEvent(_ json: String) async {
          guard let data = json.data(using: .utf8),
                let event = try? JSONDecoder().decode(CloudCommand.self, from: data) else { return }
          
          switch event.type {
          case "showOverlay":
              await MainActor.run {
                  OverlayWindow.shared.show(text: event.text ?? "")
              }
          case "requestScreen":
              await sendScreenCapture()
          default:
              break
          }
      }
      
      func send(_ dict: [String: Any]) async {
          guard let data = try? JSONSerialization.data(withJSONObject: dict),
                let json = String(data: data, encoding: .utf8) else { return }
          try? await task?.send(.string(json))
      }
  }
  ```

**Reconnect:** Same exponential backoff pattern as Android — 1s → 2s → 4s → max 30s.

**Token refresh:** Same as Android — if WebSocket gets 401, call `/auth/refresh`, reconnect.

**Testing 7.3:**
- Launch Mac app → confirm WebSocket connected (check cloud logs)
- Cloud sends `showOverlay` → overlay appears on Mac
- Kill cloud → Mac attempts reconnect → cloud restarts → Mac reconnects
- Check Keychain: JWT stored under "com.pinch.gary.access_token"

---

### Feature 7.4 — ScreenCapture: On-Demand Screenshots

**Goal:** Cloud asks for screen context → Mac captures one frame → sends JPEG over WebSocket → never stored to disk.

**Files:**
- `Screen/ScreenCapture.swift`
  ```swift
  import ScreenCaptureKit
  
  class ScreenCapture {
      static func captureFrame() async throws -> Data {
          // Get shareable content (this requires Screen Recording permission)
          let content = try await SCShareableContent.excludingDesktopWindows(false, onScreenWindowsOnly: true)
          
          guard let display = content.displays.first else {
              throw ScreenCaptureError.noDisplay
          }
          
          let filter = SCContentFilter(display: display, excludingWindows: [])
          let config = SCStreamConfiguration()
          config.width = 1920
          config.height = 1080
          config.pixelFormat = kCVPixelFormatType_32BGRA
          
          // Capture single frame
          let image = try await SCScreenshotManager.captureImage(
              contentFilter: filter,
              configuration: config
          )
          
          // Convert to JPEG (never write to disk)
          let jpegData = NSBitmapImageRep(cgImage: image)
              .representation(using: .jpeg, properties: [.compressionFactor: 0.7])!
          
          return jpegData
          // jpegData goes out of scope here — ARC deallocates
      }
  }
  ```

**GaryClient integration:**
```swift
private func sendScreenCapture() async {
    do {
        let jpegData = try await ScreenCapture.captureFrame()
        let base64 = jpegData.base64EncodedString()
        await send(["type": "screenForContext", "jpeg_b64": base64])
        // base64 string and jpegData go out of scope — deallocated
    } catch {
        await send(["type": "screenUnavailable", "reason": error.localizedDescription])
    }
}
```

**Privacy rules:**
- `captureFrame()` returns `Data` — never `URL`, never `FileHandle`
- No `FileManager.default.createFile()` anywhere in this file
- Test: `strings` the binary — must not contain any file path for screenshots

**macOS permission:** Screen Recording must be granted in System Settings. App prompts on first use.

**Testing 7.4:**
- Grant Screen Recording permission
- Cloud sends `requestScreen` → Mac captures frame → sends within 500ms
- Confirm no files in `~/Library/`, `/tmp/`, or anywhere else after capture
- Open Activity Monitor: confirm memory does not accumulate over repeated captures

---

### Feature 7.5 — VoiceSession (Mac)

**Goal:** When at desk, user can speak to Gary through Mac mic. Gary responds via bone conduction (through phone relay) or Mac speaker.

**Files:**
- `Voice/VoiceSession.swift`
  ```swift
  import LiveKit
  
  class MacVoiceSession: ObservableObject {
      private var room: Room?
      @Published var state: SessionState = .idle
      
      func activate(roomURL: String, token: String) async {
          room = Room()
          let connectOptions = ConnectOptions(autoSubscribe: true)
          try? await room?.connect(roomURL, token, connectOptions: connectOptions)
          
          // Enable mic
          try? await room?.localParticipant.setMicrophoneEnabled(true)
          state = .listening
          
          // Subscribe to remote audio (Gary's voice) → play via Mac speaker
          // LiveKit handles audio routing automatically
      }
      
      func deactivate() async {
          try? await room?.disconnect()
          room = nil
          state = .idle
      }
  }
  ```

**How voice session is triggered on Mac:**
- Mac registers for `voiceSessionStart` cloud event (same event that triggers on phone)
- If Mac is the active client (macActive sent): cloud prefers Mac mic + sends room token to Mac

**Audio output:**
- LiveKit routes Gary's TTS to Mac speaker automatically
- If user has glasses on: Gary's voice also plays on bone conduction via phone relay (both play)

**Testing 7.5:**
- Mac mic open → speak → Gary responds through Mac speaker
- Wear glasses simultaneously → Gary's voice plays through both Mac speaker AND bone conduction
- Mac in background → voice does NOT activate (only when Mac is frontmost session)

---

### Feature 7.6 — Login + Settings

**Goal:** User logs into same Pinch account as phone. Settings for overlay behavior.

**Login flow:**
- First launch → show login window (SwiftUI sheet)
- Email + password → POST to `/auth/login` → store JWT in Keychain
- On success: close login, start GaryClient connection

**Settings window (accessible from menu bar):**
```
Account: theboogeyman@gmail.com (sign out)
Connection: ● Connected to Gary
Overlay position: Bottom-right ▼ (dropdown: bottom-right, bottom-left, top-right, top-left)
Overlay duration: 8 seconds (slider 4–20s)
Screen capture: enabled ✓
Microphone: enabled ✓
```

**KeychainManager.swift:**
```swift
class KeychainManager {
    static let shared = KeychainManager()
    private let service = "com.pinch.gary"
    
    func saveAccessToken(_ token: String) {
        save(key: "access_token", value: token)
    }
    
    func getAccessToken() -> String? {
        get(key: "access_token")
    }
    
    private func save(key: String, value: String) {
        let data = value.data(using: .utf8)!
        let query: [CFString: Any] = [
            kSecClass: kSecClassGenericPassword,
            kSecAttrService: service,
            kSecAttrAccount: key,
            kSecValueData: data,
        ]
        SecItemDelete(query as CFDictionary)
        SecItemAdd(query as CFDictionary, nil)
    }
}
```

**Testing 7.6:**
- Login with account → JWT stored in Keychain (verify in Keychain Access app)
- Sign out → JWT deleted from Keychain, WebSocket disconnects
- Login from both Mac and Android simultaneously → both sessions active in cloud
- Cloud sends event → both Mac overlay AND Android overlay show (if both connected)

---

### Feature 7.7 — macOS Permissions Handling

**Goal:** App requests Screen Recording and Microphone permissions gracefully. Shows clear explanations before triggering system prompts.

**Permissions needed:**
1. Screen Recording (`CGPreflightScreenCaptureAccess()`)
2. Microphone (`AVCaptureDevice.requestAccess(for: .audio)`)
3. Accessibility (optional — for reading focused app name)

**Permission flow:**
```swift
// On first launch:
func requestPermissions() async {
    // Screen Recording
    if !CGPreflightScreenCaptureAccess() {
        // Show explanation sheet first
        // Then: CGRequestScreenCaptureAccess() — this shows system prompt
        CGRequestScreenCaptureAccess()
    }
    
    // Microphone
    let micStatus = await AVCaptureDevice.requestAccess(for: .audio)
    // Store status
}
```

**Testing 7.7:**
- Fresh install → permissions sheet shown before system prompt
- Deny Screen Recording → screen capture gracefully fails with message
- Grant all permissions → all features work

---

## Phase 7 Integration Test

- [ ] Mac app builds and runs (Xcode → build)
- [ ] No Dock icon, menu bar item visible
- [ ] Login with Pinch account credentials
- [ ] WebSocket connects to cloud (verify in cloud logs)
- [ ] Cloud sends `showOverlay` → text appears in bottom-right corner
- [ ] Overlay click-through: clicking behind overlay works normally
- [ ] Overlay auto-dismisses after 8 seconds
- [ ] Say "Gary, what am I working on?" through Mac mic → Gary uses screen context to respond
- [ ] Screen capture: `requestScreen` received → JPEG sent → no files on disk
- [ ] Wear glasses + use Mac simultaneously → Gary speaks through both bone conduction AND Mac speaker
- [ ] Sign out → reconnect → works again

---

## Phase 7 Exit Criteria

1. App builds with zero errors in Xcode
2. Menu bar app, no Dock icon
3. WebSocket connects and reconnects automatically
4. `showOverlay` command displays correctly
5. Screen capture works: captures frame, sends as JPEG, no disk writes
6. Voice session works through Mac mic
7. Keychain stores JWT correctly
8. App works on macOS 12.3+ (ScreenCaptureKit minimum)
9. All permissions handled gracefully (no crash if denied)
