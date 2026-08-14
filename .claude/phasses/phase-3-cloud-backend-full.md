# Phase 3 — Cloud Backend Full

**Timeline:** Weeks 7–10 (overlaps with Phase 4)  
**Milestone:** M3  
**Exit criteria:** Gary has tools. HA commands work. Memory persists across sessions. Vision on-demand works. All cloud routes tested.

---

## What This Phase Builds

Gary goes from "conversational" to "useful." Tools are wired up: lights, memory, and scene understanding. The cloud backend is production-ready (migrations, proper error handling, logging).

---

## Feature Breakdown

---

### Feature 3.1 — Database: Postgres + Alembic Migrations

**Goal:** Schema is version-controlled. All tables created via migrations, not raw SQL.

**Models to migrate:**

```python
# app/models/user.py
class User(Base):
    __tablename__ = "users"
    id: UUID = Column(UUID, primary_key=True, default=uuid4)
    email: str = Column(String, unique=True, nullable=False)
    password_hash: str = Column(String, nullable=False)
    stripe_customer_id: str = Column(String, nullable=True)
    subscription_status: str = Column(String, default="trial")
    created_at: datetime = Column(DateTime, default=datetime.utcnow)

# app/models/memory.py
class GaryMemory(Base):
    __tablename__ = "gary_memories"
    id: UUID = Column(UUID, primary_key=True, default=uuid4)
    user_id: UUID = Column(UUID, ForeignKey("users.id"), nullable=False)
    key: str = Column(String, nullable=False)       # "prefers_lights_dim"
    value: str = Column(Text, nullable=False)        # "true"
    source: str = Column(String, default="gary")    # "gary" | "user"
    created_at: datetime = Column(DateTime, default=datetime.utcnow)
    updated_at: datetime = Column(DateTime, onupdate=datetime.utcnow)

# app/models/conversation.py
class ConversationSummary(Base):
    __tablename__ = "conversation_summaries"
    id: UUID = Column(UUID, primary_key=True, default=uuid4)
    user_id: UUID = Column(UUID, ForeignKey("users.id"), nullable=False)
    summary: str = Column(Text, nullable=False)
    created_at: datetime = Column(DateTime, default=datetime.utcnow)
    # Raw audio/video: NEVER stored here
```

**Alembic setup:**
```bash
alembic init db/migrations
# Set sqlalchemy.url in alembic.ini to read from env
alembic revision --autogenerate -m "initial schema"
alembic upgrade head
```

**Testing 3.1:**
- Fresh `docker-compose up` → migrations run automatically on startup
- `alembic downgrade -1` → rolls back cleanly
- Confirm no raw audio/video columns exist anywhere in schema

---

### Feature 3.2 — Gary Agent: Tool Registration

**Goal:** Gary can call tools. Phase 3 tools: HA relay, frame request, memory lookup, memory save.

**Files:**
- `app/services/gary_agent.py` — expand with tools:

```python
from livekit.agents.llm import FunctionContext, ai_callable
from typing import Annotated

class GaryTools(FunctionContext):
    
    def __init__(self, user_id: str, ws_session: WebSocketSession, db: AsyncSession):
        self.user_id = user_id
        self.ws_session = ws_session  # user's WebSocket connection
        self.db = db
    
    @ai_callable(description="Turn a smart home device on or off")
    async def control_home_device(
        self,
        service: Annotated[str, "HA service like 'light.turn_on' or 'switch.toggle'"],
        entity_id: Annotated[str, "HA entity ID like 'light.kitchen' or 'switch.fan'"],
    ) -> str:
        cmd = HomeAssistantCallCommand(
            type="homeAssistantCall",
            service=service,
            entityId=entity_id,
        )
        await self.ws_session.send(cmd.model_dump_json())
        return f"Sent command: {service} to {entity_id}"
    
    @ai_callable(description="Look at what the user is seeing right now through the glasses camera")
    async def see_through_glasses(self) -> str:
        req = RequestFrameCommand(type="requestFrame", reason="user asked gary to look")
        await self.ws_session.send(req.model_dump_json())
        # Wait up to 3 seconds for frameForVision event
        frame_event = await self.ws_session.wait_for_event("frameForVision", timeout=3.0)
        if not frame_event:
            return "I couldn't see anything right now."
        jpeg_bytes = base64.b64decode(frame_event["jpeg_b64"])
        description = await gemini_vision.describe_image(jpeg_bytes)
        return description
    
    @ai_callable(description="Remember a fact about the user for future conversations")
    async def save_memory(
        self,
        key: Annotated[str, "Short snake_case key like 'prefers_dim_lights'"],
        value: Annotated[str, "The value to store"],
    ) -> str:
        memory = GaryMemory(user_id=self.user_id, key=key, value=value)
        self.db.add(memory)
        await self.db.commit()
        return f"Remembered: {key} = {value}"
    
    @ai_callable(description="Look up something Gary remembers about the user")
    async def lookup_memory(
        self,
        key: Annotated[str, "The memory key to look up"],
    ) -> str:
        result = await self.db.execute(
            select(GaryMemory).where(
                GaryMemory.user_id == self.user_id,
                GaryMemory.key == key
            )
        )
        memory = result.scalar_one_or_none()
        if memory:
            return f"{key}: {memory.value}"
        return f"No memory found for: {key}"
```

