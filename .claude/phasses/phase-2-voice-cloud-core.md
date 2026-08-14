# Phase 2 — Voice + Cloud Core

**Timeline:** Weeks 5–8  
**Milestone:** M2 (Android) + M3 (Cloud, minimal)  
**Exit criteria:** Pinch gesture → Gary speaks back through phone speaker. Minimal FastAPI server running. LiveKit + Deepgram + Gemini + Cartesia pipeline works end-to-end.

---

## What This Phase Builds

The voice pipeline — the core product loop. User pinches → mic opens → Gary hears them → Gary speaks back. This is the first time the product feels real.

**Runs in parallel with Phase 1 completion.** Cloud backend is built while Android is being tested.

---

## Feature Breakdown

---

### Feature 2.1 — Cloud: Project Scaffold

**Goal:** FastAPI app runs locally and on a server, with Docker. All secrets via environment variables.

**Directory structure to create:**
```
cloud/
├── app/
│   ├── main.py              # FastAPI app factory
│   ├── config.py            # Settings (pydantic-settings)
│   ├── dependencies.py      # FastAPI dependency injection
│   ├── routers/
│   │   ├── auth.py          # /auth/register, /auth/login, /auth/refresh
│   │   ├── websocket.py     # /ws — Gary event bus
│   │   └── health.py        # /health
│   ├── models/
│   │   ├── user.py          # SQLAlchemy User model
│   │   └── memory.py        # SQLAlchemy Memory model
│   ├── schemas/
│   │   ├── auth.py          # Pydantic schemas for auth
│   │   └── events.py        # WebSocket event schemas
│   ├── services/
│   │   ├── auth_service.py  # JWT logic
│   │   ├── gary_agent.py    # Gary LLM agent (Phase 3 expands this)
│   │   └── voice_service.py # LiveKit room management
│   └── db/
│       ├── database.py      # SQLAlchemy async engine
│       └── migrations/      # Alembic migrations
├── Dockerfile
├── docker-compose.yml
├── requirements.txt
├── .env.example             # Template (never commit real .env)
└── README.md
```

**`config.py` — all secrets from env:**
```python
from pydantic_settings import BaseSettings

class Settings(BaseSettings):
    DATABASE_URL: str
    REDIS_URL: str
    JWT_SECRET: str
    JWT_ALGORITHM: str = "HS256"
    ACCESS_TOKEN_EXPIRE_MINUTES: int = 60
    REFRESH_TOKEN_EXPIRE_DAYS: int = 30
    LIVEKIT_API_KEY: str
    LIVEKIT_API_SECRET: str
    LIVEKIT_URL: str
    DEEPGRAM_API_KEY: str
    GEMINI_API_KEY: str
    CARTESIA_API_KEY: str
    
    class Config:
        env_file = ".env"

settings = Settings()
```

**`docker-compose.yml`:**
```yaml
services:
  api:
    build: .
    ports: ["8000:8000"]
    env_file: .env
    depends_on: [postgres, redis]
  postgres:
    image: postgres:16
    environment:
      POSTGRES_DB: pinch
      POSTGRES_USER: pinch
      POSTGRES_PASSWORD: pinch_dev
    volumes: ["pgdata:/var/lib/postgresql/data"]
  redis:
    image: redis:7-alpine
volumes:
  pgdata:
```

**Testing 2.1:**
- `docker-compose up` → all services start
- `curl http://localhost:8000/health` → `{"status": "ok"}`
- `curl http://localhost:8000/docs` → Swagger UI loads

---

### Feature 2.2 — Cloud: Auth (JWT)

**Goal:** Register, login, get access token + refresh token. Phone stores tokens in Android Keystore.

**Files:**
- `app/routers/auth.py`
  - `POST /auth/register` — email + password → create user → return token pair
  - `POST /auth/login` — email + password → return token pair
  - `POST /auth/refresh` — refresh token → new access token (rotate refresh token)
