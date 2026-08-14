# Gary v1 — Complete Laptop Companion Reference

This document captures everything built and working in v1 so that v2 can be designed with full context. It covers architecture, file-by-file detail, event contracts, UI states, voice pipeline, tools, and hard-won platform fixes.

---

## What v1 is

Gary is an ambient AI assistant for smart glasses that runs a companion app on the laptop. The laptop app is an **always-on-top transparent overlay** that:

- Subscribes to a Redis pub/sub event bus
- Renders an ambient arc UI in the bottom-right corner of the screen
- Shows cards, toasts, and modals when events arrive
- Listens for spacebar hold → opens a LiveKit voice room
- Publishes results back to the bus (screenshots, clipboard confirms, permission responses)

Design ethos: "a presence, not an app." Never intrudes. The arc lives in the corner. Only the bottom-right 420×360 px hot zone accepts mouse clicks; everywhere else, clicks pass through to the desktop.

---

## Tech stack

| Layer | What |
|---|---|
| Window | pywebview 6 — frameless, transparent, always-on-top |
| UI | Plain HTML/CSS/JS in `laptop_app/web/` |
| Styles | `web/assets/gary.css` — namespaced `.g-` component library |
| Logic | `web/assets/app.js` — vanilla JS, no frameworks |
| Fonts | Hanken Grotesk (body) + JetBrains Mono (code/eyebrows) via Google Fonts |
| Bus | Redis pub/sub — single channel `"events"` |
| Python runtime | Python 3.11+, managed by `uv` |
| Voice | LiveKit Agents SDK, Deepgram STT, Gemini 2.5 Flash LLM, Cartesia TTS |
| Screen capture | mss + Pillow, saved to `~/.gary/screenshots/` |
| Entry point | `laptop_app/main_pywebview.py` |
| Voice worker | `laptop_app/voice_agent.py` |

---

## File map

```
laptop_app/
  main_pywebview.py      # Window setup, bus subscriber, spacebar trigger
  voice_agent.py         # LiveKit worker — STT/LLM/TTS pipeline + bus state
  screenshot.py          # mss screen capture → path + base64
  web/
    index.html           # Shell: transparent body, loads gary.css + app.js
    assets/
      gary.css           # All component styles, namespaced .g-
      app.js             # All UI state machine logic, exposed on window.gary

bus/
  redis_bus.py           # Bus class: connect / publish / subscribe / close

events/
  schema.py              # Event(type, timestamp, source, payload) — READ ONLY

tools/
  teach.py               # Block-by-block coding lesson tool
  youtube_notes.py       # Fetch + summarize active YouTube video
  home_assistant.py      # Hue bulb control
  show_popup.py          # Helper to publish show_popup events
  request_screenshot.py  # Helper to publish request_screenshot events
  copy_to_clipboard.py   # Helper to publish copy_to_clipboard events
  speak.py               # Helper to publish speak events
  memory.py              # Memory tool stub
  play_music.py          # Music tool stub
```

---

## Boot sequence (main_pywebview.py)

1. `NSScreen.mainScreen().frame()` reads the real display size before pywebview starts — `webview.screens` is unreliable until after `start()`.
2. `webview.create_window()` creates the window: `frameless=True`, `on_top=True`, `http_server=True`. `transparent=True` is intentionally **omitted** (see macOS 26 fix below).
3. `window.events.loaded` fires `on_loaded` → starts the Redis bus subscriber on a daemon thread.
4. `window.events.shown` fires `on_shown` → calls `_setup_click_through(window, screen_w)`.

### macOS 26 transparency fix

pywebview's `transparent=True` calls `_setDrawsTransparentBackground:` via a private KVC key that became a **fatal exception on macOS 26**. Instead, v1 applies transparency manually in `on_shown` via `AppHelper.callAfter` (dispatches to the Cocoa main thread):

```python
ns_window.setOpaque_(False)
ns_window.setBackgroundColor_(NSColor.clearColor())
# Access WKWebView via pywebview's internal BrowserView registry
instance.webview.setValue_forKey_(False, 'drawsBackground')
```

The modern `'drawsBackground'` KVC key works correctly on macOS 26.

### Click-through hot zone

`NSTimer` polls cursor position every 50 ms:
- Outside the 420×360 px bottom-right hot zone → `setIgnoresMouseEvents_(True)` (clicks pass through)
- Inside the hot zone → mouse events enabled (Gary is interactive)

The `HotZoneMonitor` NSObject is kept in `_hot_zone_monitor` to prevent Cocoa GC.

