# Phase 8 — Launch Polish

**Timeline:** Month 8–10  
**Milestone:** Post-M5  
**Exit criteria:** App on Play Store (closed beta). Stripe billing active. Onboarding handles every edge case. Cloud deployed to production. First paying beta users.

---

## What This Phase Builds

Everything needed to hand the app to real users. Billing, production deployment, crash reporting, and the polish that turns a prototype into a product.

---

## Feature Breakdown

---

### Feature 8.1 — Google Play Billing (Subscriptions)

**Goal:** Users pay $24–29/month for Gary. In-app purchase using Google Play Billing Library v7.

**Plan names:**
- "Gary Monthly" — $24.99/month
- "Gary Annual" — $249/year (~$20.75/month, 2 months free)

**Files (new):**
- `billing/BillingManager.kt`
  ```kotlin
  class BillingManager @Inject constructor(
      private val context: Context,
      private val garyClient: GaryClient
  ) {
      private lateinit var billingClient: BillingClient
      
      fun initialize() {
          billingClient = BillingClient.newBuilder(context)
              .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
              .setListener { billingResult, purchases ->
                  if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
                      scope.launch { handlePurchases(purchases) }
                  }
              }
              .build()
          billingClient.startConnection(...)
      }
      
      suspend fun querySubscriptionStatus(): SubscriptionStatus {
          // Check active purchases for GARY_MONTHLY or GARY_ANNUAL product IDs
      }
      
      fun launchBillingFlow(activity: Activity, productId: String) {
          // Launch Play Store purchase flow
      }
      
      private suspend fun handlePurchases(purchases: List<Purchase>) {
          purchases.forEach { purchase ->
              if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                  // Acknowledge + send purchase token to backend for Stripe sync
                  billingClient.acknowledgePurchase(...)
                  garyClient.sendPurchaseToken(purchase.purchaseToken)
              }
          }
      }
  }
  ```
- `billing/BillingModule.kt` — Hilt module

**Cloud side — Stripe sync:**
- Android sends purchase token to cloud after successful Play purchase
- Cloud verifies token with Google Play Developer API
- Cloud creates/updates Stripe customer + subscription record
- Cloud updates `users.subscription_status = "active"` in Postgres

**Why Stripe + Play Billing?**
- Play Billing is required for Google Play (you must use it for in-app purchases on Android)
- Stripe handles subscription management, invoices, and future web billing
- Keep both in sync: Play = source of truth for Android users, Stripe for web

**Testing 8.1:**
- Set up products in Google Play Console (test track)
- Use test account to purchase subscription → confirm Postgres updated
- Subscription expires → Gary features locked until renewed
- Test the "restore purchases" flow (user reinstalls app)

---

### Feature 8.2 — Production Cloud Deployment

**Goal:** Cloud runs on a real server with a real domain. HTTPS. Database backups. Auto-restart.

**Provider decision** (choose one at deploy time):
- **Railway** — simplest, Docker native, auto-deploy from GitHub, Postgres included. ~$20-50/month to start.
- **Render** — similar to Railway, also Docker, free Postgres tier for dev.
- **Fly.io** — more control, multi-region, slightly more complex setup. Better for scale.

**Recommendation:** Start with Railway for speed.

**Deployment checklist:**
- [ ] Dockerfile builds cleanly: `docker build -t pinch-api .`
- [ ] docker-compose works locally: `docker-compose up`
- [ ] Environment variables set in Railway/Render dashboard (never commit .env)
- [ ] Postgres: production database provisioned, connection string in env
- [ ] Redis: production Redis provisioned
- [ ] Domain: `api.pinch.app` → points to Railway deployment
- [ ] HTTPS: SSL cert via Let's Encrypt (Railway handles automatically)
- [ ] Health check: `/health` endpoint returns 200 (Railway uses this for restart)
- [ ] gary_agent worker: deployed as separate Railway service with auto-restart

**Alembic migrations on deploy:**
```dockerfile
# In Dockerfile CMD or entrypoint script:
alembic upgrade head && uvicorn app.main:app --host 0.0.0.0 --port 8000
```