- `app/services/auth_service.py`
  - `hash_password(plain: str) → str` — bcrypt
  - `verify_password(plain: str, hashed: str) → bool`
  - `create_access_token(user_id: UUID) → str`
  - `create_refresh_token(user_id: UUID) → str`
  - `decode_token(token: str) → dict`
- `app/models/user.py`
  ```python
  class User(Base):
      id: UUID
      email: str (unique)
      password_hash: str
      stripe_customer_id: str (nullable)
      subscription_status: str  # "active" | "inactive" | "trial"
      created_at: datetime
  ```

**Android side (KeystoreManager.kt):**
```kotlin
// Already scaffolded — fill in these methods:
fun saveAccessToken(token: String)
fun saveRefreshToken(token: String)
fun getAccessToken(): String?
fun getRefreshToken(): String?
fun clearTokens()
```
Tokens stored as `EncryptedSharedPreferences` backed by Android Keystore — NOT plain SharedPreferences.

**Token lifecycle:**
- Access token: 60 minutes
- Refresh token: 30 days, rotated on every use
- If refresh fails (expired): user must re-login → show login screen

**Testing 2.2:**
- Register new user, get token pair
- Use access token to hit a protected endpoint
- Wait for token expiry (or manually expire) → refresh → get new token
- On Android: confirm tokens stored in Keystore (not visible in file system)

---

### Feature 2.3 — Cloud: WebSocket Event Bus

**Goal:** Phone opens WebSocket to cloud, sends gesture events, cloud echoes back. No Gary logic yet — just the plumbing.

**Files:**
- `app/routers/websocket.py`
  ```python
  @router.websocket("/ws")
  async def gary_ws(websocket: WebSocket, token: str = Query(...)):
      # 1. Validate JWT token
      user_id = auth_service.decode_token(token)["sub"]
      # 2. Accept connection
      await websocket.accept()
      # 3. Register session (Redis: user_id → websocket)
      await session_manager.register(user_id, websocket)
      # 4. Event loop
      try:
          while True:
              data = await websocket.receive_json()
              await handle_event(user_id, data, websocket)
      except WebSocketDisconnect:
          await session_manager.unregister(user_id)
  ```
- `app/schemas/events.py` — Pydantic models for all event types:
  ```python
  class GestureDetectedEvent(BaseModel):
      type: Literal["gestureDetected"]
      gesture: str
      ts: int
  
  class ObjectsInViewEvent(BaseModel):
      type: Literal["objectsInView"]
      objects: List[str]
      ts: int
  
  class DeviceSeenEvent(BaseModel):
      type: Literal["deviceSeen"]
      markerId: int
      ts: int
  
  class FrameForVisionEvent(BaseModel):
      type: Literal["frameForVision"]
      jpeg_b64: str
      ts: int
  
  # Cloud → Phone
  class ShowOverlayCommand(BaseModel):
      type: Literal["showOverlay"]
      text: str
  
  class RequestFrameCommand(BaseModel):
      type: Literal["requestFrame"]
      reason: str
  
  class HomeAssistantCallCommand(BaseModel):
      type: Literal["homeAssistantCall"]
      service: str
      entityId: str
      data: dict = {}
  ```

**Android side — GaryClient:**
- New file: `gary/GaryClient.kt` (interface)
  ```kotlin
  interface GaryClient {
      val incomingCommands: Flow<CloudCommand>
      suspend fun connect(token: String)
      suspend fun disconnect()
      suspend fun sendGestureDetected(gesture: String)
      suspend fun sendObjectsInView(objects: List<String>)
      suspend fun sendDeviceSeen(markerId: Int)
      suspend fun sendFrameForVision(jpegBytes: ByteArray)
  }
  ```
- New file: `gary/GaryClientImpl.kt`
  - Uses `OkHttp WebSocket`
  - Reconnects with exponential backoff (same pattern as BLE)
  - JWT re-auth on reconnect
  - Parses incoming JSON → `CloudCommand` sealed class
- New file: `gary/GaryModule.kt` — Hilt module providing GaryClient