### Spacebar hold → voice trigger

A global `NSEvent` monitor on the main run loop listens for `NSEventMaskKeyDown | NSEventMaskKeyUp`. When spacebar (key code 49) is held:

1. `_space_pressed()` starts a daemon thread running `_progress_loop()`
2. Every 100ms, calls `gary.showSpaceHold(progress)` (0.0 → 1.0) to fill the arc
3. At 5.0 seconds: `_create_livekit_room()` is called
4. Creates a LiveKit room, generates a user JWT token, calls `gary.connectMic(url, token)` in the webview
5. Release before 5s: `_space_released()` resets arc to `gary.renderIndicator()`

---

## Event bus contract

**Channel:** `"events"` (single Redis channel, everyone subscribes to the same stream)

**Schema** (`events/schema.py` — do not touch):
```
Event(type: str, timestamp: float, source: str, payload: dict)
```

### Events the laptop app listens for

| Event type | Payload keys | What the app does |
|---|---|---|
| `show_popup` | `title`, `text` | Calls `gary.queuePopup(title, text)` — shows card immediately |
| `request_screenshot` | `reason` | Captures screen → publishes `screenshot_taken` |
| `request_permission` | `title`, `description` | Shows permission modal |
| `show_listening` | — | Calls `gary.showListening()` |
| `show_thinking` | — | Calls `gary.showThinking()` |
| `show_toast` | `message`, `duration` | Calls `gary.showToast(message, duration)` |
| `show_idle` | — | Calls `gary.renderIndicator()` |
| `copy_to_clipboard` | `text` | Runs `pbcopy` subprocess |
| `gary_listening` | — | Voice agent state → `gary.showListening()` |
| `gary_thinking` | — | Voice agent state → `gary.showThinking()` |
| `gary_speaking` | — | Voice agent state → `gary.showThinking()` (thinking arc while TTS plays) |
| `gary_idle` | — | Voice agent state → `gary.renderIndicator()` + `gary.disconnectMic()` |

### Events the laptop app publishes

| Event type | Source | Payload | When |
|---|---|---|---|
| `screenshot_taken` | `laptop_app` | `path, base64, width, height, reason` | After successful capture |
| `screenshot_failed` | `laptop_app` | `error, reason` | If capture throws |
| `permission_granted` | `laptop_app` | `{}` | User clicks Allow in modal |
| `permission_denied` | `laptop_app` | `{}` | User clicks Deny in modal |

---

## JS bridge (Python ↔ JS)

**Python → JS:** `window.evaluate_js("gary.someMethod(jsonArgs)")` — thread-safe in pywebview, queues on the main thread.

**JS → Python:** `pywebview.api.<method>()` — routes to `PythonAPI` class methods, called on a worker thread.

### PythonAPI methods

```python
copy_to_clipboard(text: str)      # Runs pbcopy subprocess
permission_response(allowed: bool) # Publishes permission_granted or permission_denied
```

Navigator.clipboard is **never used** — it's unreliable inside WKWebView.

---

## UI state machine (app.js / gary.css)

The entire UI is one `<div class="g-overlay">`. State transitions replace its `innerHTML`. The global `window.gary` object is the public API.

### States and their markup

| State | Function | CSS class | Description |
|---|---|---|---|
| Idle / indicator | `gary.renderIndicator()` | `.g-indicator` | Quarter-circle arc, bottom-right corner. Clickable SVG. |
| Space hold | `gary.showSpaceHold(progress)` | `.g-hold` + `.g-arc-fill` | Arc fills (0→1) as user holds spacebar. Injects `.g-arc-fill` path. |
| Listening | `gary.showListening()` | `.g-listen` | Base arc dims to 18%, comet of light animates along the path (1.9s loop). |
| Thinking | `gary.showThinking()` | `.g-think` | Three concentric arcs breathe with staggered delays (2.6s). |
| Speaking | `gary.showSpeaking()` | `.g-speak` | Faster ripple arcs (1.2s), "speaking" eyebrow label. |
| Card | `gary.renderCard(title, text)` | `.g-card[data-h]` | Fixed 288px card, scrollable body, Copy + Close buttons. Auto-dismisses after 10s. |
| Toast | `gary.showToast(message, duration)` | `.g-toast` | Overlaid (appended, not replacing). Slides in, self-dismisses after `duration`ms. |
| Permission modal | `gary.showPermission(title, desc)` | `.g-modal` | Centered modal with Deny (ghost) + Allow (pill) buttons. |