**Database backups:**
- Railway Postgres: enable daily backups (Railway Pro plan)
- Or: `pg_dump` cron job → upload to S3/Cloudflare R2

**Testing 8.2:**
- `curl https://api.pinch.app/health` → `{"status": "ok"}`
- Android app connects to production WebSocket
- Voice session works end-to-end on production
- Simulate server restart → gary_agent reconnects to LiveKit automatically

---

### Feature 8.3 — Crash Reporting + Analytics

**Goal:** Know when the app crashes in production. See which features are being used.

**Android — Firebase Crashlytics:**
```kotlin
// build.gradle.kts
implementation("com.google.firebase:firebase-crashlytics-ktx")
implementation("com.google.firebase:firebase-analytics-ktx")

// In GaryApplication.kt:
FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(true)

// Log custom events:
Firebase.analytics.logEvent("voice_session_started", null)
Firebase.analytics.logEvent("gesture_pinch", null)
Firebase.analytics.logEvent("ha_command_sent", bundleOf("service" to "light.turn_on"))
```

**What to log:**
- Voice session start/end + duration
- Gesture types (pinch, swipe, etc.) — no personal data
- HA command success/failure rate
- App crashes with stack trace

**What NOT to log:**
- Voice transcripts
- Calendar event titles
- Location coordinates (just "home"/"away")
- Any user-identifying information beyond user_id

**Cloud — structured logging:**
- Already set up in Phase 3 with structlog
- Add log aggregation: Railway shows logs in dashboard, or use Datadog/Sentry for production

**Testing 8.3:**
- Trigger a crash in debug build → confirm Crashlytics receives it
- Trigger 3 different analytics events → confirm in Firebase console
- Verify no personal data in any log event

---

### Feature 8.4 — Onboarding Edge Cases

**Goal:** Every edge case in onboarding handled. No dead ends. No state corruption.

**Edge cases to handle:**
1. **Permission denied mid-flow** → explain impact, offer "skip and continue"
2. **BLE not available** (Bluetooth off on phone) → "Please turn on Bluetooth" with link to settings
3. **Network error during account creation** → "Couldn't create account. Try again." with retry button
4. **Glasses not found in 30 seconds** → "Make sure glasses are charged and nearby. Retry | Skip"
5. **HA connection test fails** → "Couldn't connect. Check URL and that HA is running." with retry
6. **Wrong HA token** → "Authentication failed. Regenerate your token in HA profile settings."
7. **Onboarding interrupted** (user presses back/home) → resume from last completed step on next launch
8. **Account exists** during registration → "An account with this email exists. Sign in instead?"
9. **Duplicate glasses pairing** → "These glasses are already linked to another account. Continue? (replaces)"

**Resume logic:**
```kotlin
// In OnboardingViewModel:
fun getStartingStep(): OnboardingStep {
    return when {
        prefs.onboardingComplete -> OnboardingStep.Complete  // skip all
        prefs.accountCreated && !prefs.glassesPaired -> OnboardingStep.GlassesPairing
        prefs.glassesPaired && !prefs.smartHomeConfigured -> OnboardingStep.SmartHome
        else -> OnboardingStep.Welcome
    }
}
```

**Testing 8.4:**
- Walk through every error case on device
- Kill app at each step → relaunch → confirm resume works
- Complete onboarding on two different devices with same account

---

### Feature 8.5 — Rate Limiting + Fair Use Policy

**Goal:** Prevent abuse. Gary is $24.99/month — make sure heavy users don't blow up the cost model.

**Limits (enforced server-side):**
| Feature | Limit | Reason |
|---------|-------|--------|
| Voice sessions | 60 min/day | Deepgram + Cartesia cost |
| Gemini Vision calls | 50/day | API cost |
| HA commands | 500/day | Unusual if exceeded |
| WebSocket messages | 10/second | Prevent flooding |

**Implementation:**
```python
# In app/services/rate_limiter.py using Redis:
async def check_rate_limit(user_id: str, action: str, limit: int, window_seconds: int) -> bool:
    key = f"rate:{user_id}:{action}"
    current = await redis.incr(key)
    if current == 1:
        await redis.expire(key, window_seconds)
    return current <= limit

# Usage in gary_agent.py:
if not await rate_limiter.check_rate_limit(user_id, "vision_call", 50, 86400):
    return "I've looked at a lot of things today. Try again tomorrow."
```