**Updated system prompt (Phase 3):**
```
You are Gary, an ambient AI assistant running on Pinch smart glasses.
You are concise — respond in 1–3 sentences maximum.
You speak naturally. No bullet points, no markdown.
You can control smart home devices. When the user asks to do something with lights, 
switches, or appliances, use the control_home_device tool — do not just say you will.
You can see through the glasses camera when asked. Use see_through_glasses when 
the user describes something they're looking at and you need visual context.
You remember things about the user across conversations using save_memory and lookup_memory.
Do not mention that you are an AI unless directly asked.
User context is injected automatically below. Use it to be relevant.
```

**Testing 3.2:**
- Say "Gary, turn off the kitchen light" → confirm `homeAssistantCall` WebSocket event sent to phone
- Say "Gary, what am I looking at?" → confirm `requestFrame` sent, Gemini Vision called
- Say "Gary, remember that I prefer dim lights" → confirm memory row in Postgres
- New session: say "Gary, what do I prefer for lighting?" → Gary recalls "dim lights"

---

### Feature 3.3 — Context Injection

**Goal:** Every Gary LLM call automatically includes user context (calendar, location, visible devices). Gary is always aware of the user's situation.

**Context injected as system message addendum:**
```python
def build_context_block(context: UserContext) -> str:
    lines = ["\n--- User Context (auto-updated) ---"]
    if context.upcomingEvents:
        lines.append("Upcoming events:")
        for event in context.upcomingEvents[:3]:
            lines.append(f"  - {event.title} at {event.start_time}")
    lines.append(f"Location: {context.location}")
    lines.append(f"Time of day: {context.time_of_day}")
    if context.devices_seen:
        lines.append(f"Devices visible (ArUco IDs): {context.devices_seen}")
    if context.objects_in_view:
        lines.append(f"Objects in view: {', '.join(context.objects_in_view)}")
    return "\n".join(lines)
```

**Context arrives via WebSocket events:**
- `contextUpdate` event from phone → stored in Redis (user session state, TTL 5min)
- `objectsInView` event → updates Redis session state
- `deviceSeen` event → updates Redis session state
- Before each LLM call: read from Redis, inject into system prompt addendum

**Redis session state structure:**
```python
# Key: f"session:{user_id}"
# Value: JSON
{
    "upcoming_events": [...],
    "location": "home",
    "time_of_day": "evening",
    "devices_seen": [3, 7],
    "objects_in_view": ["laptop", "coffee mug"],
    "last_updated": 1234567890
}
```

**Testing 3.3:**
- Send `contextUpdate` event from Android → Redis stores it
- Trigger voice session → ask "what's next on my calendar?" → Gary uses injected context to answer
- Send `objectsInView` → ask "what am I holding?" → Gary uses YOLO data

---

### Feature 3.4 — Gemini Vision Integration

**Goal:** `see_through_glasses` tool works. Single JPEG sent to Gemini Vision API, text description returned. Image never stored.

**Files:**
- `app/services/vision_service.py`
  ```python
  import google.generativeai as genai
  from PIL import Image
  import io
  
  async def describe_image(jpeg_bytes: bytes) -> str:
      """Send JPEG to Gemini Vision, return text description. Image discarded after."""
      img = Image.open(io.BytesIO(jpeg_bytes))
      model = genai.GenerativeModel("gemini-2.5-flash")
      response = await asyncio.to_thread(
          model.generate_content,
          [
              "Describe what you see in this image concisely in 1-2 sentences. "
              "Focus on what would be most relevant to someone wearing smart glasses.",
              img
          ]
      )
      # img is garbage collected here — never written to disk
      return response.text
  ```

**Privacy enforcement:**
- `jpeg_bytes` is a local variable — no `open()`, no `write()`, no `save()` calls
- Response is text only → stored nowhere (used immediately in TTS)
- Add a comment: # PRIVACY: image is never persisted to disk or database

**Testing 3.4:**
- Pass a test JPEG to `describe_image()` → returns text
- Confirm no files created in `/tmp/` or any directory
- Confirm no database writes during vision call

---

### Feature 3.5 — Conversation Summarization

**Goal:** After each voice session ends, conversation is summarized and stored in Postgres. Raw transcripts are discarded.