### State machine rules

- `_cardVisible = true` while a card is showing. `showListening()`, `showThinking()`, `showSpeaking()` all `return` immediately if `_cardVisible` is true — voice state changes never dismiss a card.
- `_lastCard` stores the last `{title, text}`. The idle indicator's click handler always reopens it.
- `queuePopup(title, text)` sets `_lastCard` and immediately calls `renderCard` — bypasses the "tap arc to open" flow.
- `renderCard` starts a 10s auto-dismiss timer; close button cancels it.

### Arc geometry

Quarter-circle anchored at the bottom-right corner:
- SVG `viewBox="0 0 200 200"`, positioned `right:0; bottom:0`
- Arc path: `M0 200 A200 200 0 0 1 200 0` (starts bottom-left of box, sweeps to top-right)
- Total arc length ≈ π/2 × 200 ≈ **314.16 px** (used for stroke-dasharray in space hold)
- Concentric arcs: radius 200 (outer), 150 (middle, `M50 200 A150 150 0 0 1 200 50`), 100 (inner, `M100 200 A100 100 0 0 1 200 100`)

### CSS token system (gary.css)

All tokens on `:root`. Key ones:
```css
--g-accent: oklch(0.71 0.145 286)   /* periwinkle; swap hue to retheme */
--g-accent-soft: color-mix(...26%)   /* glow halos */
--g-surface: #151515
--g-text: #e8e8e8
--g-dim: #888888
--g-radius: 14px
--g-font: "Hanken Grotesk"
--g-mono: "JetBrains Mono"
--g-dur-comet: 1.9s     /* listening comet speed */
--g-dur-breathe: 2.6s   /* thinking breathe speed */
--g-dur-speak: 1.2s     /* speaking ripple speed */
```

Entrance animations: `.g-rise` (slide up 20px, 0.42s) + `.g-fade` (fade in, 0.42s) applied together to cards and modals.

---

## Screen capture (screenshot.py)

```
capture_screen() → { path, base64, width, height }
```

- Uses `mss` (fast cross-platform capture), saves PNG to `~/.gary/screenshots/`
- Also encodes as base64 for direct use in cloud LLM calls
- Called from `main_pywebview.py` via `asyncio.to_thread()` so it doesn't block the event loop

---

## Voice agent (voice_agent.py)

Run as a separate process: `uv run python -m laptop_app.voice_agent start`

Registers as a **LiveKit agent worker**. When a room is created (from spacebar hold), LiveKit dispatches a job here.

### Pipeline

```
Deepgram STT (nova-3) → Gemini 2.5 Flash LLM → Cartesia TTS (sonic-2)
```

- VAD: Silero
- TTS voice: `248be419-c632-4f23-adf1-5324ed7dbf1d` ("Barbershop Man" — clear, warm)
- Session class: `AgentSession` (LiveKit Agents SDK 1.5+)

### State → bus mapping

On `agent_state_changed` events, the voice agent publishes to the bus:

```python
"listening" → "gary_listening"
"thinking"  → "gary_thinking"
"speaking"  → "gary_speaking"
"idle"      → "gary_idle"
```

The overlay subscribes and updates its arc state accordingly.

### System prompt behaviour

Gary is instructed to be concise (1–3 sentences), conversational, no markdown, natural speech. Three capability areas are explicitly instrumented:

1. **Lights** — must call `control_lights` tool, not just say it will
2. **Teaching mode** — full diagnostic → lesson flow (see below)
3. **YouTube notes** — detect current tab, fetch transcript, summarize

### Function tools registered on GaryVoiceAgent

| Tool | What it does |
|---|---|
| `control_lights(action)` | Calls `home_assistant.control_bulb(action, bus)` |
| `begin_teaching(topic)` | Starts diagnostic flow, returns first question |
| `answer_diagnostic(answer)` | Records user answer, may ask follow-up, then builds + starts lesson |
| `next_lesson_block()` | Advances to next lesson block or ends session |
| `get_youtube_notes()` | Fetches + summarizes active YouTube video, shows popup |

### LiveKit microphone in webview (app.js)

`gary.connectMic(url, token)` — joins the LiveKit room as mic-only participant:
- Uses `LivekitClient` (loaded from CDN in index.html)
- Attaches remote audio tracks to `<audio autoplay>` elements appended to `document.body`
- Stores the room in `window._garyRoom`

`gary.disconnectMic()` — disconnects from room, nulls `window._garyRoom`. Called when `gary_idle` fires.