**Reconnect strategy:**
```
Connect attempt fails → wait 1s → retry
Fails again → wait 2s → retry
Caps at 30s between retries
On JWT expiry error (401): call refresh endpoint, then reconnect
```

**Testing 2.3:**
- Open WebSocket from Postman to ws://localhost:8000/ws?token=<jwt>
- Send `{"type": "gestureDetected", "gesture": "pinch", "ts": 123}` → cloud logs it
- On Android: perform pinch → confirm WebSocket message sent (logcat)
- Kill server → confirm Android attempts reconnect with backoff
- Restart server → confirm Android reconnects automatically

---

### Feature 2.4 — Cloud: LiveKit Room Management

**Goal:** Cloud creates a LiveKit room when a voice session starts. Phone joins the room. Voice flows both ways.

**Files:**
- `app/services/voice_service.py`
  ```python
  async def create_room(user_id: str) -> tuple[str, str]:
      """Create LiveKit room, return (room_url, participant_token)"""
      # Use LiveKit Python SDK
      # Room name: f"gary-{user_id}"
      # Return join URL + participant JWT
  
  async def create_agent_token(room_name: str) -> str:
      """Token for Gary voice agent to join the room"""
  ```
- `app/routers/websocket.py` — handle `voiceSessionStart` event:
  ```python
  # When phone sends voiceSessionStart:
  # 1. Create LiveKit room
  # 2. Send back roomUrl + token via WebSocket
  # Phone uses these to join room
  ```

**LiveKit account setup (one-time):**
- Create account at livekit.io
- Get API key + API secret
- Add to `.env`
- Use LiveKit Cloud (managed) — no self-hosting needed for now

**Testing 2.4:**
- Send `voiceSessionStart` event over WebSocket → get back room URL + token
- Use LiveKit test client (livekit.io/meet) to join same room → confirm both participants visible

---

### Feature 2.5 — Cloud: Gary Voice Agent (Minimal)

**Goal:** Gary voice agent joins the LiveKit room, transcribes speech (Deepgram), sends to Gemini, speaks back (Cartesia). No tools yet — just conversation.

**Files:**
- `app/services/gary_agent.py`
  ```python
  from livekit.agents import AutoSubscribe, JobContext, WorkerOptions, cli
  from livekit.agents.voice import VoiceAgent
  from livekit.plugins import deepgram, cartesia, google
  
  async def entrypoint(ctx: JobContext):
      await ctx.connect(auto_subscribe=AutoSubscribe.AUDIO_ONLY)
      
      agent = VoiceAgent(
          vad=silero.VAD.load(),
          stt=deepgram.STT(model="nova-3"),
          llm=google.LLM(model="gemini-2.5-flash"),
          tts=cartesia.TTS(voice="248be419-c632-4f23-adf1-5324ed7dbf1d"),
          # Cartesia voice ID: warm, clear — same as Gary v1
      )
      
      agent.start(ctx.room)
      
      # Gary's system prompt (minimal for Phase 2):
      await agent.say(
          "Hey. I'm Gary. What do you need?",
          allow_interruptions=True
      )
  
  if __name__ == "__main__":
      cli.run_app(WorkerOptions(entrypoint_fnc=entrypoint))
  ```

**Gary's system prompt (Phase 2 — no tools yet):**
```
You are Gary, an ambient AI assistant running on smart glasses. 
You are concise — respond in 1–3 sentences maximum.
You speak naturally, as if in conversation. No bullet points, no markdown.
You hear the user through their glasses microphone.
You speak back through bone conduction speakers in the glasses.
If you don't know something, say so briefly.
Do not mention that you are an AI unless directly asked.
```

**Run the voice agent as a separate process:**
```bash
python -m app.services.gary_agent start
```

**For Phase 2 the agent is started manually.** Auto-dispatch via LiveKit agent dispatch is Phase 3.

**Testing 2.5:**
- Start gary_agent process
- Join LiveKit room from test client
- Speak "Gary, what time is it?" → confirm transcription in logs
- Confirm Gemini responds with current time
- Confirm Cartesia TTS plays back
- Measure end-to-end latency: aim for <2s from end of speech to first word spoken