**Files:**
- `app/services/summary_service.py`
  ```python
  async def summarize_session(
      user_id: str,
      transcript: list[dict],  # [{"role": "user"|"gary", "text": str}]
      db: AsyncSession
  ) -> None:
      if not transcript:
          return
      
      conversation_text = "\n".join(
          f"{turn['role'].upper()}: {turn['text']}"
          for turn in transcript
      )
      
      model = genai.GenerativeModel("gemini-2.5-flash")
      summary = await asyncio.to_thread(
          model.generate_content,
          f"Summarize this conversation in 2-3 sentences. "
          f"Focus on what was decided, remembered, or actioned:\n\n{conversation_text}"
      )
      
      record = ConversationSummary(
          user_id=user_id,
          summary=summary.text
      )
      db.add(record)
      await db.commit()
      
      # transcript variable goes out of scope here — GC'd
  ```

**When triggered:**
- WebSocket `sessionEnd` event from phone → call `summarize_session()` as background task

**What is stored:** Only the text summary (~100–300 characters)  
**What is discarded:** Full transcript text (after summarization), audio (never stored to begin with)

**Testing 3.5:**
- Run a voice session with 3+ exchanges
- Send `sessionEnd` event
- Confirm summary row in Postgres
- Confirm summary is coherent (read it)
- Confirm no raw transcript stored

---

### Feature 3.6 — Error Handling + Logging

**Goal:** Cloud is production-ready. All errors are caught, logged, and returned as clean responses. No server crashes from bad input.

**Logging:**
```python
import logging
import structlog

# Structured JSON logging
structlog.configure(
    processors=[
        structlog.processors.TimeStamper(fmt="iso"),
        structlog.processors.JSONRenderer(),
    ]
)
logger = structlog.get_logger()

# Use throughout:
logger.info("voice_session_started", user_id=user_id, room=room_name)
logger.error("tool_call_failed", tool="control_home_device", error=str(e))
```

**Never log:**
- JWT tokens (even partial)
- Password hashes
- Audio transcripts verbatim (log "transcript received" not the text)
- JPEG bytes

**Global error handler (FastAPI):**
```python
@app.exception_handler(Exception)
async def generic_exception_handler(request: Request, exc: Exception):
    logger.error("unhandled_exception", path=request.url.path, error=str(exc))
    return JSONResponse({"error": "Internal server error"}, status_code=500)
```

**Testing 3.6:**
- Send malformed JSON over WebSocket → server returns error, does not crash
- Send expired JWT → 401 response
- Simulate Gemini API timeout → Gary says "Sorry, I'm having trouble right now"
- Check logs: confirm no tokens or transcripts appear in structured logs

---

### Feature 3.7 — LiveKit Agent Dispatch (Auto-start)

**Goal:** Gary agent auto-joins when a LiveKit room is created. No manual process needed.

**How LiveKit agent dispatch works:**
- Register the gary_agent worker with LiveKit Cloud
- When a room is created: LiveKit dispatches a job to the worker automatically
- Worker receives room name + metadata, joins the room

**Files:**
- `app/services/gary_agent.py` — update `entrypoint`:
  ```python
  async def entrypoint(ctx: JobContext):
      # Room metadata contains user_id (set when room was created)
      user_id = ctx.job.metadata  # passed from room creation
      
      db_session = get_db_session()
      ws_session = session_manager.get(user_id)
      
      tools = GaryTools(user_id=user_id, ws_session=ws_session, db=db_session)
      
      agent = VoiceAgent(
          vad=..., stt=..., llm=..., tts=...,
          fnc_ctx=tools,
      )
      agent.start(ctx.room)
  ```
- Deployment: run `python -m app.services.gary_agent start` as a separate Dockerfile service

**Testing 3.7:**
- Create LiveKit room from Android → confirm gary_agent logs "job received"
- Confirm Gary joins room within 2 seconds
- Confirm tools are available in the new session

---

## Phase 3 Integration Test

- [ ] `docker-compose up` starts API + worker + postgres + redis
- [ ] Register user, get JWT, connect WebSocket
- [ ] Start voice session → Gary joins → conversation works
- [ ] Say "turn off the kitchen light" → `homeAssistantCall` event received on phone
- [ ] Say "what am I looking at?" → `requestFrame` sent → Gemini Vision responds
- [ ] Say "remember that I drink black coffee" → memory stored in Postgres
- [ ] New voice session: "what do I drink?" → Gary recalls from memory
- [ ] End session → summary stored in Postgres, raw state cleared
- [ ] 10 minutes of idle → no memory leak, Redis TTL working

---

## Phase 3 Exit Criteria

1. All four Gary tools work: HA relay, vision, save_memory, lookup_memory
2. Context injection works: Gary uses calendar/location/objects data automatically
3. Session summaries stored in Postgres after every session end
4. No raw audio, video, or screen captures ever written to disk or DB
5. Structured logging working, no sensitive data in logs
6. Agent auto-dispatches via LiveKit (no manual process start)
7. Server handles bad input and tool failures gracefully (no crashes)
8. All API endpoints return proper status codes and error messages