---

## Teaching tool (tools/teach.py)

Block-by-block coding lessons driven by voice, displayed on the overlay.

### Flow

```
begin_teaching(topic)         → asks diagnostic Q1
answer_diagnostic(answer)     → may ask Q2 (for "some" experience), then starts lesson
next_lesson_block()           → advance one block at a time
```

### Experience levels

- `"beginner"` — never written code; 7–8 blocks, 1–4 lines each, jargon-free
- `"some"` — knows basic HTML/CSS; 6–7 blocks, 1–6 lines each
- `"comfortable"` — knows HTML/CSS well; 5–6 blocks, up to 8 lines, brisk pace

### Lesson spec generation

Gemini 2.5 Flash generates a JSON array of `TeachingBlock` objects via a level-specific prompt. Each block:
```json
{ "file": "index.html", "action": "append|overwrite", "code": "...", "explanation": "..." }
```

- `"append"` → opens file in `"a"` mode (adds to end)
- `"overwrite"` → opens file in `"w"` mode (replaces entire file)

Lesson files are written to `~/Gary_lessons/{slugified_topic}/`. The folder is opened in Cursor → VS Code → Finder (whichever is available).

Each block publishes a `show_popup` event so the overlay shows the code being written.

### State storage

Module-level globals (one session at a time):
- `_active_session: TeachingSession | None`
- `_diagnostic: DiagnosticState | None`

---

## YouTube Notes tool (tools/youtube_notes.py)

Triggered by voice: "get notes from this video."

### Flow

1. AppleScript reads active tab URL from Chrome, then Safari
2. Extracts YouTube video ID from URL (handles `watch?v=`, `youtu.be/`, `/embed/`, `/shorts/`)
3. Fetches title (HEAD request) and transcript (`youtube-transcript-api`) concurrently
4. Summarizes with Gemini 2.5 Flash (caps transcript at 12k chars)
5. Saves to `~/Gary_notes/{slug}/notes.txt`, opens in editor
6. Publishes `show_popup` (first 500 chars + path note)
7. Publishes `copy_to_clipboard` (full notes)
8. Returns a short spoken confirmation

### Summary format (Gemini prompt)

- **Coding/technical**: key concepts + extracted code snippets + one "Try this" challenge
- **General**: 2–3 sentence summary + 5–8 key takeaways + notable resources

---

## Bus helper (bus/redis_bus.py)

```python
bus = Bus()
await bus.connect()           # connects + pings Redis
await bus.publish(event)      # publishes Event as JSON
async for event in bus.subscribe():  # yields Event objects
await bus.close()
```

One channel: `"events"`. Events are JSON-serialized via Pydantic `model_dump_json()` and deserialized via `model_validate_json()`.

---

## Dev testing

Three terminals:

```bash
# 1 — watch all bus events
uv run python -m scripts.monitor_bus

# 2 — run the overlay
uv run python -m laptop_app.main_pywebview

# 3 — fire a test event
uv run python -m scripts.publish_event show_popup "Test message"
```

For CSS/JS work without running pywebview: open `laptop_app/web/index.html?dev` in a browser. The `?dev` query flag adds `background: #0c0c0c` to `<html>` so you can see the overlay.

Voice agent (separate terminal):
```bash
uv run python -m laptop_app.voice_agent start
```

---

## Known platform constraints

- **macOS only** (uses AppKit, NSEvent, osascript, pbcopy, NSScreen)
- pywebview's `transparent=True` crashes on macOS 26 — use the manual `drawsBackground` KVC approach
- `AppHelper.callAfter` is required for any NSWindow/NSTimer operations called from `on_shown` — calling them directly from a non-main thread crashes on macOS 26
- `NSEvent.addGlobalMonitorForEventsMatchingMask_handler_` must be called from the Cocoa main run loop; the reference must stay alive (stored in `_space_event_monitor`)
- `pbcopy` is macOS-only; Windows/Linux would need a different clipboard mechanism
- `osascript` is macOS-only; YouTube notes tool won't work elsewhere

---

## What is NOT in this repo / off-limits for v1

- `agent/` — the Pydantic AI reasoning agent (separate process)
- `bus/` definitions (only redis_bus.py was added)
- `pipelines/` — data pipelines that feed events
- `events/schema.py` — read-only contract
- `config/` — configuration
- `laptop_app/main.py`, `laptop_app/popup.py` — old PyQt6 files, reference only, do not run or edit