**User feedback:** When limit hit → Gary speaks it ("I've used up my vision calls for today. More tomorrow."), not a silent failure.

**Testing 8.5:**
- Hit vision limit → Gary says "used up vision calls"
- Hit voice time limit → Gary says "time is up for today"
- Next day: limits reset (Redis TTL expires)

---

### Feature 8.6 — Play Store Submission

**Goal:** App listed on Google Play (closed beta initially).

**Checklist:**
- [ ] App icon: 512×512 PNG, no alpha (Play Store requirement)
- [ ] Feature graphic: 1024×500 PNG
- [ ] Screenshots: 2+ phone screenshots showing main screen, permissions, and Gary speaking
- [ ] Short description (80 chars max): "Gary — your ambient AI assistant for Pinch smart glasses"
- [ ] Full description (4000 chars): what it is, what it does, privacy policy link
- [ ] Privacy policy: hosted at `pinch.app/privacy` — MUST exist, required by Play
- [ ] Content rating: complete questionnaire (this app should be "Everyone")
- [ ] App signing: use Play App Signing (Google manages signing key)
- [ ] Release track: Internal testing first → then Closed testing (invite beta users) → Open testing → Production
- [ ] Data safety form: declare what data you collect (microphone: yes, location: yes, camera: yes, storage: no personal files)

**Build config for release:**
```kotlin
// build.gradle.kts
android {
    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
    }
}
```

**Privacy policy must state:**
- We do not store audio recordings
- We do not store camera footage
- We do not store screen captures
- We collect: email, device identifiers, conversation summaries (text only)
- Data deletion: available on request

**Testing 8.6:**
- Release build installs cleanly on a fresh device
- No debug logs in release build (`BuildConfig.DEBUG` gated)
- Play Store review: typical 1–3 day review for new apps

---

### Feature 8.7 — Gary System Prompt Final Tuning

**Goal:** Gary's personality is right. Concise, warm, useful. No over-explaining, no AI-speak.

**Final system prompt:**
```
You are Gary. You run on Pinch smart glasses — always on, ambient, and with the user.

Core rules:
- Respond in 1–3 sentences. Never longer, unless reading out a list.
- Natural speech only. No markdown, no bullet points, no headers.
- Don't say "I will" before doing something — just do it.
- Don't confirm trivial actions: turning a light off? Just do it and say "Done."
- Use user's memory to personalize responses.
- If something goes wrong, say so briefly and what you'll try next.
- Never mention being an AI unless directly asked.

Tone: quiet confidence. Like a knowledgeable person who's there when you need them and silent when you don't.

[User context injected automatically below]
```

**Testing 8.7:**
- Run through 20 representative interactions
- Check for: over-explaining, AI-speak ("Certainly!", "Absolutely!", "As an AI"), unnecessary verbosity
- Time Gary's responses: first word should come within 1.5 seconds for simple commands

---

## Phase 8 Integration Test (Launch Readiness)

- [ ] Fresh install from Play Store (internal test track) → full onboarding → Gary works
- [ ] Stripe subscription purchase → correct plan activated
- [ ] Voice session under limit → works normally
- [ ] Voice session over daily limit → graceful message
- [ ] App crashes < 0.5% of sessions (Crashlytics)
- [ ] Cloud uptime: 99%+ over 7-day test period
- [ ] 5 beta users running the system for a week with no critical bugs
- [ ] Privacy policy live at correct URL

---

## Phase 8 Exit Criteria

1. App submitted to Play Store internal test track and passes review
2. Stripe billing active: purchase → subscription activated → Postgres updated
3. Production cloud running on real domain with HTTPS
4. Crash rate < 0.5% of sessions (Crashlytics dashboard)
5. Rate limiting functional: all limits enforced server-side
6. Onboarding handles all edge cases without dead ends
7. Privacy policy live and accurate
8. 5+ beta users successfully using the product for 7+ days
9. Gary response latency P90 < 2s (measured in production)