---

### Feature 2.6 — Android: VoiceSession

**Goal:** GaryCommand.Wake/PushToTalk → phone joins LiveKit room → mic opens → Gary speaks back through phone speaker (bone conduction comes in Phase 5 with hardware).

**Files (all new):**
- `voice/VoiceSession.kt` (interface)
  ```kotlin
  interface VoiceSession {
      val state: StateFlow<VoiceSessionState>
      suspend fun activate()
      suspend fun deactivate()
  }
  
  sealed class VoiceSessionState {
      object Idle : VoiceSessionState()
      object Connecting : VoiceSessionState()
      object Listening : VoiceSessionState()
      object AgentSpeaking : VoiceSessionState()
      data class Error(val message: String) : VoiceSessionState()
  }
  ```
- `voice/VoiceSessionImpl.kt`
  - Calls `GaryClient.sendVoiceSessionStart()` → receives room URL + token from cloud via WebSocket
  - Uses `LiveKit Android SDK` to join room as participant
  - Subscribes to remote audio tracks (Gary's voice) → routes to phone speaker
  - On `GaryCommand.Stop` or silence → `deactivate()`
- `voice/VoiceModule.kt` — Hilt module

**Audio routing:**
- Phase 2: Gary's voice plays through phone speaker
- Phase 5: Gary's voice sent via BLE to bone conduction transducer on glasses

**GaryOrchestrator updates:**
```kotlin
// Wire GaryCommand → VoiceSession:
GaryCommand.Wake → voiceSession.activate()
GaryCommand.Stop → voiceSession.deactivate()
GaryCommand.PushToTalk → voiceSession.activate() (PTT mode)
```

**Testing 2.6:**
- Perform pinch gesture on device → confirm VoiceSession.Connecting → Listening
- Speak "Gary, say hello" → Gary responds through phone speaker
- Perform grab/stop gesture → VoiceSession deactivates
- App backgrounded → voice session must continue (Foreground Service must hold wakelock)

---

### Feature 2.7 — Android: AppState + UI Updates for Voice

**Goal:** UI reflects voice session state. StatusDot animates correctly during Listening, Thinking, Speaking.

**Files:**
- `ui/main/MainScreen.kt` — add pulsing animations:
  - `Idle` → static green dot
  - `WakeDetected` → brief white flash
  - `Listening` → blue slow pulse (2s period)
  - `Thinking` → yellow faster pulse (1s period)  
  - `Speaking` → purple fast ripple (0.8s period)
- `ui/components/StatusDot.kt` — implement animations using `animateFloatAsState` + `InfiniteTransition`

**Testing 2.7:**
- All state transitions confirmed visually on device
- Animation does not cause jank (check with Android Studio profiler)

---

## Phase 2 Integration Test (End-to-End Voice)

Run through this on a real device with cloud running locally (or deployed):

- [ ] Cloud: `docker-compose up` starts without errors
- [ ] Cloud: `python -m app.services.gary_agent start` runs
- [ ] Android: app connects to cloud WebSocket (check logcat)
- [ ] Android: perform pinch → VoiceSession activates
- [ ] Android: StatusDot turns blue (Listening)
- [ ] Speak "Gary, what's two plus two?" → Gary responds with "Four" through speaker
- [ ] Latency: under 2.5s from end of speech to Gary's first word
- [ ] Perform grab gesture → session ends, StatusDot returns to green
- [ ] Kill cloud → Android attempts reconnect → cloud restarts → Android reconnects

---

## Phase 2 Exit Criteria

1. Pinch → Gary speaks back, end-to-end, on a real device
2. Voice latency under 2.5s (targeting 1.5–2s)
3. WebSocket reconnects automatically after server restart
4. JWT tokens stored in Android Keystore (not SharedPreferences)
5. LiveKit room creates and closes cleanly
6. Gary agent process runs without crashing for 10 minutes of idle
7. All Phase 1 tests still pass
